package com.example.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.StorageHelper
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EmptyPdfStateView
import com.example.ui.components.PdfDetailsModal
import com.example.ui.components.PdfListItemCard
import com.example.ui.components.RenamePdfDialog
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allDocs by repository.allDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var detailsInfo by remember { mutableStateOf<PdfDetailedInfo?>(null) }

    val results = remember(allDocs, query) {
        if (query.isBlank()) {
            allDocs
        } else {
            val q = query.trim().lowercase()
            allDocs.filter { doc ->
                doc.name.lowercase().contains(q) ||
                    doc.category.lowercase().contains(q) ||
                    StorageHelper.formatDateTime(doc.createdAt).lowercase().contains(q)
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Search PDFs", color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by file name, type, or date...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.primaryBlue) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, "Clear", tint = colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.cardBackground,
                        unfocusedContainerColor = colors.cardBackground,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input")
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (results.isEmpty()) {
                    EmptyPdfStateView(
                        message = if (query.isBlank()) {
                            "No PDF files found. Create or import a PDF first."
                        } else {
                            "No documents match \"$query\"."
                        }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(results, key = { it.id }) { doc ->
                            PdfListItemCard(
                                document = doc,
                                onClick = { onOpenPdf(doc) },
                                onShare = {
                                    ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc)
                                },
                                onRename = { renameTarget = doc },
                                onDelete = { deleteTarget = doc },
                                onDetails = {
                                    scope.launch { detailsInfo = repository.getPdfInfo(doc) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenamePdfDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                scope.launch {
                    runCatching { repository.renameDocument(target, newName) }
                        .onSuccess { snackbarHostState.showSnackbar("Renamed to ${it.name}.pdf") }
                }
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            count = 1,
            itemName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    repository.deleteDocuments(listOf(target))
                    snackbarHostState.showSnackbar("Deleted ${target.name}.pdf")
                }
                deleteTarget = null
            }
        )
    }

    detailsInfo?.let { info ->
        PdfDetailsModal(info = info, onDismiss = { detailsInfo = null })
    }
}
