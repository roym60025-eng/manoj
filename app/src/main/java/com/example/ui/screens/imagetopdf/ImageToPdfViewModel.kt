package com.example.ui.screens.imagetopdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.ImageToPdfPage
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageToPdfUiState(
    val pages: List<ImageToPdfPage> = emptyList(),
    val config: ImageToPdfConfig = ImageToPdfConfig(),
    val autoCropEnabled: Boolean = false,
    val isAutoCropping: Boolean = false,
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val generatedDocument: PdfDocumentEntity? = null,
    val errorMessage: String? = null,
    val editingCropPageId: String? = null
)

class ImageToPdfViewModel(
    private val repository: PdfRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToPdfUiState())
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.settingsFlow.first()
            _uiState.update { state ->
                state.copy(
                    config = state.config.copy(
                        pageSize = prefs.defaultPageSize,
                        imageQuality = prefs.defaultImageQuality
                    )
                )
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newPages = uris.map { ImageToPdfPage(uri = it) }
        val shouldAutoCrop = _uiState.value.autoCropEnabled
        _uiState.update {
            it.copy(
                pages = it.pages + newPages,
                generatedDocument = null,
                errorMessage = null
            )
        }
        if (shouldAutoCrop) {
            autoCropPagesByIds(newPages.map { it.id }.toSet())
        }
    }

    fun toggleAutoCrop(enabled: Boolean) {
        _uiState.update { it.copy(autoCropEnabled = enabled, generatedDocument = null) }
        if (enabled) {
            val pendingIds = _uiState.value.pages
                .filter { it.autoCroppedFilePath == null }
                .map { it.id }
                .toSet()
            if (pendingIds.isNotEmpty()) {
                autoCropPagesByIds(pendingIds)
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    pages = state.pages.map { p ->
                        p.copy(autoCroppedFilePath = null, autoCropSummary = null)
                    }
                )
            }
        }
    }

    fun autoCropSinglePage(id: String) {
        autoCropPagesByIds(setOf(id))
    }

    fun autoCropAllPages() {
        val ids = _uiState.value.pages.map { it.id }.toSet()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(autoCropEnabled = true) }
        autoCropPagesByIds(ids)
    }

    private fun autoCropPagesByIds(targetIds: Set<String>) {
        if (targetIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAutoCropping = true, errorMessage = null) }
            val currentPages = _uiState.value.pages
            val results = mutableMapOf<String, Pair<String, String>>()

            runCatching {
                for (page in currentPages) {
                    if (page.id in targetIds) {
                        val (outFile, analysis) = repository.autoCropImageUri(page.uri)
                        results[page.id] = outFile.absolutePath to analysis.buildSummary()
                    }
                }
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isAutoCropping = false,
                        pages = state.pages.map { p ->
                            val res = results[p.id]
                            if (res != null) {
                                p.copy(
                                    autoCroppedFilePath = res.first,
                                    autoCropSummary = res.second,
                                    cropRect = NormalizedCropRect()
                                )
                            } else {
                                p
                            }
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isAutoCropping = false, errorMessage = err.message ?: "Auto Crop failed")
                }
            }
        }
    }

    fun removePage(id: String) {
        _uiState.update { it.copy(pages = it.pages.filterNot { p -> p.id == id }) }
    }

    fun rotatePage(id: String) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { p ->
                    if (p.id == id) p.copy(rotationDegrees = (p.rotationDegrees + 90) % 360) else p
                }
            )
        }
    }

    fun openCropEditor(id: String) {
        _uiState.update { it.copy(editingCropPageId = id) }
    }

    fun closeCropEditor() {
        _uiState.update { it.copy(editingCropPageId = null) }
    }

    fun updateCrop(id: String, cropRect: NormalizedCropRect) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { p ->
                    if (p.id == id) p.copy(cropRect = cropRect, autoCroppedFilePath = null, autoCropSummary = null) else p
                },
                editingCropPageId = null
            )
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.pages
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _uiState.update { it.copy(pages = mutable) }
    }

    fun updateConfig(transform: (ImageToPdfConfig) -> ImageToPdfConfig) {
        _uiState.update { it.copy(config = transform(it.config)) }
    }

    fun createPdf() {
        val current = _uiState.value
        if (current.pages.isEmpty() || current.isGenerating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, progress = 0f, errorMessage = null) }
            runCatching {
                repository.createPdfFromImages(
                    pages = current.pages,
                    config = current.config,
                    onProgress = { p -> _uiState.update { it.copy(progress = p) } }
                )
            }.onSuccess { doc ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        progress = 1f,
                        generatedDocument = doc
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = err.message ?: "Failed to create PDF"
                    )
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

    fun resetForNew() {
        _uiState.update {
            it.copy(
                pages = emptyList(),
                generatedDocument = null,
                errorMessage = null,
                progress = 0f
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
