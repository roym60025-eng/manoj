package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.SortOrder
import com.example.pdf.StorageHelper
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.FeatureBlue
import com.example.ui.theme.FeatureCoral
import com.example.ui.theme.FeatureCyan
import com.example.ui.theme.FeatureIndigo
import com.example.ui.theme.FeatureTeal
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueGlow
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SuccessGreen
import java.io.File

@Composable
fun SmartPdfBottomBar(
    currentTabIsHome: Boolean,
    onHomeClick: () -> Unit,
    onAddClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmartPdfColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.cardBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(color = colors.divider, thickness = 0.8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Home
            BottomNavTabItem(
                label = "Home",
                icon = if (currentTabIsHome) Icons.Filled.Home else Icons.Outlined.Home,
                selected = currentTabIsHome,
                onClick = onHomeClick,
                testTag = "nav_home_button"
            )

            // Center: Large glowing floating (+) button
            Box(
                modifier = Modifier
                    .offset(y = (-14).dp)
                    .size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(PrimaryBlueGlow, Color.Transparent)
                            )
                        )
                )
                Surface(
                    onClick = onAddClick,
                    shape = CircleShape,
                    color = PrimaryBlue,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(16.dp, CircleShape, ambientColor = PrimaryBlue, spotColor = PrimaryBlue)
                        .testTag("nav_plus_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create or Import PDF",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Right: Files
            BottomNavTabItem(
                label = "Files",
                icon = if (!currentTabIsHome) Icons.Filled.Folder else Icons.Outlined.Folder,
                selected = !currentTabIsHome,
                onClick = onFilesClick,
                testTag = "nav_files_button"
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = LocalSmartPdfColors.current
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primaryBlue else colors.textSecondary,
        label = "navTint"
    )
    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateActionBottomSheet(
    onDismiss: () -> Unit,
    onCreateFromImages: () -> Unit,
    onScanDocument: () -> Unit,
    onImportPdf: () -> Unit,
    onMergePdf: () -> Unit,
    onCreateBlankPdf: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Create or Add PDF",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            SheetActionRow(
                icon = Icons.Filled.Image,
                accentColor = FeatureBlue,
                title = "Create PDF from Images",
                subtitle = "Convert JPG, PNG & WEBP photos into a PDF",
                testTag = "sheet_image_to_pdf",
                onClick = {
                    onDismiss()
                    onCreateFromImages()
                }
            )
            SheetActionRow(
                icon = Icons.Filled.DocumentScanner,
                accentColor = FeatureCyan,
                title = "Scan Document",
                subtitle = "Camera scanner with edge detection & filters",
                testTag = "sheet_scan_doc",
                onClick = {
                    onDismiss()
                    onScanDocument()
                }
            )
            SheetActionRow(
                icon = Icons.Filled.FileDownload,
                accentColor = FeatureIndigo,
                title = "Import PDF",
                subtitle = "Open from Storage, Downloads, or Documents",
                testTag = "sheet_import_pdf",
                onClick = {
                    onDismiss()
                    onImportPdf()
                }
            )
            SheetActionRow(
                icon = Icons.Filled.MergeType,
                accentColor = FeatureCoral,
                title = "Merge PDF",
                subtitle = "Combine multiple PDF files in custom order",
                testTag = "sheet_merge_pdf",
                onClick = {
                    onDismiss()
                    onMergePdf()
                }
            )
            SheetActionRow(
                icon = Icons.Filled.NoteAdd,
                accentColor = FeatureTeal,
                title = "Create Blank PDF",
                subtitle = "Start with clean blank or ruled pages",
                testTag = "sheet_blank_pdf",
                onClick = {
                    onDismiss()
                    onCreateBlankPdf()
                }
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    Surface(
        onClick = onClick,
        color = colors.secondaryBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun PdfListItemCard(
    document: PdfDocumentEntity,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmartPdfColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { if (selectionMode) onToggleSelect() else onClick() },
        color = if (isSelected) colors.primaryBlue.copy(alpha = 0.15f) else colors.secondaryBackground,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(1.5.dp, colors.primaryBlue) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pdf_item_${document.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                IconButton(
                    onClick = onToggleSelect,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) colors.primaryBlue else colors.textSecondary
                    )
                }
            }

            // Thumbnail Preview
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, colors.divider, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = document.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists() && !document.isEncrypted) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = "${document.name} preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = if (document.isEncrypted) Icons.Filled.Lock else Icons.Filled.PictureAsPdf,
                        contentDescription = "PDF thumbnail",
                        tint = if (document.isEncrypted) FeatureCoral else FeatureBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} • ${StorageHelper.formatFileSize(document.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = StorageHelper.formatDateTime(document.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }

            if (!selectionMode) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("share_pdf_${document.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share PDF",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("menu_pdf_${document.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = colors.cardBackground
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open PDF", color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.OpenInNew, null, tint = colors.primaryBlue)
                            },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.DriveFileRenameOutline, null, tint = FeatureCyan)
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, null, tint = FeatureIndigo)
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Details", color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, null, tint = colors.textSecondary)
                            },
                            onClick = {
                                menuExpanded = false
                                onDetails()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = ErrorRed) },
                            leadingIcon = {
                                Icon(Icons.Filled.DeleteOutline, null, tint = ErrorRed)
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPdfStateView(
    message: String = "No PDF files found. Create your first PDF.",
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmartPdfColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_pdfs),
            contentDescription = "No PDFs illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(actionLabel, color = Color.White)
            }
        }
    }
}

