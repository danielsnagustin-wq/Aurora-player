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

private val Context.soundCloudDataStore by preferencesDataStore(
    name = "aurora_soundcloud",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — sesión de SoundCloud (Descubrimiento, 2026-11-13).
 * Guarda SOLO el token oauth_token de la sesión del usuario, en su
 * teléfono, nunca sale del dispositivo. Se saca del WebView de login
 * (como las cookies de YouTube Music) y solo se usa para leer /me y
 * el feed de seguidos (/stream). Sin token, SoundCloud sigue anónimo.
 */
class SoundCloudStore(context: Context) {
    private val dataStore = context.applicationContext.soundCloudDataStore

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("oauth_token")
    }

    val tokenFlow: Flow<String> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[TOKEN_KEY].orEmpty() }

    val connectedFlow: Flow<Boolean> = tokenFlow.map { it.isNotBlank() }

    suspend fun setToken(token: String) {
        dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token.trim()
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
        }
    }
}
