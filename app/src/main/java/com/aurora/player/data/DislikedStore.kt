package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dislikedDataStore by preferencesDataStore(
    name = "aurora_disliked",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * MENÚ ⋮ (2026-11-11, como BitChord): "No me gusta".
 *
 * Las claves son "local:<id>" para la biblioteca y "yt:<videoId>"
 * para YouTube Music. La radio (Auto-DJ) nunca vuelve a poner una
 * canción de esta lista. Es una preferencia, no un dato de la
 * biblioteca: no va al respaldo (igual que el temporizador).
 */
class DislikedStore(private val context: Context) {

    private object Keys {
        val KEYS = stringSetPreferencesKey("disliked_keys")
    }

    val keysFlow: Flow<Set<String>> = context.dislikedDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[Keys.KEYS] ?: emptySet() }

    suspend fun toggle(key: String) {
        context.dislikedDataStore.edit { prefs ->
            val current = prefs[Keys.KEYS] ?: emptySet()
            prefs[Keys.KEYS] = if (key in current) current - key else current + key
        }
    }
}
