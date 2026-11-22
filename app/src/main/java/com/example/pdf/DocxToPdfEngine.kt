package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.text.Layout
import android.util.Xml
import androidx.core.graphics.toColorInt
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.min

/**
 * Native DOCX-to-PDF converter parsing OpenXML (document.xml, rels, media)
 * and rendering formatted headings, text runs, lists, tables, and embedded images into A4 PDF pages
 * using PDFBox for reliable cross-device vector PDF output.
 */
class DocxToPdfEngine(private val context: Context) {

    private sealed interface DocBlock {
        data class Paragraph(
            val runs: List<TextRun>,
            val isHeadingLevel: Int = 0,
            val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            val isBullet: Boolean = false
        ) : DocBlock

        data class Table(val rows: List<List<String>>) : DocBlock
        data class ImageBlock(val mediaBytes: ByteArray) : DocBlock
    }

    private data class TextRun(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val fontSizeSp: Float = 11.5f,
        val colorInt: Int = Color.rgb(26, 29, 43)
    )

    suspend fun convertDocxToPdf(
        sourceUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        onProgress(0.1f)

        val zipEntries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        zipEntries[entry.name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("Unable to read selected DOCX file")

        val docXml = zipEntries["word/document.xml"]
            ?: throw IllegalStateException("Invalid DOCX: missing word/document.xml")

        onProgress(0.3f)
        val relsMap = parseRelationships(zipEntries["word/_rels/document.xml.rels"])
        val blocks = parseDocumentXml(docXml, relsMap, zipEntries)

        onProgress(0.55f)
        renderBlocksToPdf(blocks, outputFile) { p ->
            onProgress(0.55f + p * 0.45f)
        }
        onProgress(1f)
        outputFile
    }

    private fun parseRelationships(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(relsBytes), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name?.endsWith("Relationship") == true) {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                        val normalized = target.removePrefix("/").let {
                            if (it.startsWith("word/")) it else "word/$it"
                        }
                        map[id] = normalized
                    }
                }
                event = parser.next()
            }
        }
        return map
    }

    private fun parseDocumentXml(
        xmlBytes: ByteArray,
        rels: Map<String, String>,
        zipEntries: Map<String, ByteArray>
    ): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var inTable = false
        val currentTableRows = mutableListOf<MutableList<String>>()
        var currentRow = mutableListOf<String>()
        var inCell = false
        val cellTextBuilder = StringBuilder()

        var inParagraph = false
        val currentRuns = mutableListOf<TextRun>()
        var headingLevel = 0
        var alignment = Layout.Alignment.ALIGN_NORMAL
        var isBullet = false

        var inRun = false
        var bold = false
        var italic = false
        var underline = false
        var fontSizeSp = 11.5f
        var colorInt = Color.rgb(26, 29, 43)
        val runTextBuilder = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "w:tbl" -> {
                            inTable = true
                            currentTableRows.clear()
                        }
                        "w:tr" -> if (inTable) currentRow = mutableListOf()
                        "w:tc" -> if (inTable) {
                            inCell = true
                            cellTextBuilder.clear()
                        }
                        "w:p" -> {
                            if (!inTable) {
                                inParagraph = true
                                currentRuns.clear()
                                headingLevel = 0
                                alignment = Layout.Alignment.ALIGN_NORMAL
                                isBullet = false
                            }
                        }
                        "w:pStyle" -> {
                            val valAttr = parser.getAttributeValue(null, "w:val") ?: ""
                            headingLevel = when {
                                valAttr.contains("Heading1", true) || valAttr.equals("Title", true) -> 1
                                valAttr.contains("Heading2", true) -> 2
                                valAttr.contains("Heading3", true) -> 3
                                else -> headingLevel
                            }
                        }
                        "w:jc" -> {
                            alignment = when (parser.getAttributeValue(null, "w:val")?.lowercase()) {
                                "center" -> Layout.Alignment.ALIGN_CENTER
                                "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
                                else -> Layout.Alignment.ALIGN_NORMAL
                            }
                        }
                        "w:numPr" -> isBullet = true
                        "w:r" -> {
                            inRun = true
                            bold = false
                            italic = false
                            underline = false
                            fontSizeSp = when (headingLevel) {
                                1 -> 20f
                                2 -> 16f
                                3 -> 13.5f
                                else -> 11.5f
                            }
                            colorInt = Color.rgb(26, 29, 43)
                            runTextBuilder.clear()
                        }
                        "w:b" -> {
                            val v = parser.getAttributeValue(null, "w:val")
                            if (v != "0" && v != "false") bold = true
                        }
                        "w:i" -> {
                            val v = parser.getAttributeValue(null, "w:val")
                            if (v != "0" && v != "false") italic = true
                        }
                        "w:u" -> {
                            val v = parser.getAttributeValue(null, "w:val")
                            if (v != "none") underline = true
                        }
                        "w:sz" -> {
                            val halfPt = parser.getAttributeValue(null, "w:val")?.toFloatOrNull()
                            if (halfPt != null && headingLevel == 0) {
                                fontSizeSp = (halfPt / 2f).coerceIn(8f, 36f)
                            }
                        }
                        "w:color" -> {
                            val hex = parser.getAttributeValue(null, "w:val")
                            if (!hex.isNullOrBlank() && hex != "auto" && hex.length == 6) {
                                runCatching { colorInt = "#$hex".toColorInt() }
                            }
                        }
                        "w:t" -> {
                            if (inRun) {
                                runTextBuilder.append(parser.nextText())
                            }
                        }
                        "w:br" -> if (inRun) runTextBuilder.append("\n")
                        "w:tab" -> if (inRun) runTextBuilder.append("    ")
                        "a:blip" -> {
                            val embedId = parser.getAttributeValue(null, "r:embed")
                            val targetPath = embedId?.let { rels[it] }
                            val bytes = targetPath?.let { zipEntries[it] }
                            if (bytes != null && !inTable) {
                                blocks.add(DocBlock.ImageBlock(bytes))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "w:r" -> {
                            if (inRun) {
                                val str = runTextBuilder.toString()
                                if (str.isNotEmpty()) {
                                    if (inCell) {
                                        cellTextBuilder.append(str)
                                    } else if (inParagraph) {
                                        currentRuns.add(
                                            TextRun(
                                                text = str,
                                                bold = bold || headingLevel > 0,
                                                italic = italic,
                                                underline = underline,
                                                fontSizeSp = fontSizeSp,
                                                colorInt = colorInt
                                            )
                                        )
                                    }
                                }
                                inRun = false
                            }
                        }
                        "w:p" -> {
                            if (inCell) {
                                if (cellTextBuilder.isNotEmpty()) cellTextBuilder.append(" ")
                            } else if (inParagraph) {
                                if (currentRuns.isNotEmpty()) {
                                    blocks.add(
                                        DocBlock.Paragraph(
                                            runs = currentRuns.toList(),
                                            isHeadingLevel = headingLevel,
                                            alignment = alignment,
                                            isBullet = isBullet
                                        )
                                    )
                                } else {
                                    blocks.add(DocBlock.Paragraph(emptyList()))
                                }
                                inParagraph = false
                            }
                        }
                        "w:tc" -> {
                            if (inCell) {
                                currentRow.add(cellTextBuilder.toString().trim())
                                inCell = false
                            }
                        }
                        "w:tr" -> {
                            if (inTable && currentRow.isNotEmpty()) {
                                currentTableRows.add(currentRow)
                            }
                        }
                        "w:tbl" -> {
                            if (inTable && currentTableRows.isNotEmpty()) {
                                blocks.add(DocBlock.Table(currentTableRows.toList()))
                            }
                            inTable = false
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (blocks.isEmpty()) {
            blocks.add(DocBlock.Paragraph(runs = listOf(TextRun("(Empty DOCX document)"))))
        }
        return blocks
    }

    private fun renderBlocksToPdf(
        blocks: List<DocBlock>,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val pageWidth = PDRectangle.A4.width
        val pageHeight = PDRectangle.A4.height
        val marginX = 44f
        val marginTop = 48f
        val marginBottom = 48f
        val contentWidth = pageWidth - marginX * 2f

        PDDocument().use { document ->
            val unicodeFont: PDFont? = runCatching {
                context.assets.open("fonts/inter.ttf").use { stream ->
                    PDType0Font.load(document, stream)
                }
            }.getOrNull()

            var currentPage = PDPage(PDRectangle.A4)
            document.addPage(currentPage)
            var cs = PDPageContentStream(document, currentPage)
            var cursorY = pageHeight - marginTop

            fun startNewPage() {
                cs.close()
                currentPage = PDPage(PDRectangle.A4)
                document.addPage(currentPage)
                cs = PDPageContentStream(document, currentPage)
                cursorY = pageHeight - marginTop
            }

            fun resolveFont(bold: Boolean, italic: Boolean): PDFont {
                return if (unicodeFont != null && !bold && !italic) {
                    unicodeFont
                } else when {
                    bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                    bold -> PDType1Font.HELVETICA_BOLD
                    italic -> PDType1Font.HELVETICA_OBLIQUE
                    else -> PDType1Font.HELVETICA
                }
            }

            fun safeText(font: PDFont, raw: String): String {
                if (font is PDType0Font) return raw.replace('\t', ' ')
                return buildString {
                    for (ch in raw) {
                        when {
                            ch == '\t' -> append("    ")
                            ch == '•' -> append("- ")
                            ch.code in 32..255 -> append(ch)
                            else -> append(' ')
                        }
                    }
                }
            }

            fun wrapLines(text: String, font: PDFont, fontSize: Float, maxW: Float): List<String> {
                val lines = mutableListOf<String>()
                text.split("\n").forEach { para ->
                    val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (words.isEmpty()) {
                        lines.add("")
                    } else {
                        var current = words.first()
                        for (w in words.drop(1)) {
                            val candidate = "$current $w"
                            val width = runCatching { font.getStringWidth(candidate) / 1000f * fontSize }
                                .getOrDefault(candidate.length * fontSize * 0.5f)
                            if (width <= maxW) {
                                current = candidate
                            } else {
                                lines.add(current)
                                current = w
                            }
                        }
                        lines.add(current)
                    }
                }
                return lines
            }

            val total = blocks.size.coerceAtLeast(1)
            blocks.forEachIndexed { idx, block ->
                when (block) {
                    is DocBlock.Paragraph -> {
                        if (block.runs.isEmpty()) {
                            cursorY -= 10f
                            if (cursorY < marginBottom) startNewPage()
                        } else {
                            val firstRun = block.runs.first()
                            val font = resolveFont(firstRun.bold, firstRun.italic)
                            val fontSize = block.runs.maxOf { it.fontSizeSp }
                            val rawText = buildString {
                                if (block.isBullet) append("• ")
                                block.runs.forEach { append(it.text) }
                            }
                            val cleanText = safeText(font, rawText)
                            val lines = wrapLines(cleanText, font, fontSize, contentWidth)
                            val lineHeight = fontSize * 1.38f

                            val r = Color.red(firstRun.colorInt) / 255f
                            val g = Color.green(firstRun.colorInt) / 255f
                            val b = Color.blue(firstRun.colorInt) / 255f

                            lines.forEach { line ->
                                if (cursorY - lineHeight < marginBottom) {
                                    startNewPage()
                                }
                                val lineWidth = runCatching { font.getStringWidth(line) / 1000f * fontSize }
                                    .getOrDefault(0f)
                                val drawX = when (block.alignment) {
                                    Layout.Alignment.ALIGN_CENTER -> marginX + (contentWidth - lineWidth) / 2f
                                    Layout.Alignment.ALIGN_OPPOSITE -> marginX + (contentWidth - lineWidth)
                                    else -> marginX
                                }
                                cs.beginText()
                                cs.setFont(font, fontSize)
                                cs.setNonStrokingColor(r, g, b)
                                cs.newLineAtOffset(drawX, cursorY - fontSize)
                                cs.showText(line)
                                cs.endText()
                                cursorY -= lineHeight
                            }
                            cursorY -= if (block.isHeadingLevel > 0) 8f else 5f
                        }
                    }

                    is DocBlock.Table -> {
                        val colCount = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                        val colWidth = contentWidth / colCount
                        val cellFont = PDType1Font.HELVETICA
                        val headerFont = PDType1Font.HELVETICA_BOLD
                        val cellSize = 10f

                        block.rows.forEachIndexed { rowIdx, row ->
                            val font = if (rowIdx == 0) headerFont else cellFont
                            val cellLines = (0 until colCount).map { cIdx ->
                                val raw = safeText(font, row.getOrElse(cIdx) { "" })
                                wrapLines(raw, font, cellSize, (colWidth - 10f).coerceAtLeast(24f))
                            }
                            val maxLines = cellLines.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                            val rowHeight = maxLines * 14f + 10f
                            if (cursorY - rowHeight < marginBottom) {
                                startNewPage()
                            }
                            val rowBottom = cursorY - rowHeight
                            for (cIdx in 0 until colCount) {
                                val cellX = marginX + cIdx * colWidth
                                if (rowIdx == 0) {
                                    cs.setNonStrokingColor(0.93f, 0.94f, 0.98f)
                                    cs.addRect(cellX, rowBottom, colWidth, rowHeight)
                                    cs.fill()
                                }
                                cs.setStrokingColor(0.78f, 0.81f, 0.87f)
                                cs.setLineWidth(0.8f)
                                cs.addRect(cellX, rowBottom, colWidth, rowHeight)
                                cs.stroke()

                                var textY = cursorY - 14f
                                cellLines[cIdx].forEach { line ->
                                    cs.beginText()
                                    cs.setFont(font, cellSize)
                                    cs.setNonStrokingColor(0.1f, 0.12f, 0.18f)
                                    cs.newLineAtOffset(cellX + 5f, textY)
                                    cs.showText(line)
                                    cs.endText()
                                    textY -= 14f
                                }
                            }
                            cursorY = rowBottom
                        }
                        cursorY -= 10f
                    }

                    is DocBlock.ImageBlock -> {
                        val bmp = BitmapFactory.decodeByteArray(block.mediaBytes, 0, block.mediaBytes.size)
                        if (bmp != null) {
                            val maxH = (pageHeight - marginTop - marginBottom) * 0.6f
                            val scale = min(contentWidth / bmp.width.coerceAtLeast(1), maxH / bmp.height.coerceAtLeast(1))
                                .coerceAtMost(1f)
                            val drawW = bmp.width * scale
                            val drawH = bmp.height * scale
                            if (cursorY - drawH < marginBottom) {
                                startNewPage()
                            }
                            val pdImg = JPEGFactory.createFromImage(document, bmp, 0.88f)
                            bmp.recycle()
                            cs.drawImage(pdImg, marginX, cursorY - drawH, drawW, drawH)
                            cursorY -= (drawH + 10f)
                        }
                    }
                }
                onProgress((idx + 1).toFloat() / total)
            }

            cs.close()
            document.save(outputFile)
        }
    }
}
