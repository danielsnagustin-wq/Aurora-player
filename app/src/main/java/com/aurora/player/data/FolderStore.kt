package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.folderDataStore by preferencesDataStore(
    name = "aurora_folders",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — carpetas excluidas (SPEC 005, FR-006).
 * Rutas de carpeta (no de canción) que salen de TODA la biblioteca:
 * pestañas, búsqueda, duplicados y cola. Se guardan en DataStore,
 * solo en el teléfono. Quitar una carpeta la vuelve a incluir.
 */
class FolderStore(context: Context) {
    private val dataStore = context.applicationContext.folderDataStore

    companion object {
        private val EXCLUDED_KEY = stringSetPreferencesKey("excluded_folders")
    }

    val excludedFlow: Flow<Set<String>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[EXCLUDED_KEY] ?: emptySet() }

    suspend fun exclude(path: String) {
        val clean = path.trim()
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[EXCLUDED_KEY] = (prefs[EXCLUDED_KEY] ?: emptySet()) + clean
        }
    }

    suspend fun include(path: String) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_KEY] = (prefs[EXCLUDED_KEY] ?: emptySet()) - path
        }
    }

    suspend fun clearExcluded() {
        dataStore.edit { prefs -> prefs.remove(EXCLUDED_KEY) }
    }
}
