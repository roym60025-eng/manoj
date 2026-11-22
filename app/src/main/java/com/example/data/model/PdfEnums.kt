package com.example.data.model

enum class PdfCategory(val label: String) {
    ALL("All PDFs"),
    RECENT("Recent PDFs"),
    CREATED("Created PDFs"),
    IMPORTED("Imported PDFs"),
    SCANNED("Scanned PDFs"),
    CONVERTED("Converted PDFs")
}

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    NAME_AZ("Name A-Z"),
    LARGEST_FIRST("Largest First"),
    SMALLEST_FIRST("Smallest First")
}

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    A3("A3", 842, 1191),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
    CUSTOM("Custom", 595, 842)
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ImageFitMode(val label: String) {
    CONTAIN("Fit Page"),
    COVER("Fill Page"),
    ORIGINAL("Original Size"),
    STRETCH("Stretch")
}

enum class CompressionLevel(
    val label: String,
    val description: String,
    val jpegQuality: Int,
    val scaleFactor: Float
) {
    HIGH_QUALITY("High Quality", "Best clarity, moderate reduction", 85, 0.95f),
    MEDIUM_QUALITY("Medium Quality", "Balanced quality & file size", 60, 0.78f),
    LOW_SIZE("Low Size", "Smallest size, compact resolution", 35, 0.55f)
}

enum class ScanFilter(val label: String) {
    COLOR("Color"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W")
}

enum class ImageExportFormat(val label: String, val extension: String) {
    JPG("JPG", "jpg"),
    PNG("PNG", "png")
}

enum class SaveLocationOption(val label: String) {
    APP_FOLDER("Smart PDF Maker"),
    DOCUMENTS_EXPORT("Documents / Smart PDF Maker")
}

enum class MoreToolType(
    val id: String,
    val title: String,
    val subtitle: String
) {
    SPLIT("split", "Split PDF", "Separate a PDF into parts or page ranges"),
    ROTATE("rotate", "Rotate PDF", "Rotate all or selected pages by 90°, 180°, or 270°"),
    PROTECT("protect", "Protect PDF", "Encrypt your PDF with a strong password"),
    UNLOCK("unlock", "Unlock PDF", "Remove password protection using authorized password"),
    WATERMARK("watermark", "Add Watermark", "Stamp custom diagonal text across PDF pages"),
    PAGE_NUMBERS("page_numbers", "Add Page Numbers", "Insert sequential page numbers to footer"),
    EXTRACT_PAGES("extract_pages", "Extract Pages", "Save selected pages into a new PDF file"),
    DELETE_PAGES("delete_pages", "Delete Pages", "Remove unwanted pages from a PDF"),
    REORDER_PAGES("reorder_pages", "Reorder Pages", "Change page sequence inside a PDF"),
    PDF_INFO("pdf_info", "PDF Information", "Inspect metadata, encryption, and dimensions"),
    CREATE_BLANK("create_blank", "Create Blank PDF", "Generate a clean multi-page blank PDF")
}
