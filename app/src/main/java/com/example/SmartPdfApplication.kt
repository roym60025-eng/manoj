package com.example

import android.app.Application
import com.example.data.local.SmartPdfDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.PdfRepository
import com.example.pdf.DocxToPdfEngine
import com.example.pdf.PdfGenerationEngine
import com.example.pdf.PdfManipulationEngine
import com.example.pdf.PdfRendererHelper
import com.example.pdf.StorageHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class SmartPdfApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val context = application.applicationContext
    val database: SmartPdfDatabase by lazy { SmartPdfDatabase.getInstance(context) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(context) }
    val storageHelper: StorageHelper by lazy { StorageHelper(context) }
    val rendererHelper: PdfRendererHelper by lazy { PdfRendererHelper(storageHelper) }
    val generationEngine: PdfGenerationEngine by lazy { PdfGenerationEngine(context) }
    val manipulationEngine: PdfManipulationEngine by lazy { PdfManipulationEngine() }
    val docxEngine: DocxToPdfEngine by lazy { DocxToPdfEngine(context) }

    val pdfRepository: PdfRepository by lazy {
        PdfRepository(
            context = context,
            pdfDao = database.pdfDao(),
            preferencesRepository = preferencesRepository,
            storageHelper = storageHelper,
            rendererHelper = rendererHelper,
            generationEngine = generationEngine,
            manipulationEngine = manipulationEngine,
            docxEngine = docxEngine
        )
    }
}
