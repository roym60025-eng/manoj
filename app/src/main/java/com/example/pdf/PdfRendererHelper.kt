package com.example.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRendererHelper(private val storageHelper: StorageHelper) {

    suspend fun getPageCount(pdfFile: File): Int = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext 0
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                        val width = targetWidthPx.coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun generateAndSaveThumbnail(pdfFile: File, docKey: String): String? =
        withContext(Dispatchers.IO) {
            val bmp = renderPageBitmap(pdfFile, 0, targetWidthPx = 240) ?: return@withContext null
            val thumbFile = File(storageHelper.thumbnailsDir, "thumb_${docKey}.jpg")
            runCatching {
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                bmp.recycle()
                thumbFile.absolutePath
            }.getOrElse {
                bmp.recycle()
                null
            }
        }
}
