package com.example.ui.screens.files

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfCategory
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.SortOrder
import com.example.data.repository.PdfRepository
import com.example.pdf.StorageHelper
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EmptyPdfStateView
import com.example.ui.components.MoveFolderDialog
import com.example.ui.components.PdfDetailsModal
import com.example.ui.components.PdfListItemCard
import com.example.ui.components.RenamePdfDialog
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.components.sortDocuments
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    repository: PdfRepository,
    onOpenPdf: (PdfDocumentEntity) -> Unit,
    onCreatePdf: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allDocs by repository.allDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    val existingFolders by repository.folders.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedCategory by remember { mutableStateOf(PdfCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()

    var renameTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var deleteTargets by remember { mutableStateOf<List<PdfDocumentEntity>?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var detailsInfo by remember { mutableStateOf<PdfDetailedInfo?>(null) }

    val filteredDocs = remember(allDocs, selectedCategory, searchQuery, sortOrder) {
        val now = System.currentTimeMillis()
        val recentCutoff = now - 7L * 24 * 60 * 60 * 1000
        val byCat = when (selectedCategory) {
            PdfCategory.ALL -> allDocs
            PdfCategory.RECENT -> allDocs.filter { it.createdAt >= recentCutoff }.ifEmpty { allDocs.take(10) }
            else -> allDocs.filter { it.category == selectedCategory.name }
        }
        val byQuery = if (searchQuery.isBlank()) {
            byCat
        } else {
            val q = searchQuery.trim().lowercase()
            byCat.filter { doc ->
                doc.name.lowercase().contains(q) ||
                    doc.category.lowercase().contains(q) ||
                    doc.folderName.lowercase().contains(q) ||
                    StorageHelper.formatDateTime(doc.createdAt).lowercase().contains(q)
            }
        }
        sortDocuments(byQuery, sortOrder)
    }

    LaunchedEffect(allDocs) {
        val existingIds = allDocs.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(existingIds)
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
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
                Spacer(Modifier.height(12.dp))

                if (selectionMode) {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Filled.Close, "Cancel selection", tint = colors.textPrimary)
                            }
                            Text(
                                "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                selectedIds = if (selectedIds.size == filteredDocs.size) {
                                    emptySet()
                                } else {
                                    filteredDocs.map { it.id }.toSet()
                                }
                            }) {
                                Icon(Icons.Filled.SelectAll, "Select All", tint = colors.primaryBlue)
                            }
                            if (selectedIds.size == 1) {
                                IconButton(onClick = {
                                    renameTarget = filteredDocs.firstOrNull { it.id in selectedIds }
                                }) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, "Rename", tint = colors.textPrimary)
                                }
                            }
                            IconButton(onClick = { showMoveDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move", tint = colors.textPrimary)
                            }
                            IconButton(onClick = {
                                val selected = filteredDocs.filter { it.id in selectedIds }
                                ShareAndPrintHelper.shareMultiplePdfs(context, repository.storageHelper, selected)
                            }) {
                                Icon(Icons.Filled.Share, "Share selected", tint = colors.textPrimary)
                            }
                            IconButton(onClick = {
                                deleteTargets = filteredDocs.filter { it.id in selectedIds }
                            }) {
                                Icon(Icons.Filled.DeleteOutline, "Delete selected", tint = ErrorRed)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Files",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (filteredDocs.isNotEmpty()) {
                                        selectedIds = setOf(filteredDocs.first().id)
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.SelectAll, "Multi-select", tint = colors.textPrimary)
                            }
                            Box {
                                IconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = colors.textPrimary)
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                    containerColor = colors.cardBackground
                                ) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    order.label,
                                                    color = if (order == sortOrder) colors.primaryBlue else colors.textPrimary
                                                )
                                            },
                                            onClick = {
                                                sortMenuExpanded = false
                                                sortOrder = order
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files by name, folder, or date") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.textSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, "Clear search", tint = colors.textSecondary)
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
                        .testTag("files_search_input")
                )

                Spacer(Modifier.height(12.dp))

                // Category filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PdfCategory.entries) { category ->
                        val selected = category == selectedCategory
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = colors.cardBackground,
                                labelColor = colors.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = colors.divider
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (filteredDocs.isEmpty()) {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EmptyPdfStateView(
                            message = if (searchQuery.isNotBlank()) {
                                "No PDFs matched \"$searchQuery\"."
                            } else {
                                "No PDF files in ${selectedCategory.label}."
                            },
                            actionLabel = if (searchQuery.isBlank()) "Create PDF" else null,
                            onActionClick = onCreatePdf
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            PdfListItemCard(
                                document = doc,
                                selectionMode = selectionMode,
                                isSelected = doc.id in selectedIds,
                                onClick = { onOpenPdf(doc) },
                                onToggleSelect = {
                                    selectedIds = if (doc.id in selectedIds) {
                                        selectedIds - doc.id
                                    } else {
                                        selectedIds + doc.id
                                    }
                                },
                                onShare = {
                                    ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc)
                                },
                                onRename = { renameTarget = doc },
                                onDelete = { deleteTargets = listOf(doc) },
                                onDetails = {
                                    scope.launch {
                                        detailsInfo = repository.getPdfInfo(doc)
                                    }
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
                    selectedIds = emptySet()
                }
                renameTarget = null
            }
        )
    }

    deleteTargets?.let { targets ->
        DeleteConfirmDialog(
            count = targets.size,
            itemName = targets.firstOrNull()?.name,
            onDismiss = { deleteTargets = null },
            onConfirm = {
                scope.launch {
                    repository.deleteDocuments(targets)
                    selectedIds = emptySet()
                    snackbarHostState.showSnackbar("Deleted ${targets.size} file(s)")
                }
                deleteTargets = null
            }
        )
    }

    if (showMoveDialog) {
        MoveFolderDialog(
            existingFolders = existingFolders,
            onDismiss = { showMoveDialog = false },
            onConfirm = { folder ->
                val targets = filteredDocs.filter { it.id in selectedIds }
                scope.launch {
                    repository.moveDocumentsToFolder(targets, folder)
                    selectedIds = emptySet()
                    snackbarHostState.showSnackbar("Moved to $folder")
                }
                showMoveDialog = false
            }
        )
    }

    detailsInfo?.let { info ->
        PdfDetailsModal(info = info, onDismiss = { detailsInfo = null })
    }
}
