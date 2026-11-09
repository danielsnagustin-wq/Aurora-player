package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.themePresetsFromJsonString
import com.aurora.player.model.themePresetsToJsonString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.themePresetDataStore by preferencesDataStore(
    name = "aurora_themes",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — "Mis temas" (SPEC 007, 2026-11-09).
 * Temas guardados con nombre (JSON) y colores recientes (CSV ordenado,
 * el más reciente primero, máximo 12). Solo en el teléfono.
 */
class ThemePresetStore(context: Context) {
    private val dataStore = context.applicationContext.themePresetDataStore

    companion object {
        private val THEMES_KEY = stringPreferencesKey("themes_json")
        private val RECENT_KEY = stringPreferencesKey("recent_colors_csv")
        const val MAX_RECENT_COLORS = 12
    }

    val themesFlow: Flow<List<ThemePreset>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> themePresetsFromJsonString(prefs[THEMES_KEY] ?: "[]") }

    val recentColorsFlow: Flow<List<String>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                (prefs[RECENT_KEY] ?: "").split(",").filter { it.isNotBlank() }
            }

    suspend fun saveThemes(themes: List<ThemePreset>) {
        dataStore.edit { prefs -> prefs[THEMES_KEY] = themePresetsToJsonString(themes) }
    }

    suspend fun addRecentColor(hex: String) {
        val clean = ThemeSettings.normalizeHex(hex) ?: return
        dataStore.edit { prefs ->
            val current = (prefs[RECENT_KEY] ?: "").split(",").filter { it.isNotBlank() }
            val updated = (listOf(clean) + current).distinct().take(MAX_RECENT_COLORS)
            prefs[RECENT_KEY] = updated.joinToString(",")
        }
    }
}
