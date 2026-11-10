package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.player.lyrics.DEFAULT_PROVIDER_ORDER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.lyricsProviderDataStore by preferencesDataStore(
    name = "aurora_lyrics_providers",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — orden de los proveedores de letras (Fase 2).
 * Guarda en qué orden se prueban LRCLIB, BetterLyrics y BiniLyrics.
 * El primero que encuentre la letra gana. Solo en el teléfono.
 */
class LyricsProviderStore(context: Context) {
    private val dataStore = context.applicationContext.lyricsProviderDataStore

    companion object {
        private val ORDER_KEY = stringPreferencesKey("provider_order")
    }

    val orderFlow: Flow<List<String>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val raw = prefs[ORDER_KEY] ?: return@map DEFAULT_PROVIDER_ORDER
                com.aurora.player.lyrics.parseProviderOrder(raw)
            }

    suspend fun setOrder(order: List<String>) {
        dataStore.edit { prefs ->
            prefs[ORDER_KEY] = com.aurora.player.lyrics.providerOrderToCsv(order)
        }
    }
}
