package com.aurora.player.ui.stats

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Song
import com.aurora.player.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aurora Player — estadísticas locales (ETAPA 5).
 * Todo sale del historial real (play_history, 30 s por escucha) y de
 * la biblioteca: nada inventado, nada en la nube. Sin historial, la
 * pantalla lo dice y no muestra cifras.
 */
data class StatsData(
    val totalPlays: Int,
    val totalListenedMs: Long,
    val mostPlayed: List<Pair<Song, Int>>,
    val favoriteCount: Int,
    val totalSongs: Int,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AuroraDatabase::class.java,
        "aurora-player.db",
    ).build()
    private val repository = MusicRepository(application)

    private val _stats = MutableStateFlow<StatsData?>(null)
    val stats: StateFlow<StatsData?> = _stats

    private var loaded = false

    fun load(hasAudioPermission: Boolean) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                val songs = if (hasAudioPermission) repository.loadSongs() else emptyList()
                val byId = songs.associateBy { it.id }
                val dao = db.libraryDao()
                val top = dao.mostPlayed().take(10).mapNotNull { row ->
                    byId[row.songId]?.let { it to row.c }
                }
                StatsData(
                    totalPlays = dao.totalPlays(),
                    totalListenedMs = dao.totalListenedMs(),
                    mostPlayed = top,
                    favoriteCount = dao.favoriteIds().size,
                    totalSongs = songs.size,
                )
            }
            _stats.value = data
        }
    }
}

@Composable
fun StatsScreen(hasAudioPermission: Boolean, modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = viewModel()
    val stats by viewModel.stats.collectAsState()
    LaunchedEffect(hasAudioPermission) { viewModel.load(hasAudioPermission) }
    val data = stats
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Estadísticas", style = MaterialTheme.typography.titleLarge)
        if (data == null) {
            Text("Cargando…", modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        if (data.totalPlays == 0) {
            Text(
                "Aún no hay historial. Escucha música 30 segundos o más y aquí verás tus cifras reales.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }
        Text("${data.totalPlays} reproducciones", modifier = Modifier.padding(top = 16.dp))
        Text("Tiempo escuchado: ${TimeFormat.formatTotalListened(data.totalListenedMs)}")
        Text("${data.favoriteCount} favoritas · ${data.totalSongs} canciones en la biblioteca")
        Text(
            "Tus más escuchadas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(data.mostPlayed) { index, (song, count) ->
                ListItem(
                    headlineContent = {
                        Text(
                            "${index + 1}. ${song.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Text(
                            "$count veces",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                Divider()
            }
        }
    }
}
