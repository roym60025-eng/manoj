package com.example.ui.screens.autocrop

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AutoCropEnhancement
import com.example.data.model.AutoCropPageItem
import com.example.data.model.NormalizedQuad
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.ImageProcessingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class AutoCropUiState(
    val pages: List<AutoCropPageItem> = emptyList(),
    val activeIndex: Int = 0,
    val showOriginal: Boolean = false,
    val trimUniformMargin: Boolean = true,
    val globalEnhancement: AutoCropEnhancement = AutoCropEnhancement.ORIGINAL,
    val fileName: String = "AutoCropped_Document",
    val isProcessing: Boolean = false,
    val processingLabel: String = "",
    val editingQuadPageId: String? = null,
    val generatedDocument: PdfDocumentEntity? = null,
    val message: String? = null
)

class AutoCropViewModel(
    context: Context,
    private val repository: PdfRepository,
    preselectedPdfId: Long? = null
) : AndroidViewModel(context.applicationContext as Application) {

    private val _uiState = MutableStateFlow(AutoCropUiState())
    val uiState: StateFlow<AutoCropUiState> = _uiState.asStateFlow()

    val libraryDocuments: StateFlow<List<PdfDocumentEntity>> = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        if (preselectedPdfId != null) {
            viewModelScope.launch {
                repository.getDocumentById(preselectedPdfId)?.let { doc ->
                    addPdfDocument(doc)
                }
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingLabel = "Auto-cropping & straightening ${uris.size} image(s)...",
                    generatedDocument = null,
                    message = null
                )
            }
            val currentEnhancement = _uiState.value.globalEnhancement
            val trimMargin = _uiState.value.trimUniformMargin

            runCatching {
                withContext(Dispatchers.IO) {
                    val created = mutableListOf<AutoCropPageItem>()
                    uris.forEachIndexed { idx, uri ->
                        val origBmp = ImageProcessingHelper.decodeSampledBitmap(
                            getApplication<Application>(),
                            uri,
                            maxDimension = 1800
                        )
                        val origFile = File(repository.storageHelper.tempDir, "ac_orig_${UUID.randomUUID()}.jpg")
                        ImageProcessingHelper.saveBitmapToFile(origBmp, origFile, quality = 94)

                        val (fixedBmp, analysis) = ImageProcessingHelper.autoStraightenAndCrop(
                            source = origBmp,
                            enhancement = currentEnhancement,
                            trimUniformMargin = trimMargin
                        )
                        origBmp.recycle()

                        val fixedFile = File(repository.storageHelper.tempDir, "ac_fix_${UUID.randomUUID()}.jpg")
                        ImageProcessingHelper.saveBitmapToFile(fixedBmp, fixedFile, quality = 92)
                        fixedBmp.recycle()

                        created.add(
                            AutoCropPageItem(
                                label = "Page ${_uiState.value.pages.size + idx + 1}",
                                originalFilePath = origFile.absolutePath,
                                croppedFilePath = fixedFile.absolutePath,
                                detectedQuad = analysis.quad,
                                skewAngleDegrees = analysis.skewAngleDegrees,
                                summary = analysis.buildSummary(),
                                enhancement = currentEnhancement
                            )
                        )
                    }
                    created
                }
            }.onSuccess { added ->
                _uiState.update { st ->
                    val combined = st.pages + added
                    st.copy(
                        isProcessing = false,
                        pages = combined,
                        activeIndex = st.pages.size.coerceAtMost(combined.lastIndex.coerceAtLeast(0)),
                        showOriginal = false,
                        message = "Auto-cropped ${added.size} page(s)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, message = err.message ?: "Auto Crop failed")
                }
            }
        }
    }

    fun addCameraFile(rawFile: File) {
        if (!rawFile.exists()) return
        addImages(listOf(Uri.fromFile(rawFile)))
    }

    fun addPdfDocument(doc: PdfDocumentEntity) {
        val file = File(doc.filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(message = "PDF file not found") }
            return
        }
        processPdfFile(file, doc.name)
    }

    fun addPdfUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingLabel = "Reading PDF pages...",
                    generatedDocument = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val tempPdf = repository.storageHelper.copyUriToTempFile(uri, "autocrop_src", "pdf")
                    val name = repository.storageHelper.getDisplayNameFromUri(uri)
                    tempPdf to name
                }
            }.onSuccess { (tempFile, name) ->
                processPdfFile(tempFile, name)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, message = err.message ?: "Failed to read PDF")
                }
            }
        }
    }

    private fun processPdfFile(pdfFile: File, baseName: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingLabel = "Straightening pages in $baseName...",
                    generatedDocument = null,
                    fileName = "${baseName}_AutoCropped"
                )
            }
            val currentEnhancement = _uiState.value.globalEnhancement
            val trimMargin = _uiState.value.trimUniformMargin

            runCatching {
                withContext(Dispatchers.IO) {
                    val total = repository.rendererHelper.getPageCount(pdfFile).coerceIn(1, 25)
                    val added = mutableListOf<AutoCropPageItem>()
                    for (pageIdx in 0 until total) {
                        val rendered = repository.rendererHelper.renderPageBitmap(pdfFile, pageIdx, targetWidthPx = 1500)
                            ?: continue
                        val origFile = File(repository.storageHelper.tempDir, "ac_pdf_orig_${UUID.randomUUID()}.jpg")
                        ImageProcessingHelper.saveBitmapToFile(rendered, origFile, quality = 93)

                        val (fixedBmp, analysis) = ImageProcessingHelper.autoStraightenAndCrop(
                            source = rendered,
                            enhancement = currentEnhancement,
                            trimUniformMargin = trimMargin
                        )
                        rendered.recycle()

                        val fixedFile = File(repository.storageHelper.tempDir, "ac_pdf_fix_${UUID.randomUUID()}.jpg")
                        ImageProcessingHelper.saveBitmapToFile(fixedBmp, fixedFile, quality = 92)
                        fixedBmp.recycle()

                        added.add(
                            AutoCropPageItem(
                                label = "PDF Page ${pageIdx + 1}",
                                originalFilePath = origFile.absolutePath,
                                croppedFilePath = fixedFile.absolutePath,
                                detectedQuad = analysis.quad,
                                skewAngleDegrees = analysis.skewAngleDegrees,
                                summary = analysis.buildSummary(),
                                enhancement = currentEnhancement
                            )
                        )
                    }
                    require(added.isNotEmpty()) { "Could not extract pages from PDF (file may be password protected)" }
                    added
                }
            }.onSuccess { added ->
                _uiState.update { st ->
                    val combined = st.pages + added
                    st.copy(
                        isProcessing = false,
                        pages = combined,
                        activeIndex = st.pages.size.coerceAtMost(combined.lastIndex.coerceAtLeast(0)),
                        showOriginal = false,
                        message = "Straightened ${added.size} PDF page(s)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, message = err.message ?: "Failed to process PDF")
                }
            }
        }
    }

    fun selectPage(index: Int) {
        if (index in _uiState.value.pages.indices) {
            _uiState.update { it.copy(activeIndex = index) }
        }
    }

    fun setShowOriginal(showOriginal: Boolean) {
        _uiState.update { it.copy(showOriginal = showOriginal) }
    }

    fun toggleTrimMargin(enabled: Boolean) {
        _uiState.update { it.copy(trimUniformMargin = enabled) }
        reprocessActivePage(enhancement = _uiState.value.globalEnhancement, trimMargin = enabled)
    }

    fun setEnhancement(enhancement: AutoCropEnhancement) {
        _uiState.update { it.copy(globalEnhancement = enhancement) }
        reprocessAllPages(enhancement)
    }

    fun reRunAutoCropActivePage() {
        val state = _uiState.value
        reprocessActivePage(enhancement = state.globalEnhancement, trimMargin = state.trimUniformMargin)
    }

    private fun reprocessActivePage(enhancement: AutoCropEnhancement, trimMargin: Boolean) {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.activeIndex) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Updating Auto Crop...") }
            runCatching {
                withContext(Dispatchers.IO) {
                    val orig = ImageProcessingHelper.decodeFileBitmap(File(page.originalFilePath), 1800)
                    val (fixed, analysis) = ImageProcessingHelper.autoStraightenAndCrop(
                        source = orig,
                        enhancement = enhancement,
                        trimUniformMargin = trimMargin
                    )
                    orig.recycle()
                    val rotated = if (page.rotationDegrees % 360 != 0) {
                        ImageProcessingHelper.rotateBitmap(fixed, page.rotationDegrees)
                    } else {
                        fixed
                    }
                    val outFile = File(repository.storageHelper.tempDir, "ac_fix_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(rotated, outFile, quality = 92)
                    rotated.recycle()
                    page.copy(
                        croppedFilePath = outFile.absolutePath,
                        detectedQuad = analysis.quad,
                        skewAngleDegrees = analysis.skewAngleDegrees,
                        summary = analysis.buildSummary(),
                        enhancement = enhancement
                    )
                }
            }.onSuccess { updated ->
                _uiState.update { st ->
                    val list = st.pages.toMutableList()
                    if (st.activeIndex in list.indices) list[st.activeIndex] = updated
                    st.copy(pages = list, isProcessing = false, generatedDocument = null)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, message = err.message) }
            }
        }
    }

    private fun reprocessAllPages(enhancement: AutoCropEnhancement) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        val trimMargin = _uiState.value.trimUniformMargin
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Applying ${enhancement.label}...") }
            runCatching {
                withContext(Dispatchers.IO) {
                    pages.map { page ->
                        val orig = ImageProcessingHelper.decodeFileBitmap(File(page.originalFilePath), 1800)
                        val (fixed, analysis) = ImageProcessingHelper.autoStraightenAndCrop(
                            source = orig,
                            enhancement = enhancement,
                            trimUniformMargin = trimMargin
                        )
                        orig.recycle()
                        val rotated = if (page.rotationDegrees % 360 != 0) {
                            ImageProcessingHelper.rotateBitmap(fixed, page.rotationDegrees)
                        } else {
                            fixed
                        }
                        val outFile = File(repository.storageHelper.tempDir, "ac_fix_${UUID.randomUUID()}.jpg")
                        ImageProcessingHelper.saveBitmapToFile(rotated, outFile, quality = 92)
                        rotated.recycle()
                        page.copy(
                            croppedFilePath = outFile.absolutePath,
                            detectedQuad = analysis.quad,
                            skewAngleDegrees = analysis.skewAngleDegrees,
                            summary = analysis.buildSummary(),
                            enhancement = enhancement
                        )
                    }
                }
            }.onSuccess { updated ->
                _uiState.update { it.copy(pages = updated, isProcessing = false, generatedDocument = null) }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, message = err.message) }
            }
        }
    }

    fun rotateActivePage() {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.activeIndex) ?: return
        val nextDeg = (page.rotationDegrees + 90) % 360
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Rotating page...") }
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = ImageProcessingHelper.decodeFileBitmap(File(page.croppedFilePath), 1800)
                    val rotated = ImageProcessingHelper.rotateBitmap(bmp, 90)
                    val outFile = File(repository.storageHelper.tempDir, "ac_rot_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(rotated, outFile, quality = 92)
                    rotated.recycle()
                    page.copy(croppedFilePath = outFile.absolutePath, rotationDegrees = nextDeg)
                }
            }.onSuccess { updated ->
                _uiState.update { st ->
                    val list = st.pages.toMutableList()
                    if (st.activeIndex in list.indices) list[st.activeIndex] = updated
                    st.copy(pages = list, isProcessing = false, generatedDocument = null)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, message = err.message) }
            }
        }
    }

    fun openQuadEditor() {
        val page = _uiState.value.pages.getOrNull(_uiState.value.activeIndex) ?: return
        _uiState.update { it.copy(editingQuadPageId = page.id) }
    }

    fun closeQuadEditor() {
        _uiState.update { it.copy(editingQuadPageId = null) }
    }

    fun applyManualQuad(pageId: String, quad: NormalizedQuad) {
        val state = _uiState.value
        val idx = state.pages.indexOfFirst { it.id == pageId }
        if (idx < 0) {
            closeQuadEditor()
            return
        }
        val page = state.pages[idx]
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    editingQuadPageId = null,
                    isProcessing = true,
                    processingLabel = "Applying corner alignment..."
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val orig = ImageProcessingHelper.decodeFileBitmap(File(page.originalFilePath), 1800)
                    var warped = ImageProcessingHelper.applyPerspectiveCorrection(orig, quad)
                    orig.recycle()
                    warped = when (state.globalEnhancement) {
                        AutoCropEnhancement.ORIGINAL -> warped
                        AutoCropEnhancement.CLEAN_DOC -> ImageProcessingHelper.applyScanAdjustments(
                            warped, com.example.data.model.ScanFilter.COLOR, 14f, 1.24f, 0
                        ).also { warped.recycle() }
                        AutoCropEnhancement.CRISP_BW -> ImageProcessingHelper.applyScanAdjustments(
                            warped, com.example.data.model.ScanFilter.BLACK_AND_WHITE, 12f, 1.15f, 0
                        ).also { warped.recycle() }
                    }
                    if (page.rotationDegrees % 360 != 0) {
                        warped = ImageProcessingHelper.rotateBitmap(warped, page.rotationDegrees)
                    }
                    val outFile = File(repository.storageHelper.tempDir, "ac_man_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(warped, outFile, quality = 92)
                    warped.recycle()
                    page.copy(
                        croppedFilePath = outFile.absolutePath,
                        detectedQuad = quad,
                        summary = "Custom 4-corner alignment applied"
                    )
                }
            }.onSuccess { updated ->
                _uiState.update { st ->
                    val list = st.pages.toMutableList()
                    if (idx in list.indices) list[idx] = updated
                    st.copy(pages = list, isProcessing = false, generatedDocument = null)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, message = err.message) }
            }
        }
    }

    fun removeActivePage() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        val mutable = state.pages.toMutableList()
        mutable.removeAt(state.activeIndex)
        _uiState.update {
            it.copy(
                pages = mutable,
                activeIndex = state.activeIndex.coerceAtMost((mutable.size - 1).coerceAtLeast(0)),
                generatedDocument = null
            )
        }
    }

    fun updateFileName(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    fun exportAsPdf() {
        val state = _uiState.value
        if (state.pages.isEmpty() || state.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Creating straightened PDF...") }
            runCatching {
                repository.createPdfFromAutoCrop(state.pages, state.fileName)
            }.onSuccess { doc ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        generatedDocument = doc,
                        message = "Saved ${doc.name}.pdf"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, message = err.message ?: "Failed to create PDF")
                }
            }
        }
    }

    fun exportAsImages() {
        val state = _uiState.value
        if (state.pages.isEmpty() || state.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Saving straightened images...") }
            runCatching {
                repository.exportAutoCroppedImages(state.pages, state.fileName)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        message = "Saved ${result.files.size} straightened image(s) to Pictures"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, message = err.message ?: "Failed to export images")
                }
            }
        }
    }

    fun exportToUserUri(doc: PdfDocumentEntity, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repository.exportPdfToUserUri(doc, uri) }.isSuccess
            onDone(ok)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
