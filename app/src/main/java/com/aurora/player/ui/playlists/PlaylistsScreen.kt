package com.aurora.player.ui.playlists

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.PlaylistEntity
import com.aurora.player.model.Song
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.M3u
import com.aurora.player.util.TimeFormat

/**
 * Aurora Player — Playlists (Etapa 1, cableada con Room).
 * Crear, listar, borrar, ver canciones, añadir/quitar, tocar para
 * reproducir y exportar como M3U. Todo guardado en el teléfono.
 */
@Composable
fun PlaylistsScreen(
    hasAudioPermission: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlaylistsViewModel = viewModel()
    val playlists by viewModel.playlists.collectAsState()
    val smartPlaylists by viewModel.smartPlaylists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val selectedSongs by viewModel.selectedSongs.collectAsState()
    var newName by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    // ETAPA 5: playlist inteligente seleccionada (solo lectura).
    var smartSelected by remember { mutableStateOf<SmartPlaylist?>(null) }
    // AUDITORÍA (2026-11-09): borrar una playlist era un toque sin
    // confirmación y la lista se perdía. Ahora pregunta antes.
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh(hasAudioPermission)
    }

    val selected = playlists.firstOrNull { it.id == selectedId }
    // RONDA 2 (2026-11-09): importar M3U. El parser existía pero
    // nadie lo llamaba. Se elige el archivo, se cruzan sus rutas con
    // la biblioteca y se crea la playlist con las que existen.
    val context = LocalContext.current
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            // Ronda 26: si no se puede abrir el archivo, antes se
            // salía en silencio y el usuario no sabía por qué no
            // se importó nada. Ahora se avisa.
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() }
            if (text == null) {
                importMessage = "No se pudo leer ese archivo M3U."
                return@rememberLauncherForActivityResult
            }
            val entries = M3u.parse(text)
            val byPath = allSongs.associateBy { it.path }
            val byFileName = allSongs.associateBy { java.io.File(it.path).name }
            val libraryPaths = allSongs.map { it.path }
            val matched = entries.mapNotNull { entry ->
                byPath[entry.path]
                    // Ronda 18: las rutas relativas no se pueden
                    // resolver contra la ubicación del M3U (el
                    // selector no da una ruta real), así que se
                    // compara el sufijo de la ruta.
                    ?: M3u.matchBySuffix(entry.path, libraryPaths)?.let { byPath[it] }
                    ?: byFileName[java.io.File(entry.path).name]
                    ?: allSongs.firstOrNull { it.title == entry.title }
                    // Ronda 18: el #EXTINF que exportamos es
                    // "artista - título", no el título solo: sin
                    // esta rama el rescate por título nunca casaba
                    // con nuestros propios M3U.
                    ?: allSongs.firstOrNull { "${it.artist} - ${it.title}" == entry.title }
            }.distinctBy { it.id }
            // RONDA 5 (2026-11-09): el nombre salía de
            // lastPathSegment, que en un Uri de contenido es un ID
            // de documento ("12345") o una ruta rara, no el nombre
            // del archivo. Ahora se lee DISPLAY_NAME de verdad.
            val displayName = try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
            val name = (displayName ?: uri.lastPathSegment?.substringAfterLast("/"))
                ?.removeSuffix(".m3u")
                ?.removeSuffix(".M3U")
                ?.ifBlank { "Importada" } ?: "Importada"
            viewModel.importM3u(name, matched.map { it.id })
            importMessage = if (matched.isEmpty()) {
                "El archivo M3U no tenía ninguna canción de tu biblioteca."
            } else {
                "Importadas ${matched.size} de ${entries.size} canciones de \"$name\"."
            }
        } catch (e: Exception) {
            ErrorLog.log(context, "Importar M3U", e)
            importMessage = "No se pudo leer ese archivo M3U."
        }
    }
    if (selected != null && picking) {
        SongPicker(
            allSongs = allSongs,
            currentIds = selectedSongs.map { it.id }.toSet(),
            onSave = { ids ->
                viewModel.setPlaylistSongs(selected.id, ids)
                picking = false
            },
            onCancel = { picking = false },
            modifier = modifier,
        )
        return
    }
    if (selected != null) {
        PlaylistDetail(
            name = selected.name,
            songs = selectedSongs,
            onBack = { viewModel.selectPlaylist(null) },
            onSongClick = onSongClick,
            onRemoveSong = { viewModel.removeSongFromPlaylist(selected.id, it.id) },
            onAddSongs = { picking = true },
            modifier = modifier,
        )
        return
    }
    val smart = smartSelected
    if (smart != null) {
        PlaylistDetail(
            name = smart.name,
            songs = smart.songs,
            onBack = { smartSelected = null },
            onSongClick = onSongClick,
            onRemoveSong = {},
            onAddSongs = {},
            readOnly = true,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Playlists")
        if (!hasAudioPermission) {
            Text(
                "Aurora Player necesita permiso para leer tus archivos de audio. Sin él no hay biblioteca que mostrar.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nombre de la playlist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    viewModel.createPlaylist(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
                colors = auroraButtonColors(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Crear")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            OutlinedButton(onClick = { importLauncher.launch("*/*") }) {
                Text("Importar M3U")
            }
            Text(
                "Trae una lista .m3u de otro reproductor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        importMessage?.let { message ->
            Text(message, modifier = Modifier.padding(top = 8.dp))
        }
        if (smartPlaylists.isNotEmpty()) {
            Text("Inteligentes", modifier = Modifier.padding(top = 16.dp))
            for (smart in smartPlaylists) {
                ListItem(
                    headlineContent = {
                        Text(smart.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            "${smart.description} · ${smart.songs.size} canciones",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { smartSelected = smart },
                )
                Divider()
            }
        }
        if (playlists.isEmpty()) {
            Text(
                "Crea tu primera playlist. Se guarda en el teléfono y se puede exportar como M3U.",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = {
                            Text(
                                playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                "Toca para ver sus canciones",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { playlistToDelete = playlist }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Borrar ${playlist.name}")
                            }
                        },
                        modifier = Modifier.clickable { viewModel.selectPlaylist(playlist.id) },
                    )
                    Divider()
                }
            }
        }
    }
    playlistToDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(doomed.id)
                    playlistToDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancelar") }
            },
            title = { Text("Borrar \"${doomed.name}\"") },
            text = { Text("La playlist se borra del teléfono. Las canciones no se borran, solo la lista.") },
        )
    }
}

@Composable
private fun PlaylistDetail(
    name: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onAddSongs: () -> Unit,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val entries = songs.map { song ->
                    M3u.Entry(
                        song.durationMs / 1000,
                        "${song.artist} - ${song.title}",
                        song.path,
                    )
                }
                out.write(M3u.build(entries).toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // Exportar falló: la playlist sigue intacta en Room.
            ErrorLog.log(context, "Exportar M3U", e)
        }
    }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(name, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (!readOnly) {
                Button(onClick = onAddSongs, colors = auroraButtonColors()) {
                    Text("Añadir canciones")
                }
            }
            OutlinedButton(
                // RONDA 5 (2026-11-09): un nombre de playlist con
                // "/" rompía el nombre de archivo sugerido al
                // exportar. Se sanea aquí, sin tocar el nombre real.
                onClick = { exportLauncher.launch("${name.replace("/", "_")}.m3u") },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Exportar M3U")
            }
        }
        if (songs.isEmpty()) {
            Text(
                "Esta playlist aún no tiene canciones.",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(songs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = {
                            Text(
                                song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                "${song.artist} · ${song.album}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    TimeFormat.formatDuration(song.durationMs),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!readOnly) {
                                    IconButton(onClick = { onRemoveSong(song) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Quitar ${song.title}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSongClick(song, songs) },
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun SongPicker(
    allSongs: List<Song>,
    currentIds: Set<Long>,
    onSave: (List<Long>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by remember(currentIds) { mutableStateOf(currentIds) }
    // AUDITORÍA (2026-11-09): buscar una canción entre 1471 sin
    // buscador era un suplicio. Ahora el picker filtra por texto.
    var pickerQuery by remember { mutableStateOf("") }
    val visibleSongs = if (pickerQuery.isBlank()) allSongs else allSongs.filter {
        it.title.contains(pickerQuery, ignoreCase = true) ||
            it.artist.contains(pickerQuery, ignoreCase = true) ||
            it.album.contains(pickerQuery, ignoreCase = true)
    }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Añadir canciones")
        OutlinedTextField(
            value = pickerQuery,
            onValueChange = { pickerQuery = it },
            label = { Text("Buscar título, artista o álbum") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Button(
                onClick = {
                    val ordered = allSongs.filter { it.id in picked }.map { it.id }
                    onSave(ordered)
                },
                colors = auroraButtonColors(),
            ) {
                Text("Guardar (${picked.size})")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Cancelar")
            }
        }
        if (allSongs.isEmpty()) {
            Text(
                "No hay canciones en la biblioteca todavía.",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else if (visibleSongs.isEmpty()) {
            Text(
                "Nada coincide con la búsqueda.",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(visibleSongs, key = { it.id }) { song ->
                    val checked = song.id in picked
                    ListItem(
                        headlineContent = {
                            Text(
                                song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                "${song.artist} · ${song.album}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    picked = if (it) picked + song.id else picked - song.id
                                },
                            )
                        },
                        modifier = Modifier.clickable {
                            picked = if (checked) picked - song.id else picked + song.id
                        },
                    )
                    Divider()
                }
            }
        }
    }
}
