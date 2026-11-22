package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserSettings
import com.example.ui.navigation.SmartPdfNavGraph
import com.example.ui.theme.SmartPdfMakerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as SmartPdfApplication).container

        setContent {
            val settings by appContainer.preferencesRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            SmartPdfMakerTheme(darkTheme = settings.darkTheme) {
                SmartPdfNavGraph(container = appContainer)
            }
        }
    }
}
