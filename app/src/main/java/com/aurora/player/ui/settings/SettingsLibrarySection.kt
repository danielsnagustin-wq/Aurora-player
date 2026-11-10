package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.FolderStore
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.theme.auroraButtonColors
import kotlinx.coroutines.launch

/**
 * Sección Biblioteca, partida de SettingsScreen (2026-11-09, paso
 * estructural 2): une los DOS bloques LIBRARY que vivían separados
 * (duración mínima + pestañas visibles + tamaño de portada, y
 * carpetas excluidas). Las carpetas se miden solas: crean su propio
 * FolderStore dentro.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsLibrarySection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
    visibleTabs: Set<Int>,
    onSetTabVisible: (Int, Boolean) -> Unit,
    rowCoverDp: Int,
    onSetRowCoverSize: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // RONDA 2 (2026-11-09): la duración mínima existía en el
    // modelo y la biblioteca la usaba, pero NO HABÍA control para
    // cambiarla en Ajustes: era un ajuste muerto. Ahora sí se
    // puede tocar; se aplica al volver a escanear la biblioteca.
    SettingsGroupTitle("Biblioteca")
    SettingsGroupDescription("Qué canciones entran en la biblioteca. Las muy cortas suelen ser tonos o notas de voz.")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((ms, label) in listOf(
            0L to "Todo",
            5_000L to "Desde 5 s",
            10_000L to "Desde 10 s",
            30_000L to "Desde 30 s",
            60_000L to "Desde 1 min",
        )) {
            if (player.minDurationMs == ms) {
                Button(
                    onClick = { onSavePlayer(player.copy(minDurationMs = ms)) },
                    colors = auroraButtonColors(),
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSavePlayer(player.copy(minDurationMs = ms)) }) {
                    Text(label)
                }
            }
        }
    }
    SettingsGroupDescription(
        "Se aplica al volver a escanear la biblioteca.",
        modifier = Modifier.padding(top = 4.dp),
    )
    SettingsGroupTitle("Pestañas visibles")
    SettingsGroupDescription("Elige qué pestañas se ven en el inicio. Siempre queda al menos una visible.")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        for ((index, label) in listOf(
            "Canciones", "Artistas", "Álbumes", "Favoritos", "Géneros", "Carpetas", "Online",
        ).withIndex()) {
            if (index in visibleTabs) {
                Button(
                    onClick = { onSetTabVisible(index, false) },
                    colors = auroraButtonColors(),
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSetTabVisible(index, true) }) {
                    Text(label)
                }
            }
        }
    }
    SettingsGroupTitle("Tamaño de portada en la fila")
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((dp, label) in listOf(
            32 to "Pequeña", 40 to "Normal", 56 to "Grande", 72 to "Muy grande",
        )) {
            if (rowCoverDp == dp) {
                Button(
                    onClick = { onSetRowCoverSize(dp) },
                    colors = auroraButtonColors(),
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSetRowCoverSize(dp) }) {
                    Text(label)
                }
            }
        }
    }
    // SPEC 005 FR-006: carpetas excluidas (se excluyen con
    // mantener pulsado en la pestaña Carpetas; aquí se quitan).
    val folderStore = remember { FolderStore(context) }
    val excludedFolders by folderStore.excludedFlow.collectAsState(initial = emptySet())
    SettingsGroupTitle("Carpetas excluidas")
    SettingsGroupDescription("Estas carpetas no aparecen en ninguna pestaña, ni en la búsqueda, ni en la cola. Quítalas para volver a incluirlas.")
    if (excludedFolders.isEmpty()) {
        SettingsGroupDescription("Ninguna carpeta excluida.", modifier = Modifier.padding(top = 8.dp))
    } else {
        for (folder in excludedFolders.sorted()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(folder, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch { folderStore.include(folder) }
                }) { Text("Quitar") }
            }
        }
        TextButton(
            onClick = { scope.launch { folderStore.clearExcluded() } },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Quitar todas") }
    }
}
