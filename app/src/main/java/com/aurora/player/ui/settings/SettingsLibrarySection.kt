package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.aurora.player.ui.design.AuroraMultiChips
import com.aurora.player.ui.design.AuroraOptionChips
import com.aurora.player.ui.design.AuroraSectionHeader
import kotlinx.coroutines.launch

/**
 * Sección Biblioteca, partida de SettingsScreen (2026-11-09, paso
 * estructural 2): une los DOS bloques LIBRARY que vivían separados
 * (duración mínima + pestañas visibles + tamaño de portada, y
 * carpetas excluidas). Las carpetas se miden solas: crean su propio
 * FolderStore dentro.
 */
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
    AuroraSectionHeader(
        title = "Biblioteca",
        description = "Qué canciones entran en la biblioteca. Las muy cortas suelen ser tonos o notas de voz. Se aplica al volver a escanear la biblioteca.",
    )
    AuroraOptionChips(
        options = listOf(
            "0" to "Todo",
            "5000" to "Desde 5 s",
            "10000" to "Desde 10 s",
            "30000" to "Desde 30 s",
            "60000" to "Desde 1 min",
        ),
        selected = player.minDurationMs.toString(),
        onSelect = { onSavePlayer(player.copy(minDurationMs = it.toLong())) },
    )
    AuroraSectionHeader(
        title = "Pestañas visibles",
        description = "Elige qué pestañas se ven en el inicio. Siempre queda al menos una visible.",
    )
    AuroraMultiChips(
        options = listOf(
            "Canciones", "Artistas", "Álbumes", "Favoritos", "Géneros", "Carpetas", "Online", "Listas",
        ).withIndex().map { (index, label) -> index.toString() to label },
        selected = visibleTabs.map { it.toString() }.toSet(),
        onToggle = { onSetTabVisible(it.toInt(), it.toInt() !in visibleTabs) },
    )
    AuroraSectionHeader(title = "Tamaño de portada en la fila")
    AuroraOptionChips(
        options = listOf(
            "32" to "Pequeña", "40" to "Normal", "56" to "Grande", "72" to "Muy grande",
        ),
        selected = rowCoverDp.toString(),
        onSelect = { onSetRowCoverSize(it.toInt()) },
    )
    // SPEC 005 FR-006: carpetas excluidas (se excluyen con
    // mantener pulsado en la pestaña Carpetas; aquí se quitan).
    val folderStore = remember { FolderStore(context) }
    val excludedFolders by folderStore.excludedFlow.collectAsState(initial = emptySet())
    AuroraSectionHeader(
        title = "Carpetas excluidas",
        description = "Estas carpetas no aparecen en ninguna pestaña, ni en la búsqueda, ni en la cola. Quítalas para volver a incluirlas.",
    )
    if (excludedFolders.isEmpty()) {
        Text(
            "Ninguna carpeta excluida.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
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
