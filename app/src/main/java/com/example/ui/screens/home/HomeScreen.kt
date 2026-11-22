package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.SortOrder
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EmptyPdfStateView
import com.example.ui.components.PdfDetailsModal
import com.example.ui.components.PdfListItemCard
import com.example.ui.components.RenamePdfDialog
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.FeatureAmber
import com.example.ui.theme.FeatureBlue
import com.example.ui.theme.FeatureCoral
import com.example.ui.theme.FeatureCyan
import com.example.ui.theme.FeatureEmerald
import com.example.ui.theme.FeatureIndigo
import com.example.ui.theme.FeatureTeal
import com.example.ui.theme.FeatureViolet
import com.example.ui.theme.LocalSmartPdfColors

private data class HomeFeatureItem(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onImageToPdf: () -> Unit,
    onSmartScan: () -> Unit,
    onImportPdf: () -> Unit,
    onCompressPdf: () -> Unit,
    onPdfToJpg: () -> Unit,
    onMergePdf: () -> Unit,
    onDocxToPdf: () -> Unit,
    onMoreTools: () -> Unit,
    onAutoCrop: () -> Unit = {},
    onOpenPdf: (PdfDocumentEntity) -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var sortExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val features = remember(
        onImageToPdf, onSmartScan, onImportPdf, onCompressPdf,
        onPdfToJpg, onMergePdf, onDocxToPdf, onMoreTools
    ) {
        listOf(
            HomeFeatureItem("Image to PDF", Icons.Filled.Image, FeatureBlue, "feature_image_to_pdf", onImageToPdf),
            HomeFeatureItem("Smart Scan", Icons.Filled.DocumentScanner, FeatureCyan, "feature_smart_scan", onSmartScan),
            HomeFeatureItem("Import PDF", Icons.Filled.FileDownload, FeatureIndigo, "feature_import_pdf", onImportPdf),
            HomeFeatureItem("Compress PDF", Icons.Filled.Compress, FeatureAmber, "feature_compress_pdf", onCompressPdf),
            HomeFeatureItem("PDF to JPG", Icons.Filled.PhotoLibrary, FeatureCoral, "feature_pdf_to_jpg", onPdfToJpg),
            HomeFeatureItem("Merge PDF", Icons.AutoMirrored.Filled.MergeType, FeatureEmerald, "feature_merge_pdf", onMergePdf),
            HomeFeatureItem("DOCX to PDF", Icons.Filled.Description, FeatureTeal, "feature_docx_to_pdf", onDocxToPdf),
            HomeFeatureItem("More", Icons.Filled.GridView, FeatureViolet, "feature_more", onMoreTools)
        )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Top Header: Home + Search + Settings
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.testTag("home_heading")
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onOpenSearch,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search PDFs",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // 4-Column Feature Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        features.chunked(4).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowItems.forEach { item ->
                                    FeatureGridTile(
                                        item = item,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Separate Auto Crop & Straighten Feature Card
                item {
                    Surface(
                        onClick = onAutoCrop,
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FeatureCyan.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feature_auto_crop")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(FeatureCyan.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CropRotate,
                                    contentDescription = "Auto Crop & Straighten",
                                    tint = FeatureCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Auto Crop & Straighten",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = FeatureCyan.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "AUTO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = FeatureCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Fix tilted document photos & crooked PDFs automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }
                    }
                }

                if (state.isImporting) {
                    item {
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.primaryBlue
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Importing PDF...", color = colors.textPrimary)
                            }
                        }
                    }
                }

                // Large Rounded Dark Card: "All" PDFs Section
                item {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "All",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.textPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = colors.secondaryBackground,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = state.documents.size.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.primaryBlue,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Box {
                                    Surface(
                                        onClick = { sortExpanded = true },
                                        color = colors.secondaryBackground,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("home_sort_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = "Sort PDFs",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = state.sortOrder.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = sortExpanded,
                                        onDismissRequest = { sortExpanded = false },
                                        containerColor = colors.cardBackground
                                    ) {
                                        SortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        order.label,
                                                        color = if (order == state.sortOrder) colors.primaryBlue else colors.textPrimary
                                                    )
                                                },
                                                onClick = {
                                                    sortExpanded = false
                                                    viewModel.setSortOrder(order)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.documents.isEmpty()) {
                                EmptyPdfStateView(
                                    message = "No PDF files found. Create your first PDF.",
                                    actionLabel = "Create PDF",
                                    onActionClick = onImageToPdf
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    state.documents.forEach { doc ->
                                        PdfListItemCard(
                                            document = doc,
                                            onClick = { onOpenPdf(doc) },
                                            onShare = {
                                                ShareAndPrintHelper.sharePdf(
                                                    context,
                                                    viewModel.repository.storageHelper,
                                                    doc
                                                )
                                            },
                                            onRename = { renameTarget = doc },
                                            onDelete = { deleteTarget = doc },
                                            onDetails = { viewModel.loadDetails(doc) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    renameTarget?.let { target ->
        RenamePdfDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameDocument(target, newName)
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
                viewModel.deleteDocument(target)
                deleteTarget = null
            }
        )
    }

    state.activeDetails?.let { info ->
        PdfDetailsModal(
            info = info,
            onDismiss = { viewModel.dismissDetails() }
        )
    }
}

@Composable
private fun FeatureGridTile(
    item: HomeFeatureItem,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmartPdfColors.current
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag(item.testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.iconCircleBg),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(item.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
