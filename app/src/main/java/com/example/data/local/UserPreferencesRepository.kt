package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PageSizeOption
import com.example.data.model.SaveLocationOption
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_pdf_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val DEFAULT_SAVE_LOCATION = stringPreferencesKey("default_save_location")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            defaultPageSize = prefs[Keys.DEFAULT_PAGE_SIZE]?.let { name ->
                PageSizeOption.entries.firstOrNull { it.name == name }
            } ?: PageSizeOption.A4,
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 85,
            defaultSaveLocation = prefs[Keys.DEFAULT_SAVE_LOCATION]?.let { name ->
                SaveLocationOption.entries.firstOrNull { it.name == name }
            } ?: SaveLocationOption.APP_FOLDER
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultImageQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_IMAGE_QUALITY] = quality.coerceIn(10, 100) }
    }

    suspend fun setDefaultSaveLocation(location: SaveLocationOption) {
        context.dataStore.edit { it[Keys.DEFAULT_SAVE_LOCATION] = location.name }
    }
}
