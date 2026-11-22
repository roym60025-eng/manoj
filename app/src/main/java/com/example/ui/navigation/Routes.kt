package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object WelcomeRoute

@Serializable
data object HomeRoute

@Serializable
data object FilesRoute

@Serializable
data object ImageToPdfRoute

@Serializable
data object ScannerRoute

@Serializable
data class CompressPdfRoute(val preselectedId: Long? = null)

@Serializable
data class PdfToJpgRoute(val preselectedId: Long? = null)

@Serializable
data object MergePdfRoute

@Serializable
data object DocxToPdfRoute

@Serializable
data object MoreToolsRoute

@Serializable
data class PdfToolDetailRoute(val toolId: String, val preselectedId: Long? = null)

@Serializable
data class PdfViewerRoute(val pdfId: Long)

@Serializable
data object SettingsRoute

@Serializable
data object SearchRoute
