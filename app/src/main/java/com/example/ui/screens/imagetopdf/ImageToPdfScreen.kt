package com.example.ui.screens.imagetopdf

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageToPdfPage
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.ImageProcessingHelper
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    viewModel: ImageToPdfViewModel,
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenPdf: (PdfDocumentEntity) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.addImages(uris)
    }

    val safImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addImages(uris)
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

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Image to PDF", color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch(
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
                // Result Card if PDF was just generated
                state.generatedDocument?.let { doc ->
                    item {
                        PdfGeneratedSuccessCard(
                            document = doc,
                            subtitle = "Images converted to PDF!",
                            onOpenPdf = { onOpenPdf(doc) },
                            onSharePdf = {
                                ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc)
                            },
                            onSavePdf = { savePdfLauncher.launch("${doc.name}.pdf") }
                        )
                    }
                }

                // Picker buttons card
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
                            Text(
                                text = "Select Images (JPG, JPEG, PNG, WEBP)",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pick_gallery_images")
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gallery", color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        safImageLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, colors.divider),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pick_files_images")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Files", color = colors.textPrimary)
                                }
                            }
                            if (state.pages.isNotEmpty()) {
                                Text(
                                    text = "${state.pages.size} image(s) selected • Long-press & drag or use arrows to reorder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Surface(
                                color = colors.secondaryBackground,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoFixHigh,
                                            contentDescription = "Auto Crop",
                                            tint = com.example.ui.theme.FeatureCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Auto Crop & Straighten",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                "Fix tilted documents automatically",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                    if (state.isAutoCropping) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                            color = PrimaryBlue
                                        )
                                    } else {
                                        Switch(
                                            checked = state.autoCropEnabled,
                                            onCheckedChange = { viewModel.toggleAutoCrop(it) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                                            modifier = Modifier.testTag("image_to_pdf_autocrop_switch")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected Images List with Drag & Drop Reorder, Rotate, Crop, Remove
                if (state.pages.isNotEmpty()) {
                    itemsIndexed(state.pages, key = { _, p -> p.id }) { index, page ->
                        val isDragging = draggingIndex == index
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(18.dp),
                            border = if (isDragging) BorderStroke(1.5.dp, PrimaryBlue) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                                .pointerInput(state.pages.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val rowStepPx = 110.dp.toPx()
                                            val curIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                            if (dragOffsetY > rowStepPx && curIdx < state.pages.lastIndex) {
                                                viewModel.movePage(curIdx, curIdx + 1)
                                                draggingIndex = curIdx + 1
                                                dragOffsetY -= rowStepPx
                                            } else if (dragOffsetY < -rowStepPx && curIdx > 0) {
                                                viewModel.movePage(curIdx, curIdx - 1)
                                                draggingIndex = curIdx - 1
                                                dragOffsetY += rowStepPx
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        }
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.secondaryBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val previewModel: Any = page.autoCroppedFilePath?.let { java.io.File(it) } ?: page.uri
                                    AsyncImage(
                                        model = previewModel,
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(page.rotationDegrees.toFloat())
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Page ${index + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    val tags = buildList {
                                        if (page.autoCroppedFilePath != null) add("Auto-Cropped")
                                        if (page.rotationDegrees != 0) add("${page.rotationDegrees}°")
                                        if (!page.cropRect.isFull) add("Cropped")
                                    }.joinToString(" • ")
                                    Text(
                                        text = page.autoCropSummary?.takeIf { page.autoCroppedFilePath != null }
                                            ?: if (tags.isEmpty()) "Original" else tags,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (page.autoCroppedFilePath != null) com.example.ui.theme.FeatureCyan else colors.textSecondary
                                    )
                                }
                                if (index > 0) {
                                    IconButton(onClick = { viewModel.movePage(index, index - 1) }) {
                                        Icon(Icons.Filled.ArrowUpward, "Move up", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (index < state.pages.lastIndex) {
                                    IconButton(onClick = { viewModel.movePage(index, index + 1) }) {
                                        Icon(Icons.Filled.ArrowDownward, "Move down", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { viewModel.autoCropSinglePage(page.id) }) {
                                    Icon(Icons.Filled.AutoFixHigh, "Auto Crop page", tint = com.example.ui.theme.FeatureCyan, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.rotatePage(page.id) }) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate 90°", tint = PrimaryBlueLight, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.openCropEditor(page.id) }) {
                                    Icon(Icons.Filled.Crop, "Crop image", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.removePage(page.id) }) {
                                    Icon(Icons.Filled.Close, "Remove image", tint = ErrorRed, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    // PDF Configuration Options Card
                    item {
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("PDF Settings", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)

                                OutlinedTextField(
                                    value = state.config.fileName,
                                    onValueChange = { name -> viewModel.updateConfig { it.copy(fileName = name) } },
                                    label = { Text("PDF File Name") },
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
                                        .testTag("image_to_pdf_filename")
                                )

                                // Page Size
                                Text("Page Size", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(PageSizeOption.entries) { size ->
                                        val selected = state.config.pageSize == size
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.updateConfig { it.copy(pageSize = size) } },
                                            label = { Text(size.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = colors.secondaryBackground,
                                                labelColor = colors.textSecondary
                                            )
                                        )
                                    }
                                }

                                if (state.config.pageSize == PageSizeOption.CUSTOM) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = state.config.customWidthMm.toString(),
                                            onValueChange = { v ->
                                                val num = v.filter { it.isDigit() }.toIntOrNull() ?: 210
                                                viewModel.updateConfig { it.copy(customWidthMm = num) }
                                            },
                                            label = { Text("Width (mm)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = state.config.customHeightMm.toString(),
                                            onValueChange = { v ->
                                                val num = v.filter { it.isDigit() }.toIntOrNull() ?: 297
                                                viewModel.updateConfig { it.copy(customHeightMm = num) }
                                            },
                                            label = { Text("Height (mm)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // Orientation
                                Text("Orientation", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PageOrientation.entries.forEach { orient ->
                                        val selected = state.config.orientation == orient
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.updateConfig { it.copy(orientation = orient) } },
                                            label = { Text(orient.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = colors.secondaryBackground,
                                                labelColor = colors.textSecondary
                                            )
                                        )
                                    }
                                }

                                // Fit Mode
                                Text("Image Fitting Mode", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(ImageFitMode.entries) { fit ->
                                        val selected = state.config.fitMode == fit
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.updateConfig { it.copy(fitMode = fit) } },
                                            label = { Text(fit.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = colors.secondaryBackground,
                                                labelColor = colors.textSecondary
                                            )
                                        )
                                    }
                                }

                                // Image Quality Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Image Quality", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                    Text("${state.config.imageQuality}%", style = MaterialTheme.typography.labelLarge, color = PrimaryBlueLight)
                                }
                                Slider(
                                    value = state.config.imageQuality.toFloat(),
                                    onValueChange = { q -> viewModel.updateConfig { it.copy(imageQuality = q.roundToInt()) } },
                                    valueRange = 25f..100f,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                                )

                                // Page Margins Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Page Margins", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                    Text("${state.config.marginDp} pt", style = MaterialTheme.typography.labelLarge, color = PrimaryBlueLight)
                                }
                                Slider(
                                    value = state.config.marginDp.toFloat(),
                                    onValueChange = { m -> viewModel.updateConfig { it.copy(marginDp = m.roundToInt()) } },
                                    valueRange = 0f..64f,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                                )

                                // Page Numbers Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Add Page Numbers", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                    Switch(
                                        checked = state.config.addPageNumbers,
                                        onCheckedChange = { checked -> viewModel.updateConfig { it.copy(addPageNumbers = checked) } },
                                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
                                    )
                                }
                            }
                        }
                    }

                    // Create PDF CTA
                    item {
                        if (state.isGenerating) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    color = PrimaryBlue,
                                    trackColor = colors.secondaryBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    "Creating PDF (${(state.progress * 100).roundToInt()}%)...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.createPdf() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .testTag("create_pdf_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Create PDF (${state.pages.size} ${if (state.pages.size == 1) "Page" else "Pages"})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Crop Image Dialog
    val editingPage = state.pages.firstOrNull { it.id == state.editingCropPageId }
    if (editingPage != null) {
        ImageCropDialog(
            page = editingPage,
            onDismiss = { viewModel.closeCropEditor() },
            onApply = { rect -> viewModel.updateCrop(editingPage.id, rect) }
        )
    }
}

@Composable
private fun ImageCropDialog(
    page: ImageToPdfPage,
    onDismiss: () -> Unit,
    onApply: (NormalizedCropRect) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    var previewBmp by remember(page.id) { mutableStateOf<Bitmap?>(null) }
    var left by remember(page.id) { mutableFloatStateOf(page.cropRect.left) }
    var top by remember(page.id) { mutableFloatStateOf(page.cropRect.top) }
    var right by remember(page.id) { mutableFloatStateOf(page.cropRect.right) }
    var bottom by remember(page.id) { mutableFloatStateOf(page.cropRect.bottom) }

    LaunchedEffect(page.id) {
        previewBmp = withContext(Dispatchers.IO) {
            runCatching { ImageProcessingHelper.decodeSampledBitmap(context, page.uri, 720) }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        title = { Text("Crop Image", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    previewBmp?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Crop preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Visual crop rectangle overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = (left * 220).dp,
                                top = (top * 220).dp,
                                end = ((1f - right) * 220).dp,
                                bottom = ((1f - bottom) * 220).dp
                            )
                            .border(2.dp, PrimaryBlueLight)
                    )
                }

                Text("Left / Right Margins", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = left,
                        onValueChange = { left = it.coerceAtMost(right - 0.15f) },
                        valueRange = 0f..0.8f,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = right,
                        onValueChange = { right = it.coerceAtLeast(left + 0.15f) },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Top / Bottom Margins", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = top,
                        onValueChange = { top = it.coerceAtMost(bottom - 0.15f) },
                        valueRange = 0f..0.8f,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = bottom,
                        onValueChange = { bottom = it.coerceAtLeast(top + 0.15f) },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(NormalizedCropRect(left, top, right, bottom)) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Apply Crop", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    previewBmp?.let { bmp ->
                        val rect = ImageProcessingHelper.detectAutoCropRect(bmp)
                        left = rect.left; top = rect.top; right = rect.right; bottom = rect.bottom
                    }
                }) {
                    Text("Auto", color = com.example.ui.theme.FeatureCyan)
                }
                TextButton(onClick = {
                    left = 0f; top = 0f; right = 1f; bottom = 1f
                }) {
                    Text("Reset", color = PrimaryBlueLight)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        }
    )
}
