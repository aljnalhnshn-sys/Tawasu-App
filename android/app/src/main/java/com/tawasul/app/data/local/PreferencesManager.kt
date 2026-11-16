package com.tawasul.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tawasul_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val darkModeKey = booleanPreferencesKey("dark_mode_enabled")
    private val useSystemThemeKey = booleanPreferencesKey("use_system_theme")

    val isDarkModeFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        val useSystem = prefs[useSystemThemeKey] ?: true
        if (useSystem) null else (prefs[darkModeKey] ?: false)
    }

    suspend fun setDarkMode(dark: Boolean?) {
        context.dataStore.edit { prefs ->
            if (dark == null) {
                prefs[useSystemThemeKey] = true
            } else {
                prefs[useSystemThemeKey] = false
                prefs[darkModeKey] = dark
            }
        }
    }
}
