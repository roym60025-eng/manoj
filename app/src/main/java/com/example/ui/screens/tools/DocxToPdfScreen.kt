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
import androidx.compose.material.icons.filled.Description
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
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.FeatureTeal
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxToPdfScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedDocxUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDocxName by remember { mutableStateOf("") }
    var outputName by remember { mutableStateOf("Converted_Word_Doc") }
    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var generatedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val docxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedDocxUri = uri
            val display = repository.storageHelper.getDisplayNameFromUri(uri)
            selectedDocxName = "$display.docx"
            outputName = display
            generatedDoc = null
        }
    }

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = generatedDoc
        if (uri != null && doc != null) {
            scope.launch {
                val ok = runCatching { repository.exportPdfToUserUri(doc, uri) }.isSuccess
                snackbarHostState.showSnackbar(if (ok) "PDF saved" else "Save failed")
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("DOCX to PDF", color = colors.textPrimary) },
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
                        Text(
                            "Convert Word Document (.docx) to PDF",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            "Preserves headings, text styling, tables, bullet lists, and embedded images.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )

                        OutlinedButton(
                            onClick = {
                                docxPickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, FeatureTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("pick_docx_button")
                        ) {
                            Icon(Icons.Filled.Description, null, tint = FeatureTeal)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedDocxName.isBlank()) "Select DOCX Document" else selectedDocxName,
                                color = FeatureTeal
                            )
                        }

                        if (selectedDocxUri != null) {
                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { outputName = it },
                                label = { Text("Output PDF Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (selectedDocxUri != null) {
                    if (isConverting) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = PrimaryBlue,
                                trackColor = colors.secondaryBackground,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                            )
                            Text(
                                "Converting DOCX (${(progress * 100).roundToInt()}%)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val uri = selectedDocxUri ?: return@Button
                                scope.launch {
                                    isConverting = true
                                    progress = 0f
                                    runCatching {
                                        repository.convertDocxToPdf(
                                            docxUri = uri,
                                            outputFileName = outputName.ifBlank { "Converted_Word_Doc" },
                                            onProgress = { progress = it }
                                        )
                                    }.onSuccess { doc ->
                                        generatedDoc = doc
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "DOCX conversion failed")
                                    }
                                    isConverting = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("convert_docx_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Convert to PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                generatedDoc?.let { doc ->
                    PdfGeneratedSuccessCard(
                        document = doc,
                        subtitle = "DOCX converted to PDF!",
                        onOpenPdf = { onOpenPdf(doc) },
                        onSharePdf = { ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc) },
                        onSavePdf = { saveSafLauncher.launch("${doc.name}.pdf") }
                    )
                }
            }
        }
    }
}
