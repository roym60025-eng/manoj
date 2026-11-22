package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.ImageToPdfPage
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.ScanPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PdfGenerationEngine(private val context: Context) {

    fun resolvePageRect(
        sizeOption: PageSizeOption,
        orientation: PageOrientation,
        customWidthMm: Int = 210,
        customHeightMm: Int = 297
    ): PDRectangle {
        val baseW: Float
        val baseH: Float
        if (sizeOption == PageSizeOption.CUSTOM) {
            val mmToPt = 72f / 25.4f
            baseW = (customWidthMm.coerceIn(50, 1200)) * mmToPt
            baseH = (customHeightMm.coerceIn(50, 1800)) * mmToPt
        } else {
            baseW = sizeOption.widthPt.toFloat()
            baseH = sizeOption.heightPt.toFloat()
        }
        return if (orientation == PageOrientation.LANDSCAPE) {
            PDRectangle(max(baseW, baseH), min(baseW, baseH))
        } else {
            PDRectangle(min(baseW, baseH), max(baseW, baseH))
        }
    }

    suspend fun createPdfFromImages(
        pages: List<ImageToPdfPage>,
        config: ImageToPdfConfig,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "Select at least one image" }
        outputFile.parentFile?.mkdirs()

        val pageRect = resolvePageRect(
            config.pageSize,
            config.orientation,
            config.customWidthMm,
            config.customHeightMm
        )
        val marginPt = config.marginDp.coerceIn(0, 80).toFloat()
        val qualityFloat = (config.imageQuality.coerceIn(15, 100) / 100f)

        PDDocument().use { document ->
            val total = pages.size
            pages.forEachIndexed { index, pageItem ->
                val page = PDPage(pageRect)
                document.addPage(page)

                var bitmap = ImageProcessingHelper.decodeSampledBitmap(context, pageItem.uri, maxDimension = 2048)
                if (!pageItem.cropRect.isFull) {
                    bitmap = ImageProcessingHelper.cropBitmap(bitmap, pageItem.cropRect)
                }
                if (pageItem.rotationDegrees % 360 != 0) {
                    bitmap = ImageProcessingHelper.rotateBitmap(bitmap, pageItem.rotationDegrees)
                }

                // Ensure RGB_8888/white background for transparent PNGs/WEBPs before JPEG embed
                val flattened = flattenOnWhite(bitmap)
                if (flattened !== bitmap) bitmap.recycle()

                val pdImage = JPEGFactory.createFromImage(document, flattened, qualityFloat)
                val imgW = flattened.width.toFloat().coerceAtLeast(1f)
                val imgH = flattened.height.toFloat().coerceAtLeast(1f)
                flattened.recycle()

                val pageW = pageRect.width
                val pageH = pageRect.height
                val bottomReserved = if (config.addPageNumbers) 22f else 0f
                val availW = (pageW - marginPt * 2f).coerceAtLeast(20f)
                val availH = (pageH - marginPt * 2f - bottomReserved).coerceAtLeast(20f)

                val (drawW, drawH) = when (config.fitMode) {
                    ImageFitMode.CONTAIN -> {
                        val scale = min(availW / imgW, availH / imgH)
                        (imgW * scale) to (imgH * scale)
                    }
                    ImageFitMode.COVER -> {
                        val scale = max(availW / imgW, availH / imgH)
                        (imgW * scale) to (imgH * scale)
                    }
                    ImageFitMode.STRETCH -> availW to availH
                    ImageFitMode.ORIGINAL -> {
                        val ptW = imgW * 0.75f
                        val ptH = imgH * 0.75f
                        if (ptW > availW || ptH > availH) {
                            val scale = min(availW / ptW, availH / ptH)
                            (ptW * scale) to (ptH * scale)
                        } else {
                            ptW to ptH
                        }
                    }
                }

                val drawX = marginPt + (availW - drawW) / 2f
                val drawY = marginPt + bottomReserved + (availH - drawH) / 2f

                PDPageContentStream(document, page).use { cs ->
                    if (config.fitMode == ImageFitMode.COVER) {
                        cs.saveGraphicsState()
                        cs.addRect(marginPt, marginPt + bottomReserved, availW, availH)
                        cs.clip()
                        cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                        cs.restoreGraphicsState()
                    } else {
                        cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                    }

                    if (config.addPageNumbers) {
                        val label = "Page ${index + 1} of $total"
                        val fontSize = 10f
                        val textWidth = PDType1Font.HELVETICA.getStringWidth(label) / 1000f * fontSize
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, fontSize)
                        cs.setNonStrokingColor(0.35f, 0.38f, 0.45f)
                        cs.newLineAtOffset((pageW - textWidth) / 2f, 12f)
                        cs.showText(label)
                        cs.endText()
                    }
                }

                onProgress((index + 1).toFloat() / total.toFloat())
            }

            document.save(outputFile)
        }
        outputFile
    }

    suspend fun createPdfFromScannedPages(
        pages: List<ScanPage>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "Capture at least one page" }
        outputFile.parentFile?.mkdirs()

        val pageRect = PDRectangle.A4
        val margin = 16f

        PDDocument().use { document ->
            pages.forEachIndexed { index, scan ->
                val file = File(scan.processedFilePath)
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw IllegalStateException("Missing scanned page image")
                val flattened = flattenOnWhite(bitmap)
                if (flattened !== bitmap) bitmap.recycle()

                val useLandscape = flattened.width > flattened.height * 1.15f
                val rect = if (useLandscape) {
                    PDRectangle(pageRect.height, pageRect.width)
                } else {
                    pageRect
                }
                val page = PDPage(rect)
                document.addPage(page)

                val pdImage = JPEGFactory.createFromImage(document, flattened, 0.88f)
                val availW = rect.width - margin * 2f
                val availH = rect.height - margin * 2f
                val scale = min(availW / flattened.width, availH / flattened.height)
                val drawW = flattened.width * scale
                val drawH = flattened.height * scale
                val drawX = (rect.width - drawW) / 2f
                val drawY = (rect.height - drawH) / 2f
                flattened.recycle()

                PDPageContentStream(document, page).use { cs ->
                    cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                }
                onProgress((index + 1).toFloat() / pages.size.toFloat())
            }
            document.save(outputFile)
        }
        outputFile
    }

    suspend fun createBlankPdf(
        outputFile: File,
        pageCount: Int,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        withRuledLines: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val rect = resolvePageRect(pageSize, orientation)
        PDDocument().use { document ->
            val count = pageCount.coerceIn(1, 50)
            repeat(count) {
                val page = PDPage(rect)
                document.addPage(page)
                if (withRuledLines) {
                    PDPageContentStream(document, page).use { cs ->
                        cs.setStrokingColor(0.86f, 0.88f, 0.93f)
                        cs.setLineWidth(0.6f)
                        var y = rect.height - 60f
                        while (y > 48f) {
                            cs.moveTo(40f, y)
                            cs.lineTo(rect.width - 40f, y)
                            cs.stroke()
                            y -= 24f
                        }
                    }
                }
            }
            document.save(outputFile)
        }
        outputFile
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        return result
    }
}
