package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * Las pestañas son índices 0..7 (Canciones, Artistas, Álbumes,
 * Favoritos, Géneros, Carpetas, Online, Listas); nunca se esconde
 * la última. Una pestaña NUEVA (índice mayor que el guardado) se
 * muestra por defecto: nadie la ha escondido todavía.
 */
class LibraryPrefsStore(context: Context) {
    private val dataStore = context.applicationContext.libraryPrefsDataStore

    companion object {
        const val TAB_COUNT = 8
        val ALL_TABS: Set<Int> = (0 until TAB_COUNT).toSet()
        // UI-1 (2026-11-11) + revisión Aurora: 40 dp en una fila de
        // 72 dp dejaba la portada como miniatura; 52 dp le da
        // presencia sin robar sitio al título (rango 32..96).
        const val DEFAULT_ROW_COVER_DP = 52
        private val VISIBLE_TABS_KEY = stringPreferencesKey("visible_tabs_csv")
        // Cuántas pestañas conocía el teléfono cuando se guardó la
        // selección. Sin esta clave (instalaciones anteriores a
        // Listas) se asume 7, así la pestaña 7 aparece visible.
        private val TABS_VERSION_KEY = intPreferencesKey("tabs_version")
        private val LAST_TAB_KEY = intPreferencesKey("last_tab")
        private val ROW_COVER_KEY = intPreferencesKey("row_cover_dp")
        private val IMAGE_CACHE_KEY = booleanPreferencesKey("image_cache_enabled")
        // Ronda 19: el orden de clasificación (LibrarySort.name).
        // Se guarda como texto para no acoplar data con ui.
        private val SORT_KEY = stringPreferencesKey("sort_order")
    }

    val visibleTabsFlow: Flow<Set<Int>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val raw = prefs[VISIBLE_TABS_KEY] ?: return@map ALL_TABS
                parseVisibleTabs(raw, prefs[TABS_VERSION_KEY] ?: 7)
            }

    val lastTabFlow: Flow<Int> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> (prefs[LAST_TAB_KEY] ?: 0).coerceIn(0, TAB_COUNT - 1) }

    val rowCoverSizeFlow: Flow<Int> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> (prefs[ROW_COVER_KEY] ?: DEFAULT_ROW_COVER_DP).coerceIn(32, 96) }

    /** Orden de clasificación guardado (nombre de LibrarySort; "TITLE" por defecto). */
    val sortFlow: Flow<String> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[SORT_KEY] ?: "TITLE" }

    suspend fun saveSort(name: String) {
        dataStore.edit { prefs -> prefs[SORT_KEY] = name }
    }

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
            val current = visibleTabsFlowCsv(
                prefs[VISIBLE_TABS_KEY],
                prefs[TABS_VERSION_KEY] ?: 7,
            )
            val updated = if (visible) current + index else current - index
            // Nunca esconder la última pestaña: el inicio no puede
            // quedarse sin pestañas.
            prefs[VISIBLE_TABS_KEY] = updated.ifEmpty { current }.sorted().joinToString(",")
            prefs[TABS_VERSION_KEY] = TAB_COUNT
        }
    }

    private fun visibleTabsFlowCsv(raw: String?, knownVersion: Int): Set<Int> {
        if (raw.isNullOrBlank()) return ALL_TABS
        return parseVisibleTabs(raw, knownVersion)
    }

    /**
     * Parsea el CSV guardado. Las pestañas con índice >= knownVersion
     * no existían cuando se guardó la selección: se añaden visibles
     * (nadie las ha escondido). Sin versión guardada se asume 7.
     */
    private fun parseVisibleTabs(raw: String, knownVersion: Int): Set<Int> {
        val parsed = raw.split(",").mapNotNull { it.toIntOrNull() }
            .filter { it in 0 until TAB_COUNT }.toSet()
        val withNew = if (knownVersion < TAB_COUNT) {
            parsed + (knownVersion until TAB_COUNT).toSet()
        } else {
            parsed
        }
        return withNew.ifEmpty { ALL_TABS }
    }

    suspend fun saveLastTab(index: Int) {
        if (index !in 0 until TAB_COUNT) return
        dataStore.edit { prefs -> prefs[LAST_TAB_KEY] = index }
    }

    suspend fun setRowCoverSize(dp: Int) {
        dataStore.edit { prefs -> prefs[ROW_COVER_KEY] = dp.coerceIn(32, 96) }
    }
}