@Composable
fun RenamePdfDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        title = { Text("Rename PDF", color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("File Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    count: Int,
    itemName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        title = {
            Text(
                if (count == 1) "Delete PDF?" else "Delete $count PDFs?",
                color = colors.textPrimary
            )
        },
        text = {
            Text(
                text = if (itemName != null && count == 1) {
                    "Are you sure you want to permanently delete \"$itemName\"?"
                } else {
                    "Are you sure you want to permanently delete $count selected PDF files?"
                },
                color = colors.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun MoveFolderDialog(
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    var folderName by remember { mutableStateOf(StorageHelper.ROOT_FOLDER_NAME) }
    val presets = (listOf(StorageHelper.ROOT_FOLDER_NAME, "Work", "Receipts", "Personal") + existingFolders).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        title = { Text("Move to Folder", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                presets.forEach { preset ->
                    Surface(
                        onClick = { folderName = preset },
                        color = if (folderName == preset) colors.primaryBlue.copy(alpha = 0.2f) else colors.secondaryBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = colors.primaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(preset, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName.ifBlank { StorageHelper.ROOT_FOLDER_NAME }) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Move", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun PdfDetailsModal(
    info: PdfDetailedInfo,
    onDismiss: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        title = { Text("Document Information", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("File Name", info.fileName)
                DetailRow("Folder", info.folderName)
                DetailRow("Pages", info.pageCount.toString())
                DetailRow("Size", StorageHelper.formatFileSize(info.fileSizeBytes))
                DetailRow("Dimensions", info.pageDimensions)
                DetailRow("Version", info.pdfVersion)
                DetailRow("Encrypted", if (info.isEncrypted) "Yes (Password Protected)" else "No")
                DetailRow("Author", info.author)
                DetailRow("Creator", info.creator)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = LocalSmartPdfColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun PdfGeneratedSuccessCard(
    document: PdfDocumentEntity,
    subtitle: String = "PDF created successfully",
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmartPdfColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(subtitle, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "${document.name}.pdf • ${document.pageCount} pages • ${StorageHelper.formatFileSize(document.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            val thumbFile = document.thumbnailPath?.let { File(it) }
            if (thumbFile != null && thumbFile.exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.secondaryBackground),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = "Generated PDF preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_open_pdf")
                ) {
                    Text("Open PDF", color = Color.White, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onSharePdf,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_share_pdf")
                ) {
                    Text("Share", color = colors.textPrimary, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onSavePdf,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PrimaryBlueLight.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_save_pdf")
                ) {
                    Text("Save", color = PrimaryBlueLight, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPdfPickerSheet(
    documents: List<PdfDocumentEntity>,
    onDismiss: () -> Unit,
    onSelect: (PdfDocumentEntity) -> Unit,
    onImportExternal: (() -> Unit)? = null
) {
    val colors = LocalSmartPdfColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select PDF Document", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                if (onImportExternal != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onImportExternal()
                    }) {
                        Text("Browse Device", color = PrimaryBlueLight)
                    }
                }
            }
            if (documents.isEmpty()) {
                EmptyPdfStateView(
                    message = "No PDFs in your library yet. Browse device storage to pick one.",
                    actionLabel = if (onImportExternal != null) "Browse Device" else null,
                    onActionClick = onImportExternal?.let { fn ->
                        {
                            onDismiss()
                            fn()
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        Surface(
                            onClick = {
                                onSelect(doc)
                                onDismiss()
                            },
                            color = colors.secondaryBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = PrimaryBlueLight
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        doc.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${doc.pageCount} pages • ${StorageHelper.formatFileSize(doc.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun sortDocuments(docs: List<PdfDocumentEntity>, order: SortOrder): List<PdfDocumentEntity> {
    return when (order) {
        SortOrder.NEWEST_FIRST -> docs.sortedByDescending { it.createdAt }
        SortOrder.OLDEST_FIRST -> docs.sortedBy { it.createdAt }
        SortOrder.NAME_AZ -> docs.sortedBy { it.name.lowercase() }
        SortOrder.LARGEST_FIRST -> docs.sortedByDescending { it.sizeBytes }
        SortOrder.SMALLEST_FIRST -> docs.sortedBy { it.sizeBytes }
    }
}
