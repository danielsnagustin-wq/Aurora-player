package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.libraryPrefsDataStore by preferencesDataStore(
    name = "aurora_library_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — inicio personalizable (RONDA 2, 2026-11-09).
 * Qué pestañas se ven en el inicio, cuál fue la última abierta y qué
 * tamaño tienen las portadas de las filas. Solo en el teléfono.
 * Las pestañas son índices 0..6 (Canciones, Artistas, Álbumes,
 * Favoritos, Géneros, Carpetas, Online); nunca se esconde la última.
 */
class LibraryPrefsStore(context: Context) {
    private val dataStore = context.applicationContext.libraryPrefsDataStore

    companion object {
        const val TAB_COUNT = 7
        val ALL_TABS: Set<Int> = (0 until TAB_COUNT).toSet()
        const val DEFAULT_ROW_COVER_DP = 40
        private val VISIBLE_TABS_KEY = stringPreferencesKey("visible_tabs_csv")
        private val LAST_TAB_KEY = intPreferencesKey("last_tab")
        private val ROW_COVER_KEY = intPreferencesKey("row_cover_dp")
        private val IMAGE_CACHE_KEY = booleanPreferencesKey("image_cache_enabled")
    }

    val visibleTabsFlow: Flow<Set<Int>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val raw = prefs[VISIBLE_TABS_KEY] ?: return@map ALL_TABS
                val parsed = raw.split(",").mapNotNull { it.toIntOrNull() }
                    .filter { it in 0 until TAB_COUNT }.toSet()
                parsed.ifEmpty { ALL_TABS }
            }

    val lastTabFlow: Flow<Int> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> (prefs[LAST_TAB_KEY] ?: 0).coerceIn(0, TAB_COUNT - 1) }

    val rowCoverSizeFlow: Flow<Int> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> (prefs[ROW_COVER_KEY] ?: DEFAULT_ROW_COVER_DP).coerceIn(32, 96) }

    /**
     * Asistente de configuración (estilo Namida, 2026-11-09): caché
     * de portadas en memoria. Apagada, cada fila vuelve a cargar su
     * portada desde MediaStore (más lento, menos memoria). Por
     * defecto encendida.
     */
    val imageCacheEnabledFlow: Flow<Boolean> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[IMAGE_CACHE_KEY] ?: true }

    suspend fun setImageCacheEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[IMAGE_CACHE_KEY] = enabled }
    }

    suspend fun setTabVisible(index: Int, visible: Boolean) {
        if (index !in 0 until TAB_COUNT) return
        dataStore.edit { prefs ->
            val current = visibleTabsFlowCsv(prefs[VISIBLE_TABS_KEY])
            val updated = if (visible) current + index else current - index
            // Nunca esconder la última pestaña: el inicio no puede
            // quedarse sin pestañas.
            prefs[VISIBLE_TABS_KEY] = updated.ifEmpty { current }.sorted().joinToString(",")
        }
    }

    private fun visibleTabsFlowCsv(raw: String?): Set<Int> {
        if (raw.isNullOrBlank()) return ALL_TABS
        return raw.split(",").mapNotNull { it.toIntOrNull() }
            .filter { it in 0 until TAB_COUNT }.toSet().ifEmpty { ALL_TABS }
    }

    suspend fun saveLastTab(index: Int) {
        if (index !in 0 until TAB_COUNT) return
        dataStore.edit { prefs -> prefs[LAST_TAB_KEY] = index }
    }

    suspend fun setRowCoverSize(dp: Int) {
        dataStore.edit { prefs -> prefs[ROW_COVER_KEY] = dp.coerceIn(32, 96) }
    }
}
