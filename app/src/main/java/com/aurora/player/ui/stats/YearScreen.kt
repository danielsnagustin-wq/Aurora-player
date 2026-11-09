package com.aurora.player.ui.stats

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.PlaylistEntity
import com.aurora.player.data.PlaylistSongEntity
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RONDA 7 P3: Tu año. El resumen del año con el historial real: nada
 * inventado, nada en la nube. "Crear playlist" guarda de verdad una
 * playlist "Tu año 2026" con las 10 más escuchadas (si ya existe, se
 * actualizan sus canciones, no se duplica).
 */
class YearViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AuroraDatabase.getInstance(application)
    private val repository = MusicRepository(application)
    private val playerStore = PlayerSettingsStore(application)

    private val _years = MutableStateFlow<List<Int>>(emptyList())
    val years: StateFlow<List<Int>> = _years
    private val _stats = MutableStateFlow<YearStats?>(null)
    val stats: StateFlow<YearStats?> = _stats
    private val _playlistCreated = MutableStateFlow<String?>(null)
    val playlistCreated: StateFlow<String?> = _playlistCreated

    fun loadYears(hasAudioPermission: Boolean) {
        viewModelScope.launch {
            val found = try {
                withContext(Dispatchers.IO) {
                    val cal = Calendar.getInstance()
                    db.libraryDao().historyPlayedAt().mapTo(mutableSetOf()) { ts ->
                        cal.timeInMillis = ts
                        cal.get(Calendar.YEAR)
                    }
                }
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Cargar años", e)
                emptySet()
            }
            val current = Calendar.getInstance().get(Calendar.YEAR)
            _years.value = (found + current).sortedDescending()
        }
    }

    fun loadYear(year: Int, hasAudioPermission: Boolean) {
        _stats.value = null
        _playlistCreated.value = null
        viewModelScope.launch {
            val data = try {
                withContext(Dispatchers.IO) {
                    val minDurationMs = playerStore.settingsFlow.first().minDurationMs
                    val songs = if (hasAudioPermission) repository.loadSongs(minDurationMs) else emptyList()
                    val byId = songs.associateBy { it.id }
                    val (start, end) = yearRange(year)
                    val history = db.libraryDao().historyInYear(start, end)
                    computeYearStats(year, history, byId)
                }
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Cargar Tu año", e)
                null
            }
            _stats.value = data
        }
    }

    fun createYearPlaylist(stats: YearStats) {
        if (stats.topSongIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val name = "Tu año ${stats.year}"
            val dao = db.playlistDao()
            val existing = dao.playlistByName(name)
            if (existing != null) {
                dao.replaceSongs(
                    existing.id,
                    stats.topSongIds.mapIndexed { index, songId ->
                        PlaylistSongEntity(playlistId = existing.id, songId = songId, position = index)
                    },
                )
            } else {
                val id = dao.insertPlaylist(PlaylistEntity(name = name))
                dao.replaceSongs(
                    id,
                    stats.topSongIds.mapIndexed { index, songId ->
                        PlaylistSongEntity(playlistId = id, songId = songId, position = index)
                    },
                )
            }
            _playlistCreated.value = "$name · ${stats.topSongIds.size} canciones"
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearScreen(hasAudioPermission: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: YearViewModel = viewModel()
    val years by viewModel.years.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val created by viewModel.playlistCreated.collectAsState()
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    LaunchedEffect(Unit) { viewModel.loadYears(hasAudioPermission) }
    LaunchedEffect(selectedYear) { viewModel.loadYear(selectedYear, hasAudioPermission) }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tu año $selectedYear", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }
        }
        if (years.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                for (year in years) {
                    YearChip(name = "$year", selected = year == selectedYear, onClick = { selectedYear = year })
                }
            }
        }
        val data = stats
        if (data == null) {
            Text("Cargando…", modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        if (data.totalPlays == 0) {
            Text(
                "Aún no escuchaste nada en ${data.year}. Escucha música 30 segundos o más y aquí verás tu resumen del año.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }
        Text(
            "Tiempo escuchado: ${TimeFormat.formatTotalListened(data.totalListenedMs)}",
            modifier = Modifier.padding(top = 16.dp),
        )
        Text("${data.totalPlays} reproducciones en ${data.distinctDays} días distintos")
        Text(
            "Tus más escuchadas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        for ((index, pair) in data.topSongs.withIndex()) {
            val (song, count) = pair
            ListItem(
                headlineContent = { Text("${index + 1}. ${song.title}") },
                supportingContent = { Text(song.artist) },
                trailingContent = { Text("$count veces") },
            )
            Divider()
        }
        if (data.topArtists.isNotEmpty()) {
            Text(
                "Tus artistas",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            for ((name, count) in data.topArtists) {
                ListItem(
                    headlineContent = { Text(name) },
                    trailingContent = { Text("$count veces") },
                )
                Divider()
            }
        }
        if (data.topGenres.isNotEmpty()) {
            Text(
                "Tu género",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            for ((genre, count) in data.topGenres) {
                ListItem(
                    headlineContent = { Text(genre) },
                    trailingContent = { Text("$count veces") },
                )
                Divider()
            }
        }
        data.firstPlayedAt?.let {
            Text("Primera escucha: ${formatDate(it)}", modifier = Modifier.padding(top = 16.dp))
        }
        data.lastPlayedAt?.let {
            Text("Última escucha: ${formatDate(it)}")
        }
        Button(
            onClick = { viewModel.createYearPlaylist(data) },
            colors = auroraButtonColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Crear playlist \"Tu año ${data.year}\"")
        }
        created?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun YearChip(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, colors = auroraButtonColors()) { Text(name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(name) }
    }
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.getDefault()).format(Date(ts))
