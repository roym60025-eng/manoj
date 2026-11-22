package com.example.ui.screens.more

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MoreToolType
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.StorageHelper
import com.example.ui.components.LibraryPdfPickerSheet
import com.example.ui.components.PdfGeneratedSuccessCard
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolDetailScreen(
    toolType: MoreToolType,
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
    var showPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultDocs by remember { mutableStateOf<List<PdfDocumentEntity>>(emptyList()) }

    // Page thumbnails for page-level tools
    var pageThumbs by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var selectedPageIndices by remember { mutableStateOf(setOf<Int>()) }
    var reorderIndices by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Tool-specific params
    var splitEveryN by remember { mutableIntStateOf(1) }
    var rotateDegrees by remember { mutableIntStateOf(90) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.28f) }
    var watermarkSize by remember { mutableFloatStateOf(44f) }
    var pageNumStart by remember { mutableIntStateOf(1) }
    var pageNumAtBottom by remember { mutableStateOf(true) }
    var pdfInfo by remember { mutableStateOf<PdfDetailedInfo?>(null) }

    // Blank PDF params
    var blankName by remember { mutableStateOf("Blank_Document") }
    var blankPageCount by remember { mutableIntStateOf(3) }
    var blankPageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var blankOrientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var blankRuled by remember { mutableStateOf(false) }

    LaunchedEffect(preselectedId, allDocs) {
        if (selectedDoc == null && preselectedId != null) {
            selectedDoc = allDocs.firstOrNull { it.id == preselectedId }
        }
    }

    LaunchedEffect(selectedDoc, toolType) {
        val doc = selectedDoc ?: return@LaunchedEffect
        resultDocs = emptyList()
        if (toolType == MoreToolType.PDF_INFO) {
            pdfInfo = repository.getPdfInfo(doc)
        }
        val needsPages = toolType in setOf(
            MoreToolType.ROTATE,
            MoreToolType.EXTRACT_PAGES,
            MoreToolType.DELETE_PAGES,
            MoreToolType.REORDER_PAGES
        )
        if (needsPages) {
            val file = File(doc.filePath)
            val count = repository.rendererHelper.getPageCount(file).coerceAtMost(30)
            selectedPageIndices = emptySet()
            reorderIndices = (0 until doc.pageCount).toList()
            pageThumbs = (0 until count).map { idx -> repository.renderPdfPage(file, idx, 260) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.importPdfFromUri(uri) }
                    .onSuccess { selectedDoc = it }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Import failed") }
            }
        }
    }

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = resultDocs.firstOrNull()
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
                title = { Text(toolType.title, color = colors.textPrimary) },
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
                if (toolType != MoreToolType.CREATE_BLANK) {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Target PDF File", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            val doc = selectedDoc
                            if (doc == null) {
                                OutlinedButton(
                                    onClick = { showPicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, PrimaryBlueLight),
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_select_pdf")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = PrimaryBlueLight)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Select PDF Document", color = PrimaryBlueLight)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${doc.name}.pdf (${doc.pageCount} pages)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { showPicker = true }) {
                                        Text("Change", color = PrimaryBlueLight)
                                    }
                                }
                            }
                        }
                    }
                }

                // Tool-specific controls
                when (toolType) {
                    MoreToolType.CREATE_BLANK -> {
                        Surface(
                            color = colors.cardBackground,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = blankName,
                                    onValueChange = { blankName = it },
                                    label = { Text("File Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pages", color = colors.textSecondary)
                                    Text("$blankPageCount", color = PrimaryBlueLight, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = blankPageCount.toFloat(),
                                    onValueChange = { blankPageCount = it.roundToInt().coerceIn(1, 30) },
                                    valueRange = 1f..30f
                                )
                                Text("Page Size", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf(PageSizeOption.A4, PageSizeOption.A3, PageSizeOption.LETTER, PageSizeOption.LEGAL)) { sz ->
                                        FilterChip(
                                            selected = blankPageSize == sz,
                                            onClick = { blankPageSize = sz },
                                            label = { Text(sz.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = colors.secondaryBackground,
                                                labelColor = colors.textSecondary
                                            )
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PageOrientation.entries.forEach { orient ->
                                        FilterChip(
                                            selected = blankOrientation == orient,
                                            onClick = { blankOrientation = orient },
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ruled notebook lines", color = colors.textPrimary)
                                    Switch(checked = blankRuled, onCheckedChange = { blankRuled = it })
                                }
                            }
                        }
                    }

                    MoreToolType.SPLIT -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pages per split file", color = colors.textSecondary)
                                    Text("$splitEveryN", color = PrimaryBlueLight, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = splitEveryN.toFloat(),
                                    onValueChange = { splitEveryN = it.roundToInt().coerceAtLeast(1) },
                                    valueRange = 1f..(selectedDoc!!.pageCount.coerceAtLeast(2)).toFloat()
                                )
                            }
                        }
                    }

                    MoreToolType.ROTATE -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Rotation Angle", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(90, 180, 270).forEach { deg ->
                                        FilterChip(
                                            selected = rotateDegrees == deg,
                                            onClick = { rotateDegrees = deg },
                                            label = { Text("${deg}°") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = colors.secondaryBackground,
                                                labelColor = colors.textSecondary
                                            )
                                        )
                                    }
                                }
                                Text("Tap pages to rotate (leave unselected for all pages)", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                PageSelectionGrid(
                                    thumbs = pageThumbs,
                                    selected = selectedPageIndices,
                                    onToggle = { idx ->
                                        selectedPageIndices = if (idx in selectedPageIndices) selectedPageIndices - idx else selectedPageIndices + idx
                                    }
                                )
                            }
                        }
                    }

                    MoreToolType.PROTECT, MoreToolType.UNLOCK -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (toolType == MoreToolType.PROTECT) "Set Encryption Password" else "Enter Current Authorized Password",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                "Toggle password"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("pdf_password_input")
                                )
                            }
                        }
                    }

                    MoreToolType.WATERMARK -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = { watermarkText = it },
                                    label = { Text("Watermark Text") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Opacity (${(watermarkOpacity * 100).roundToInt()}%)", color = colors.textSecondary)
                                Slider(value = watermarkOpacity, onValueChange = { watermarkOpacity = it }, valueRange = 0.1f..0.8f)
                                Text("Font Size (${watermarkSize.roundToInt()} pt)", color = colors.textSecondary)
                                Slider(value = watermarkSize, onValueChange = { watermarkSize = it }, valueRange = 20f..72f)
                            }
                        }
                    }

                    MoreToolType.PAGE_NUMBERS -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = pageNumStart.toString(),
                                    onValueChange = { v -> pageNumStart = v.filter { it.isDigit() }.toIntOrNull() ?: 1 },
                                    label = { Text("Start Numbering From") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = pageNumAtBottom,
                                        onClick = { pageNumAtBottom = true },
                                        label = { Text("Bottom Center") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryBlue,
                                            selectedLabelColor = Color.White,
                                            containerColor = colors.secondaryBackground,
                                            labelColor = colors.textSecondary
                                        )
                                    )
                                    FilterChip(
                                        selected = !pageNumAtBottom,
                                        onClick = { pageNumAtBottom = false },
                                        label = { Text("Top Center") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryBlue,
                                            selectedLabelColor = Color.White,
                                            containerColor = colors.secondaryBackground,
                                            labelColor = colors.textSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    MoreToolType.EXTRACT_PAGES, MoreToolType.DELETE_PAGES -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (toolType == MoreToolType.EXTRACT_PAGES) "Tap pages to extract (${selectedPageIndices.size} selected)"
                                    else "Tap pages to delete (${selectedPageIndices.size} selected)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                PageSelectionGrid(
                                    thumbs = pageThumbs,
                                    selected = selectedPageIndices,
                                    onToggle = { idx ->
                                        selectedPageIndices = if (idx in selectedPageIndices) selectedPageIndices - idx else selectedPageIndices + idx
                                    }
                                )
                            }
                        }
                    }

                    MoreToolType.REORDER_PAGES -> if (selectedDoc != null) {
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Reorder Pages", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                reorderIndices.forEachIndexed { pos, origIdx ->
                                    Surface(color = colors.secondaryBackground, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Position ${pos + 1}: Original Page ${origIdx + 1}",
                                                color = colors.textPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (pos > 0) {
                                                IconButton(onClick = {
                                                    val list = reorderIndices.toMutableList()
                                                    val item = list.removeAt(pos)
                                                    list.add(pos - 1, item)
                                                    reorderIndices = list
                                                }) {
                                                    Icon(Icons.Filled.ArrowUpward, "Move up", tint = colors.textSecondary)
                                                }
                                            }
                                            if (pos < reorderIndices.lastIndex) {
                                                IconButton(onClick = {
                                                    val list = reorderIndices.toMutableList()
                                                    val item = list.removeAt(pos)
                                                    list.add(pos + 1, item)
                                                    reorderIndices = list
                                                }) {
                                                    Icon(Icons.Filled.ArrowDownward, "Move down", tint = colors.textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MoreToolType.PDF_INFO -> pdfInfo?.let { info ->
                        Surface(color = colors.cardBackground, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("PDF Metadata", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                InfoLine("File Name", info.fileName)
                                InfoLine("Pages", info.pageCount.toString())
                                InfoLine("Size", StorageHelper.formatFileSize(info.fileSizeBytes))
                                InfoLine("Dimensions", info.pageDimensions)
                                InfoLine("Version", info.pdfVersion)
                                InfoLine("Encrypted", if (info.isEncrypted) "Yes" else "No")
                                InfoLine("Title", info.title)
                                InfoLine("Author", info.author)
                                InfoLine("Creator", info.creator)
                                InfoLine("Producer", info.producer)
                            }
                        }
                    }
                }

                if (toolType != MoreToolType.PDF_INFO) {
                    val canRun = toolType == MoreToolType.CREATE_BLANK || selectedDoc != null
                    if (isProcessing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("Processing...", color = colors.textPrimary)
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    runCatching {
                                        when (toolType) {
                                            MoreToolType.CREATE_BLANK -> listOf(
                                                repository.createBlankPdf(
                                                    blankName.ifBlank { "Blank_Document" },
                                                    blankPageCount,
                                                    blankPageSize,
                                                    blankOrientation,
                                                    blankRuled
                                                )
                                            )
                                            MoreToolType.SPLIT -> repository.splitPdf(selectedDoc!!, splitEveryN)
                                            MoreToolType.ROTATE -> listOf(repository.rotatePdf(selectedDoc!!, rotateDegrees, selectedPageIndices))
                                            MoreToolType.PROTECT -> listOf(repository.protectPdf(selectedDoc!!, password))
                                            MoreToolType.UNLOCK -> listOf(repository.unlockPdf(selectedDoc!!, password))
                                            MoreToolType.WATERMARK -> listOf(repository.addWatermark(selectedDoc!!, watermarkText, watermarkOpacity, watermarkSize))
                                            MoreToolType.PAGE_NUMBERS -> listOf(repository.addPageNumbers(selectedDoc!!, pageNumStart, pageNumAtBottom))
                                            MoreToolType.EXTRACT_PAGES -> listOf(repository.extractPages(selectedDoc!!, selectedPageIndices.toList()))
                                            MoreToolType.DELETE_PAGES -> listOf(repository.deletePages(selectedDoc!!, selectedPageIndices))
                                            MoreToolType.REORDER_PAGES -> listOf(repository.reorderPages(selectedDoc!!, reorderIndices))
                                            MoreToolType.PDF_INFO -> emptyList()
                                        }
                                    }.onSuccess { docs ->
                                        resultDocs = docs
                                        snackbarHostState.showSnackbar("Completed! Created ${docs.size} PDF(s)")
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Operation failed")
                                    }
                                    isProcessing = false
                                }
                            },
                            enabled = canRun,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("execute_tool_button")
                        ) {
                            Text("Run ${toolType.title}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                resultDocs.forEach { doc ->
                    PdfGeneratedSuccessCard(
                        document = doc,
                        subtitle = "${toolType.title} complete",
                        onOpenPdf = { onOpenPdf(doc) },
                        onSharePdf = { ShareAndPrintHelper.sharePdf(context, repository.storageHelper, doc) },
                        onSavePdf = { saveSafLauncher.launch("${doc.name}.pdf") }
                    )
                }
            }
        }
    }

    if (showPicker) {
        LibraryPdfPickerSheet(
            documents = allDocs,
            onDismiss = { showPicker = false },
            onSelect = { selectedDoc = it },
            onImportExternal = { importLauncher.launch(arrayOf("application/pdf")) }
        )
    }
}

@Composable
private fun PageSelectionGrid(
    thumbs: List<Bitmap?>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(thumbs) { idx, bmp ->
            val isSel = idx in selected
            Box(
                modifier = Modifier
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = if (isSel) 2.5.dp else 1.dp,
                        color = if (isSel) PrimaryBlue else colors.divider,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onToggle(idx) }
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${idx + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                ) {
                    Text(
                        "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (isSel) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        tint = PrimaryBlue,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalSmartPdfColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary, fontWeight = FontWeight.Medium)
    }
}
