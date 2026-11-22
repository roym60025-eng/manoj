package com.example.data.model

import android.net.Uri
import java.io.File
import java.util.UUID

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

data class NormalizedQuad(
    val topLeftX: Float = 0.06f,
    val topLeftY: Float = 0.08f,
    val topRightX: Float = 0.94f,
    val topRightY: Float = 0.08f,
    val bottomRightX: Float = 0.94f,
    val bottomRightY: Float = 0.92f,
    val bottomLeftX: Float = 0.06f,
    val bottomLeftY: Float = 0.92f
)

data class ImageToPdfPage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val autoCroppedFilePath: String? = null,
    val autoCropSummary: String? = null
)

data class AutoCropAnalysis(
    val quad: NormalizedQuad,
    val skewAngleDegrees: Float,
    val boundaryDetected: Boolean,
    val skewDetected: Boolean
) {
    fun buildSummary(): String {
        val parts = mutableListOf<String>()
        if (skewDetected) {
            val formatted = String.format(java.util.Locale.US, "%.1f°", kotlin.math.abs(skewAngleDegrees))
            parts.add("Straightened $formatted tilt")
        }
        if (boundaryDetected) {
            parts.add("Edges & margins cropped")
        }
        return if (parts.isEmpty()) "Already aligned" else parts.joinToString(" • ")
    }
}

data class AutoCropPageItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val originalFilePath: String,
    val croppedFilePath: String,
    val detectedQuad: NormalizedQuad,
    val skewAngleDegrees: Float = 0f,
    val summary: String = "",
    val enhancement: AutoCropEnhancement = AutoCropEnhancement.ORIGINAL,
    val rotationDegrees: Int = 0
)

data class ImageToPdfConfig(
    val fileName: String = "Images_Document",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val customWidthMm: Int = 210,
    val customHeightMm: Int = 297,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginDp: Int = 24,
    val fitMode: ImageFitMode = ImageFitMode.CONTAIN,
    val imageQuality: Int = 85,
    val addPageNumbers: Boolean = true
)

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val rawFilePath: String,
    val processedFilePath: String,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.COLOR,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val quad: NormalizedQuad = NormalizedQuad()
)

data class PdfCompressionResult(
    val document: PdfDocumentEntity,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val reductionPercent: Int
)

data class PdfDetailedInfo(
    val fileName: String,
    val filePath: String,
    val folderName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val isEncrypted: Boolean,
    val pdfVersion: String,
    val title: String,
    val author: String,
    val subject: String,
    val creator: String,
    val producer: String,
    val pageDimensions: String
)

data class PdfTextMatch(
    val pageIndex: Int,
    val snippet: String
)

data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val darkTheme: Boolean = true,
    val languageCode: String = "en",
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultImageQuality: Int = 85,
    val defaultSaveLocation: SaveLocationOption = SaveLocationOption.APP_FOLDER
)

data class ConvertedImageOutput(
    val files: List<File>,
    val outputDirectory: File
)
