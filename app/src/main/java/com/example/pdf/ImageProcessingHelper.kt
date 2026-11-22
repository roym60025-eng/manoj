package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.example.data.model.AutoCropAnalysis
import com.example.data.model.AutoCropEnhancement
import com.example.data.model.NormalizedCropRect
import com.example.data.model.NormalizedQuad
import com.example.data.model.ScanFilter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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

    private data class LineFit(
        val slope: Float,
        val intercept: Float,
        val inlierRatio: Float
    )

    private data class Pt(val x: Float, val y: Float)

    /**
     * Detects true 4-corner document quadrilateral (including tilted & perspective-skewed sheets).
     */
    fun detectDocumentQuad(bitmap: Bitmap): NormalizedQuad {
        return analyzeDocumentBoundary(bitmap).first
    }

    fun detectAutoCropRect(bitmap: Bitmap): NormalizedCropRect {
        val (quad, detected) = analyzeDocumentBoundary(bitmap)
        if (!detected) return NormalizedCropRect()
        val left = min(quad.topLeftX, quad.bottomLeftX).coerceIn(0f, 0.85f)
        val top = min(quad.topLeftY, quad.topRightY).coerceIn(0f, 0.85f)
        val right = max(quad.topRightX, quad.bottomRightX).coerceIn(left + 0.1f, 1f)
        val bottom = max(quad.bottomLeftY, quad.bottomRightY).coerceIn(top + 0.1f, 1f)
        return NormalizedCropRect(left, top, right, bottom)
    }

    /**
     * Runs full auto-crop & straightening analysis and returns a newly allocated straightened Bitmap
     * along with metadata describing what perspective/skew corrections were applied.
     * Note: Does NOT recycle [source].
     */
    fun autoStraightenAndCrop(
        source: Bitmap,
        enhancement: AutoCropEnhancement = AutoCropEnhancement.ORIGINAL,
        trimUniformMargin: Boolean = true
    ): Pair<Bitmap, AutoCropAnalysis> {
        val (quad, boundaryDetected) = analyzeDocumentBoundary(source)

        val topSlopeAngle = (atan2(
            ((quad.topRightY - quad.topLeftY) * source.height).toDouble(),
            ((quad.topRightX - quad.topLeftX) * source.width).toDouble().coerceAtLeast(1.0)
        ) * 180.0 / PI).toFloat()

        var working: Bitmap
        var finalSkewAngle = 0f
        var anySkewFixed = false
        var anyBoundaryFixed = boundaryDetected

        if (boundaryDetected) {
            working = applyPerspectiveCorrection(source, quad)
            if (abs(topSlopeAngle) >= 0.8f) {
                finalSkewAngle = -topSlopeAngle
                anySkewFixed = true
            }
            // Refine any residual text/content skew remaining after quad warp
            val residualSkew = detectSkewAngle(working)
            if (abs(residualSkew) in 0.8f..16f) {
                val deskewed = rotateDeskewAndTrim(working, residualSkew)
                if (deskewed !== working) {
                    working.recycle()
                    working = deskewed
                }
                finalSkewAngle += residualSkew
                anySkewFixed = true
            }
        } else {
            val contentSkew = detectSkewAngle(source)
            if (abs(contentSkew) >= 0.8f) {
                working = rotateDeskewAndTrim(source, contentSkew)
                finalSkewAngle = contentSkew
                anySkewFixed = true
                anyBoundaryFixed = true
            } else {
                working = source.copy(Bitmap.Config.ARGB_8888, false)
            }
        }

        if (trimUniformMargin) {
            val trimRect = detectUniformBorderRect(working)
            if (!trimRect.isFull) {
                val trimmed = cropBitmap(working, trimRect)
                working = trimmed
                anyBoundaryFixed = true
            }
        }

        when (enhancement) {
            AutoCropEnhancement.ORIGINAL -> Unit
            AutoCropEnhancement.CLEAN_DOC -> {
                val cleaned = applyScanAdjustments(
                    source = working,
                    filter = ScanFilter.COLOR,
                    brightness = 14f,
                    contrast = 1.24f,
                    rotationDegrees = 0
                )
                working.recycle()
                working = cleaned
            }
            AutoCropEnhancement.CRISP_BW -> {
                val bw = applyScanAdjustments(
                    source = working,
                    filter = ScanFilter.BLACK_AND_WHITE,
                    brightness = 12f,
                    contrast = 1.15f,
                    rotationDegrees = 0
                )
                working.recycle()
                working = bw
            }
        }

        val analysis = AutoCropAnalysis(
            quad = quad,
            skewAngleDegrees = finalSkewAngle,
            boundaryDetected = anyBoundaryFixed,
            skewDetected = anySkewFixed
        )
        return working to analysis
    }

    fun analyzeDocumentBoundary(bitmap: Bitmap): Pair<NormalizedQuad, Boolean> {
        val targetW = 200
        val scale = targetW.toFloat() / bitmap.width.coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(48)
        val small = bitmap.scale(targetW, targetH)

        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small !== bitmap) small.recycle()

        val luma = FloatArray(pixels.size)
        val rArr = IntArray(pixels.size)
        val gArr = IntArray(pixels.size)
        val bArr = IntArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            rArr[i] = r
            gArr[i] = g
            bArr[i] = b
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Estimate outer border background color & interior document luminance
        val borderLumas = mutableListOf<Float>()
        val borderRs = mutableListOf<Int>()
        val borderGs = mutableListOf<Int>()
        val borderBs = mutableListOf<Int>()
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                if (x < 3 || x >= targetW - 3 || y < 3 || y >= targetH - 3) {
                    val idx = y * targetW + x
                    borderLumas.add(luma[idx])
                    borderRs.add(rArr[idx])
                    borderGs.add(gArr[idx])
                    borderBs.add(bArr[idx])
                }
            }
        }
        borderLumas.sort()
        borderRs.sort()
        borderGs.sort()
        borderBs.sort()
        val bgLuma = borderLumas[borderLumas.size / 2]
        val bgR = borderRs[borderRs.size / 2]
        val bgG = borderGs[borderGs.size / 2]
        val bgB = borderBs[borderBs.size / 2]

        val centerLumas = mutableListOf<Float>()
        for (y in (targetH * 0.3f).toInt() until (targetH * 0.7f).toInt() step 2) {
            for (x in (targetW * 0.3f).toInt() until (targetW * 0.7f).toInt() step 2) {
                centerLumas.add(luma[y * targetW + x])
            }
        }
        centerLumas.sort()
        val fgLuma = if (centerLumas.isNotEmpty()) centerLumas[centerLumas.size / 2] else bgLuma
        val bgContrast = abs(fgLuma - bgLuma)
        val hasDistinctBackground = bgContrast > 18f
        val bgDiffThreshold = (bgContrast * 0.42f).coerceIn(18f, 46f)
        val gradThreshold = 22f

        fun isBoundaryPixel(idx: Int): Boolean {
            if (hasDistinctBackground) {
                val colorDist = (abs(rArr[idx] - bgR) + abs(gArr[idx] - bgG) + abs(bArr[idx] - bgB)) / 3f
                if (abs(luma[idx] - bgLuma) > bgDiffThreshold || colorDist > bgDiffThreshold) {
                    return true
                }
            }
            return false
        }

        // Collect 4-side edge hits
        val leftPts = mutableListOf<Pt>()
        val rightPts = mutableListOf<Pt>()
        val topPts = mutableListOf<Pt>()
        val bottomPts = mutableListOf<Pt>()

        val yStart = (targetH * 0.08f).toInt()
        val yEnd = (targetH * 0.92f).toInt()
        val xHalfLeft = (targetW * 0.55f).toInt()
        val xHalfRight = (targetW * 0.45f).toInt()

        for (y in yStart until yEnd step 2) {
            for (x in 3 until xHalfLeft) {
                val idx = y * targetW + x
                val grad = abs(luma[idx + 1] - luma[idx - 2])
                if (grad > gradThreshold || isBoundaryPixel(idx)) {
                    leftPts.add(Pt(y.toFloat(), x.toFloat())) // x = m*y + c
                    break
                }
            }
            for (x in (targetW - 4) downTo xHalfRight) {
                val idx = y * targetW + x
                val grad = abs(luma[idx - 1] - luma[idx + 2])
                if (grad > gradThreshold || isBoundaryPixel(idx)) {
                    rightPts.add(Pt(y.toFloat(), x.toFloat())) // x = m*y + c
                    break
                }
            }
        }

        val xStart = (targetW * 0.08f).toInt()
        val xEnd = (targetW * 0.92f).toInt()
        val yHalfTop = (targetH * 0.55f).toInt()
        val yHalfBottom = (targetH * 0.45f).toInt()

        for (x in xStart until xEnd step 2) {
            for (y in 3 until yHalfTop) {
                val idx = y * targetW + x
                val grad = abs(luma[(y + 1) * targetW + x] - luma[(y - 2) * targetW + x])
                if (grad > gradThreshold || isBoundaryPixel(idx)) {
                    topPts.add(Pt(x.toFloat(), y.toFloat())) // y = m*x + c
                    break
                }
            }
            for (y in (targetH - 4) downTo yHalfBottom) {
                val idx = y * targetW + x
                val grad = abs(luma[(y - 1) * targetW + x] - luma[(y + 2) * targetW + x])
                if (grad > gradThreshold || isBoundaryPixel(idx)) {
                    bottomPts.add(Pt(x.toFloat(), y.toFloat())) // y = m*x + c
                    break
                }
            }
        }

        // Fit 4 RANSAC lines
        val leftLine = fitRobustLine(leftPts, tolerance = 3.2f)
        val rightLine = fitRobustLine(rightPts, tolerance = 3.2f)
        val topLine = fitRobustLine(topPts, tolerance = 3.2f)
        val bottomLine = fitRobustLine(bottomPts, tolerance = 3.2f)

        var rayQuad: NormalizedQuad? = null
        var rayScore = 0f

        if (leftLine != null && rightLine != null && topLine != null && bottomLine != null) {
            val tl = intersectHV(topLine, leftLine, targetW, targetH)
            val tr = intersectHV(topLine, rightLine, targetW, targetH)
            val br = intersectHV(bottomLine, rightLine, targetW, targetH)
            val bl = intersectHV(bottomLine, leftLine, targetW, targetH)
            if (tl != null && tr != null && br != null && bl != null) {
                val candidate = NormalizedQuad(
                    topLeftX = (tl.x / targetW).coerceIn(0.005f, 0.995f),
                    topLeftY = (tl.y / targetH).coerceIn(0.005f, 0.995f),
                    topRightX = (tr.x / targetW).coerceIn(0.005f, 0.995f),
                    topRightY = (tr.y / targetH).coerceIn(0.005f, 0.995f),
                    bottomRightX = (br.x / targetW).coerceIn(0.005f, 0.995f),
                    bottomRightY = (br.y / targetH).coerceIn(0.005f, 0.995f),
                    bottomLeftX = (bl.x / targetW).coerceIn(0.005f, 0.995f),
                    bottomLeftY = (bl.y / targetH).coerceIn(0.005f, 0.995f)
                )
                if (isValidQuad(candidate)) {
                    rayQuad = candidate
                    rayScore = (leftLine.inlierRatio + rightLine.inlierRatio +
                        topLine.inlierRatio + bottomLine.inlierRatio) / 4f
                }
            }
        }

        // Pass B: Minimum-area rotated rectangle from contrasting foreground pixels
        var rotatedQuad: NormalizedQuad? = null
        if (hasDistinctBackground) {
            rotatedQuad = fitMinimumAreaRotatedQuad(
                targetW, targetH, luma, rArr, gArr, bArr,
                bgLuma, bgR, bgG, bgB, bgDiffThreshold
            )
        }

        val chosenQuad = when {
            rayQuad != null && rayScore >= 0.45f && isMeaningfulQuad(rayQuad) -> rayQuad
            rotatedQuad != null && isMeaningfulQuad(rotatedQuad) -> rotatedQuad
            rayQuad != null && isMeaningfulQuad(rayQuad) -> rayQuad
            else -> null
        }

        return if (chosenQuad != null) {
            chosenQuad to true
        } else {
            NormalizedQuad(
                topLeftX = 0.02f, topLeftY = 0.02f,
                topRightX = 0.98f, topRightY = 0.02f,
                bottomRightX = 0.98f, bottomRightY = 0.98f,
                bottomLeftX = 0.02f, bottomLeftY = 0.98f
            ) to false
        }
    }

    private fun fitRobustLine(points: List<Pt>, tolerance: Float): LineFit? {
        if (points.size < 4) return null
        val maxSamples = min(points.size, 24)
        val step = (points.size.toFloat() / maxSamples).coerceAtLeast(1f)
        val sampled = (0 until maxSamples).map { i ->
            points[(i * step).toInt().coerceAtMost(points.lastIndex)]
        }

        var bestSlope = 0f
        var bestIntercept = 0f
        var bestCount = -1

        for (i in sampled.indices) {
            for (j in i + 1 until sampled.size) {
                val p1 = sampled[i]
                val p2 = sampled[j]
                val dx = p2.x - p1.x
                if (abs(dx) < 6f) continue
                val m = (p2.y - p1.y) / dx
                if (abs(m) > 1.25f) continue
                val c = p1.y - m * p1.x

                var count = 0
                for (p in points) {
                    val err = abs(p.y - (m * p.x + c))
                    if (err <= tolerance) count++
                }
                if (count > bestCount) {
                    bestCount = count
                    bestSlope = m
                    bestIntercept = c
                }
            }
        }

        if (bestCount < max(4, (points.size * 0.25f).toInt())) return null

        // Refine using least-squares on inliers
        val inliers = points.filter { abs(it.y - (bestSlope * it.x + bestIntercept)) <= tolerance }
        if (inliers.size < 4) return null
        var sumX = 0.0
        var sumY = 0.0
        for (p in inliers) {
            sumX += p.x
            sumY += p.y
        }
        val meanX = sumX / inliers.size
        val meanY = sumY / inliers.size
        var num = 0.0
        var den = 0.0
        for (p in inliers) {
            val dx = p.x - meanX
            val dy = p.y - meanY
            num += dx * dy
            den += dx * dx
        }
        val finalSlope = if (den > 1e-4) (num / den).toFloat() else bestSlope
        if (abs(finalSlope) > 1.25f) return null
        val finalIntercept = (meanY - finalSlope * meanX).toFloat()
        return LineFit(finalSlope, finalIntercept, inliers.size.toFloat() / points.size)
    }

    private fun intersectHV(hLine: LineFit, vLine: LineFit, w: Int, h: Int): Pt? {
        // hLine: y = mH * x + cH
        // vLine: x = mV * y + cV
        val denom = 1f - vLine.slope * hLine.slope
        if (abs(denom) < 0.1f) return null
        val x = (vLine.slope * hLine.intercept + vLine.intercept) / denom
        val y = hLine.slope * x + hLine.intercept
        if (x < -w * 0.12f || x > w * 1.12f || y < -h * 0.12f || y > h * 1.12f) return null
        return Pt(x, y)
    }

    private fun fitMinimumAreaRotatedQuad(
        w: Int,
        h: Int,
        luma: FloatArray,
        rArr: IntArray,
        gArr: IntArray,
        bArr: IntArray,
        bgLuma: Float,
        bgR: Int,
        bgG: Int,
        bgB: Int,
        threshold: Float
    ): NormalizedQuad? {
        val fgPoints = mutableListOf<Pt>()
        for (y in 4 until h - 4 step 2) {
            for (x in 4 until w - 4 step 2) {
                val idx = y * w + x
                val cDist = (abs(rArr[idx] - bgR) + abs(gArr[idx] - bgG) + abs(bArr[idx] - bgB)) / 3f
                if (abs(luma[idx] - bgLuma) > threshold || cDist > threshold) {
                    fgPoints.add(Pt(x.toFloat(), y.toFloat()))
                }
            }
        }
        val maxSamples = ((w - 8) / 2) * ((h - 8) / 2)
        if (fgPoints.size < maxSamples * 0.12f || fgPoints.size > maxSamples * 0.90f) {
            return null
        }

        val cx = w / 2f
        val cy = h / 2f

        fun evalBounds(thetaDeg: Float): FloatArray {
            val rad = Math.toRadians(thetaDeg.toDouble())
            val cs = cos(rad).toFloat()
            val sn = sin(rad).toFloat()
            val us = FloatArray(fgPoints.size)
            val vs = FloatArray(fgPoints.size)
            for (i in fgPoints.indices) {
                val px = fgPoints[i].x - cx
                val py = fgPoints[i].y - cy
                us[i] = px * cs + py * sn
                vs[i] = -px * sn + py * cs
            }
            us.sort()
            vs.sort()
            val loIdx = (fgPoints.size * 0.01f).toInt()
            val hiIdx = (fgPoints.size * 0.99f).toInt().coerceAtMost(fgPoints.lastIndex)
            val uMin = us[loIdx]
            val uMax = us[hiIdx]
            val vMin = vs[loIdx]
            val vMax = vs[hiIdx]
            val area = max(1f, uMax - uMin) * max(1f, vMax - vMin)
            return floatArrayOf(area, uMin, uMax, vMin, vMax)
        }

        var bestAngle = 0f
        var bestScore = Float.MAX_VALUE
        var bestStats = FloatArray(5)

        var angle = -44f
        while (angle <= 44f) {
            val stats = evalBounds(angle)
            val penalized = stats[0] * (1f + 0.0008f * abs(angle))
            if (penalized < bestScore) {
                bestScore = penalized
                bestAngle = angle
                bestStats = stats
            }
            angle += 2f
        }

        // Fine search around bestAngle
        var fine = bestAngle - 2.0f
        while (fine <= bestAngle + 2.0f) {
            val stats = evalBounds(fine)
            val penalized = stats[0] * (1f + 0.0008f * abs(fine))
            if (penalized < bestScore) {
                bestScore = penalized
                bestAngle = fine
                bestStats = stats
            }
            fine += 0.25f
        }

        val uMin = bestStats[1]
        val uMax = bestStats[2]
        val vMin = bestStats[3]
        val vMax = bestStats[4]

        val rad = Math.toRadians(bestAngle.toDouble())
        val cs = cos(rad).toFloat()
        val sn = sin(rad).toFloat()

        fun toImg(u: Float, v: Float): Pt {
            val x = cx + u * cs - v * sn
            val y = cy + u * sn + v * cs
            return Pt((x / w).coerceIn(0.005f, 0.995f), (y / h).coerceIn(0.005f, 0.995f))
        }

        val tl = toImg(uMin, vMin)
        val tr = toImg(uMax, vMin)
        val br = toImg(uMax, vMax)
        val bl = toImg(uMin, vMax)

        val quad = NormalizedQuad(
            topLeftX = tl.x, topLeftY = tl.y,
            topRightX = tr.x, topRightY = tr.y,
            bottomRightX = br.x, bottomRightY = br.y,
            bottomLeftX = bl.x, bottomLeftY = bl.y
        )
        return if (isValidQuad(quad)) quad else null
    }

    private fun isValidQuad(q: NormalizedQuad): Boolean {
        val pts = listOf(
            Pt(q.topLeftX, q.topLeftY),
            Pt(q.topRightX, q.topRightY),
            Pt(q.bottomRightX, q.bottomRightY),
            Pt(q.bottomLeftX, q.bottomLeftY)
        )
        val topW = hypot((pts[1].x - pts[0].x).toDouble(), (pts[1].y - pts[0].y).toDouble())
        val bottomW = hypot((pts[2].x - pts[3].x).toDouble(), (pts[2].y - pts[3].y).toDouble())
        val leftH = hypot((pts[3].x - pts[0].x).toDouble(), (pts[3].y - pts[0].y).toDouble())
        val rightH = hypot((pts[2].x - pts[1].x).toDouble(), (pts[2].y - pts[1].y).toDouble())
        if (topW < 0.18 || bottomW < 0.18 || leftH < 0.18 || rightH < 0.18) return false

        // Convexity check via cross product sign consistency
        var sign = 0f
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-4f) return false
            if (sign == 0f) sign = cross else if (cross * sign <= 0f) return false
        }

        // Shoelace area check
        var area = 0f
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            area += (a.x * b.y - b.x * a.y)
        }
        return abs(area) * 0.5f >= 0.14f
    }

    private fun isMeaningfulQuad(q: NormalizedQuad): Boolean {
        val hasMargin = q.topLeftX > 0.035f || q.topLeftY > 0.035f ||
            q.topRightX < 0.965f || q.bottomRightY < 0.965f
        val isSlanted = abs(q.topRightY - q.topLeftY) > 0.012f ||
            abs(q.bottomRightY - q.bottomLeftY) > 0.012f ||
            abs(q.bottomLeftX - q.topLeftX) > 0.012f ||
            abs(q.bottomRightX - q.topRightX) > 0.012f
        return hasMargin || isSlanted
    }

    /**
     * Detects text/line skew angle in degrees (-20..+20). Positive means rotate clockwise to deskew.
     */
    fun detectSkewAngle(bitmap: Bitmap): Float {
        val targetW = 180
        val scale = targetW.toFloat() / bitmap.width.coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(48)
        val small = bitmap.scale(targetW, targetH)

        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small !== bitmap) small.recycle()

        val luma = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            luma[i] = 0.299f * ((c shr 16) and 0xFF) +
                0.587f * ((c shr 8) and 0xFF) +
                0.114f * (c and 0xFF)
        }

        val edgePts = mutableListOf<Pt>()
        val xLo = (targetW * 0.09f).toInt()
        val xHi = (targetW * 0.91f).toInt()
        val yLo = (targetH * 0.09f).toInt()
        val yHi = (targetH * 0.91f).toInt()
        val cx = targetW / 2f
        val cy = targetH / 2f

        for (y in yLo until yHi) {
            for (x in xLo until xHi) {
                val idx = y * targetW + x
                val gx = abs(luma[idx + 1] - luma[idx - 1])
                val gy = abs(luma[idx + targetW] - luma[idx - targetW])
                if (gy * 1.4f + gx * 0.5f > 32f) {
                    edgePts.add(Pt(x - cx, y - cy))
                }
            }
        }
        if (edgePts.size < 30) return 0f

        val diag = hypot(targetW.toDouble(), targetH.toDouble()).toInt() + 4
        val hist = IntArray(diag)

        fun scoreAngle(deg: Float): Double {
            hist.fill(0)
            val rad = Math.toRadians(deg.toDouble())
            val cs = cos(rad).toFloat()
            val sn = sin(rad).toFloat()
            val halfDiag = diag / 2f
            for (p in edgePts) {
                val yRot = (p.x * sn + p.y * cs + halfDiag).roundToInt()
                if (yRot in 0 until diag) {
                    hist[yRot]++
                }
            }
            var energy = 0.0
            for (i in 0 until diag - 1) {
                val diff = (hist[i + 1] - hist[i]).toDouble()
                energy += diff * diff
            }
            return energy
        }

        val zeroScore = scoreAngle(0f).coerceAtLeast(1.0)
        var bestAngle = 0f
        var bestScore = zeroScore

        var deg = -20f
        while (deg <= 20f) {
            val s = scoreAngle(deg)
            if (s > bestScore) {
                bestScore = s
                bestAngle = deg
            }
            deg += 1f
        }

        var fine = (bestAngle - 1.5f).coerceAtLeast(-22f)
        val fineEnd = (bestAngle + 1.5f).coerceAtMost(22f)
        while (fine <= fineEnd) {
            val s = scoreAngle(fine)
            if (s > bestScore) {
                bestScore = s
                bestAngle = fine
            }
            fine += 0.25f
        }

        return if (abs(bestAngle) >= 0.8f && bestScore > zeroScore * 1.12) {
            bestAngle
        } else {
            0f
        }
    }

    private fun rotateDeskewAndTrim(source: Bitmap, angleDegrees: Float): Bitmap {
        if (abs(angleDegrees) < 0.4f) return source.copy(Bitmap.Config.ARGB_8888, false)
        val w = source.width
        val h = source.height

        // Sample interior perimeter median color to fill deskew corners seamlessly
        val fillColor = estimatePaperBackgroundColor(source)

        val rad = Math.toRadians(abs(angleDegrees).toDouble())
        val cs = cos(rad)
        val sn = sin(rad)
        // Compute inscribed clean rectangle so corner fill wedges are minimized/cropped
        val newW = (w * cs - h * sn).roundToInt()
        val newH = (h * cs - w * sn).roundToInt()
        val useInscribedCrop = newW > w * 0.55 && newH > h * 0.55

        val outW = if (useInscribedCrop) newW else w
        val outH = if (useInscribedCrop) newH else h

        val output = createBitmap(outW, outH)
        val canvas = Canvas(output)
        canvas.drawColor(fillColor)

        val matrix = Matrix().apply {
            postTranslate(-w / 2f, -h / 2f)
            postRotate(angleDegrees)
            postTranslate(outW / 2f, outH / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    private fun estimatePaperBackgroundColor(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val samples = listOf(
            bitmap[(w * 0.15f).toInt(), (h * 0.08f).toInt()],
            bitmap[(w * 0.85f).toInt(), (h * 0.08f).toInt()],
            bitmap[(w * 0.15f).toInt(), (h * 0.92f).toInt()],
            bitmap[(w * 0.85f).toInt(), (h * 0.92f).toInt()]
        )
        val r = samples.map { Color.red(it) }.sorted()[2]
        val g = samples.map { Color.green(it) }.sorted()[2]
        val b = samples.map { Color.blue(it) }.sorted()[2]
        return Color.rgb(r, g, b)
    }

    private fun detectUniformBorderRect(bitmap: Bitmap): NormalizedCropRect {
        val targetW = 120
        val scale = targetW.toFloat() / bitmap.width.coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(36)
        val small = bitmap.scale(targetW, targetH)
        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small !== bitmap) small.recycle()

        fun isDarkOrFlatBorderCol(x: Int, refR: Int, refG: Int, refB: Int): Boolean {
            var diffs = 0
            for (y in 4 until targetH - 4 step 2) {
                val c = pixels[y * targetW + x]
                val d = abs(((c shr 16) and 0xFF) - refR) +
                    abs(((c shr 8) and 0xFF) - refG) +
                    abs((c and 0xFF) - refB)
                if (d > 48) diffs++
            }
            return diffs <= 2
        }

        fun isDarkOrFlatBorderRow(y: Int, refR: Int, refG: Int, refB: Int): Boolean {
            var diffs = 0
            for (x in 4 until targetW - 4 step 2) {
                val c = pixels[y * targetW + x]
                val d = abs(((c shr 16) and 0xFF) - refR) +
                    abs(((c shr 8) and 0xFF) - refG) +
                    abs((c and 0xFF) - refB)
                if (d > 48) diffs++
            }
            return diffs <= 2
        }

        val tl = pixels[0]
        val refLuma = 0.299f * Color.red(tl) + 0.587f * Color.green(tl) + 0.114f * Color.blue(tl)
        // Only trim dark scanner/photo borders (< 85 luma) automatically so white paper margins aren't over-cropped
        if (refLuma > 85f) return NormalizedCropRect()

        var leftCol = 0
        while (leftCol < targetW * 0.25 && isDarkOrFlatBorderCol(leftCol, Color.red(tl), Color.green(tl), Color.blue(tl))) {
            leftCol++
        }
        val tr = pixels[targetW - 1]
        var rightCol = targetW - 1
        while (rightCol > targetW * 0.75 && isDarkOrFlatBorderCol(rightCol, Color.red(tr), Color.green(tr), Color.blue(tr))) {
            rightCol--
        }
        var topRow = 0
        while (topRow < targetH * 0.25 && isDarkOrFlatBorderRow(topRow, Color.red(tl), Color.green(tl), Color.blue(tl))) {
            topRow++
        }
        val bl = pixels[(targetH - 1) * targetW]
        var bottomRow = targetH - 1
        while (bottomRow > targetH * 0.75 && isDarkOrFlatBorderRow(bottomRow, Color.red(bl), Color.green(bl), Color.blue(bl))) {
            bottomRow--
        }

        val l = (leftCol.toFloat() / targetW).coerceIn(0f, 0.25f)
        val r = ((rightCol + 1).toFloat() / targetW).coerceIn(0.75f, 1f)
        val t = (topRow.toFloat() / targetH).coerceIn(0f, 0.25f)
        val b = ((bottomRow + 1).toFloat() / targetH).coerceIn(0.75f, 1f)

        return if (l > 0.025f || t > 0.025f || r < 0.975f || b < 0.975f) {
            NormalizedCropRect(l, t, r, b)
        } else {
            NormalizedCropRect()
        }
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
        if (!ok) return source.copy(Bitmap.Config.ARGB_8888, false)

        val output = createBitmap(outW, outH)
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
        var working = createBitmap(source.width, source.height)
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
