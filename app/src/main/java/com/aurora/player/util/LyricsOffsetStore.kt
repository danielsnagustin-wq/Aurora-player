package com.aurora.player.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Aurora Player — ajuste de sincronización de letras por canción
 * (idea de Gramophone/Retro: el .lrc puede venir unos ms desplazado
 * y el usuario lo corrige). Un entero por canción en ms, de -5000 a
 * +5000; 0 = en tiempo. Guardado en DataStore, solo en el teléfono.
 */
private val Context.lyricsOffsetDataStore by preferencesDataStore("aurora_lyrics_offset")

class LyricsOffsetStore(private val context: Context) {

    private val key = stringPreferencesKey("offsets")

    suspend fun offsetFor(songId: Long): Int {
        val raw = context.lyricsOffsetDataStore.data.map { it[key] ?: "" }.first()
        return raw.split(";")
            .firstOrNull { it.startsWith("$songId:") }
            ?.substringAfter(":")
            ?.toIntOrNull()
            ?.coerceIn(-5000, 5000)
            ?: 0
    }

    suspend fun saveOffset(songId: Long, offsetMs: Int) {
        val value = offsetMs.coerceIn(-5000, 5000)
        context.lyricsOffsetDataStore.edit { prefs ->
            val current = (prefs[key] ?: "").split(";")
                .filter { it.isNotBlank() && !it.startsWith("$songId:") }
            val updated = current + "$songId:$value"
            prefs[key] = updated.joinToString(";")
        }
    }
}
