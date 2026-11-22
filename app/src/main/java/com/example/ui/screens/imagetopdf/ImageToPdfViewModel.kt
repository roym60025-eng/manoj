package com.example.ui.screens.imagetopdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.ImageToPdfPage
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
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
        _uiState.update { it.copy(pages = it.pages + newPages, generatedDocument = null, errorMessage = null) }
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
                pages = state.pages.map { p -> if (p.id == id) p.copy(cropRect = cropRect) else p },
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
