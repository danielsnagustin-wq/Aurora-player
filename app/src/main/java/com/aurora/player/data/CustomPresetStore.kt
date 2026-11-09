package com.aurora.player.data

import android.content.Context
import androidx.datastore.preferences.core.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.player.audio.EqualizerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
        val current = presetsFlow.first().filter { it.name != clean }
        val updated = current + EqualizerManager.Preset(clean, levelsDb.map { it.coerceIn(-15f, 15f) })
        dataStore.edit { it[PRESETS_KEY] = encode(updated) }
    }

    suspend fun delete(name: String) {
        val updated = presetsFlow.first().filter { it.name != name }
        dataStore.edit { it[PRESETS_KEY] = encode(updated) }
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
