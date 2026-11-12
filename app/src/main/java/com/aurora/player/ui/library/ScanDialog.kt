package com.aurora.player.ui.library

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.model.FolderEntry
import com.aurora.player.model.treeDocumentIdToPath

/**
 * ESCANEAR (2026-11-11, como BitChord): volver a buscar canciones
 * nuevas y elegir qué carpetas entran en la biblioteca.
 *
 * ESCANEAR AHORA vuelve a leer MediaStore (la biblioteca se
 * actualiza sola al terminar). Escaneo avanzado marca las carpetas
 * con tu música: con carpetas marcadas SOLO esas entran, así no
 * suben los audios que no son música (notas de voz, tonos) de
 * otras carpetas. Sin marcar nada se escanea todo, como siempre.
 */
/**
 * ESCANEAR (2026-11-11, como BitChord): cifras del último escaneo
 * para el diálogo Escanear. totalScanned = lo que devolvió
 * MediaStore (con el filtro de duración mínima, que es parte del
 * escaneo); accessible = lo que entró en la biblioteca;
 * excludedOrMissing = la diferencia: canciones que quedaron fuera
 * SOLO por los filtros de carpeta (excluidas o no marcadas).
 * foldersToScan = carpetas distintas que aportaron al menos una
 * canción accesible (ronda 178: contar las rutas marcadas inflaba
 * el número con carpetas vacías o padre+hija).
 */
data class ScanStats(
    val totalScanned: Int,
    val accessible: Int,
    val excludedOrMissing: Int,
    val foldersToScan: Int,
    val excludedFolders: Int,
)

@Composable
fun ScanDialog(
    stats: ScanStats?,
    folders: List<FolderEntry>,
    included: Set<String>,
    isScanning: Boolean,
    onRescan: () -> Unit,
    onSetIncluded: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(false) }
    // Selector de carpetas del sistema (como el paso de carpetas
    // del asistente): devuelve la ruta, que es lo que guarda el
    // filtro (MediaStore ya ve los archivos; no hace falta guardar
    // el permiso del árbol).
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val path = treeDocumentIdToPath(DocumentsContract.getTreeDocumentId(uri))
            if (path != null) onSetIncluded(included + path)
        }
    }
    // Carpetas a mostrar: las del último escaneo + las marcadas que
    // no salen en el escaneo (p. ej. solo tienen audios cortos).
    val shownFolders = remember(folders, included) {
        val fromScan = folders.associateBy { it.path }
        val extra = included.filter { it !in fromScan }
            .map { FolderEntry(it, it.substringAfterLast('/'), 0) }
        (folders + extra).sortedBy { it.name.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escanear") },
        text = {
            Column {
                Button(
                    onClick = onRescan,
                    enabled = !isScanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isScanning) "Escaneando…" else "ESCANEAR AHORA")
                }
                TextButton(onClick = { advancedOpen = !advancedOpen }) {
                    Text("Escaneo avanzado")
                }
                if (stats != null) {
                    Text("Canciones totales en la biblioteca: ${stats.totalScanned}")
                    Text("Total de canciones accesibles en la biblioteca: ${stats.accessible}")
                    // Ronda 178: la diferencia solo cuenta los
                    // filtros de carpeta (la duración mínima ya
                    // filtra dentro del escaneo); la etiqueta dice
                    // lo que mide, no "faltantes".
                    Text("Excluidas por carpeta: ${stats.excludedOrMissing}")
                    Text("Carpetas a escanear: ${stats.foldersToScan}")
                    Text("Carpetas excluidas: ${stats.excludedFolders}")
                } else {
                    Text("Aún no hay un escaneo: pulsa ESCANEAR AHORA.")
                }
                if (advancedOpen) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Marca solo las carpetas con tu música. Sin marcar nada, se escanea todo el teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(shownFolders, key = { it.path }) { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = folder.path in included,
                                    onCheckedChange = { checked ->
                                        onSetIncluded(
                                            if (checked) included + folder.path
                                            else included - folder.path,
                                        )
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(folder.name)
                                    Text(
                                        folder.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("${folder.songCount}")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { folderPicker.launch(null) }) {
                            Text("Añadir carpeta…")
                        }
                        if (included.isNotEmpty()) {
                            TextButton(onClick = { onSetIncluded(emptySet()) }) {
                                Text("Limpiar selección")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}
