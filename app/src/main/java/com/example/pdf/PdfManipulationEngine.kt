package com.example.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.CompressionLevel
import com.example.data.model.ConvertedImageOutput
import com.example.data.model.ImageExportFormat
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfTextMatch
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class PdfManipulationEngine {

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(inputFiles.size >= 2) { "Select at least two PDF files to merge" }
        outputFile.parentFile?.mkdirs()
        val merger = PDFMergerUtility()
        inputFiles.forEachIndexed { index, file ->
            merger.addSource(file)
            onProgress(((index + 1).toFloat() / inputFiles.size) * 0.5f)
        }
        merger.destinationFileName = outputFile.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        onProgress(1f)
        outputFile
    }

    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        level: CompressionLevel,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        // First try compressing embedded image XObjects inside PDDocument to preserve text vectors
        val tempReencoded = File.createTempFile("compress_xobj_", ".pdf", outputFile.parentFile)
        var reencodedImagesCount = 0

        runCatching {
            PDDocument.load(inputFile).use { doc ->
                val pageCount = doc.numberOfPages.coerceAtLeast(1)
                for (i in 0 until pageCount) {
                    val page = doc.getPage(i)
                    val resources = page.resources
                    if (resources != null) {
                        for (name in resources.xObjectNames) {
                            val xObj = resources.getXObject(name)
                            if (xObj is PDImageXObject) {
                                val bmp = xObj.image
                                val scaledW = (bmp.width * level.scaleFactor).roundToInt().coerceAtLeast(32)
                                val scaledH = (bmp.height * level.scaleFactor).roundToInt().coerceAtLeast(32)
                                val scaled = if (scaledW < bmp.width || scaledH < bmp.height) {
                                    Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true).also {
                                        if (it !== bmp) bmp.recycle()
                                    }
                                } else {
                                    bmp
                                }
                                val compressedXObj = JPEGFactory.createFromImage(
                                    doc,
                                    scaled,
                                    level.jpegQuality / 100f
                                )
                                scaled.recycle()
                                resources.put(name, compressedXObj)
                                reencodedImagesCount++
                            }
                        }
                    }
                    onProgress(((i + 1).toFloat() / pageCount) * 0.6f)
                }
                doc.save(tempReencoded)
            }
        }

        // If the PDF had images re-encoded or is already smaller, return tempReencoded
        val originalSize = inputFile.length()
        if (tempReencoded.exists() && tempReencoded.length() in 1 until originalSize) {
            tempReencoded.copyTo(outputFile, overwrite = true)
            tempReencoded.delete()
            onProgress(1f)
            return@withContext outputFile
        }

        // Raster compression using PdfRenderer -> compact JPEG pages (wrapped safely for JVM/device resilience)
        val rasterOk = runCatching {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtLeast(1)
                    PDDocument().use { outDoc ->
                        val targetWidth = when (level) {
                            CompressionLevel.HIGH_QUALITY -> 1400
                            CompressionLevel.MEDIUM_QUALITY -> 1050
                            CompressionLevel.LOW_SIZE -> 720
                        }
                        for (i in 0 until count) {
                            renderer.openPage(i).use { page ->
                                val ptW = page.width.toFloat()
                                val ptH = page.height.toFloat()
                                val scale = targetWidth.toFloat() / ptW.coerceAtLeast(1f)
                                val bmpW = targetWidth.coerceAtLeast(100)
                                val bmpH = (ptH * scale).roundToInt().coerceAtLeast(100)
                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val outPage = PDPage(PDRectangle(ptW, ptH))
                                outDoc.addPage(outPage)
                                val jpeg = JPEGFactory.createFromImage(
                                    outDoc,
                                    bmp,
                                    level.jpegQuality / 100f
                                )
                                bmp.recycle()
                                PDPageContentStream(outDoc, outPage).use { cs ->
                                    cs.drawImage(jpeg, 0f, 0f, ptW, ptH)
                                }
                            }
                            onProgress(0.6f + ((i + 1).toFloat() / count) * 0.4f)
                        }
                        outDoc.save(outputFile)
                    }
                }
            }
        }.isSuccess

        if (!rasterOk) {
            if (tempReencoded.exists() && tempReencoded.length() > 0) {
                tempReencoded.copyTo(outputFile, overwrite = true)
            } else {
                inputFile.copyTo(outputFile, overwrite = true)
            }
        }
        tempReencoded.delete()
        onProgress(1f)
        outputFile
    }

    suspend fun convertPdfToImages(
        inputFile: File,
        selectedPageIndices: List<Int>,
        format: ImageExportFormat,
        quality: Int,
        outputDir: File,
        baseName: String,
        onProgress: (Float) -> Unit = {}
    ): ConvertedImageOutput = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val createdFiles = mutableListOf<File>()
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val indices = if (selectedPageIndices.isEmpty()) {
                    (0 until renderer.pageCount).toList()
                } else {
                    selectedPageIndices.filter { it in 0 until renderer.pageCount }.distinct().sorted()
                }
                require(indices.isNotEmpty()) { "No valid pages selected" }

                indices.forEachIndexed { idx, pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val targetW = 1440
                        val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
                        val targetH = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val outFile = File(
                            outputDir,
                            "${StorageHelper.sanitizeFileBase(baseName)}_page_${pageIndex + 1}.${format.extension}"
                        )
                        FileOutputStream(outFile).use { out ->
                            val compressFormat = if (format == ImageExportFormat.PNG) {
                                Bitmap.CompressFormat.PNG
                            } else {
                                Bitmap.CompressFormat.JPEG
                            }
                            bmp.compress(compressFormat, quality.coerceIn(10, 100), out)
                        }
                        bmp.recycle()
                        createdFiles.add(outFile)
                    }
                    onProgress((idx + 1).toFloat() / indices.size)
                }
            }
        }
        ConvertedImageOutput(createdFiles, outputDir)
    }

    suspend fun splitPdf(
        inputFile: File,
        splitEveryNPages: Int,
        outputDir: File,
        baseName: String
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val outputFiles = mutableListOf<File>()
        PDDocument.load(inputFile).use { doc ->
            val splitter = Splitter().apply {
                setSplitAtPage(splitEveryNPages.coerceAtLeast(1))
            }
            val parts = splitter.split(doc)
            parts.forEachIndexed { idx, partDoc ->
                partDoc.use {
                    val out = File(
                        outputDir,
                        "${StorageHelper.sanitizeFileBase(baseName)}_part_${idx + 1}.pdf"
                    )
                    var uniqueOut = out
                    var counter = 1
                    while (uniqueOut.exists()) {
                        uniqueOut = File(
                            outputDir,
                            "${StorageHelper.sanitizeFileBase(baseName)}_part_${idx + 1}_$counter.pdf"
                        )
                        counter++
                    }
                    it.save(uniqueOut)
                    outputFiles.add(uniqueOut)
                }
            }
        }
        outputFiles
    }

    suspend fun rotatePdf(
        inputFile: File,
        outputFile: File,
        degrees: Int,
        selectedPageIndices: Set<Int>
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                if (selectedPageIndices.isEmpty() || selectedPageIndices.contains(i)) {
                    val page = doc.getPage(i)
                    page.rotation = (page.rotation + degrees) % 360
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun protectPdf(
        inputFile: File,
        outputFile: File,
        password: String
    ): File = withContext(Dispatchers.IO) {
        require(password.isNotBlank()) { "Password cannot be blank" }
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { doc ->
            val ap = AccessPermission().apply {
                setCanPrint(true)
                setCanModify(false)
            }
            val policy = StandardProtectionPolicy(password, password, ap).apply {
                encryptionKeyLength = 128
                permissions = ap
            }
            doc.protect(policy)
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun unlockPdf(
        inputFile: File,
        outputFile: File,
        password: String
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun addWatermark(
        inputFile: File,
        outputFile: File,
        watermarkText: String,
        opacity: Float = 0.28f,
        fontSize: Float = 44f,
        angleDegrees: Double = 38.0
    ): File = withContext(Dispatchers.IO) {
        require(watermarkText.isNotBlank()) { "Watermark text cannot be blank" }
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { doc ->
            val font = PDType1Font.HELVETICA_BOLD
            val textWidth = font.getStringWidth(watermarkText) / 1000f * fontSize
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val box = page.mediaBox
                val cx = box.width / 2f
                val cy = box.height / 2f
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 0.85f)
                    }
                    cs.setGraphicsStateParameters(gs)
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(0.27f, 0.36f, 1.0f)
                    val rad = Math.toRadians(angleDegrees)
                    val rotateMatrix = Matrix.getRotateInstance(rad, cx, cy)
                    val offsetMatrix = Matrix.getTranslateInstance(-textWidth / 2f, -fontSize / 3f)
                    cs.setTextMatrix(Matrix.concatenate(rotateMatrix, offsetMatrix))
                    cs.showText(watermarkText)
                    cs.endText()
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun addPageNumbers(
        inputFile: File,
        outputFile: File,
        startNumber: Int = 1,
        atBottom: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { doc ->
            val total = doc.numberOfPages
            val font = PDType1Font.HELVETICA
            val fontSize = 11f
            for (i in 0 until total) {
                val page = doc.getPage(i)
                val box = page.mediaBox
                val label = "${i + startNumber} / ${total + startNumber - 1}"
                val textWidth = font.getStringWidth(label) / 1000f * fontSize
                val y = if (atBottom) 16f else (box.height - 24f)
                val x = (box.width - textWidth) / 2f
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(0.25f, 0.28f, 0.36f)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun extractPages(
        inputFile: File,
        outputFile: File,
        pageIndices: List<Int>
    ): File = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "Select at least one page to extract" }
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { src ->
            PDDocument().use { dst ->
                pageIndices.distinct().sorted().forEach { idx ->
                    if (idx in 0 until src.numberOfPages) {
                        dst.importPage(src.getPage(idx))
                    }
                }
                require(dst.numberOfPages > 0) { "No valid pages to extract" }
                dst.save(outputFile)
            }
        }
        outputFile
    }

    suspend fun deletePages(
        inputFile: File,
        outputFile: File,
        pageIndicesToDelete: Set<Int>
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { src ->
            val remaining = (0 until src.numberOfPages).filterNot { it in pageIndicesToDelete }
            require(remaining.isNotEmpty()) { "Cannot delete all pages from a PDF" }
            PDDocument().use { dst ->
                remaining.forEach { idx -> dst.importPage(src.getPage(idx)) }
                dst.save(outputFile)
            }
        }
        outputFile
    }

    suspend fun reorderPages(
        inputFile: File,
        outputFile: File,
        newOrderIndices: List<Int>
    ): File = withContext(Dispatchers.IO) {
        require(newOrderIndices.isNotEmpty()) { "Page order cannot be empty" }
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { src ->
            PDDocument().use { dst ->
                newOrderIndices.forEach { idx ->
                    if (idx in 0 until src.numberOfPages) {
                        dst.importPage(src.getPage(idx))
                    }
                }
                dst.save(outputFile)
            }
        }
        outputFile
    }

    suspend fun getPdfInfo(file: File, folderName: String): PdfDetailedInfo =
        withContext(Dispatchers.IO) {
            runCatching {
                PDDocument.load(file).use { doc ->
                    val info = doc.documentInformation
                    val firstBox = if (doc.numberOfPages > 0) doc.getPage(0).mediaBox else null
                    val dim = if (firstBox != null) {
                        "${firstBox.width.roundToInt()} × ${firstBox.height.roundToInt()} pt"
                    } else {
                        "Unknown"
                    }
                    PdfDetailedInfo(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        folderName = folderName,
                        fileSizeBytes = file.length(),
                        pageCount = doc.numberOfPages,
                        isEncrypted = doc.isEncrypted,
                        pdfVersion = "PDF ${doc.version}",
                        title = info?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                        author = info?.author?.takeIf { it.isNotBlank() } ?: "Not specified",
                        subject = info?.subject?.takeIf { it.isNotBlank() } ?: "Not specified",
                        creator = info?.creator?.takeIf { it.isNotBlank() } ?: "Smart PDF Maker",
                        producer = info?.producer?.takeIf { it.isNotBlank() } ?: "PDFBox Engine",
                        pageDimensions = dim
                    )
                }
            }.getOrElse {
                PdfDetailedInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    folderName = folderName,
                    fileSizeBytes = file.length(),
                    pageCount = 0,
                    isEncrypted = true,
                    pdfVersion = "Protected PDF",
                    title = file.nameWithoutExtension,
                    author = "Encrypted",
                    subject = "Password protected",
                    creator = "Unknown",
                    producer = "Unknown",
                    pageDimensions = "Locked"
                )
            }
        }

    suspend fun searchTextInPdf(file: File, query: String): List<PdfTextMatch> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || !file.exists()) return@withContext emptyList()
            val trimmed = query.trim()
            val matches = mutableListOf<PdfTextMatch>()
            runCatching {
                PDDocument.load(file).use { doc ->
                    if (doc.isEncrypted) return@use
                    val stripper = PDFTextStripper()
                    for (p in 1..doc.numberOfPages) {
                        stripper.startPage = p
                        stripper.endPage = p
                        val text = stripper.getText(doc) ?: continue
                        val idx = text.indexOf(trimmed, ignoreCase = true)
                        if (idx >= 0) {
                            val start = (idx - 35).coerceAtLeast(0)
                            val end = (idx + trimmed.length + 55).coerceAtMost(text.length)
                            val snippet = text.substring(start, end)
                                .replace("\n", " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            matches.add(PdfTextMatch(pageIndex = p - 1, snippet = "...$snippet..."))
                        }
                    }
                }
            }
            matches
        }
}
