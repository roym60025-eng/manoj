package com.example.ui.screens.autocrop

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AutoCropPageItem
import com.example.data.model.NormalizedQuad
import com.example.pdf.ImageProcessingHelper
import com.example.ui.theme.FeatureCyan
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import kotlin.math.hypot

@Composable
fun AdjustQuadDialog(
    page: AutoCropPageItem,
    onDismiss: () -> Unit,
    onApply: (NormalizedQuad) -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val bitmap = remember(page.id) { BitmapFactory.decodeFile(page.originalFilePath) }
    var quad by remember(page.id) { mutableStateOf(page.detectedQuad) }
    var activeCorner by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        title = { Text("Adjust Document Corners", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Drag any corner handle to refine perspective straightening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Adjust document corners",
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
                                        quad = when (activeCorner) {
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
                        drawPath(path, color = FeatureCyan, style = Stroke(width = 2.5.dp.toPx()))
                        listOf(tl, tr, br, bl).forEach { pt ->
                            drawCircle(color = PrimaryBlue, radius = 12.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 12.dp.toPx(), center = pt, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(quad) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Apply", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (bitmap != null) {
                        quad = ImageProcessingHelper.detectDocumentQuad(bitmap)
                    }
                }) {
                    Text("Auto Detect", color = FeatureCyan)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        }
    )
}
