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

private val Context.spotifyDataStore by preferencesDataStore(
    name = "aurora_spotify",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — conexión a Spotify como BitChord (2026-11-09).
 * Guarda SOLO la cookie sp_dc del usuario, en su teléfono, nunca
 * sale del dispositivo. Sin cookie no hay conexión. El token de
 * acceso se pide con esa cookie y solo vive en memoria.
 */
class SpotifyStore(context: Context) {
    private val dataStore = context.applicationContext.spotifyDataStore

    companion object {
        private val COOKIE_KEY = stringPreferencesKey("sp_dc")
    }

    val cookieFlow: Flow<String> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[COOKIE_KEY].orEmpty() }

    val connectedFlow: Flow<Boolean> = cookieFlow.map { it.isNotBlank() }

    suspend fun setCookie(cookie: String) {
        dataStore.edit { prefs ->
            prefs[COOKIE_KEY] = cookie.trim()
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(COOKIE_KEY)
        }
    }
}
