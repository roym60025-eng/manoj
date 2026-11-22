package com.example.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class StorageHelper(private val context: Context) {

    val smartPdfRootDir: File
        get() = File(context.filesDir, ROOT_FOLDER_NAME).apply { if (!exists()) mkdirs() }

    val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }

    val convertedImagesDir: File
        get() = File(smartPdfRootDir, "Converted Images").apply { if (!exists()) mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, "pdf_temp").apply { if (!exists()) mkdirs() }

    fun getFolderDir(folderName: String): File {
        val sanitized = sanitizeName(folderName).ifBlank { ROOT_FOLDER_NAME }
        return if (sanitized == ROOT_FOLDER_NAME) {
            smartPdfRootDir
        } else {
            File(smartPdfRootDir, sanitized).apply { if (!exists()) mkdirs() }
        }
    }

    fun generateUniquePdfFile(desiredName: String, folderName: String = ROOT_FOLDER_NAME): File {
        val dir = getFolderDir(folderName)
        val base = sanitizeFileBase(desiredName).ifBlank {
            "Document_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        }
        var candidate = File(dir, "$base.pdf")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$index.pdf")
            index++
        }
        return candidate
    }

    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun copyUriToTempFile(uri: Uri, prefix: String = "import", extension: String = "tmp"): File {
        val outFile = File(tempDir, "${prefix}_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open selected file")
        return outFile
    }

    fun getDisplayNameFromUri(uri: Uri): String {
        var result = "Imported_Document"
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            cursor.getString(idx)?.let { name ->
                                result = name.substringBeforeLast('.')
                            }
                        }
                    }
                }
        } else {
            uri.lastPathSegment?.let { seg ->
                result = seg.substringAfterLast('/').substringBeforeLast('.')
            }
        }
        return sanitizeFileBase(result).ifBlank { "Imported_Document" }
    }

    fun copyPdfToPublicDocuments(sourceFile: File, displayName: String): Uri? {
        if (!sourceFile.exists()) return null
        val safeName = if (displayName.endsWith(".pdf", true)) displayName else "$displayName.pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$ROOT_FOLDER_NAME")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
            }
            uri
        } else {
            null
        }
    }

    fun copyImageToPublicPictures(sourceFile: File, subFolder: String): Uri? {
        if (!sourceFile.exists()) return null
        val mime = if (sourceFile.extension.equals("png", true)) "image/png" else "image/jpeg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ROOT_FOLDER_NAME/$subFolder"
                )
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
            }
            uri
        } else {
            null
        }
    }

    fun writePdfToUserUri(sourceFile: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Could not write to selected destination")
    }

    companion object {
        const val ROOT_FOLDER_NAME = "Smart PDF Maker"

        fun sanitizeFileBase(raw: String): String {
            return raw.trim()
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
        }

        fun sanitizeName(raw: String): String {
            return raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                .coerceIn(0, units.lastIndex)
            return String.format(
                Locale.US,
                if (digitGroups == 0) "%.0f %s" else "%.1f %s",
                bytes / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }

        fun formatDateTime(epochMillis: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            return sdf.format(Date(epochMillis))
        }
    }
}
