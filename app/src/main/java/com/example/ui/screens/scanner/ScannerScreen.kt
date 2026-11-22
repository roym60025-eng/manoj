package com.example.ui.screens.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.NormalizedQuad
import com.example.data.model.PdfDocumentEntity
import com.example.data.model.ScanFilter
import com.example.data.repository.PdfRepository
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.FeatureCyan
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onGalleryImagePicked(it) }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = state.generatedDocument
        if (uri != null && doc != null) {
            viewModel.exportToUserUri(doc, uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Scanned PDF saved" else "Save failed"
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.stage) {
                            ScannerStage.CAMERA -> "Smart Scanner"
                            ScannerStage.CROP_QUAD -> "Adjust Document Edges"
                            ScannerStage.ENHANCE_AND_PAGES -> "Scan Preview (${state.pages.size})"
                        },
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (state.stage == ScannerStage.CAMERA) {
                        TextButton(onClick = {
                            galleryImportLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Text("From Photo", color = PrimaryBlueLight)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.stage) {
                ScannerStage.CAMERA -> {
                    if (hasCameraPermission) {
                        CameraCaptureView(
                            repository = repository,
                            pagesCount = state.pages.size,
                            onCaptured = { file -> viewModel.onCapturedFile(file) },
                            onImportPhoto = {
                                galleryImportLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    } else {
                        CameraPermissionRationaleView(
                            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            onOpenSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            onImportPhoto = {
                                galleryImportLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }

                ScannerStage.CROP_QUAD -> {
                    val rawPath = state.pendingRawPath
                    if (rawPath != null) {
                        PerspectiveCropStageView(
                            rawFilePath = rawPath,
                            quad = state.pendingQuad,
                            onQuadChanged = { viewModel.updatePendingQuad(it) },
                            onAutoDetect = { viewModel.autoDetectPendingQuad() },
                            onConfirmCrop = { viewModel.confirmPerspectiveCrop() },
                            onRetake = { viewModel.addAnotherPage() }
                        )
                    }
                }

                ScannerStage.ENHANCE_AND_PAGES -> {
                    EnhanceAndExportStageView(
                        state = state,
                        repository = repository,
                        onSelectPage = { viewModel.selectPage(it) },
                        onRotate = { viewModel.updateActivePageAdjustments(rotateDelta = 90) },
                        onFilterChange = { viewModel.updateActivePageAdjustments(filter = it) },
                        onBrightnessChange = { viewModel.updateActivePageAdjustments(brightness = it) },
                        onContrastChange = { viewModel.updateActivePageAdjustments(contrast = it) },
                        onDeletePage = { viewModel.deleteActivePage() },
                        onAddPage = { viewModel.addAnotherPage() },
                        onFileNameChange = { viewModel.updateFileName(it) },
                        onExportPdf = { viewModel.exportToPdf() },
                        onOpenPdf = onOpenPdf,
                        onSharePdf = { doc ->
                            ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc)
                        },
                        onSavePdf = { doc -> savePdfLauncher.launch("${doc.name}.pdf") }
                    )
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
                            Text("Processing document...", color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionRationaleView(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportPhoto: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = PrimaryBlue, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Grant camera access to scan physical documents with edge detection, perspective correction, and filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Allow Camera", color = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onImportPhoto,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.PhotoLibrary, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan from Gallery Photo", color = colors.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Open App Settings", color = PrimaryBlueLight)
        }
    }
}

@Composable
private fun CameraCaptureView(
    repository: PdfRepository,
    pagesCount: Int,
    onCaptured: (File) -> Unit,
    onImportPhoto: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var flashEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(flashEnabled) {
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                imageCapture = capture
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
            }.onFailure {
                onError("Camera unavailable on this device — use 'From Photo' to scan an image.")
            }
        }
        providerFuture.addListener(listener, executor)
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Document framing guide overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padX = size.width * 0.09f
            val padY = size.height * 0.14f
            drawRect(
                color = FeatureCyan.copy(alpha = 0.75f),
                topLeft = Offset(padX, padY),
                size = androidx.compose.ui.geometry.Size(size.width - padX * 2f, size.height - padY * 2f),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Bottom camera controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onImportPhoto,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(Icons.Filled.PhotoLibrary, "Pick photo", tint = Color.White)
            }

            // Capture shutter
            Surface(
                onClick = {
                    val cap = imageCapture
                    if (cap == null) {
                        onError("Camera is still initializing")
                        return@Surface
                    }
                    val outFile = File(repository.storageHelper.tempDir, "cap_${UUID.randomUUID()}.jpg")
                    val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                    cap.takePicture(
                        opts,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptured(outFile)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onError(exception.message ?: "Capture failed")
                            }
                        }
                    )
                },
                shape = CircleShape,
                color = PrimaryBlue,
                border = BorderStroke(4.dp, Color.White),
                modifier = Modifier
                    .size(72.dp)
                    .testTag("scanner_capture_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (pagesCount > 0) {
                        Text(
                            "$pagesCount",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(
                onClick = { flashEnabled = !flashEnabled },
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Toggle flash",
                    tint = if (flashEnabled) Color.Yellow else Color.White
                )
            }
        }
    }
}

@Composable
private fun PerspectiveCropStageView(
    rawFilePath: String,
    quad: NormalizedQuad,
    onQuadChanged: (NormalizedQuad) -> Unit,
    onAutoDetect: () -> Unit,
    onConfirmCrop: () -> Unit,
    onRetake: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val bitmap = remember(rawFilePath) { BitmapFactory.decodeFile(rawFilePath) }
    var activeCorner by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Drag any corner circle to align the document edges for perspective correction.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured scan",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(quad) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val corners = listOf(
                                    Offset(quad.topLeftX * w, quad.topLeftY * h),
                                    Offset(quad.topRightX * w, quad.topRightY * h),
                                    Offset(quad.bottomRightX * w, quad.bottomRightY * h),
                                    Offset(quad.bottomLeftX * w, quad.bottomLeftY * h)
                                )
                                var bestIdx = -1
                                var bestDist = 110f
                                corners.forEachIndexed { idx, c ->
                                    val d = hypot(pos.x - c.x, pos.y - c.y)
                                    if (d < bestDist) {
                                        bestDist = d
                                        bestIdx = idx
                                    }
                                }
                                activeCorner = bestIdx
                            },
                            onDrag = { change, dragAmount ->
                                if (activeCorner < 0) return@detectDragGestures
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val dx = dragAmount.x / w
                                val dy = dragAmount.y / h
                                val next = when (activeCorner) {
                                    0 -> quad.copy(
                                        topLeftX = (quad.topLeftX + dx).coerceIn(0.01f, 0.8f),
                                        topLeftY = (quad.topLeftY + dy).coerceIn(0.01f, 0.8f)
                                    )
                                    1 -> quad.copy(
                                        topRightX = (quad.topRightX + dx).coerceIn(0.2f, 0.99f),
                                        topRightY = (quad.topRightY + dy).coerceIn(0.01f, 0.8f)
                                    )
                                    2 -> quad.copy(
                                        bottomRightX = (quad.bottomRightX + dx).coerceIn(0.2f, 0.99f),
                                        bottomRightY = (quad.bottomRightY + dy).coerceIn(0.2f, 0.99f)
                                    )
                                    3 -> quad.copy(
                                        bottomLeftX = (quad.bottomLeftX + dx).coerceIn(0.01f, 0.8f),
                                        bottomLeftY = (quad.bottomLeftY + dy).coerceIn(0.2f, 0.99f)
                                    )
                                    else -> quad
                                }
                                onQuadChanged(next)
                            },
                            onDragEnd = { activeCorner = -1 }
                        )
                    }
            ) {
                val tl = Offset(quad.topLeftX * size.width, quad.topLeftY * size.height)
                val tr = Offset(quad.topRightX * size.width, quad.topRightY * size.height)
                val br = Offset(quad.bottomRightX * size.width, quad.bottomRightY * size.height)
                val bl = Offset(quad.bottomLeftX * size.width, quad.bottomLeftY * size.height)

                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }
                drawPath(path, color = FeatureCyan, style = Stroke(width = 3.dp.toPx()))
                listOf(tl, tr, br, bl).forEach { pt ->
                    drawCircle(color = PrimaryBlue, radius = 14.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = 14.dp.toPx(), center = pt, style = Stroke(2.5.dp.toPx()))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.weight(1f)
            ) {
                Text("Retake", color = colors.textPrimary)
            }
            OutlinedButton(
                onClick = onAutoDetect,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FeatureCyan.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AutoFixHigh, null, tint = FeatureCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Auto", color = FeatureCyan)
            }
            Button(
                onClick = onConfirmCrop,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Crop", color = Color.White)
            }
        }
    }
}

