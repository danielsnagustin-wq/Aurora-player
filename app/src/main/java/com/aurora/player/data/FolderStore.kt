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
 *
 * ESCANEAR (2026-11-11): carpetas A escanear (inclusión). Vacío =
 * se escanea todo, como siempre. Con carpetas marcadas, SOLO esas
 * entran en la biblioteca: así no suben los audios que no son
 * música (notas de voz, tonos) de otras carpetas.
 */
class FolderStore(context: Context) {
    private val dataStore = context.applicationContext.folderDataStore

    companion object {
        private val EXCLUDED_KEY = stringSetPreferencesKey("excluded_folders")
        private val INCLUDED_KEY = stringSetPreferencesKey("included_folders")
    }

    val excludedFlow: Flow<Set<String>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[EXCLUDED_KEY] ?: emptySet() }

    val includedFlow: Flow<Set<String>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[INCLUDED_KEY] ?: emptySet() }

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

    /** Reemplaza el conjunto de carpetas a escanear (el diálogo escribe el conjunto marcado de una vez). */
    suspend fun setIncluded(paths: Set<String>) {
        val clean = paths.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        dataStore.edit { prefs ->
            if (clean.isEmpty()) prefs.remove(INCLUDED_KEY) else prefs[INCLUDED_KEY] = clean
        }
    }

    suspend fun addIncluded(path: String) {
        val clean = path.trim()
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[INCLUDED_KEY] = (prefs[INCLUDED_KEY] ?: emptySet()) + clean
        }
    }

    suspend fun removeIncluded(path: String) {
        dataStore.edit { prefs ->
            prefs[INCLUDED_KEY] = (prefs[INCLUDED_KEY] ?: emptySet()) - path
        }
    }

    suspend fun clearIncluded() {
        dataStore.edit { prefs -> prefs.remove(INCLUDED_KEY) }
    }
}
