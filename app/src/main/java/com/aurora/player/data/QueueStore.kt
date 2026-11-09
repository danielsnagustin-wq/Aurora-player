package com.aurora.player.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Aurora Player — cola persistente (ETAPA 4).
 * Guarda los ids de la cola, el índice actual y la posición, en
 * DataStore (sin migración Room: la cola es estado, no datos del
 * usuario). Solo se restaura si "Reanudar donde quedó" está activo.
 */
private val Context.queueDataStore by preferencesDataStore(name = "aurora_queue")

data class SavedQueue(
    val songIds: List<Long> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
)

class QueueStore(private val context: Context) {

    private object Keys {
        val IDS = stringPreferencesKey("queue_ids")
        val INDEX = intPreferencesKey("queue_index")
        val POSITION = longPreferencesKey("queue_position_ms")
    }

    val queueFlow: Flow<SavedQueue> = context.queueDataStore.data.map { prefs ->
        SavedQueue(
            songIds = (prefs[Keys.IDS] ?: "")
                .split(",").mapNotNull { it.toLongOrNull() },
            index = prefs[Keys.INDEX] ?: 0,
            positionMs = prefs[Keys.POSITION] ?: 0L,
        )
    }

    suspend fun save(songIds: List<Long>, index: Int, positionMs: Long) {
        context.queueDataStore.edit { prefs ->
            prefs[Keys.IDS] = songIds.joinToString(",")
            prefs[Keys.INDEX] = index
            prefs[Keys.POSITION] = positionMs
        }
    }

    suspend fun clear() {
        context.queueDataStore.edit { prefs ->
            prefs[Keys.IDS] = ""
            prefs[Keys.INDEX] = 0
            prefs[Keys.POSITION] = 0L
        }
    }
}
