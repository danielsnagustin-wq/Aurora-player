package com.aurora.player.ui.library

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aurora.player.model.Song
import com.aurora.player.tags.SongTags
import com.aurora.player.tags.TagIO
import com.aurora.player.tags.TagWriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aurora.player.online.TagCandidate
import com.aurora.player.online.searchMusicBrainz
import com.aurora.player.online.searchDeezer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import java.net.HttpURLConnection
import java.net.URL

/**
 * Editor manual de etiquetas (slice 6, paso 3): título, artista,
 * artista de álbum, álbum, compositor, género, año, pista, disco,
 * letrista, comentario y portada. Los campos vacíos no se cambian
 * (borrar un campo es trabajo posterior, no se finge).
 *
 * Guardado seguro: se trabaja sobre una copia temporal en cacheDir;
 * el original solo se toca al final, por File directo cuando se
 * puede o por MediaStore.createWriteRequest (el sistema pide permiso
 * una vez) cuando no. Si algo falla, el original queda intacto.
 */
@Composable
fun TagEditorDialog(
    song: Song,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    onlineMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempFile by remember { mutableStateOf<File?>(null) }
    var initial by remember { mutableStateOf<SongTags?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var coverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var coverFile by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var pendingTemp by remember { mutableStateOf<File?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("") }
    var disc by remember { mutableStateOf("") }
    var lyricist by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var renameFile by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<TagCandidate>>(emptyList()) }
    var showCandidates by remember { mutableStateOf(false) }

    // Cargar la copia temporal y las etiquetas actuales una sola vez.
    LaunchedEffect(song.id) {
        val prepared = withContext(Dispatchers.IO) {
            try {
                // jaudiotagger elige el lector por la EXTENSIÓN: el
                // temporal tiene que terminar en la extensión real
                // de la canción, si no TagIO.read/write fallan
                // (run 43359629777: TagWriterTest:49).
                val ext = song.path.substringAfterLast('.', "").lowercase()
                val tempName = if (ext.isNotEmpty()) "tagedit-${song.id}.$ext" else "tagedit-${song.id}.tmp"
                val temp = File(context.cacheDir, tempName)
                context.contentResolver.openInputStream(song.uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: return@withContext null
                temp to TagIO.read(temp)
            } catch (_: Exception) {
                null
            }
        }
        if (prepared == null || prepared.second == null) {
            loadError = "No se pudo leer el archivo de la canción."
        } else {
            tempFile = prepared.first
            initial = prepared.second
            coverBitmap = withContext(Dispatchers.IO) {
                try {
                    TagIO.readCover(prepared.first)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    // Rellenar los campos cuando llegan las etiquetas (una sola vez).
    LaunchedEffect(initial) {
        val tags = initial ?: return@LaunchedEffect
        title = tags.title.orEmpty()
        artist = tags.artist.orEmpty()
        albumArtist = tags.albumArtist.orEmpty()
        album = tags.album.orEmpty()
        composer = tags.composer.orEmpty()
        genre = tags.genre.orEmpty()
        year = tags.year?.toString().orEmpty()
        track = tags.track?.toString().orEmpty()
        disc = tags.disc?.toString().orEmpty()
        lyricist = tags.lyricist.orEmpty()
        comment = tags.comment.orEmpty()
    }

    // Al cerrar sin guardar, el temporal se borra (salvo que ya se
    // haya convertido en el archivo original con commit).
    fun dismissCleanly() {
        if (!committed) tempFile?.let { TagWriter.discard(it) }
        coverFile?.delete()
        onDismiss()
    }

    // Portada nueva: se copia a cacheDir y se muestra. Sin vista
    // previa no hay botón de guardar que la use (R-26).
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                try {
                    val out = File(context.cacheDir, "tagedit-cover-${song.id}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    out.takeIf { it.exists() && it.length() > 0 }
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(copied.absolutePath)
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                coverFile = copied
                coverBitmap = bitmap
            } else {
                copied.delete()
            }
        }
    }

    // El sistema pidió permiso para escribir: ya concedido, copiar
    // el temporal dentro del archivo por su Uri.
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            saveError = "Sin permiso no se puede escribir en el archivo."
            saving = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val temp = pendingTemp ?: return@launch
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(song.uri, "wt")?.use { out ->
                        temp.inputStream().use { it.copyTo(out) }
                    } != null
                } catch (_: Exception) {
                    false
                }
            }
            if (!ok) {
                saveError = "No se pudo escribir en el archivo."
                saving = false
                return@launch
            }
            MediaScannerConnection.scanFile(context, arrayOf(song.path), null, null)
            maybeRename()
            TagWriter.discard(temp)
            coverFile?.delete()
            onSaved()
        }
    }

    fun save() {
        val temp = tempFile ?: return
        saving = true
        saveError = null
        scope.launch {
            val edited = SongTags(
                title = title.takeIf { it.isNotBlank() },
                artist = artist.takeIf { it.isNotBlank() },
                albumArtist = albumArtist.takeIf { it.isNotBlank() },
                album = album.takeIf { it.isNotBlank() },
                composer = composer.takeIf { it.isNotBlank() },
                genre = genre.takeIf { it.isNotBlank() },
                year = year.toIntOrNull(),
                track = track.toIntOrNull(),
                disc = disc.toIntOrNull(),
                lyricist = lyricist.takeIf { it.isNotBlank() },
                comment = comment.takeIf { it.isNotBlank() },
            )
            val written = withContext(Dispatchers.IO) {
                if (!TagIO.write(temp, edited)) {
                    false
                } else if (coverFile != null) {
                    TagIO.writeCover(temp, coverFile!!)
                } else {
                    true
                }
            }
            if (!written) {
                saveError = "No se pudo escribir las etiquetas."
                saving = false
                return@launch
            }
            val target = File(song.path)
            // En Android 9 o menos no existe createWriteRequest y el
            // acceso directo al archivo sí funciona: se intenta igual.
            if (target.canWrite() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val done = withContext(Dispatchers.IO) { TagWriter.commit(temp, target) }
                if (!done) {
                    saveError = "No se pudo guardar en el archivo."
                    saving = false
                    return@launch
                }
                committed = true
                MediaScannerConnection.scanFile(context, arrayOf(song.path), null, null)
                maybeRename()
                coverFile?.delete()
                onSaved()
            } else {
                // Android 10+: el sistema pide permiso una vez por lote.
                try {
                    val request = MediaStore.createWriteRequest(
                        context.contentResolver,
                        listOf(song.uri),
                    )
                    pendingTemp = temp
                    writeLauncher.launch(
                        IntentSenderRequest.Builder(request.intentSender).build(),
                    )
                } catch (_: Exception) {
                    saveError = "No se pudo pedir permiso para escribir."
                    saving = false
                }
            }
        }
    }
    
    fun maybeRename() {
        if (!renameFile) return
        if (title.isBlank() || artist.isBlank()) return
        try {
            val target = File(song.path)
            val ext = song.path.substringAfterLast('.', "")
            val newName = buildRenamedFileName(artist, title, ext) ?: return
            if (newName == target.name) return
            val renamed = File(target.parentFile, newName)
            if (renamed.exists()) return
            if (target.renameTo(renamed)) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(song.path, renamed.absolutePath),
                    null,
                    null
                )
            }
        } catch (_: Exception) {
            // ignore – rename failure must not affect save success
        }
    }

    // ---- Online search ----------------------------------------------------
    fun searchOnline() {
        scope.launch {
            searching = true
            searchError = null
            try {
                val qTitle = title.takeIf { it.isNotBlank() } ?: song.title
                val qArtist = artist.takeIf { it.isNotBlank() } ?: song.artist
                val mb = searchMusicBrainz(qTitle, qArtist) ?: emptyList()
                val dz = searchDeezer(qTitle, qArtist) ?: emptyList()
                val all = mb + dz
                if (all.isEmpty()) {
                    searchError = "Sin resultados en internet."
                } else {
                    candidates = all
                    showCandidates = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: java.io.IOException) {
                searchError = "Sin conexión a internet."
            } catch (_: Exception) {
                searchError = "No se pudo buscar."
            } finally {
                searching = false
            }
        }
    }

    // ---- Apply candidate from online search ---------------------------------
    fun applyCandidate(c: TagCandidate) {
        c.title?.let { if (it.isNotBlank()) title = it }
        c.artist?.let { if (it.isNotBlank()) artist = it }
        c.album?.let { if (it.isNotBlank()) album = it }
        c.year?.let { year = it.toString() }
        if (c.coverUrl != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL(c.coverUrl)
                        connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        connection.connect()
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val bytes = connection.inputStream.readBytes()
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                val file = File(context.cacheDir, "tagedit-cover-${song.id}")
                                file.outputStream().use { it.write(bytes) }
                                coverFile = file
                                coverBitmap = bitmap
                            }
                        }
                    } catch (_: Exception) {
                        // ignore failures
                    } finally {
                        connection?.disconnect()
                    }
                }
            }
        }
        showCandidates = false
    }
    
    AlertDialog(
        onDismissRequest = { if (!saving) dismissCleanly() },
        title = { Text("Editar etiquetas") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    loadError != null -> {
                        Text(
                            loadError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    initial == null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                "Leyendo etiquetas…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val bitmap = coverBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Portada actual",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                                }
                            }
                            OutlinedButton(
                                onClick = { coverPicker.launch("image/*") },
                                modifier = Modifier.height(48.dp).padding(start = 12.dp),
                            ) {
                                Text(if (coverFile != null) "Portada nueva lista" else "Elegir portada")
                            }
                        }
                        if (onlineMode) {
                            OutlinedButton(
                                onClick = { searchOnline() },
                                enabled = !searching && initial != null,
                                modifier = Modifier.height(48.dp).padding(start = 12.dp),
                            ) {
                                Text(if (searching) "Buscando…" else "Buscar en internet")
                            }
                            if (searchError != null) {
                                Text(
                                    searchError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        TagField("Título", title) { title = it }
                        TagField("Artista", artist) { artist = it }
                        TagField("Artista de álbum", albumArtist) { albumArtist = it }
                        TagField("Álbum", album) { album = it }
                        TagField("Compositor", composer) { composer = it }
                        TagField("Género", genre) { genre = it }
                        TagField("Año", year, KeyboardType.Number) { year = it }
                        TagField("Pista", track, KeyboardType.Number) { track = it }
                        TagField("Disco", disc, KeyboardType.Number) { disc = it }
                        TagField("Letrista", lyricist) { lyricist = it }
                        TagField("Comentario", comment) { comment = it }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Checkbox(
                                checked = renameFile,
                                onCheckedChange = { renameFile = it },
                                enabled = title.isNotBlank() && artist.isNotBlank()
                            )
                            Text(
                                "Renombrar el archivo como «Artista - Título»",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(
                            "Los campos vacíos quedan como están.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (saveError != null) {
                            Text(
                                saveError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { save() },
                enabled = !saving && initial != null && loadError == null,
            ) {
                Text(if (saving) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) dismissCleanly() }) {
                Text("Cancelar")
            }
        },
    )
    if (showCandidates) {
        AlertDialog(
            onDismissRequest = { showCandidates = false },
            title = { Text("Elegir resultado") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(candidates, key = { index, c -> "$index|${c.source}|${c.mbid ?: c.title}" }) { _, c ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyCandidate(c) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                "${c.title} — ${c.artist}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                listOfNotNull(c.album, c.year?.toString(), c.source).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCandidates = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Campo de texto del editor (una línea; los números usan teclado numérico). */
@Composable
private fun TagField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

// Build a safe filename "Artist - Title.ext". Returns null if artist or title are blank.
internal fun buildRenamedFileName(artist: String, title: String, ext: String): String? {
    if (artist.isBlank() || title.isBlank()) return null

    fun sanitize(input: String): String {
        // Replace illegal filename characters and control chars with a space
        var s = input.replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), " ")
        // Collapse consecutive whitespace and trim
        s = s.replace(Regex("\\s+"), " ").trim()
        // Truncate each part to 200 characters
        if (s.length > 200) s = s.substring(0, 200)
        return s
    }

    val safeArtist = sanitize(artist)
    val safeTitle = sanitize(title)
    return "${safeArtist.take(200)} - ${safeTitle.take(200)}.$ext"
}
