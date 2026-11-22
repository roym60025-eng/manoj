package com.example.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.data.model.MoreToolType
import com.example.ui.components.CreateActionBottomSheet
import com.example.ui.components.SmartPdfBottomBar
import com.example.ui.factory
import com.example.ui.screens.autocrop.AutoCropScreen
import com.example.ui.screens.autocrop.AutoCropViewModel
import com.example.ui.screens.files.FilesScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.imagetopdf.ImageToPdfScreen
import com.example.ui.screens.imagetopdf.ImageToPdfViewModel
import com.example.ui.screens.more.MoreToolsScreen
import com.example.ui.screens.more.PdfToolDetailScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.scanner.ScannerViewModel
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tools.CompressPdfScreen
import com.example.ui.screens.tools.DocxToPdfScreen
import com.example.ui.screens.tools.MergePdfScreen
import com.example.ui.screens.tools.PdfToJpgScreen
import com.example.ui.screens.viewer.PdfViewerScreen
import com.example.ui.screens.welcome.WelcomeScreen
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SmartPdfNavGraph(
    container: AppContainer
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    var initialRoute by remember { mutableStateOf<Any?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = container.preferencesRepository.settingsFlow.first()
        initialRoute = if (prefs.onboardingCompleted) HomeRoute else WelcomeRoute
    }

    val homeViewModel: HomeViewModel = viewModel(
        factory = container.factory { HomeViewModel(pdfRepository) }
    )

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                container.preferencesRepository.setOnboardingCompleted(true)
            }
            homeViewModel.importPdf(uri) { imported ->
                navController.navigate(HomeRoute) {
                    popUpTo(0) { inclusive = true }
                }
                navController.navigate(PdfViewerRoute(imported.id))
            }
        }
    }

    fun finishOnboardingAndGo(destination: Any) {
        scope.launch {
            container.preferencesRepository.setOnboardingCompleted(true)
        }
        navController.navigate(HomeRoute) {
            popUpTo(0) { inclusive = true }
        }
        if (destination != HomeRoute) {
            navController.navigate(destination)
        }
    }

    val start = initialRoute
    if (start == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCloseToHome = { finishOnboardingAndGo(HomeRoute) },
                onImageToPdf = { finishOnboardingAndGo(ImageToPdfRoute) },
                onScanToPdf = { finishOnboardingAndGo(ScannerRoute) },
                onImportPdf = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                onTryOthers = { finishOnboardingAndGo(MoreToolsRoute) }
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenSearch = { navController.navigate(SearchRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onImageToPdf = { navController.navigate(ImageToPdfRoute) },
                onSmartScan = { navController.navigate(ScannerRoute) },
                onImportPdf = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                onCompressPdf = { navController.navigate(CompressPdfRoute()) },
                onPdfToJpg = { navController.navigate(PdfToJpgRoute()) },
                onMergePdf = { navController.navigate(MergePdfRoute) },
                onDocxToPdf = { navController.navigate(DocxToPdfRoute) },
                onMoreTools = { navController.navigate(MoreToolsRoute) },
                onAutoCrop = { navController.navigate(AutoCropRoute()) },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) },
                bottomBar = {
                    SmartPdfBottomBar(
                        currentTabIsHome = true,
                        onHomeClick = { },
                        onAddClick = { showCreateSheet = true },
                        onFilesClick = {
                            navController.navigate(FilesRoute) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            )
        }

        composable<FilesRoute> {
            FilesScreen(
                repository = container.pdfRepository,
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) },
                onCreatePdf = { showCreateSheet = true },
                bottomBar = {
                    SmartPdfBottomBar(
                        currentTabIsHome = false,
                        onHomeClick = {
                            navController.navigate(HomeRoute) {
                                popUpTo<HomeRoute> { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onAddClick = { showCreateSheet = true },
                        onFilesClick = { }
                    )
                }
            )
        }

        composable<ImageToPdfRoute> {
            val vm: ImageToPdfViewModel = viewModel(
                factory = container.factory { ImageToPdfViewModel(pdfRepository, preferencesRepository) }
            )
            ImageToPdfScreen(
                viewModel = vm,
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<ScannerRoute> {
            val vm: ScannerViewModel = viewModel(
                factory = container.factory { ScannerViewModel(context.applicationContext, pdfRepository) }
            )
            ScannerScreen(
                viewModel = vm,
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<AutoCropRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AutoCropRoute>()
            val vm: AutoCropViewModel = viewModel(
                factory = container.factory {
                    AutoCropViewModel(context.applicationContext, pdfRepository, route.preselectedPdfId)
                }
            )
            AutoCropScreen(
                viewModel = vm,
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<CompressPdfRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CompressPdfRoute>()
            CompressPdfScreen(
                repository = container.pdfRepository,
                preselectedId = route.preselectedId,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<PdfToJpgRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PdfToJpgRoute>()
            PdfToJpgScreen(
                repository = container.pdfRepository,
                preselectedId = route.preselectedId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<MergePdfRoute> {
            MergePdfScreen(
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<DocxToPdfRoute> {
            DocxToPdfScreen(
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<MoreToolsRoute> {
            MoreToolsScreen(
                onBack = { navController.popBackStack() },
                onSelectTool = { tool ->
                    if (tool == MoreToolType.AUTO_CROP) {
                        navController.navigate(AutoCropRoute())
                    } else {
                        navController.navigate(PdfToolDetailRoute(tool.id))
                    }
                }
            )
        }

        composable<PdfToolDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PdfToolDetailRoute>()
            val toolType = MoreToolType.entries.firstOrNull { it.id == route.toolId } ?: MoreToolType.SPLIT
            PdfToolDetailScreen(
                toolType = toolType,
                repository = container.pdfRepository,
                preselectedId = route.preselectedId,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }

        composable<PdfViewerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PdfViewerRoute>()
            PdfViewerScreen(
                pdfId = route.pdfId,
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onUnlockPdf = { id ->
                    navController.navigate(PdfToolDetailRoute(MoreToolType.UNLOCK.id, preselectedId = id))
                }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                preferencesRepository = container.preferencesRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable<SearchRoute> {
            SearchScreen(
                repository = container.pdfRepository,
                onBack = { navController.popBackStack() },
                onOpenPdf = { doc -> navController.navigate(PdfViewerRoute(doc.id)) }
            )
        }
    }

    if (showCreateSheet) {
        CreateActionBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreateFromImages = { navController.navigate(ImageToPdfRoute) },
            onScanDocument = { navController.navigate(ScannerRoute) },
            onAutoCrop = { navController.navigate(AutoCropRoute()) },
            onImportPdf = { importPdfLauncher.launch(arrayOf("application/pdf")) },
            onMergePdf = { navController.navigate(MergePdfRoute) },
            onCreateBlankPdf = {
                navController.navigate(PdfToolDetailRoute(MoreToolType.CREATE_BLANK.id))
            }
        )
    }
}
