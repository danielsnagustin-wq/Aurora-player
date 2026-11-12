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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aurora.player.util.StatsCsvRow
import com.aurora.player.util.exportStatsCsv
import com.aurora.player.util.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

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
    // RONDA 165: si la carga falla, la pantalla se quedaba en
    // "Cargando…" para siempre (data null = cargando, sin error ni
    // reintento). Este estado distingue el fallo de la carga.
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private var loaded = false
    private var loadedPermission: Boolean? = null
    private var lastSongsById: Map<Long, Song>? = null

    init {
        // RONDA 123: las cifras se quedaban viejas si una escucha se
        // registraba con Estadísticas abierta (load() solo corría al
        // entrar). El flujo en vivo del historial re-carga solo las
        // consultas de Room; la biblioteca (MediaStore) no se
        // re-escanea: lastSongsById guarda el mapa de la primera carga.
        viewModelScope.launch {
            db.libraryDao().statsPerSongFlow().collect {
                val byId = lastSongsById
                if (loaded && byId != null) {
                    _stats.value = try {
                        withContext(Dispatchers.IO) { loadDaoStats(byId) }
                    } catch (e: Exception) {
                        ErrorLog.log(getApplication(), "Cargar estadísticas", e)
                        _loadFailed.value = true
                        null
                    }
                }
            }
        }
    }

    /** Solo las consultas de Room (sin re-escanear MediaStore). */
    private suspend fun loadDaoStats(byId: Map<Long, Song>): StatsData {
        val dao = db.libraryDao()
        val top = dao.mostPlayed().take(10).mapNotNull { row ->
            byId[row.songId]?.let { it to row.c }
        }
        // Gráfico semanal: reproducciones por día CALENDARIO,
        // últimos 7 días. Los intervalos fijos de 24 h desde
        // "ahora" metían en "hoy" escuchas de ayer cerca de
        // la medianoche (ronda 13); las medianoche se
        // calculan con Calendar, que respeta el horario
        // de verano.
        val now = System.currentTimeMillis()
        val dayStarts = (0..6).map { ago ->
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -ago)
            }.timeInMillis
        }
        val perDay = MutableList(7) { 0 }
        for (ts in dao.historyPlayedAt()) {
            if (ts > now) continue
            val daysAgo = dayStarts.indexOfFirst { ts >= it }
            if (daysAgo in 0..6) perDay[6 - daysAgo]++
        }
        return StatsData(
            totalPlays = dao.totalPlays(),
            totalListenedMs = dao.totalListenedMs(),
            mostPlayed = top,
            favoriteCount = dao.favoriteIds().size,
            totalSongs = byId.size,
            playsLast7Days = perDay,
        )
    }

    fun load(hasAudioPermission: Boolean) {
        // Refinamiento: si la primera carga fue sin permiso y el usuario
        // lo concede después, hay que volver a cargar (antes quedaba vacío).
        if (loaded && loadedPermission == hasAudioPermission) return
        loaded = true
        loadedPermission = hasAudioPermission
        _loadFailed.value = false
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
                    lastSongsById = byId
                    loadDaoStats(byId)
                }
            } catch (e: Exception) {
                // Un fallo no deja las estadísticas colgadas: se
                // registra y la pantalla muestra su estado vacío.
                ErrorLog.log(getApplication(), "Cargar estadísticas", e)
                _loadFailed.value = true
                null
            }
            _stats.value = data
        }
    }
    /** Vuelve a cargar al entrar a la pantalla: las cifras se quedan
     * viejas si solo se cargan una vez por arranque. */
    /** Fase 1b: exportar estadísticas a CSV (Descargas/AuroraPlayer). */
    suspend fun exportCsv(hasAudioPermission: Boolean): Result<String> {
        val minDurationMs = playerStore.settingsFlow.first().minDurationMs
        val songs = if (hasAudioPermission) repository.loadSongs(minDurationMs) else emptyList()
        val byId = songs.associateBy { it.id }
        val rows = db.libraryDao().statsPerSong().map { r ->
            val song = byId[r.songId]
            StatsCsvRow(
                title = song?.title ?: "Canción eliminada",
                artist = song?.artist ?: "",
                album = song?.album ?: "",
                plays = r.plays,
                listenedMinutes = r.listenedMs / 60_000,
                lastPlayedAtMs = r.lastPlayed,
            )
        }
        return exportStatsCsv(getApplication(), rows)
    }

    fun refresh(hasAudioPermission: Boolean) {
        loaded = false
        load(hasAudioPermission)
    }
}

@Composable
fun StatsScreen(hasAudioPermission: Boolean, modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = viewModel()
    val stats by viewModel.stats.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    // RONDA 79: la clave era Unit, así que si el permiso de música
    // se concedía en Ajustes del sistema con esta pantalla abierta
    // (MainActivity.onResume ya lo vuelve a comprobar), la pantalla
    // seguía vacía hasta salir y volver a entrar. Con el permiso
    // como clave, al cambiar se vuelve a cargar.
    LaunchedEffect(hasAudioPermission) { viewModel.refresh(hasAudioPermission) }
    // RONDA 7 P3: Tu año.
    var showYear by remember { mutableStateOf(false) }
    // Fase 1b: exportar estadísticas a CSV.
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val data = stats
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Estadísticas", style = MaterialTheme.typography.titleLarge)
        if (showYear) {
            YearScreen(hasAudioPermission = hasAudioPermission, onClose = { showYear = false })
            return@Column
        }
        OutlinedButton(
            onClick = { showYear = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Tu año")
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val result = viewModel.exportCsv(hasAudioPermission)
                    exportMessage = result.fold(
                        onSuccess = { "Guardado en $it" },
                        onFailure = { "No se pudo exportar: ${it.message}" },
                    )
                }
            },
            enabled = data != null && data.totalPlays > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Exportar estadísticas (CSV)")
        }
        exportMessage?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data == null) {
            // RONDA 165: un fallo de carga no es "Cargando…" para
            // siempre: se muestra el error y se puede reintentar.
            if (loadFailed) {
                Text(
                    "No se pudieron cargar las estadísticas.",
                    modifier = Modifier.padding(top = 16.dp),
                )
                OutlinedButton(
                    onClick = { viewModel.refresh(hasAudioPermission) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Reintentar")
                }
            } else {
                Text("Cargando…", modifier = Modifier.padding(top = 16.dp))
            }
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
    // Ronda 22: las barras cuentan por medianoches de calendario
    // (ronda 13); las etiquetas restaban bloques fijos de 24 h y
    // alrededor del cambio de horario caían en el día equivocado.
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(6 - indexFromOldest))
    }
    return SimpleDateFormat("EE", Locale.getDefault()).format(cal.time).take(2).uppercase()
}
