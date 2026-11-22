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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.CompressionLevel
import com.example.data.model.PdfCompressionResult
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.StorageHelper
import com.example.ui.components.LibraryPdfPickerSheet
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressPdfScreen(
    repository: PdfRepository,
    preselectedId: Long? = null,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allDocs by repository.allDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var level by remember { mutableStateOf(CompressionLevel.MEDIUM_QUALITY) }
    var overwriteOriginal by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<PdfCompressionResult?>(null) }

    LaunchedEffect(preselectedId, allDocs) {
        if (selectedDoc == null && preselectedId != null) {
            selectedDoc = allDocs.firstOrNull { it.id == preselectedId }
        }
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.importPdfFromUri(uri) }
                    .onSuccess {
                        selectedDoc = it
                        result = null
                    }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Import failed") }
            }
        }
    }

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = result?.document
        if (uri != null && doc != null) {
            scope.launch {
                val ok = runCatching { repository.exportPdfToUserUri(doc, uri) }.isSuccess
                snackbarHostState.showSnackbar(if (ok) "Compressed PDF saved" else "Save failed")
            }
        }
    }

    fun runCompression(confirmedOverwrite: Boolean) {
        val doc = selectedDoc ?: return
        scope.launch {
            isProcessing = true
            progress = 0f
            runCatching {
                repository.compressPdf(
                    source = doc,
                    level = level,
                    overwriteOriginal = confirmedOverwrite,
                    onProgress = { p -> progress = p }
                )
            }.onSuccess { res ->
                result = res
            }.onFailure { err ->
                snackbarHostState.showSnackbar(err.message ?: "Compression failed")
            }
            isProcessing = false
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Compress PDF", color = colors.textPrimary) },
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
                Surface(
                    color = colors.cardBackground,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Selected PDF", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        val doc = selectedDoc
                        if (doc == null) {
                            OutlinedButton(
                                onClick = { showPicker = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PrimaryBlueLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("compress_select_pdf")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, tint = PrimaryBlueLight)
                                Spacer(Modifier.width(8.dp))
                                Text("Choose PDF File", color = PrimaryBlueLight)
                            }
                        } else {
                            Surface(
                                color = colors.secondaryBackground,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = PrimaryBlueLight)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${doc.name}.pdf", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                        Text(
                                            "Original size: ${StorageHelper.formatFileSize(doc.sizeBytes)} • ${doc.pageCount} pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                    TextButton(onClick = { showPicker = true }) {
                                        Text("Change", color = PrimaryBlueLight)
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = colors.cardBackground,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Compression Quality", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        CompressionLevel.entries.forEach { option ->
                            Surface(
                                onClick = { level = option },
                                color = if (level == option) PrimaryBlue.copy(alpha = 0.16f) else colors.secondaryBackground,
                                shape = RoundedCornerShape(14.dp),
                                border = if (level == option) BorderStroke(1.5.dp, PrimaryBlue) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = level == option,
                                        onClick = { level = option },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(option.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                        Text(option.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Replace original file", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                Text(
                                    "Requires confirmation before modifying original",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = overwriteOriginal,
                                onCheckedChange = { overwriteOriginal = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
                            )
                        }
                    }
                }

                if (isProcessing) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = PrimaryBlue,
                        trackColor = colors.secondaryBackground,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    )
                } else {
                    Button(
                        onClick = {
                            if (overwriteOriginal) {
                                showOverwriteConfirm = true
                            } else {
                                runCompression(false)
                            }
                        },
                        enabled = selectedDoc != null,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("compress_now_button")
                    ) {
                        Icon(Icons.Filled.Compress, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Compress PDF", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                result?.let { res ->
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Compression Summary", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Original: ${StorageHelper.formatFileSize(res.originalSizeBytes)}", color = colors.textSecondary)
                                Text("Compressed: ${StorageHelper.formatFileSize(res.compressedSizeBytes)}", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Reduced by ${res.reductionPercent}%",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PrimaryBlueLight
                            )
                        }
                    }

                    PdfGeneratedSuccessCard(
                        document = res.document,
                        subtitle = "Compressed PDF ready",
                        onOpenPdf = { onOpenPdf(res.document) },
                        onSharePdf = { ShareAndPrintHelper.sharePdf(context, repository.storageHelper, res.document) },
                        onSavePdf = { saveSafLauncher.launch("${res.document.name}.pdf") }
                    )
                }
            }
        }
    }

    if (showPicker) {
        LibraryPdfPickerSheet(
            documents = allDocs,
            onDismiss = { showPicker = false },
            onSelect = {
                selectedDoc = it
                result = null
            },
            onImportExternal = { importSafLauncher.launch(arrayOf("application/pdf")) }
        )
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            containerColor = colors.cardBackground,
            title = { Text("Overwrite Original PDF?", color = colors.textPrimary) },
            text = {
                Text(
                    "This will permanently replace \"${selectedDoc?.name}.pdf\" with the compressed version.",
                    color = WarningOrange
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteConfirm = false
                        runCompression(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Confirm Overwrite", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}
