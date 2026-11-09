package com.aurora.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.eqStateDataStore by preferencesDataStore(name = "aurora_eq_state")

/**
 * Aurora Player — estado del ecualizador que sobrevive al reinicio
 * del servicio (refinamiento: antes, al reiniciar el servicio el EQ
 * volvía a cero y apagado aunque el usuario lo hubiera dejado puesto).
 * Solo niveles y activación: nada de canciones ni datos personales.
 */
data class EqState(
    val enabled: Boolean = false,
    val levelsDb: List<Float> = List(10) { 0f },
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
)

class EqStateStore(context: Context) {
    private val dataStore = context.applicationContext.eqStateDataStore

    private object Keys {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val LEVELS = stringPreferencesKey("eq_levels")
        val BASS = intPreferencesKey("eq_bass")
        val VIRTUALIZER = intPreferencesKey("eq_virtualizer")
    }

    val stateFlow: Flow<EqState> = dataStore.data.map { prefs ->
        val levels = (prefs[Keys.LEVELS] ?: "")
            .split(",").mapNotNull { it.toFloatOrNull() }
            .takeIf { it.size == 10 } ?: List(10) { 0f }
        EqState(
            enabled = prefs[Keys.ENABLED] ?: false,
            levelsDb = levels,
            bassBoost = (prefs[Keys.BASS] ?: 0).coerceIn(0, 1000),
            virtualizer = (prefs[Keys.VIRTUALIZER] ?: 0).coerceIn(0, 1000),
        )
    }

    suspend fun save(state: EqState) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = state.enabled
            prefs[Keys.LEVELS] = state.levelsDb.map { it.coerceIn(-15f, 15f) }.joinToString(",")
            prefs[Keys.BASS] = state.bassBoost.coerceIn(0, 1000)
            prefs[Keys.VIRTUALIZER] = state.virtualizer.coerceIn(0, 1000)
        }
    }
}
