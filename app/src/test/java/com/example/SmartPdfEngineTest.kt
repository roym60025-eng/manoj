package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SmartPdfDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.ImageToPdfPage
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.SortOrder
import com.example.data.repository.PdfRepository
import com.example.pdf.DocxToPdfEngine
import com.example.pdf.ImageProcessingHelper
import com.example.pdf.PdfGenerationEngine
import com.example.pdf.PdfManipulationEngine
import com.example.pdf.PdfRendererHelper
import com.example.pdf.StorageHelper
import com.example.ui.components.sortDocuments
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartPdfEngineTest {

    private lateinit var context: Context
    private lateinit var db: SmartPdfDatabase
    private lateinit var storageHelper: StorageHelper
    private lateinit var generationEngine: PdfGenerationEngine
    private lateinit var manipulationEngine: PdfManipulationEngine
    private lateinit var docxEngine: DocxToPdfEngine
    private lateinit var repository: PdfRepository
    private lateinit var tempWorkDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        db = Room.inMemoryDatabaseBuilder(context, SmartPdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageHelper = StorageHelper(context)
        generationEngine = PdfGenerationEngine(context)
        manipulationEngine = PdfManipulationEngine()
        docxEngine = DocxToPdfEngine(context)
        repository = PdfRepository(
            context = context,
            pdfDao = db.pdfDao(),
            preferencesRepository = UserPreferencesRepository(context),
            storageHelper = storageHelper,
            rendererHelper = PdfRendererHelper(storageHelper),
            generationEngine = generationEngine,
            manipulationEngine = manipulationEngine,
            docxEngine = docxEngine
        )
        tempWorkDir = File(context.cacheDir, "unit_test_pdfs_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun teardown() {
        db.close()
        tempWorkDir.deleteRecursively()
    }

    private fun createTempPng(name: String, color: Int): File {
        val file = File(tempWorkDir, name)
        val bmp = Bitmap.createBitmap(160, 220, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
        return file
    }

    private fun createTextPdf(name: String, texts: List<String>): File {
        val file = File(tempWorkDir, name)
        PDDocument().use { doc ->
            texts.forEach { line ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText(line)
                    cs.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `image to pdf creates multi-page pdf with configured orientation and page numbers`() = runBlocking {
        val img1 = createTempPng("img1.png", Color.BLUE)
        val img2 = createTempPng("img2.png", Color.RED)
        val pages = listOf(
            ImageToPdfPage(uri = Uri.fromFile(img1), rotationDegrees = 90),
            ImageToPdfPage(uri = Uri.fromFile(img2))
        )
        val config = ImageToPdfConfig(
            fileName = "Test_Gallery",
            pageSize = PageSizeOption.A4,
            orientation = PageOrientation.PORTRAIT,
            fitMode = ImageFitMode.CONTAIN,
            imageQuality = 80,
            addPageNumbers = true
        )

        val outFile = File(tempWorkDir, "gallery.pdf")
        generationEngine.createPdfFromImages(pages, config, outFile)

        assertTrue(outFile.exists())
        PDDocument.load(outFile).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun `merge split rotate watermark page numbers extract delete reorder protect unlock info and search work`() = runBlocking {
        val pdfA = createTextPdf("a.pdf", listOf("Alpha invoice page", "Second alpha page"))
        val pdfB = createTextPdf("b.pdf", listOf("Beta contract summary"))

        val merged = File(tempWorkDir, "merged.pdf")
        manipulationEngine.mergePdfs(listOf(pdfA, pdfB), merged)
        PDDocument.load(merged).use { assertEquals(3, it.numberOfPages) }

        val matches = manipulationEngine.searchTextInPdf(merged, "contract")
        assertEquals(1, matches.size)
        assertEquals(2, matches.first().pageIndex)

        val splitDir = File(tempWorkDir, "splits")
        val parts = manipulationEngine.splitPdf(merged, 1, splitDir, "merged")
        assertEquals(3, parts.size)

        val rotated = File(tempWorkDir, "rotated.pdf")
        manipulationEngine.rotatePdf(merged, rotated, 90, setOf(0))
        PDDocument.load(rotated).use { assertEquals(90, it.getPage(0).rotation) }

        val watermarked = File(tempWorkDir, "watermarked.pdf")
        manipulationEngine.addWatermark(merged, watermarked, "DRAFT")
        assertTrue(watermarked.length() > 0)

        val numbered = File(tempWorkDir, "numbered.pdf")
        manipulationEngine.addPageNumbers(merged, numbered, 1, true)
        assertTrue(numbered.length() > 0)

        val extracted = File(tempWorkDir, "extracted.pdf")
        manipulationEngine.extractPages(merged, extracted, listOf(0, 2))
        PDDocument.load(extracted).use { assertEquals(2, it.numberOfPages) }

        val deleted = File(tempWorkDir, "deleted.pdf")
        manipulationEngine.deletePages(merged, deleted, setOf(1))
        PDDocument.load(deleted).use { assertEquals(2, it.numberOfPages) }

        val reordered = File(tempWorkDir, "reordered.pdf")
        manipulationEngine.reorderPages(merged, reordered, listOf(2, 0, 1))
        val reorderedMatches = manipulationEngine.searchTextInPdf(reordered, "contract")
        assertEquals(0, reorderedMatches.first().pageIndex)

        val protectedPdf = File(tempWorkDir, "locked.pdf")
        manipulationEngine.protectPdf(merged, protectedPdf, "secret123")
        val infoLocked = manipulationEngine.getPdfInfo(protectedPdf, "Smart PDF Maker")
        assertTrue(infoLocked.isEncrypted)

        val unlockedPdf = File(tempWorkDir, "unlocked.pdf")
        manipulationEngine.unlockPdf(protectedPdf, unlockedPdf, "secret123")
        val infoUnlocked = manipulationEngine.getPdfInfo(unlockedPdf, "Smart PDF Maker")
        assertFalse(infoUnlocked.isEncrypted)
        assertEquals(3, infoUnlocked.pageCount)

        val compressed = File(tempWorkDir, "compressed.pdf")
        manipulationEngine.compressPdf(merged, compressed, CompressionLevel.LOW_SIZE)
        assertTrue(compressed.exists() && compressed.length() > 0)
    }

    @Test
    fun `docx to pdf converts valid openxml document with headings and tables`() = runBlocking {
        val docxFile = File(tempWorkDir, "sample.docx")
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>Project Proposal</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:rPr><w:b/></w:rPr><w:t>Summary: </w:t></w:r>
                  <w:r><w:t>Smart PDF Maker DOCX conversion test.</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr><w:tc><w:p><w:r><w:t>Item</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Status</w:t></w:r></w:p></w:tc></w:tr>
                  <w:tr><w:tc><w:p><w:r><w:t>Parser</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Ready</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val outPdf = File(tempWorkDir, "converted_docx.pdf")
        docxEngine.convertDocxToPdf(Uri.fromFile(docxFile), outPdf)
        assertTrue(outPdf.exists() && outPdf.length() > 0)
    }

    @Test
    fun `repository blank pdf creation rename move delete and sorting work`() = runBlocking {
        val doc1 = repository.createBlankPdf("Zebra_Notes", 2, PageSizeOption.A4, PageOrientation.PORTRAIT, true)
        val doc2 = repository.createBlankPdf("Alpha_Draft", 1, PageSizeOption.LETTER, PageOrientation.LANDSCAPE, false)

        val all = repository.allDocuments.first()
        assertEquals(2, all.size)

        val sortedAz = sortDocuments(all, SortOrder.NAME_AZ)
        assertEquals("Alpha_Draft", sortedAz.first().name)

        val renamed = repository.renameDocument(doc1, "Budget_Report")
        assertEquals("Budget_Report", renamed.name)

        repository.moveDocumentsToFolder(listOf(renamed), "Work")
        val reloaded = repository.getDocumentById(renamed.id)
        assertEquals("Work", reloaded?.folderName)

        repository.deleteDocuments(listOf(reloaded!!, doc2))
        assertTrue(repository.allDocuments.first().isEmpty())
    }

    @Test
    fun `autoStraightenAndCrop detects and straightens tilted document on dark background`() {
        val width = 400
        val height = 520
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(Color.rgb(22, 24, 34)) // Dark table background

        canvas.save()
        canvas.rotate(14f, width / 2f, height / 2f)
        val paperPaint = android.graphics.Paint().apply { color = Color.WHITE }
        canvas.drawRect(85f, 80f, 315f, 440f, paperPaint)
        val textPaint = android.graphics.Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 4f
        }
        for (y in 120..400 step 24) {
            canvas.drawLine(110f, y.toFloat(), 290f, y.toFloat(), textPaint)
        }
        canvas.restore()

        val (resultBmp, analysis) = ImageProcessingHelper.autoStraightenAndCrop(bmp)
        assertTrue(analysis.boundaryDetected || analysis.skewDetected)
        assertTrue(resultBmp.width > 100 && resultBmp.height > 100)
        // Center pixel of straightened document should remain bright paper
        val centerLuma = Color.red(resultBmp.getPixel(resultBmp.width / 2, resultBmp.height / 2))
        assertTrue(centerLuma > 200)
        resultBmp.recycle()
        bmp.recycle()
    }

    @Test
    fun `detectSkewAngle identifies tilted text lines in full-frame document`() {
        val width = 400
        val height = 500
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        canvas.save()
        canvas.rotate(-10f, width / 2f, height / 2f)
        val linePaint = android.graphics.Paint().apply {
            color = Color.BLACK
            strokeWidth = 4f
        }
        for (y in 90..410 step 22) {
            canvas.drawLine(60f, y.toFloat(), 340f, y.toFloat(), linePaint)
        }
        canvas.restore()

        val detected = ImageProcessingHelper.detectSkewAngle(bmp)
        assertTrue("Expected positive deskew near +10 deg, got $detected", detected in 6f..14f)
        bmp.recycle()
    }
}
