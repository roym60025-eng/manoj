package com.example.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.SortOrder
import com.example.data.repository.PdfRepository
import com.example.ui.components.sortDocuments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val documents: List<PdfDocumentEntity> = emptyList(),
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val isImporting: Boolean = false,
    val message: String? = null,
    val activeDetails: PdfDetailedInfo? = null
)

class HomeViewModel(
    val repository: PdfRepository
) : ViewModel() {

    private val sortOrderFlow = MutableStateFlow(SortOrder.NEWEST_FIRST)
    private val isImportingFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)
    private val detailsFlow = MutableStateFlow<PdfDetailedInfo?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allDocuments,
        sortOrderFlow,
        isImportingFlow,
        messageFlow,
        detailsFlow
    ) { docs, sort, importing, msg, details ->
        HomeUiState(
            documents = sortDocuments(docs, sort),
            sortOrder = sort,
            isImporting = importing,
            message = msg,
            activeDetails = details
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun setSortOrder(order: SortOrder) {
        sortOrderFlow.value = order
    }

    fun importPdf(uri: Uri, onImported: (PdfDocumentEntity) -> Unit = {}) {
        viewModelScope.launch {
            isImportingFlow.value = true
            runCatching {
                repository.importPdfFromUri(uri)
            }.onSuccess { doc ->
                messageFlow.value = "Imported ${doc.name}.pdf"
                onImported(doc)
            }.onFailure { err ->
                messageFlow.value = err.message ?: "Failed to import PDF"
            }
            isImportingFlow.value = false
        }
    }

    fun renameDocument(document: PdfDocumentEntity, newName: String) {
        viewModelScope.launch {
            runCatching {
                repository.renameDocument(document, newName)
            }.onSuccess {
                messageFlow.value = "Renamed to ${it.name}.pdf"
            }.onFailure {
                messageFlow.value = it.message ?: "Rename failed"
            }
        }
    }

    fun deleteDocument(document: PdfDocumentEntity) {
        viewModelScope.launch {
            runCatching {
                repository.deleteDocuments(listOf(document))
            }.onSuccess {
                messageFlow.value = "Deleted ${document.name}.pdf"
            }.onFailure {
                messageFlow.value = it.message ?: "Delete failed"
            }
        }
    }

    fun loadDetails(document: PdfDocumentEntity) {
        viewModelScope.launch {
            detailsFlow.value = repository.getPdfInfo(document)
        }
    }

    fun dismissDetails() {
        detailsFlow.value = null
    }

    fun clearMessage() {
        messageFlow.value = null
    }
}
