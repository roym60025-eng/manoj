package com.example.ui.screens.autocrop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.AutoCropEnhancement
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.ui.components.LibraryPdfPickerSheet
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.FeatureCyan
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCropScreen(
    viewModel: AutoCropViewModel,
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPdfPicker by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.addImages(uris)
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addImages(uris)
    }

    val externalPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addPdfUri(it) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved) {
            pendingCameraFile?.let { viewModel.addCameraFile(it) }
        }
        pendingCameraFile = null
    }

    fun launchCameraCapture() {
        val file = File(repository.storageHelper.tempDir, "ac_cam_${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        val uri = repository.storageHelper.getFileProviderUri(file)
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Camera permission denied. Use Gallery or Files to add photos.")
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = state.generatedDocument
        if (uri != null && doc != null) {
            viewModel.exportToUserUri(doc, uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "PDF saved to selected folder" else "Failed to save PDF"
                    )
                }
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Auto Crop & Straighten", color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text("Add Photos", color = PrimaryBlueLight)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.generatedDocument?.let { doc ->
                    item {
                        PdfGeneratedSuccessCard(
                            document = doc,
                            subtitle = "Straightened PDF created!",
                            onOpenPdf = { onOpenPdf(doc) },
                            onSharePdf = {
                                ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc)
                            },
                            onSavePdf = { savePdfLauncher.launch("${doc.name}.pdf") }
                        )
                    }
                }

                // Add Document Source Card
                item {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(FeatureCyan.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.CropRotate, null, tint = FeatureCyan, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Automatic Tilt & Margin Fix",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        "Add any tilted photo or crooked PDF—edges and skew are fixed automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("autocrop_pick_gallery")
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gallery", color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        filesLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, colors.divider),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("autocrop_pick_files")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Files", color = colors.textPrimary)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            launchCameraCapture()
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, colors.divider),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("autocrop_pick_camera")
                                ) {
                                    Icon(Icons.Filled.CameraAlt, null, tint = PrimaryBlueLight, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Camera", color = colors.textPrimary)
                                }
                                OutlinedButton(
                                    onClick = { showPdfPicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, FeatureCyan.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("autocrop_pick_pdf")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = FeatureCyan, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("From PDF", color = FeatureCyan)
                                }
                            }
                        }
                    }
                }

                val activePage = state.pages.getOrNull(state.activeIndex)
                if (activePage == null) {
                    item {
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlue.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.AutoFixHigh, null, tint = PrimaryBlueLight, modifier = Modifier.size(32.dp))
                                }
                                Text(
                                    "No Document Added Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    "Pick a skewed document photo or crooked PDF above. Tilt angle and perspective borders are straightened automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Before / After Preview Card
                    item {
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${activePage.label} (${state.activeIndex + 1}/${state.pages.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            activePage.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SuccessGreen
                                        )
                                    }
                                    Surface(
                                        onClick = { viewModel.setShowOriginal(!state.showOriginal) },
                                        color = if (state.showOriginal) colors.secondaryBackground else PrimaryBlue.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("autocrop_compare_toggle")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Compare,
                                                "Compare before and after",
                                                tint = if (state.showOriginal) colors.textSecondary else PrimaryBlueLight,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (state.showOriginal) "Before" else "After",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (state.showOriginal) colors.textPrimary else PrimaryBlueLight,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val displayPath = if (state.showOriginal) {
                                        activePage.originalFilePath
                                    } else {
                                        activePage.croppedFilePath
                                    }
                                    AsyncImage(
                                        model = File(displayPath),
                                        contentDescription = if (state.showOriginal) "Original document" else "Auto-cropped document",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                }

                                if (state.pages.size > 1) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, page ->
                                            val selected = idx == state.activeIndex
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 56.dp, height = 74.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (selected) 2.dp else 1.dp,
                                                        color = if (selected) PrimaryBlue else colors.divider,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { viewModel.selectPage(idx) }
                                            ) {
                                                AsyncImage(
                                                    model = File(page.croppedFilePath),
                                                    contentDescription = "Page ${idx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }

                                // Fine-tune / Page actions row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.openQuadEditor() },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colors.divider),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("autocrop_adjust_corners")
                                    ) {
                                        Icon(Icons.Filled.Crop, null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Adjust", color = colors.textPrimary, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.reRunAutoCropActivePage() },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, FeatureCyan.copy(alpha = 0.55f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("autocrop_reauto_button")
                                    ) {
                                        Icon(Icons.Filled.AutoFixHigh, null, tint = FeatureCyan, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Auto", color = FeatureCyan, maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = { viewModel.rotateActivePage() },
                                        modifier = Modifier.testTag("autocrop_rotate_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate 90°", tint = PrimaryBlueLight)
                                    }
                                    IconButton(onClick = { viewModel.removeActivePage() }) {
                                        Icon(Icons.Filled.DeleteOutline, "Remove page", tint = ErrorRed)
                                    }
                                }
                            }
                        }
                    }

                    // Output & Enhancement Options Card
                    item {
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Enhancement & Export", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AutoCropEnhancement.entries.forEach { mode ->
                                        val selected = state.globalEnhancement == mode
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.setEnhancement(mode) },
                                            label = { Text(mode.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = colors.secondaryBackground,
                                                labelColor = colors.textSecondary
                                            )
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Auto-Trim Dark Margins", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                        Text("Removes black scanner bars & outer edges", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                    Switch(
                                        checked = state.trimUniformMargin,
                                        onCheckedChange = { viewModel.toggleTrimMargin(it) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
                                    )
                                }

                                OutlinedTextField(
                                    value = state.fileName,
                                    onValueChange = { viewModel.updateFileName(it) },
                                    label = { Text("Output File Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryBlue,
                                        unfocusedBorderColor = colors.divider,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("autocrop_filename_input")
                                )

                                Button(
                                    onClick = { viewModel.exportAsPdf() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("autocrop_export_pdf_button")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Save as Straightened PDF (${state.pages.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.exportAsImages() },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.divider),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("autocrop_save_images_button")
                                ) {
                                    Icon(Icons.Filled.Image, null, tint = PrimaryBlueLight, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save Fixed Images to Pictures", color = colors.textPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(state.processingLabel.ifBlank { "Auto-cropping..." }, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showPdfPicker) {
        LibraryPdfPickerSheet(
            documents = libraryDocs,
            onDismiss = { showPdfPicker = false },
            onSelect = { doc -> viewModel.addPdfDocument(doc) },
            onImportExternal = { externalPdfLauncher.launch(arrayOf("application/pdf")) }
        )
    }

    val editingPage = state.pages.firstOrNull { it.id == state.editingQuadPageId }
    if (editingPage != null) {
        AdjustQuadDialog(
            page = editingPage,
            onDismiss = { viewModel.closeQuadEditor() },
            onApply = { quad -> viewModel.applyManualQuad(editingPage.id, quad) }
        )
    }
}
