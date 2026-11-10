package com.aurora.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.aurora.player.data.ThemePresetStore
import com.aurora.player.model.MAX_THEMES
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.themePresetFromJsonValue
import com.aurora.player.online.JsonParser
import kotlinx.coroutines.launch

/**
 * Sección Mis temas, partida de SettingsScreen (2026-11-09, paso
 * estructural 3e, SPEC 007): guardar, aplicar, renombrar, duplicar,
 * exportar e importar temas. La tienda de temas y el mensaje de
 * importación llegan desde SettingsScreen; el resto vive aquí.
 */
@Composable
fun SettingsThemesSection(
    applied: ThemeSettings,
    savedThemes: List<ThemePreset>,
    onApplyTheme: (ThemeSettings) -> Unit,
    themeImportMessage: String?,
    onThemeImportMessageChange: (String?) -> Unit,
    presetStore: ThemePresetStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsGroupTitle("Mis temas")
    SettingsGroupDescription("Tus temas guardados usan los mismos colores de Interfaz. Aplicar uno cambia todo el aspecto al instante.")
    val pickTheme = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                null
            }
            if (text == null) {
                onThemeImportMessageChange("No se pudo leer ese archivo.")
                return@launch
            }
            val preset = try {
                themePresetFromJsonValue(JsonParser.parse(text))
            } catch (_: Exception) {
                null
            }
            if (preset == null) {
                onThemeImportMessageChange("Ese archivo no es un tema de Aurora Player.")
                return@launch
            }
            val name = if (savedThemes.any { it.name == preset.name }) {
                "${preset.name} (2)"
            } else {
                preset.name
            }
            presetStore.saveThemes((savedThemes + preset.copy(name = name)).takeLast(MAX_THEMES))
            onThemeImportMessageChange("Tema \"$name\" importado.")
        }
    }
    ThemePresetsSection(
        presets = savedThemes,
        onApply = { onApplyTheme(it.settings) },
        onSave = { name ->
            scope.launch {
                val updated = (savedThemes.filter { it.name != name } + ThemePreset(name, applied))
                    .takeLast(MAX_THEMES)
                presetStore.saveThemes(updated)
            }
        },
        onRename = { preset, name ->
            scope.launch {
                presetStore.saveThemes(
                    savedThemes.map { if (it.name == preset.name) it.copy(name = name) else it },
                )
            }
        },
        onDelete = { preset ->
            scope.launch {
                presetStore.saveThemes(savedThemes.filter { it.name != preset.name })
            }
        },
        onDuplicate = { preset ->
            scope.launch {
                var name = "${preset.name} (copia)"
                var n = 2
                while (savedThemes.any { it.name == name }) {
                    name = "${preset.name} (copia $n)"
                    n++
                }
                presetStore.saveThemes(
                    (savedThemes + preset.copy(name = name)).takeLast(MAX_THEMES),
                )
            }
        },
        onExport = { preset ->
            scope.launch {
                val result = exportThemeFile(context, preset)
                onThemeImportMessageChange(result.fold(
                    onSuccess = { "Tema exportado en $it." },
                    onFailure = { "No se pudo exportar el tema." },
                ))
            }
        },
        onImportClick = { pickTheme.launch(arrayOf("application/json")) },
        importMessage = themeImportMessage,
    )
}
