package com.example.ui.screens.tools

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ConvertedImageOutput
import com.example.data.model.ImageExportFormat
import com.example.data.model.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.ui.components.LibraryPdfPickerSheet
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToJpgScreen(
    repository: PdfRepository,
    preselectedId: Long? = null,
    onBack: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allDocs by repository.allDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var pagePreviews by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var selectedPages by remember { mutableStateOf(setOf<Int>()) }
    var format by remember { mutableStateOf(ImageExportFormat.JPG) }
    var quality by remember { mutableIntStateOf(90) }
    var showPicker by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var convertedOutput by remember { mutableStateOf<ConvertedImageOutput?>(null) }

    LaunchedEffect(preselectedId, allDocs) {
        if (selectedDoc == null && preselectedId != null) {
            selectedDoc = allDocs.firstOrNull { it.id == preselectedId }
        }
    }

    LaunchedEffect(selectedDoc) {
        val doc = selectedDoc ?: return@LaunchedEffect
        convertedOutput = null
        val file = File(doc.filePath)
        val count = repository.rendererHelper.getPageCount(file).coerceAtMost(30)
        selectedPages = (0 until doc.pageCount).toSet()
        pagePreviews = (0 until count).map { idx ->
            repository.renderPdfPage(file, idx, 320)
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

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("PDF to JPG / PNG", color = colors.textPrimary) },
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Source PDF", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        val doc = selectedDoc
                        if (doc == null) {
                            OutlinedButton(
                                onClick = { showPicker = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PrimaryBlueLight),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, tint = PrimaryBlueLight)
                                Spacer(Modifier.width(8.dp))
                                Text("Select PDF File", color = PrimaryBlueLight)
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

                if (selectedDoc != null && pagePreviews.isNotEmpty()) {
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Select Pages (${selectedPages.size}/${selectedDoc!!.pageCount})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                                TextButton(onClick = {
                                    val total = selectedDoc!!.pageCount
                                    selectedPages = if (selectedPages.size == total) emptySet() else (0 until total).toSet()
                                }) {
                                    Text(
                                        if (selectedPages.size == selectedDoc!!.pageCount) "Clear" else "Select All",
                                        color = PrimaryBlueLight
                                    )
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.height(230.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(pagePreviews) { idx, bmp ->
                                    val isSel = idx in selectedPages
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(0.75f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (isSel) 2.5.dp else 1.dp,
                                                color = if (isSel) PrimaryBlue else colors.divider,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                selectedPages = if (isSel) selectedPages - idx else selectedPages + idx
                                            }
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
                            Text("Output Settings", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ImageExportFormat.entries.forEach { fmt ->
                                    FilterChip(
                                        selected = format == fmt,
                                        onClick = { format = fmt },
                                        label = { Text(fmt.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryBlue,
                                            selectedLabelColor = Color.White,
                                            containerColor = colors.secondaryBackground,
                                            labelColor = colors.textSecondary
                                        )
                                    )
                                }
                            }
                            if (format == ImageExportFormat.JPG) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Image Quality", color = colors.textSecondary)
                                    Text("$quality%", color = PrimaryBlueLight, fontWeight = FontWeight.SemiBold)
                                }
                                Slider(
                                    value = quality.toFloat(),
                                    onValueChange = { quality = it.roundToInt() },
                                    valueRange = 40f..100f,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                                )
                            }
                        }
                    }

                    if (isConverting) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = PrimaryBlue,
                            trackColor = colors.secondaryBackground,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                    } else {
                        Button(
                            onClick = {
                                val doc = selectedDoc ?: return@Button
                                scope.launch {
                                    isConverting = true
                                    progress = 0f
                                    runCatching {
                                        repository.convertPdfToImages(
                                            source = doc,
                                            selectedPageIndices = selectedPages.toList(),
                                            format = format,
                                            quality = quality,
                                            onProgress = { progress = it }
                                        )
                                    }.onSuccess { out ->
                                        convertedOutput = out
                                        snackbarHostState.showSnackbar("Saved ${out.files.size} image(s) to Smart PDF Maker & Pictures")
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Conversion failed")
                                    }
                                    isConverting = false
                                }
                            },
                            enabled = selectedPages.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("convert_pdf_to_images_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Convert ${selectedPages.size} Page(s) to ${format.label}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                convertedOutput?.let { out ->
                    Surface(
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Converted ${out.files.size} ${format.label} Image(s)",
                                style = MaterialTheme.typography.titleMedium,
                                color = SuccessGreen
                            )
                            Text(
                                "Saved in Smart PDF Maker / Converted Images",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(out.files) { file ->
                                    AsyncImage(
                                        model = file,
                                        contentDescription = file.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(84.dp, 110.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                            Button(
                                onClick = { ShareAndPrintHelper.shareImages(context, repository.storageHelper, out.files) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Share, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Share Converted Images", color = Color.White)
                            }
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
            onSelect = { selectedDoc = it },
            onImportExternal = { importLauncher.launch(arrayOf("application/pdf")) }
        )
    }
}
