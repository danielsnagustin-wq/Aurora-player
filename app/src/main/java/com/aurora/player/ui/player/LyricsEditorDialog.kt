package com.aurora.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.aurora.player.util.LrcLine
import com.aurora.player.util.formatLrcTime
import java.util.UUID

/**
 * Fase 1b — editor de letras (.lrc).
 * Edita el texto de cada línea, borra líneas y añade nuevas (con el
 * tiempo de la posición actual). Los tiempos no se editan: una línea
 * editada pierde sus tiempos de palabra (el texto nuevo no los tiene)
 * y las líneas sin tocar conservan su karaoke. Al guardar, las líneas
 * vacías se descartan y el resto se ordena por tiempo.
 */
@Composable
fun LyricsEditorDialog(
    lyrics: List<LrcLine>,
    positionMs: Long,
    onDismiss: () -> Unit,
    onSave: (List<LrcLine>) -> Unit,
) {
    // RONDA 2 (2026-11-13, P1): cada línea lleva un id estable y la
    // lista es remember(lyrics). Antes la clave del LazyColumn era el
    // ÍNDICE y borrar/añadir una línea desplazaba el foco y el cursor
    // a la línea equivocada (el texto visible viene de la lista, pero
    // el foco/IME van pegados a la clave). Tampoco valen timeMs (dos
    // líneas pueden compartirlo) ni el texto (cambia a cada tecla).
    // Y si la canción cambia con el diálogo abierto, el editor se
    // reinicia con la letra nueva: antes guardaba la letra VIEJA
    // sobre la canción NUEVA (onSaveLyrics construye el Song desde el
    // MediaItem actual).
    var lines by remember(lyrics) {
        mutableStateOf(lyrics.map { UUID.randomUUID().toString() to it.copy() })
    }
    // Ronda 19: salir sin guardar (tocar fuera, atrás o Cancelar)
    // tiraba la edición en silencio. Si hay cambios, se pregunta.
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val isDirty = lines.map { it.second } != lyrics
    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("¿Descartar los cambios?") },
            text = { Text("Editó la letra y no la guardó. Si sale ahora, los cambios se pierden.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Seguir editando") }
            },
        )
    }
    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text("Editar letras") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    itemsIndexed(lines, key = { _, (id, _) -> id }) { index, (_, line) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                formatLrcTime(line.timeMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            OutlinedTextField(
                                value = line.text,
                                onValueChange = { newText ->
                                    lines = lines.toMutableList().also {
                                        it[index] = it[index].first to
                                            line.copy(text = newText, words = emptyList())
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    lines = lines.filterIndexed { i, _ -> i != index }
                                },
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Borrar línea")
                            }
                        }
                    }
                }
                TextButton(
                    // RONDA 91: la línea nueva se mete en su sitio
                    // por tiempo, no al final: si no, el editor la
                    // mostraba abajo aunque fuera de mitad de canción.
                    onClick = {
                        lines = (lines + (UUID.randomUUID().toString() to LrcLine(positionMs, "")))
                            .sortedBy { it.second.timeMs }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Añadir línea en ${formatLrcTime(positionMs)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(lines.map { it.second }.filter { it.text.isNotBlank() }) },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = { requestDismiss() }) { Text("Cancelar") }
        },
    )
}
