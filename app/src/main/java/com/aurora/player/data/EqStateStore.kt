package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.eqStateDataStore by preferencesDataStore(
    name = "aurora_eq_state",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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
    /** Tone 2D (Fase 2): graves y agudos en dB, capa sobre los 10 niveles. */
    val toneBassDb: Float = 0f,
    val toneTrebleDb: Float = 0f,
)

class EqStateStore(context: Context) {
    private val dataStore = context.applicationContext.eqStateDataStore

    private object Keys {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val LEVELS = stringPreferencesKey("eq_levels")
        val BASS = intPreferencesKey("eq_bass")
        val VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        val PER_OUTPUT = booleanPreferencesKey("eq_per_output")
        val TONE_BASS = floatPreferencesKey("eq_tone_bass")
        val TONE_TREBLE = floatPreferencesKey("eq_tone_treble")
    }

    /** EQ distinto por salida (OFF por defecto: un solo EQ global, como hasta ahora). */
    val perOutputFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[Keys.PER_OUTPUT] ?: false }

    suspend fun savePerOutput(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.PER_OUTPUT] = enabled }
    }

    /**
     * EQ guardado para una salida ("speaker", "wired", "usb",
     * "bluetooth:<nombre>"). Formato: "enabled|niveles,bass|virt"
     * o con Tone 2D: "enabled|niveles,bass|virt|graves,agudos".
     * Los de 4 partes (viejos) se leen con tono en cero.
     * Si no hay nada guardado para esa salida, sale el EQ global.
     */
    fun routeStateFlow(routeKey: String): Flow<EqState?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[stringPreferencesKey("eq_route_$routeKey")] ?: return@map null
            parseRouteState(raw)
        }

    suspend fun saveRoute(routeKey: String, state: EqState) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("eq_route_$routeKey")] =
                "${if (state.enabled) 1 else 0}|" +
                state.levelsDb.map { it.coerceIn(-15f, 15f) }.joinToString(",") +
                "|${state.bassBoost.coerceIn(0, 1000)}|${state.virtualizer.coerceIn(0, 1000)}" +
                "|${state.toneBassDb.coerceIn(-10f, 10f)},${state.toneTrebleDb.coerceIn(-10f, 10f)}"
        }
    }

    private fun parseRouteState(raw: String): EqState? {
        val parts = raw.split("|")
        if (parts.size !in 4..5) return null
        val levels = parts[1].split(",").mapNotNull { it.toFloatOrNull() }
            .takeIf { it.size == 10 }?.map { it.coerceIn(-15f, 15f) } ?: return null
        val tone = if (parts.size == 5) {
            parts[4].split(",").mapNotNull { it.toFloatOrNull() }
                .takeIf { it.size == 2 } ?: return null
        } else listOf(0f, 0f)
        return EqState(
            enabled = parts[0] == "1",
            levelsDb = levels,
            bassBoost = parts[2].toIntOrNull()?.coerceIn(0, 1000) ?: return null,
            virtualizer = parts[3].toIntOrNull()?.coerceIn(0, 1000) ?: return null,
            toneBassDb = tone[0].coerceIn(-10f, 10f),
            toneTrebleDb = tone[1].coerceIn(-10f, 10f),
        )
    }

    val stateFlow: Flow<EqState> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        val levels = (prefs[Keys.LEVELS] ?: "")
            .split(",").mapNotNull { it.toFloatOrNull() }
            .takeIf { it.size == 10 } ?: List(10) { 0f }
        EqState(
            enabled = prefs[Keys.ENABLED] ?: false,
            levelsDb = levels,
            bassBoost = (prefs[Keys.BASS] ?: 0).coerceIn(0, 1000),
            virtualizer = (prefs[Keys.VIRTUALIZER] ?: 0).coerceIn(0, 1000),
            toneBassDb = (prefs[Keys.TONE_BASS] ?: 0f).coerceIn(-10f, 10f),
            toneTrebleDb = (prefs[Keys.TONE_TREBLE] ?: 0f).coerceIn(-10f, 10f),
        )
    }

    suspend fun save(state: EqState) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = state.enabled
            prefs[Keys.LEVELS] = state.levelsDb.map { it.coerceIn(-15f, 15f) }.joinToString(",")
            prefs[Keys.BASS] = state.bassBoost.coerceIn(0, 1000)
            prefs[Keys.VIRTUALIZER] = state.virtualizer.coerceIn(0, 1000)
            prefs[Keys.TONE_BASS] = state.toneBassDb.coerceIn(-10f, 10f)
            prefs[Keys.TONE_TREBLE] = state.toneTrebleDb.coerceIn(-10f, 10f)
        }
    }
}
