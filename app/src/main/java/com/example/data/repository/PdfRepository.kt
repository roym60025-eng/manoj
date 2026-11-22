package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.local.PdfDao
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.CompressionLevel
import com.example.data.model.ConvertedImageOutput
import com.example.data.model.ImageExportFormat
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.ImageToPdfPage
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfCategory
import com.example.data.model.PdfCompressionResult
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.PdfTextMatch
import com.example.data.model.SaveLocationOption
import com.example.data.model.ScanPage
import com.example.pdf.DocxToPdfEngine
import com.example.pdf.PdfGenerationEngine
import com.example.pdf.PdfManipulationEngine
import com.example.pdf.PdfRendererHelper
import com.example.pdf.StorageHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(
    private val context: Context,
    private val pdfDao: PdfDao,
    private val preferencesRepository: UserPreferencesRepository,
    val storageHelper: StorageHelper,
    val rendererHelper: PdfRendererHelper,
    private val generationEngine: PdfGenerationEngine,
    private val manipulationEngine: PdfManipulationEngine,
    private val docxEngine: DocxToPdfEngine
) {
    val allDocuments: Flow<List<PdfDocumentEntity>> = pdfDao.observeAllDocuments()
    val folders: Flow<List<String>> = pdfDao.observeFolders()

    fun observeDocument(id: Long): Flow<PdfDocumentEntity?> = pdfDao.observeById(id)

    suspend fun getDocumentById(id: Long): PdfDocumentEntity? = pdfDao.getById(id)

    private suspend fun registerPdfFile(
        file: File,
        displayName: String,
        category: PdfCategory,
        folderName: String = StorageHelper.ROOT_FOLDER_NAME
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var isEncrypted = false
        var pageCount = rendererHelper.getPageCount(file)
        if (pageCount == 0) {
            runCatching {
                PDDocument.load(file).use { doc ->
                    pageCount = doc.numberOfPages
                    isEncrypted = doc.isEncrypted
                }
            }.onFailure {
                isEncrypted = true
            }
        }
        val docKey = "${now}_${file.nameWithoutExtension.hashCode()}"
        val thumbPath = if (!isEncrypted) {
            rendererHelper.generateAndSaveThumbnail(file, docKey)
        } else {
            null
        }

        val prefs = preferencesRepository.settingsFlow.first()
        if (prefs.defaultSaveLocation == SaveLocationOption.DOCUMENTS_EXPORT) {
            runCatching { storageHelper.copyPdfToPublicDocuments(file, displayName) }
        }

        val entity = PdfDocumentEntity(
            name = StorageHelper.sanitizeFileBase(displayName).ifBlank { file.nameWithoutExtension },
            filePath = file.absolutePath,
            folderName = folderName,
            category = category.name,
            pageCount = pageCount,
            sizeBytes = file.length(),
            createdAt = now,
            updatedAt = now,
            isEncrypted = isEncrypted,
            thumbnailPath = thumbPath
        )
        val id = pdfDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun importPdfFromUri(uri: Uri): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val displayName = storageHelper.getDisplayNameFromUri(uri)
        val destFile = storageHelper.generateUniquePdfFile(displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot read selected PDF file")

        registerPdfFile(destFile, destFile.nameWithoutExtension, PdfCategory.IMPORTED)
    }

    suspend fun createPdfFromImages(
        pages: List<ImageToPdfPage>,
        config: ImageToPdfConfig,
        onProgress: (Float) -> Unit = {}
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile(config.fileName)
        generationEngine.createPdfFromImages(pages, config, outFile, onProgress)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CREATED)
    }

    suspend fun createPdfFromScans(
        pages: List<ScanPage>,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile(fileName)
        generationEngine.createPdfFromScannedPages(pages, outFile, onProgress)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.SCANNED)
    }

    suspend fun createBlankPdf(
        fileName: String,
        pageCount: Int,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        withRuledLines: Boolean
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile(fileName)
        generationEngine.createBlankPdf(outFile, pageCount, pageSize, orientation, withRuledLines)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CREATED)
    }

    suspend fun compressPdf(
        source: PdfDocumentEntity,
        level: CompressionLevel,
        overwriteOriginal: Boolean,
        onProgress: (Float) -> Unit = {}
    ): PdfCompressionResult = withContext(Dispatchers.IO) {
        val srcFile = File(source.filePath)
        require(srcFile.exists()) { "Source PDF file no longer exists" }
        val origSize = srcFile.length()

        if (overwriteOriginal) {
            val tempOut = File.createTempFile("comp_overwrite_", ".pdf", storageHelper.tempDir)
            manipulationEngine.compressPdf(srcFile, tempOut, level, onProgress)
            tempOut.copyTo(srcFile, overwrite = true)
            tempOut.delete()
            val newSize = srcFile.length()
            val updatedThumb = rendererHelper.generateAndSaveThumbnail(srcFile, "${source.id}_${System.currentTimeMillis()}")
            val updated = source.copy(
                sizeBytes = newSize,
                updatedAt = System.currentTimeMillis(),
                thumbnailPath = updatedThumb ?: source.thumbnailPath
            )
            pdfDao.update(updated)
            val pct = if (origSize > 0) (((origSize - newSize) * 100.0) / origSize).toInt().coerceAtLeast(0) else 0
            PdfCompressionResult(updated, origSize, newSize, pct)
        } else {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_compressed", source.folderName)
            manipulationEngine.compressPdf(srcFile, outFile, level, onProgress)
            val newDoc = registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
            val pct = if (origSize > 0) (((origSize - newDoc.sizeBytes) * 100.0) / origSize).toInt().coerceAtLeast(0) else 0
            PdfCompressionResult(newDoc, origSize, newDoc.sizeBytes, pct)
        }
    }

    suspend fun convertPdfToImages(
        source: PdfDocumentEntity,
        selectedPageIndices: List<Int>,
        format: ImageExportFormat,
        quality: Int,
        onProgress: (Float) -> Unit = {}
    ): ConvertedImageOutput = withContext(Dispatchers.IO) {
        val srcFile = File(source.filePath)
        require(srcFile.exists()) { "Source PDF file no longer exists" }
        val folderName = "${StorageHelper.sanitizeFileBase(source.name)}_${System.currentTimeMillis() / 1000}"
        val outDir = File(storageHelper.convertedImagesDir, folderName)
        val result = manipulationEngine.convertPdfToImages(
            inputFile = srcFile,
            selectedPageIndices = selectedPageIndices,
            format = format,
            quality = quality,
            outputDir = outDir,
            baseName = source.name,
            onProgress = onProgress
        )
        result.files.forEach { imgFile ->
            runCatching { storageHelper.copyImageToPublicPictures(imgFile, folderName) }
        }
        result
    }

    suspend fun mergePdfs(
        sources: List<File>,
        outputFileName: String,
        onProgress: (Float) -> Unit = {}
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile(outputFileName)
        manipulationEngine.mergePdfs(sources, outFile, onProgress)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CREATED)
    }

    suspend fun convertDocxToPdf(
        docxUri: Uri,
        outputFileName: String,
        onProgress: (Float) -> Unit = {}
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile(outputFileName)
        docxEngine.convertDocxToPdf(docxUri, outFile, onProgress)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED)
    }

    suspend fun splitPdf(source: PdfDocumentEntity, splitEveryNPages: Int): List<PdfDocumentEntity> =
        withContext(Dispatchers.IO) {
            val srcFile = File(source.filePath)
            val files = manipulationEngine.splitPdf(
                srcFile,
                splitEveryNPages,
                storageHelper.getFolderDir(source.folderName),
                source.name
            )
            files.map { f ->
                registerPdfFile(f, f.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
            }
        }

    suspend fun rotatePdf(source: PdfDocumentEntity, degrees: Int, selectedPages: Set<Int>): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_rotated", source.folderName)
            manipulationEngine.rotatePdf(File(source.filePath), outFile, degrees, selectedPages)
            registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
        }

    suspend fun protectPdf(source: PdfDocumentEntity, password: String): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_protected", source.folderName)
            manipulationEngine.protectPdf(File(source.filePath), outFile, password)
            registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
        }

    suspend fun unlockPdf(source: PdfDocumentEntity, password: String): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_unlocked", source.folderName)
            manipulationEngine.unlockPdf(File(source.filePath), outFile, password)
            registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
        }

    suspend fun addWatermark(
        source: PdfDocumentEntity,
        text: String,
        opacity: Float,
        fontSize: Float
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile("${source.name}_watermarked", source.folderName)
        manipulationEngine.addWatermark(File(source.filePath), outFile, text, opacity, fontSize)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
    }

    suspend fun addPageNumbers(
        source: PdfDocumentEntity,
        startFrom: Int,
        atBottom: Boolean
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val outFile = storageHelper.generateUniquePdfFile("${source.name}_numbered", source.folderName)
        manipulationEngine.addPageNumbers(File(source.filePath), outFile, startFrom, atBottom)
        registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
    }

    suspend fun extractPages(source: PdfDocumentEntity, selectedPages: List<Int>): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_extracted", source.folderName)
            manipulationEngine.extractPages(File(source.filePath), outFile, selectedPages)
            registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
        }

    suspend fun deletePages(source: PdfDocumentEntity, pagesToDelete: Set<Int>): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_trimmed", source.folderName)
            manipulationEngine.deletePages(File(source.filePath), outFile, pagesToDelete)
            registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
        }

    suspend fun reorderPages(source: PdfDocumentEntity, newOrder: List<Int>): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val outFile = storageHelper.generateUniquePdfFile("${source.name}_reordered", source.folderName)
            manipulationEngine.reorderPages(File(source.filePath), outFile, newOrder)
            registerPdfFile(outFile, outFile.nameWithoutExtension, PdfCategory.CONVERTED, source.folderName)
        }

    suspend fun getPdfInfo(source: PdfDocumentEntity): PdfDetailedInfo =
        manipulationEngine.getPdfInfo(File(source.filePath), source.folderName)

    suspend fun searchTextInPdf(source: PdfDocumentEntity, query: String): List<PdfTextMatch> =
        manipulationEngine.searchTextInPdf(File(source.filePath), query)

    suspend fun renderPdfPage(file: File, pageIndex: Int, widthPx: Int = 1080): Bitmap? =
        rendererHelper.renderPageBitmap(file, pageIndex, widthPx)

    suspend fun renameDocument(document: PdfDocumentEntity, newName: String): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val sanitized = StorageHelper.sanitizeFileBase(newName)
            require(sanitized.isNotBlank()) { "File name cannot be empty" }
            val oldFile = File(document.filePath)
            val newFile = storageHelper.generateUniquePdfFile(sanitized, document.folderName)
            if (oldFile.exists()) {
                oldFile.renameTo(newFile)
            }
            val updated = document.copy(
                name = newFile.nameWithoutExtension,
                filePath = if (newFile.exists()) newFile.absolutePath else document.filePath,
                updatedAt = System.currentTimeMillis()
            )
            pdfDao.update(updated)
            updated
        }

    suspend fun moveDocumentsToFolder(documents: List<PdfDocumentEntity>, targetFolder: String) =
        withContext(Dispatchers.IO) {
            val sanitizedFolder = StorageHelper.sanitizeName(targetFolder)
                .ifBlank { StorageHelper.ROOT_FOLDER_NAME }
            val targetDir = storageHelper.getFolderDir(sanitizedFolder)
            documents.forEach { doc ->
                val src = File(doc.filePath)
                var dest = File(targetDir, src.name)
                if (src.exists() && src.parentFile?.absolutePath != targetDir.absolutePath) {
                    var idx = 1
                    while (dest.exists()) {
                        dest = File(targetDir, "${doc.name}_$idx.pdf")
                        idx++
                    }
                    if (src.renameTo(dest)) {
                        pdfDao.update(
                            doc.copy(
                                folderName = sanitizedFolder,
                                filePath = dest.absolutePath,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    pdfDao.update(
                        doc.copy(
                            folderName = sanitizedFolder,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

    suspend fun deleteDocuments(documents: List<PdfDocumentEntity>) = withContext(Dispatchers.IO) {
        documents.forEach { doc ->
            runCatching { File(doc.filePath).delete() }
            doc.thumbnailPath?.let { runCatching { File(it).delete() } }
        }
        pdfDao.deleteByIds(documents.map { it.id })
    }

    suspend fun exportPdfToUserUri(document: PdfDocumentEntity, targetUri: Uri) =
        withContext(Dispatchers.IO) {
            val file = File(document.filePath)
            require(file.exists()) { "PDF file not found" }
            storageHelper.writePdfToUserUri(file, targetUri)
        }
}
