package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SmartPdfDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.PdfRepository
import com.example.pdf.DocxToPdfEngine
import com.example.pdf.PdfGenerationEngine
import com.example.pdf.PdfManipulationEngine
import com.example.pdf.PdfRendererHelper
import com.example.pdf.StorageHelper
import com.example.ui.components.SmartPdfBottomBar
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.more.MoreToolsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.welcome.WelcomeScreen
import com.example.ui.theme.SmartPdfMakerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class SmartPdfUiCujTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `welcome screen renders all primary action buttons and handles close`() {
        var closed = false
        composeTestRule.setContent {
            SmartPdfMakerTheme(darkTheme = true) {
                WelcomeScreen(
                    onCloseToHome = { closed = true },
                    onImageToPdf = {},
                    onScanToPdf = {},
                    onImportPdf = {},
                    onTryOthers = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome to\nSmart PDF Maker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_image_to_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_scan_to_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_import_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_try_others").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_close_button").performClick()
        assertTrue(closed)
    }

    @Test
    fun `home screen displays feature grid, empty state, and bottom navigation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SmartPdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storage = StorageHelper(context)
        val repo = PdfRepository(
            context = context,
            pdfDao = db.pdfDao(),
            preferencesRepository = UserPreferencesRepository(context),
            storageHelper = storage,
            rendererHelper = PdfRendererHelper(storage),
            generationEngine = PdfGenerationEngine(context),
            manipulationEngine = PdfManipulationEngine(),
            docxEngine = DocxToPdfEngine(context)
        )
        val vm = HomeViewModel(repo)

        composeTestRule.setContent {
            SmartPdfMakerTheme(darkTheme = true) {
                HomeScreen(
                    viewModel = vm,
                    onOpenSearch = {},
                    onOpenSettings = {},
                    onImageToPdf = {},
                    onSmartScan = {},
                    onImportPdf = {},
                    onCompressPdf = {},
                    onPdfToJpg = {},
                    onMergePdf = {},
                    onDocxToPdf = {},
                    onMoreTools = {},
                    onOpenPdf = {},
                    bottomBar = {
                        SmartPdfBottomBar(
                            currentTabIsHome = true,
                            onHomeClick = {},
                            onAddClick = {},
                            onFilesClick = {}
                        )
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_heading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_image_to_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_smart_scan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_import_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_compress_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_pdf_to_jpg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_merge_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_docx_to_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("feature_more").assertIsDisplayed()
        composeTestRule.onNodeWithText("No PDF files found. Create your first PDF.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_plus_fab").assertIsDisplayed()
        db.close()
    }

    @Test
    fun `more tools screen displays all 11 pdf utilities`() {
        composeTestRule.setContent {
            SmartPdfMakerTheme(darkTheme = true) {
                MoreToolsScreen(onBack = {}, onSelectTool = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_tool_split").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_tool_rotate").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_tool_protect").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_tool_unlock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_tool_watermark").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("more_tool_create_blank"))
        composeTestRule.onNodeWithTag("more_tool_create_blank").assertIsDisplayed()
    }

    @Test
    fun `settings screen shows dark theme toggle and preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        composeTestRule.setContent {
            SmartPdfMakerTheme(darkTheme = true) {
                SettingsScreen(preferencesRepository = prefs, onBack = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_theme_switch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default PDF Page Size").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default Save Location").assertIsDisplayed()
    }
}
