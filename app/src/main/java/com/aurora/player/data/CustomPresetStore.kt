package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.player.audio.EqualizerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.eqPresetDataStore by preferencesDataStore(
    name = "aurora_eq_presets",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — presets de EQ propios del usuario.
 * Se guardan en DataStore, solo en el teléfono. Un preset propio es
 * nombre + 10 niveles en dB. Los presets de fábrica (35) no se
 * borran ni se editan: se restablecen volviendo a aplicarlos.
 * Codificación simple en un solo string: "nombre|v,v,...;nombre|v,v,...".
 */
class CustomPresetStore(context: Context) {
    private val dataStore = context.applicationContext.eqPresetDataStore

    companion object {
        private val PRESETS_KEY = stringPreferencesKey("custom_presets")
    }

    val presetsFlow: Flow<List<EqualizerManager.Preset>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> decode(prefs[PRESETS_KEY] ?: "") }

    suspend fun save(name: String, levelsDb: List<Float>) {
        val clean = name.trim().take(40)
        if (clean.isEmpty() || levelsDb.size != 10) return
        // RONDA 2 (2026-11-13, P1): leer la lista FUERA de edit {}
        // y escribir dentro es una carrera: dos guardados a la vez
        // leen la misma lista y uno pisa al otro (se pierde un preset).
        // La lectura va DENTRO de edit {}: DataStore la ejecuta bajo
        // su propio Mutex, así cada guardado ve el último valor.
        dataStore.edit { prefs ->
            val current = decode(prefs[PRESETS_KEY] ?: "").filter { it.name != clean }
            val updated = current + EqualizerManager.Preset(clean, levelsDb.map { it.coerceIn(-15f, 15f) })
            prefs[PRESETS_KEY] = encode(updated)
        }
    }

    suspend fun delete(name: String) {
        dataStore.edit { prefs ->
            val updated = decode(prefs[PRESETS_KEY] ?: "").filter { it.name != name }
            prefs[PRESETS_KEY] = encode(updated)
        }
    }

    private fun encode(presets: List<EqualizerManager.Preset>): String {
        return presets.joinToString(";") { p ->
            p.name.replace(";", "").replace("|", "") + "|" + p.levelsDb.joinToString(",")
        }
    }

    private fun decode(raw: String): List<EqualizerManager.Preset> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size != 2) return@mapNotNull null
            val levels = parts[1].split(",").mapNotNull { it.toFloatOrNull() }
            if (levels.size != 10) return@mapNotNull null
            EqualizerManager.Preset(parts[0], levels)
        }
    }
}
