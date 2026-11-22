package com.example.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.PageSizeOption
import com.example.data.model.SaveLocationOption
import com.example.data.model.UserSettings
import com.example.ui.components.ShareAndPrintHelper
import com.example.ui.theme.LocalSmartPdfColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SupportedLanguages = listOf(
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "hi" to "हिन्दी"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: UserPreferencesRepository,
    onBack: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by preferencesRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSaveLocDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = colors.textPrimary) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Appearance & Language", style = MaterialTheme.typography.labelLarge, color = PrimaryBlueLight)

                Surface(color = colors.cardBackground, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.DarkMode, null, tint = PrimaryBlueLight)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dark Theme", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                Text(
                                    if (settings.darkTheme) "Premium Dark UI active" else "Light Mode active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = settings.darkTheme,
                                onCheckedChange = { enabled ->
                                    scope.launch { preferencesRepository.setDarkTheme(enabled) }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                                modifier = Modifier.testTag("settings_theme_switch")
                            )
                        }

                        SettingsRowItem(
                            icon = Icons.Filled.Language,
                            title = "Language",
                            subtitle = SupportedLanguages.firstOrNull { it.first == settings.languageCode }?.second ?: "English",
                            onClick = { showLanguageDialog = true }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("PDF Defaults", style = MaterialTheme.typography.labelLarge, color = PrimaryBlueLight)

                Surface(color = colors.cardBackground, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Filled.AspectRatio,
                            title = "Default PDF Page Size",
                            subtitle = settings.defaultPageSize.label,
                            onClick = { showPageSizeDialog = true }
                        )
                        SettingsRowItem(
                            icon = Icons.Filled.HighQuality,
                            title = "Default Image Quality",
                            subtitle = "${settings.defaultImageQuality}%",
                            onClick = { showQualityDialog = true }
                        )
                        SettingsRowItem(
                            icon = Icons.Filled.Folder,
                            title = "Default Save Location",
                            subtitle = settings.defaultSaveLocation.label,
                            onClick = { showSaveLocDialog = true }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Permissions & Info", style = MaterialTheme.typography.labelLarge, color = PrimaryBlueLight)

                Surface(color = colors.cardBackground, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Filled.Security,
                            title = "App Permissions",
                            subtitle = "Camera: ${if (cameraGranted) "Granted" else "Not granted"} • Tap to manage",
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )
                        SettingsRowItem(
                            icon = Icons.Filled.PrivacyTip,
                            title = "Privacy Policy",
                            subtitle = "All PDF processing stays private on your device",
                            onClick = { showPrivacyDialog = true }
                        )
                        SettingsRowItem(
                            icon = Icons.Filled.Info,
                            title = "About App",
                            subtitle = "Smart PDF Maker v1.0",
                            onClick = { showAboutDialog = true }
                        )
                        SettingsRowItem(
                            icon = Icons.Filled.StarRate,
                            title = "Rate App",
                            subtitle = "Share your feedback",
                            onClick = { showRateDialog = true }
                        )
                        SettingsRowItem(
                            icon = Icons.Filled.Share,
                            title = "Share App",
                            subtitle = "Recommend Smart PDF Maker",
                            onClick = { ShareAndPrintHelper.shareApp(context) }
                        )
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("Select Language", color = colors.textPrimary) },
            text = {
                Column {
                    SupportedLanguages.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { preferencesRepository.setLanguageCode(code) }
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.languageCode == code, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("Default PDF Page Size", color = colors.textPrimary) },
            text = {
                Column {
                    listOf(PageSizeOption.A4, PageSizeOption.A3, PageSizeOption.LETTER, PageSizeOption.LEGAL).forEach { sz ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { preferencesRepository.setDefaultPageSize(sz) }
                                    showPageSizeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.defaultPageSize == sz, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(sz.label, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showQualityDialog) {
        var sliderVal by remember(settings.defaultImageQuality) { mutableStateOf(settings.defaultImageQuality.toFloat()) }
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("Default Image Quality (${sliderVal.roundToInt()}%)", color = colors.textPrimary) },
            text = {
                Slider(value = sliderVal, onValueChange = { sliderVal = it }, valueRange = 30f..100f)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { preferencesRepository.setDefaultImageQuality(sliderVal.roundToInt()) }
                        showQualityDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            }
        )
    }

    if (showSaveLocDialog) {
        AlertDialog(
            onDismissRequest = { showSaveLocDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("Default Save Location", color = colors.textPrimary) },
            text = {
                Column {
                    SaveLocationOption.entries.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { preferencesRepository.setDefaultSaveLocation(loc) }
                                    showSaveLocDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.defaultSaveLocation == loc, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(loc.label, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaveLocDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("Privacy Policy", color = colors.textPrimary) },
            text = {
                Text(
                    "Smart PDF Maker processes all documents, images, and scans completely offline on your device. No files or personal data are uploaded to external servers.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("About Smart PDF Maker", color = colors.textPrimary) },
            text = {
                Text(
                    "Version 1.0\n\nA complete mobile PDF studio for creating, scanning, editing, converting, compressing, merging, and securing PDF documents.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    if (showRateDialog) {
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            containerColor = colors.cardBackground,
            title = { Text("Rate Smart PDF Maker", color = colors.textPrimary) },
            text = {
                Text("Enjoying Smart PDF Maker? Thank you for supporting our PDF utility app!", color = colors.textSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRateDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Thank you for your 5-star support!") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("5 Stars", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) { Text("Later") }
            }
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalSmartPdfColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PrimaryBlueLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}
