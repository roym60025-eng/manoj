package com.example.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MoreToolType
import com.example.ui.theme.FeatureAmber
import com.example.ui.theme.FeatureBlue
import com.example.ui.theme.FeatureCoral
import com.example.ui.theme.FeatureCyan
import com.example.ui.theme.FeatureEmerald
import com.example.ui.theme.FeatureIndigo
import com.example.ui.theme.FeatureTeal
import com.example.ui.theme.FeatureViolet
import com.example.ui.theme.LocalSmartPdfColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsScreen(
    onBack: () -> Unit,
    onSelectTool: (MoreToolType) -> Unit
) {
    val colors = LocalSmartPdfColors.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("All PDF Tools", color = colors.textPrimary) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MoreToolType.entries) { tool ->
                    val (icon, accent) = toolVisuals(tool)
                    Surface(
                        onClick = { onSelectTool(tool) },
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("more_tool_${tool.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, tool.title, tint = accent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tool.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                tint = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toolVisuals(tool: MoreToolType): Pair<ImageVector, Color> {
    return when (tool) {
        MoreToolType.SPLIT -> Icons.AutoMirrored.Filled.CallSplit to FeatureBlue
        MoreToolType.ROTATE -> Icons.AutoMirrored.Filled.RotateRight to FeatureCyan
        MoreToolType.PROTECT -> Icons.Filled.Lock to FeatureCoral
        MoreToolType.UNLOCK -> Icons.Filled.LockOpen to FeatureEmerald
        MoreToolType.WATERMARK -> Icons.Filled.BrandingWatermark to FeatureIndigo
        MoreToolType.PAGE_NUMBERS -> Icons.Filled.FormatListNumbered to FeatureAmber
        MoreToolType.EXTRACT_PAGES -> Icons.Filled.FileCopy to FeatureTeal
        MoreToolType.DELETE_PAGES -> Icons.Filled.DeleteSweep to FeatureCoral
        MoreToolType.REORDER_PAGES -> Icons.Filled.SwapVert to FeatureViolet
        MoreToolType.PDF_INFO -> Icons.Filled.Info to FeatureBlue
        MoreToolType.CREATE_BLANK -> Icons.Filled.NoteAdd to FeatureEmerald
    }
}