@Composable
private fun EnhanceAndExportStageView(
    state: ScannerUiState,
    repository: PdfRepository,
    onSelectPage: (Int) -> Unit,
    onRotate: () -> Unit,
    onFilterChange: (ScanFilter) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onDeletePage: () -> Unit,
    onAddPage: () -> Unit,
    onFileNameChange: (String) -> Unit,
    onExportPdf: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit,
    onSharePdf: (PdfDocumentEntity) -> Unit,
    onSavePdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val activePage = state.pages.getOrNull(state.activePageIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        state.generatedDocument?.let { doc ->
            PdfGeneratedSuccessCard(
                document = doc,
                subtitle = "Scanned PDF saved!",
                onOpenPdf = { onOpenPdf(doc) },
                onSharePdf = { onSharePdf(doc) },
                onSavePdf = { onSavePdf(doc) }
            )
        }

        if (activePage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(activePage.processedFilePath),
                    contentDescription = "Scanned page preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }

            // Multi-page strip
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.pages) { idx, page ->
                    val selected = idx == state.activePageIndex
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 74.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) PrimaryBlue else colors.divider,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectPage(idx) }
                    ) {
                        AsyncImage(
                            model = File(page.processedFilePath),
                            contentDescription = "Page ${idx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                item {
                    Surface(
                        onClick = onAddPage,
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PrimaryBlueLight.copy(alpha = 0.6f)),
                        modifier = Modifier.size(width = 56.dp, height = 74.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.AddAPhoto, "Add page", tint = PrimaryBlueLight, modifier = Modifier.size(20.dp))
                            Text("Add", style = MaterialTheme.typography.labelSmall, color = PrimaryBlueLight)
                        }
                    }
                }
            }

            // Enhancement Controls
            Surface(
                color = colors.cardBackground,
                shape = RoundedCornerShape(20.dp),
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
                        Text("Filters & Adjustments", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Row {
                            IconButton(onClick = onRotate) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate", tint = PrimaryBlueLight)
                            }
                            IconButton(onClick = onDeletePage) {
                                Icon(Icons.Filled.DeleteOutline, "Delete page", tint = ErrorRed)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScanFilter.entries.forEach { filter ->
                            val selected = activePage.filter == filter
                            FilterChip(
                                selected = selected,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = colors.secondaryBackground,
                                    labelColor = colors.textSecondary
                                )
                            )
                        }
                    }

                    Text("Brightness", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Slider(
                        value = activePage.brightness,
                        onValueChange = onBrightnessChange,
                        valueRange = -70f..70f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                    )

                    Text("Contrast", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Slider(
                        value = activePage.contrast,
                        onValueChange = onContrastChange,
                        valueRange = 0.6f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                    )

                    OutlinedTextField(
                        value = state.fileName,
                        onValueChange = onFileNameChange,
                        label = { Text("PDF File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("export_scan_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Scanned PDF (${state.pages.size} Pages)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
