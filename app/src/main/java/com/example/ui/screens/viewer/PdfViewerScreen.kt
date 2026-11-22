package com.example.ui.screens.viewer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfDetailedInfo
import com.example.data.model.PdfTextMatch
import com.example.data.repository.PdfRepository
import com.example.ui.components.PdfDetailsModal
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.FeatureCoral
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfId: Long,
    repository: PdfRepository,
    onBack: () -> Unit,
    onUnlockPdf: (Long) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val document by repository.observeDocument(pdfId).collectAsStateWithLifecycle(initialValue = null)
    val listState = rememberLazyListState()

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var fullScreen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<PdfTextMatch>>(emptyList()) }
    var activeMatchIdx by remember { mutableIntStateOf(0) }
    var detailsInfo by remember { mutableStateOf<PdfDetailedInfo?>(null) }

    val pageCount by produceState(initialValue = 0, document) {
        val doc = document
        value = if (doc != null) {
            repository.rendererHelper.getPageCount(File(doc.filePath)).coerceAtLeast(doc.pageCount)
        } else {
            0
        }
    }

    val currentPageIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3.2f)
    }

    LaunchedEffect(searchQuery, document) {
        val doc = document
        if (doc != null && searchQuery.length >= 2) {
            searchMatches = repository.searchTextInPdf(doc, searchQuery)
            activeMatchIdx = 0
            searchMatches.firstOrNull()?.let { match ->
                listState.animateScrollToItem(match.pageIndex)
            }
        } else {
            searchMatches = emptyList()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            AnimatedVisibility(visible = !fullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                document?.name ?: "PDF Viewer",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                            if (pageCount > 0) {
                                Text(
                                    "Page ${currentPageIndex + 1} of $pageCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("viewer_search_toggle")) {
                            Icon(Icons.Filled.Search, "Search text", tint = colors.textPrimary)
                        }
                        IconButton(onClick = {
                            document?.let { ShareAndPrintHelper.sharePdf(context, repository.storageHelper, it) }
                        }) {
                            Icon(Icons.Filled.Share, "Share", tint = colors.textPrimary)
                        }
                        IconButton(onClick = {
                            document?.let {
                                ShareAndPrintHelper.printPdf(context, File(it.filePath), it.name)
                            }
                        }) {
                            Icon(Icons.Filled.Print, "Print", tint = colors.textPrimary)
                        }
                        IconButton(onClick = {
                            document?.let { doc ->
                                scope.launch { detailsInfo = repository.getPdfInfo(doc) }
                            }
                        }) {
                            Icon(Icons.Filled.Info, "Info", tint = colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val doc = document
            if (doc == null) {
                CircularProgressIndicator(
                    color = PrimaryBlue,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (doc.isEncrypted) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Lock, null, tint = FeatureCoral, modifier = Modifier.size(56.dp))
                    Text("This PDF is password protected", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    Text(
                        "Use the Unlock PDF tool with the authorized password to open and view it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Surface(
                        onClick = { onUnlockPdf(doc.id) },
                        color = PrimaryBlue,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "Unlock PDF",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = searchOpen && !fullScreen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.cardBackground)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search text inside PDF...") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchOpen = false
                                    }) {
                                        Icon(Icons.Filled.Close, "Close search")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("viewer_search_input")
                            )
                            if (searchMatches.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${activeMatchIdx + 1}/${searchMatches.size} match (Page ${searchMatches[activeMatchIdx].pageIndex + 1})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrimaryBlueLight
                                    )
                                    Row {
                                        Surface(
                                            onClick = {
                                                activeMatchIdx = (activeMatchIdx - 1 + searchMatches.size) % searchMatches.size
                                                scope.launch { listState.animateScrollToItem(searchMatches[activeMatchIdx].pageIndex) }
                                            },
                                            color = colors.secondaryBackground,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Prev", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = colors.textPrimary)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            onClick = {
                                                activeMatchIdx = (activeMatchIdx + 1) % searchMatches.size
                                                scope.launch { listState.animateScrollToItem(searchMatches[activeMatchIdx].pageIndex) }
                                            },
                                            color = colors.secondaryBackground,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Next", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = colors.textPrimary)
                                        }
                                    }
                                }
                                Text(
                                    searchMatches[activeMatchIdx].snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 2
                                )
                            } else if (searchQuery.length >= 2) {
                                Text("No matches found", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
                    }

                    // Main Vertical PDF Pages Scroller with Pinch & Double-Tap Zoom
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .transformable(state = transformableState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        zoomScale = if (zoomScale > 1.2f) 1f else 2f
                                    }
                                )
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale
                                )
                        ) {
                            itemsIndexed((0 until pageCount).toList()) { _, pageIdx ->
                                PdfRenderedPageCard(
                                    file = File(doc.filePath),
                                    pageIndex = pageIdx,
                                    repository = repository
                                )
                            }
                        }

                        // Floating Zoom & Fullscreen Pill
                        Surface(
                            color = colors.cardBackground.copy(alpha = 0.92f),
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f) }) {
                                    Icon(Icons.Filled.ZoomOut, "Zoom out", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    "${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textPrimary
                                )
                                IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f) }) {
                                    Icon(Icons.Filled.ZoomIn, "Zoom in", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { fullScreen = !fullScreen }) {
                                    Icon(
                                        if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        "Toggle full screen",
                                        tint = PrimaryBlueLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Page Thumbnails Scrubber
                    AnimatedVisibility(visible = !fullScreen && pageCount > 1) {
                        Surface(
                            color = colors.cardBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed((0 until pageCount.coerceAtMost(40)).toList()) { _, idx ->
                                    PdfThumbnailChip(
                                        file = File(doc.filePath),
                                        pageIndex = idx,
                                        isActive = idx == currentPageIndex,
                                        repository = repository,
                                        onClick = {
                                            scope.launch { listState.animateScrollToItem(idx) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailsInfo?.let { info ->
        PdfDetailsModal(info = info, onDismiss = { detailsInfo = null })
    }
}

@Composable
private fun PdfRenderedPageCard(
    file: File,
    pageIndex: Int,
    repository: PdfRepository
) {
    val colors = LocalSmartPdfColors.current
    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath, pageIndex) {
        value = repository.renderPdfPage(file, pageIndex, widthPx = 1080)
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(colors.cardBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        }
    }
}

@Composable
private fun PdfThumbnailChip(
    file: File,
    pageIndex: Int,
    isActive: Boolean,
    repository: PdfRepository,
    onClick: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val thumb by produceState<Bitmap?>(initialValue = null, file.absolutePath, pageIndex) {
        value = repository.renderPdfPage(file, pageIndex, widthPx = 140)
    }
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 58.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) PrimaryBlue else colors.divider,
                shape = RoundedCornerShape(8.dp)
            )
            .background(colors.secondaryBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Thumb ${pageIndex + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp)
        ) {
            Text(
                "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
