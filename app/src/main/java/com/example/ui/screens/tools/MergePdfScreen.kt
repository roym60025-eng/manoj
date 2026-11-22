package com.example.ui.screens.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.StorageHelper
import com.example.ui.components.LibraryPdfPickerSheet
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allDocs by repository.allDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPdfs by remember { mutableStateOf<List<PdfDocumentEntity>>(emptyList()) }
    var outputName by remember { mutableStateOf("Merged_Document") }
    var showPicker by remember { mutableStateOf(false) }
    var isMerging by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mergedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val importMultiPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imported = uris.mapNotNull { uri ->
                    runCatching { repository.importPdfFromUri(uri) }.getOrNull()
                }
                selectedPdfs = selectedPdfs + imported
                mergedDoc = null
            }
        }
    }

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = mergedDoc
        if (uri != null && doc != null) {
            scope.launch {
                val ok = runCatching { repository.exportPdfToUserUri(doc, uri) }.isSuccess
                snackbarHostState.showSnackbar(if (ok) "Merged PDF saved" else "Save failed")
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Merge PDFs", color = colors.textPrimary) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                mergedDoc?.let { doc ->
                    PdfGeneratedSuccessCard(
                        document = doc,
                        subtitle = "PDFs merged successfully!",
                        onOpenPdf = { onOpenPdf(doc) },
                        onSharePdf = { ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc) },
                        onSavePdf = { saveSafLauncher.launch("${doc.name}.pdf") }
                    )
                }

                Surface(
                    color = colors.cardBackground,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Add PDF Files (${selectedPdfs.size} selected)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("merge_add_from_library")
                            ) {
                                Icon(Icons.Filled.Add, null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Library", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { importMultiPdfLauncher.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.divider),
                                modifier = Modifier.weight(1f).testTag("merge_add_from_device")
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, tint = colors.textPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("Device", color = colors.textPrimary)
                            }
                        }

                        if (selectedPdfs.isEmpty()) {
                            Text(
                                "Select at least 2 PDF files to merge them in your preferred sequence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        } else {
                            selectedPdfs.forEachIndexed { idx, doc ->
                                Surface(
                                    color = colors.secondaryBackground,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, null, tint = PrimaryBlueLight)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${idx + 1}. ${doc.name}.pdf", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                            Text(
                                                "${doc.pageCount} pages • ${StorageHelper.formatFileSize(doc.sizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary
                                            )
                                        }
                                        if (idx > 0) {
                                            IconButton(onClick = {
                                                val list = selectedPdfs.toMutableList()
                                                val item = list.removeAt(idx)
                                                list.add(idx - 1, item)
                                                selectedPdfs = list
                                            }) {
                                                Icon(Icons.Filled.ArrowUpward, "Move up", tint = colors.textSecondary)
                                            }
                                        }
                                        if (idx < selectedPdfs.lastIndex) {
                                            IconButton(onClick = {
                                                val list = selectedPdfs.toMutableList()
                                                val item = list.removeAt(idx)
                                                list.add(idx + 1, item)
                                                selectedPdfs = list
                                            }) {
                                                Icon(Icons.Filled.ArrowDownward, "Move down", tint = colors.textSecondary)
                                            }
                                        }
                                        IconButton(onClick = {
                                            selectedPdfs = selectedPdfs.toMutableList().apply { removeAt(idx) }
                                        }) {
                                            Icon(Icons.Filled.Close, "Remove", tint = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedPdfs.size >= 2) {
                    OutlinedTextField(
                        value = outputName,
                        onValueChange = { outputName = it },
                        label = { Text("Merged Output Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isMerging) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = PrimaryBlue,
                            trackColor = colors.secondaryBackground,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isMerging = true
                                    progress = 0f
                                    runCatching {
                                        repository.mergePdfs(
                                            sources = selectedPdfs.map { File(it.filePath) },
                                            outputFileName = outputName.ifBlank { "Merged_Document" },
                                            onProgress = { progress = it }
                                        )
                                    }.onSuccess { doc ->
                                        mergedDoc = doc
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Merge failed")
                                    }
                                    isMerging = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("merge_pdfs_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Merge ${selectedPdfs.size} PDFs", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        LibraryPdfPickerSheet(
            documents = allDocs,
            onDismiss = { showPicker = false },
            onSelect = { doc ->
                selectedPdfs = selectedPdfs + doc
                mergedDoc = null
            }
        )
    }
}
