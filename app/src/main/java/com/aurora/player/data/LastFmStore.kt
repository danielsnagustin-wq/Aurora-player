package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.lastFmDataStore by preferencesDataStore(
    name = "aurora_lastfm",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — llave de la API de Last.fm (2026-11-13).
 * Guarda SOLO la llave de la app "Aurora Player", en su teléfono, nunca
 * sale del dispositivo. Sin llave el cliente no hace red (devuelve
 * null/vacío). La llave se crea en last.fm/api/account/create y se
 * guarda aquí cuando exista.
 */
class LastFmStore(context: Context) {
    private val dataStore = context.applicationContext.lastFmDataStore

    companion object {
        private val API_KEY = stringPreferencesKey("lastfm_api_key")
    }

    val apiKeyFlow: Flow<String> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[API_KEY].orEmpty() }

    val connectedFlow: Flow<Boolean> = apiKeyFlow.map { it.isNotBlank() }

    suspend fun saveApiKey(apiKey: String) {
        dataStore.edit { prefs ->
            prefs[API_KEY] = apiKey.trim()
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(API_KEY)
        }
    }
}
