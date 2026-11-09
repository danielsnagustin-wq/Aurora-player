package com.aurora.player.ui.stats

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.model.Song
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    /** Reproducciones por día, últimos 7 días, del más viejo al de hoy. */
    val playsLast7Days: List<Int> = List(7) { 0 },
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AuroraDatabase.getInstance(application)
    private val repository = MusicRepository(application)
    private val playerStore = PlayerSettingsStore(application)

    private val _stats = MutableStateFlow<StatsData?>(null)
    val stats: StateFlow<StatsData?> = _stats

    private var loaded = false
    private var loadedPermission: Boolean? = null

    fun load(hasAudioPermission: Boolean) {
        // Refinamiento: si la primera carga fue sin permiso y el usuario
        // lo concede después, hay que volver a cargar (antes quedaba vacío).
        if (loaded && loadedPermission == hasAudioPermission) return
        loaded = true
        loadedPermission = hasAudioPermission
        viewModelScope.launch {
            val data = try {
                withContext(Dispatchers.IO) {
                    // RONDA 3 (2026-11-09): cargaba con la duración
                    // mínima por defecto (5 s) aunque el usuario
                    // hubiera elegido otra: "Total de canciones" no
                    // coincidía con la biblioteca.
                    val minDurationMs = playerStore.settingsFlow.first().minDurationMs
                    val songs = if (hasAudioPermission) repository.loadSongs(minDurationMs) else emptyList()
                    val byId = songs.associateBy { it.id }
                    val dao = db.libraryDao()
                    val top = dao.mostPlayed().take(10).mapNotNull { row ->
                        byId[row.songId]?.let { it to row.c }
                    }
                    // Gráfico semanal: reproducciones por día, últimos 7 días.
                    val now = System.currentTimeMillis()
                    val dayMs = 24L * 60 * 60 * 1000
                    val perDay = MutableList(7) { 0 }
                    for (ts in dao.historyPlayedAt()) {
                        val daysAgo = ((now - ts) / dayMs).toInt()
                        if (daysAgo in 0..6) perDay[6 - daysAgo]++
                    }
                    StatsData(
                        totalPlays = dao.totalPlays(),
                        totalListenedMs = dao.totalListenedMs(),
                        mostPlayed = top,
                        favoriteCount = dao.favoriteIds().size,
                        totalSongs = songs.size,
                        playsLast7Days = perDay,
                    )
                }
            } catch (e: Exception) {
                // Un fallo no deja las estadísticas colgadas: se
                // registra y la pantalla muestra su estado vacío.
                ErrorLog.log(getApplication(), "Cargar estadísticas", e)
                null
            }
            _stats.value = data
        }
    }
    /** Vuelve a cargar al entrar a la pantalla: las cifras se quedan
     * viejas si solo se cargan una vez por arranque. */
    fun refresh(hasAudioPermission: Boolean) {
        loaded = false
        load(hasAudioPermission)
    }
}

@Composable
fun StatsScreen(hasAudioPermission: Boolean, modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = viewModel()
    val stats by viewModel.stats.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh(hasAudioPermission) }
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
        // Gráfico semanal (idea open source, Namida): reproducciones
        // por día, últimos 7 días, con datos reales del historial.
        if (data.playsLast7Days.any { it > 0 }) {
            Text(
                "Últimos 7 días",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            val max = (data.playsLast7Days.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                data.playsLast7Days.forEachIndexed { index, count ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text("$count", style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier.fillMaxWidth(0.6f)
                                .height((count.toFloat() / max * 64).dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            dayLabel(index),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
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

/** Inicial del día (LU/MA/MI/JU/VI/SA/DO) para la columna del gráfico semanal. */
private fun dayLabel(indexFromOldest: Int): String {
    val dayMs = 24L * 60 * 60 * 1000
    val date = Date(System.currentTimeMillis() - (6 - indexFromOldest) * dayMs)
    return SimpleDateFormat("EE", Locale.getDefault()).format(date).take(2).uppercase()
}
