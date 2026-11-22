package com.example.ui.screens.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NormalizedQuad
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.ScanFilter
import com.example.data.model.ScanPage
import com.example.data.repository.PdfRepository
import com.example.pdf.ImageProcessingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ScannerStage {
    CAMERA,
    CROP_QUAD,
    ENHANCE_AND_PAGES
}

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAMERA,
    val pages: List<ScanPage> = emptyList(),
    val activePageIndex: Int = 0,
    val pendingRawPath: String? = null,
    val pendingQuad: NormalizedQuad = NormalizedQuad(),
    val fileName: String = "Scanned_Document",
    val isProcessing: Boolean = false,
    val generatedDocument: PdfDocumentEntity? = null,
    val errorMessage: String? = null
)

class ScannerViewModel(
    private val context: Context,
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onCapturedFile(rawFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = ImageProcessingHelper.decodeFileBitmap(rawFile, maxDimension = 1800)
                    val normalizedRaw = File(repository.storageHelper.tempDir, "scan_raw_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(bmp, normalizedRaw, quality = 94)
                    val detectedQuad = ImageProcessingHelper.detectDocumentQuad(bmp)
                    bmp.recycle()
                    normalizedRaw.absolutePath to detectedQuad
                }
            }.onSuccess { (path, quad) ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        pendingRawPath = path,
                        pendingQuad = quad,
                        stage = ScannerStage.CROP_QUAD
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = err.message ?: "Scan processing failed")
                }
            }
        }
    }

    fun onGalleryImagePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = ImageProcessingHelper.decodeSampledBitmap(context, uri, maxDimension = 1800)
                    val rawFile = File(repository.storageHelper.tempDir, "scan_raw_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(bmp, rawFile, quality = 94)
                    val quad = ImageProcessingHelper.detectDocumentQuad(bmp)
                    bmp.recycle()
                    rawFile.absolutePath to quad
                }
            }.onSuccess { (path, quad) ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        pendingRawPath = path,
                        pendingQuad = quad,
                        stage = ScannerStage.CROP_QUAD
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = err.message ?: "Could not load image")
                }
            }
        }
    }

    fun updatePendingQuad(quad: NormalizedQuad) {
        _uiState.update { it.copy(pendingQuad = quad) }
    }

    fun autoDetectPendingQuad() {
        val path = _uiState.value.pendingRawPath ?: return
        viewModelScope.launch {
            val quad = withContext(Dispatchers.IO) {
                val bmp = ImageProcessingHelper.decodeFileBitmap(File(path), 1024)
                val q = ImageProcessingHelper.detectDocumentQuad(bmp)
                bmp.recycle()
                q
            }
            _uiState.update { it.copy(pendingQuad = quad) }
        }
    }

    fun confirmPerspectiveCrop() {
        val rawPath = _uiState.value.pendingRawPath ?: return
        val quad = _uiState.value.pendingQuad
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val rawBmp = ImageProcessingHelper.decodeFileBitmap(File(rawPath), 1800)
                    val warped = ImageProcessingHelper.applyPerspectiveCorrection(rawBmp, quad)
                    if (warped !== rawBmp) rawBmp.recycle()
                    val baseCroppedFile = File(repository.storageHelper.tempDir, "scan_crop_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(warped, baseCroppedFile, quality = 92)
                    val processedFile = File(repository.storageHelper.tempDir, "scan_proc_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(warped, processedFile, quality = 90)
                    warped.recycle()
                    ScanPage(
                        rawFilePath = baseCroppedFile.absolutePath,
                        processedFilePath = processedFile.absolutePath,
                        quad = quad
                    )
                }
            }.onSuccess { scanPage ->
                _uiState.update { state ->
                    val newPages = state.pages + scanPage
                    state.copy(
                        isProcessing = false,
                        pages = newPages,
                        activePageIndex = newPages.lastIndex,
                        pendingRawPath = null,
                        stage = ScannerStage.ENHANCE_AND_PAGES
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) }
            }
        }
    }

    fun selectPage(index: Int) {
        if (index in _uiState.value.pages.indices) {
            _uiState.update { it.copy(activePageIndex = index) }
        }
    }

    fun updateActivePageAdjustments(
        filter: ScanFilter? = null,
        brightness: Float? = null,
        contrast: Float? = null,
        rotateDelta: Int = 0
    ) {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.activePageIndex) ?: return
        val updated = page.copy(
            filter = filter ?: page.filter,
            brightness = brightness ?: page.brightness,
            contrast = contrast ?: page.contrast,
            rotationDegrees = (page.rotationDegrees + rotateDelta) % 360
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val base = ImageProcessingHelper.decodeFileBitmap(File(updated.rawFilePath), 1800)
                    val adjusted = ImageProcessingHelper.applyScanAdjustments(
                        source = base,
                        filter = updated.filter,
                        brightness = updated.brightness,
                        contrast = updated.contrast,
                        rotationDegrees = updated.rotationDegrees
                    )
                    base.recycle()
                    val newProcFile = File(repository.storageHelper.tempDir, "scan_proc_${UUID.randomUUID()}.jpg")
                    ImageProcessingHelper.saveBitmapToFile(adjusted, newProcFile, quality = 90)
                    adjusted.recycle()
                    updated.copy(processedFilePath = newProcFile.absolutePath)
                }
            }.onSuccess { finalPage ->
                _uiState.update { st ->
                    val list = st.pages.toMutableList()
                    if (st.activePageIndex in list.indices) {
                        list[st.activePageIndex] = finalPage
                    }
                    st.copy(pages = list, isProcessing = false)
                }
            }.onFailure {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun deleteActivePage() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        val mutable = state.pages.toMutableList()
        mutable.removeAt(state.activePageIndex)
        _uiState.update {
            it.copy(
                pages = mutable,
                activePageIndex = (state.activePageIndex).coerceAtMost((mutable.size - 1).coerceAtLeast(0)),
                stage = if (mutable.isEmpty()) ScannerStage.CAMERA else ScannerStage.ENHANCE_AND_PAGES
            )
        }
    }

    fun addAnotherPage() {
        _uiState.update { it.copy(stage = ScannerStage.CAMERA, generatedDocument = null) }
    }

    fun updateFileName(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    fun exportToPdf() {
        val state = _uiState.value
        if (state.pages.isEmpty() || state.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            runCatching {
                repository.createPdfFromScans(state.pages, state.fileName)
            }.onSuccess { doc ->
                _uiState.update { it.copy(isProcessing = false, generatedDocument = doc) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = err.message ?: "Export failed")
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

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
