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

private val Context.ytMusicDataStore by preferencesDataStore(
    name = "aurora_ytmusic",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — sesión de YouTube Music (2026-11-10).
 * Guarda SOLO las cookies de la sesión del usuario, en su teléfono,
 * nunca salen del dispositivo. Sin cookies no hay sesión. No se
 * guarda ningún nombre, correo ni dato de la cuenta de Google.
 */
class YouTubeMusicStore(context: Context) {
    private val dataStore = context.applicationContext.ytMusicDataStore

    companion object {
        private val COOKIES_KEY = stringPreferencesKey("cookies")
    }

    val cookiesFlow: Flow<String> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[COOKIES_KEY].orEmpty() }

    val connectedFlow: Flow<Boolean> = cookiesFlow.map { it.isNotBlank() }

    suspend fun setCookies(cookies: String) {
        dataStore.edit { prefs ->
            prefs[COOKIES_KEY] = cookies.trim()
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(COOKIES_KEY)
        }
    }
}
