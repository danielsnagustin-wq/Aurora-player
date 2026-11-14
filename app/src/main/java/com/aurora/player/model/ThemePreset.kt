package com.aurora.player.model

import com.aurora.player.data.themeSettingsFromJsonValue
import com.aurora.player.data.themeSettingsToJsonValue
import com.aurora.player.online.JsonParser
import com.aurora.player.online.JsonValue
import com.aurora.player.online.arr
import com.aurora.player.online.obj
import com.aurora.player.online.str
import com.aurora.player.util.toJsonString

/** Máximo de temas guardados (igual que las colas con nombre: 20). */
const val MAX_THEMES = 20

/**
 * Aurora Player — un tema guardado (SPEC 007, 2026-11-09): nombre +
 * los mismos 23 campos de ThemeSettings. La serialización REUSA la
 * de BackupData (themeSettingsToJsonValue / FromJsonValue): no hay
 * una segunda copia de la conversión.
 */
data class ThemePreset(
    val name: String,
    val settings: ThemeSettings,
)

/** Nombre recortado a 40 caracteres; null si queda vacío. */
fun normalizePresetName(name: String): String? =
    name.trim().take(40).takeIf { it.isNotEmpty() }

fun themePresetToJsonValue(preset: ThemePreset): JsonValue.Obj =
    JsonValue.Obj(
        linkedMapOf(
            "name" to JsonValue.Str(preset.name),
            "settings" to themeSettingsToJsonValue(preset.settings),
        ),
    )

fun themePresetFromJsonValue(v: JsonValue?): ThemePreset? {
    val name = v.obj("name").str() ?: return null
    val clean = normalizePresetName(name) ?: return null
    return ThemePreset(clean, themeSettingsFromJsonValue(v.obj("settings")))
}

fun themePresetsToJsonString(presets: List<ThemePreset>): String =
    JsonValue.Arr(presets.map { themePresetToJsonValue(it) }).toJsonString()

/** JSON roto -> lista vacía (nunca se rompe la app por un tema malo). */
fun themePresetsFromJsonString(text: String): List<ThemePreset> = try {
    JsonParser.parse(text).arr().orEmpty().mapNotNull { themePresetFromJsonValue(it) }
} catch (_: Exception) {
    emptyList()
}
