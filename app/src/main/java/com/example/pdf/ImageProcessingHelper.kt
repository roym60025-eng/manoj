package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.data.model.NormalizedCropRect
import com.example.data.model.NormalizedQuad
import com.example.data.model.ScanFilter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessingHelper {

    fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var sampleSize = 1
        val maxSide = max(options.outWidth, options.outHeight)
        if (maxSide > maxDimension && maxDimension > 0) {
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image")

        val exifRotation = readExifRotation(context, uri)
        return if (exifRotation != 0) {
            rotateBitmap(rawBitmap, exifRotation)
        } else {
            rawBitmap
        }
    }

    fun decodeFileBitmap(file: File, maxDimension: Int = 2048): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var sampleSize = 1
        val maxSide = max(options.outWidth, options.outHeight)
        if (maxSide > maxDimension && maxDimension > 0) {
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Failed to decode file bitmap")

        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        val rotation = when (exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return if (rotation != 0) rotateBitmap(bitmap, rotation) else bitmap
    }

    private fun readExifRotation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        return rotated
    }

    fun cropBitmap(source: Bitmap, rect: NormalizedCropRect): Bitmap {
        if (rect.isFull) return source
        val left = (rect.left.coerceIn(0f, 0.95f) * source.width).roundToInt()
        val top = (rect.top.coerceIn(0f, 0.95f) * source.height).roundToInt()
        val right = (rect.right.coerceIn(rect.left + 0.05f, 1f) * source.width).roundToInt()
        val bottom = (rect.bottom.coerceIn(rect.top + 0.05f, 1f) * source.height).roundToInt()
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(source, left, top, w, h)
        if (cropped != source) source.recycle()
        return cropped
    }

    /**
     * Heuristic document boundary detection using luminance gradient scanning on a downscaled preview.
     */
    fun detectDocumentQuad(bitmap: Bitmap): NormalizedQuad {
        val targetW = 160
        val scale = targetW.toFloat() / bitmap.width.coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(40)
        val small = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        val luma = FloatArray(targetW * targetH)
        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small != bitmap) small.recycle()

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Scan inward from each edge for strong contrast transition
        var minX = targetW * 0.08f
        var maxX = targetW * 0.92f
        var minY = targetH * 0.08f
        var maxY = targetH * 0.92f

        val threshold = 24f
        val yMidStart = (targetH * 0.2f).toInt()
        val yMidEnd = (targetH * 0.8f).toInt()
        val leftHits = mutableListOf<Int>()
        val rightHits = mutableListOf<Int>()

        for (y in yMidStart until yMidEnd step 4) {
            for (x in 4 until targetW / 2) {
                if (abs(luma[y * targetW + x] - luma[y * targetW + x - 2]) > threshold) {
                    leftHits.add(x)
                    break
                }
            }
            for (x in targetW - 5 downTo targetW / 2) {
                if (abs(luma[y * targetW + x] - luma[y * targetW + x + 2]) > threshold) {
                    rightHits.add(x)
                    break
                }
            }
        }

        val xMidStart = (targetW * 0.2f).toInt()
        val xMidEnd = (targetW * 0.8f).toInt()
        val topHits = mutableListOf<Int>()
        val bottomHits = mutableListOf<Int>()

        for (x in xMidStart until xMidEnd step 4) {
            for (y in 4 until targetH / 2) {
                if (abs(luma[y * targetW + x] - luma[(y - 2) * targetW + x]) > threshold) {
                    topHits.add(y)
                    break
                }
            }
            for (y in targetH - 5 downTo targetH / 2) {
                if (abs(luma[y * targetW + x] - luma[(y + 2) * targetW + x]) > threshold) {
                    bottomHits.add(y)
                    break
                }
            }
        }

        if (leftHits.size >= 4) minX = leftHits.sorted()[leftHits.size / 2].toFloat()
        if (rightHits.size >= 4) maxX = rightHits.sorted()[rightHits.size / 2].toFloat()
        if (topHits.size >= 4) minY = topHits.sorted()[topHits.size / 2].toFloat()
        if (bottomHits.size >= 4) maxY = bottomHits.sorted()[bottomHits.size / 2].toFloat()

        if (maxX - minX < targetW * 0.25f) {
            minX = targetW * 0.06f
            maxX = targetW * 0.94f
        }
        if (maxY - minY < targetH * 0.25f) {
            minY = targetH * 0.08f
            maxY = targetH * 0.92f
        }

        val lx = (minX / targetW).coerceIn(0.02f, 0.4f)
        val rx = (maxX / targetW).coerceIn(0.6f, 0.98f)
        val ty = (minY / targetH).coerceIn(0.02f, 0.4f)
        val by = (maxY / targetH).coerceIn(0.6f, 0.98f)

        return NormalizedQuad(
            topLeftX = lx, topLeftY = ty,
            topRightX = rx, topRightY = ty,
            bottomRightX = rx, bottomRightY = by,
            bottomLeftX = lx, bottomLeftY = by
        )
    }

    /**
     * Real perspective correction using Android Matrix.setPolyToPoly from 4 quad points to rectangular bounds.
     */
    fun applyPerspectiveCorrection(source: Bitmap, quad: NormalizedQuad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val src = floatArrayOf(
            quad.topLeftX * w, quad.topLeftY * h,
            quad.topRightX * w, quad.topRightY * h,
            quad.bottomRightX * w, quad.bottomRightY * h,
            quad.bottomLeftX * w, quad.bottomLeftY * h
        )

        val topWidth = hypot((src[2] - src[0]).toDouble(), (src[3] - src[1]).toDouble())
        val bottomWidth = hypot((src[4] - src[6]).toDouble(), (src[5] - src[7]).toDouble())
        val leftHeight = hypot((src[6] - src[0]).toDouble(), (src[7] - src[1]).toDouble())
        val rightHeight = hypot((src[4] - src[2]).toDouble(), (src[5] - src[3]).toDouble())

        val outW = max(topWidth, bottomWidth).roundToInt().coerceIn(64, 2400)
        val outH = max(leftHeight, rightHeight).roundToInt().coerceIn(64, 3200)

        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(src, 0, dst, 0, 4)
        if (!ok) return source

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    /**
     * Applies ScanFilter (Color / Grayscale / B&W), brightness (-100..100), and contrast (0.5..2.0).
     */
    fun applyScanAdjustments(
        source: Bitmap,
        filter: ScanFilter,
        brightness: Float,
        contrast: Float,
        rotationDegrees: Int
    ): Bitmap {
        var working = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(working)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        when (filter) {
            ScanFilter.COLOR -> Unit
            ScanFilter.GRAYSCALE -> cm.setSaturation(0f)
            ScanFilter.BLACK_AND_WHITE -> cm.setSaturation(0f)
        }

        val effectiveContrast = if (filter == ScanFilter.BLACK_AND_WHITE) {
            (contrast * 1.9f).coerceIn(0.8f, 3.2f)
        } else {
            contrast.coerceIn(0.5f, 2.2f)
        }

        val translate = (-0.5f * effectiveContrast + 0.5f) * 255f + brightness
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                effectiveContrast, 0f, 0f, 0f, translate,
                0f, effectiveContrast, 0f, 0f, translate,
                0f, 0f, effectiveContrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bcMatrix)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (filter == ScanFilter.BLACK_AND_WHITE) {
            val pixels = IntArray(working.width * working.height)
            working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val lum = (p shr 16) and 0xFF
                pixels[i] = if (lum > 140) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
            }
            working.setPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
        }

        if (rotationDegrees % 360 != 0) {
            working = rotateBitmap(working, rotationDegrees)
        }
        return working
    }

    fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int = 90) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val format = if (file.extension.equals("png", true)) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(format, quality.coerceIn(10, 100), out)
        }
    }
}
