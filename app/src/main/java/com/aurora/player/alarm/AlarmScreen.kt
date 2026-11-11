package com.aurora.player.alarm

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.AlarmStore
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.PlaylistEntity
import com.aurora.player.model.Song
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.util.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RONDA 7 P4: alarma musical. Elige hora, qué suena y si se repite
 * cada día. Suena aunque la app esté cerrada y sobrevive a un
 * reinicio. Suena por el canal de alarma: se oye aunque el teléfono
 * esté en silencio. En reposo profundo algunos fabricantes pueden
 * retrasarla unos minutos: se dice, no se finge.
 */
class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AlarmStore(application)
    private val db = AuroraDatabase.getInstance(application)
    private val repository = MusicRepository(application)
    private val playerStore = PlayerSettingsStore(application)

    private val _settings = MutableStateFlow<AlarmSettings?>(null)
    val settings: StateFlow<AlarmSettings?> = _settings
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists
    private val _canSchedule = MutableStateFlow(true)
    val canSchedule: StateFlow<Boolean> = _canSchedule
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun load() {
        viewModelScope.launch {
            val data = try {
                withContext(Dispatchers.IO) {
                    val minDurationMs = playerStore.settingsFlow.first().minDurationMs
                    Triple(
                        store.settingsFlow.first(),
                        repository.loadSongs(minDurationMs),
                        db.playlistDao().allPlaylists(),
                    )
                }
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Cargar alarma", e)
                null
            }
            data?.let { (s, songs, playlists) ->
                _settings.value = s
                _songs.value = songs
                _playlists.value = playlists
            }
            _canSchedule.value = AlarmScheduler.canScheduleExactAlarms(getApplication())
            // Ronda 19: si la alarma quedó guardada como activa sin
            // programar (sin permiso al guardar), reprogramarla al
            // entrar con el permiso ya concedido. schedule() es
            // idempotente (mismo PendingIntent, FLAG_UPDATE_CURRENT).
            val loaded = _settings.value
            if (loaded != null && loaded.enabled && _canSchedule.value) {
                AlarmScheduler.schedule(getApplication(), loaded)
            }
        }
    }

    fun refreshPermission() {
        _canSchedule.value = AlarmScheduler.canScheduleExactAlarms(getApplication())
        // Ronda 19: lo mismo al pulsar "Actualizar" tras conceder
        // el permiso en los ajustes del sistema.
        if (_canSchedule.value) {
            viewModelScope.launch(Dispatchers.IO) {
                val saved = store.settingsFlow.first()
                if (saved.enabled) AlarmScheduler.schedule(getApplication(), saved)
            }
        }
    }

    fun save(settings: AlarmSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            store.save(settings)
            if (!settings.enabled) {
                AlarmScheduler.cancel(getApplication())
                _message.value = "Alarma apagada."
                return@launch
            }
            if (!AlarmScheduler.canScheduleExactAlarms(getApplication())) {
                _message.value = "Falta el permiso de alarmas exactas: concédelo y vuelve a guardar."
                return@launch
            }
            AlarmScheduler.schedule(getApplication(), settings)
            _message.value = describeNextAlarm(settings.hour, settings.minute, System.currentTimeMillis())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: AlarmViewModel = viewModel()
    val saved by viewModel.settings.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val canSchedule by viewModel.canSchedule.collectAsState()
    val message by viewModel.message.collectAsState()

    var initialized by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(30) }
    var daily by remember { mutableStateOf(true) }
    var source by remember { mutableStateOf<AlarmSource>(AlarmSource.ShuffleAll) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSongPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(saved) {
        val s = saved ?: return@LaunchedEffect
        if (!initialized) {
            enabled = s.enabled
            hour = s.hour
            minute = s.minute
            daily = s.daily
            source = s.source
            initialized = true
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alarma musical")
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alarma activada")
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Text("Hora", modifier = Modifier.padding(top = 16.dp))
        OutlinedButton(onClick = { showTimePicker = true }) {
            Text(formatAlarmTime(hour, minute))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Repetir cada día")
            Switch(checked = daily, onCheckedChange = { daily = it })
        }

        Text("Qué suena", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlarmChip(
                name = "Mezclar todo",
                selected = source is AlarmSource.ShuffleAll,
                onClick = { source = AlarmSource.ShuffleAll },
            )
            AlarmChip(
                name = "Una canción",
                selected = source is AlarmSource.Song,
                onClick = { showSongPicker = true },
            )
            AlarmChip(
                name = "Una playlist",
                selected = source is AlarmSource.Playlist,
                onClick = {
                    val first = playlists.firstOrNull()
                    if (first != null) source = AlarmSource.Playlist(first.id, first.name)
                },
            )
        }
        when (val s = source) {
            is AlarmSource.Song -> {
                Text("Canción: ${s.title}", modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(
                    onClick = { showSongPicker = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Elegir otra canción")
                }
            }
            is AlarmSource.Playlist -> {
                if (playlists.isEmpty()) {
                    Text("No tienes playlists todavía.", modifier = Modifier.padding(top = 8.dp))
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        for (p in playlists) {
                            AlarmChip(
                                name = p.name,
                                selected = s.playlistId == p.id,
                                onClick = { source = AlarmSource.Playlist(p.id, p.name) },
                            )
                        }
                    }
                }
            }
            AlarmSource.ShuffleAll -> {
                Text("Se mezclan todas tus canciones.", modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (!canSchedule) {
            Text(
                "Falta el permiso de alarmas exactas: sin él la alarma no puede sonar a la hora.",
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = { openExactAlarmSettings(context) }) {
                    Text("Permitir alarmas exactas")
                }
                TextButton(onClick = { viewModel.refreshPermission() }) {
                    Text("Actualizar")
                }
            }
        }

        if (enabled && canSchedule) {
            Text(
                describeNextAlarm(hour, minute, System.currentTimeMillis()),
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Button(
            onClick = {
                viewModel.save(AlarmSettings(enabled, hour, minute, daily, source))
            },
            colors = auroraButtonColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Guardar alarma")
        }
        message?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "En reposo profundo algunos fabricantes pueden retrasar la alarma unos minutos. Suena por el canal de alarma: se oye aunque el teléfono esté en silencio.",
            modifier = Modifier.padding(top = 16.dp),
        )
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            text = { TimePicker(state = state) },
        )
    }

    if (showSongPicker) {
        SongPickerDialog(
            songs = songs,
            onPick = {
                source = AlarmSource.Song(it.id, it.title)
                showSongPicker = false
            },
            onClose = { showSongPicker = false },
        )
    }
}

@Composable
private fun AlarmChip(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, colors = auroraButtonColors()) { Text(name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(name) }
    }
}

@Composable
private fun SongPickerDialog(songs: List<Song>, onPick: (Song) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, songs) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) songs
        else songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancelar") }
        },
        title = { Text("Elegir canción") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.height(360.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        ListItem(
                            headlineContent = {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.clickable { onPick(song) },
                        )
                    }
                }
            }
        },
    )
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
