@file:OptIn(ExperimentalFoundationApi::class)
package com.aurora.player.ui.player

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import com.aurora.player.data.PlaylistEntity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.aurora.player.audio.AtmosSupport
import com.aurora.player.audio.WaveformCache
import com.aurora.player.audio.detectAtmosSupport
import com.aurora.player.audio.measureWaveform
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.media3.common.Player
import com.aurora.player.data.NamedQueueWithCount
import com.aurora.player.model.GestureAction
import com.aurora.player.model.KaraokeMode
import com.aurora.player.model.Song
import com.aurora.player.model.karaokeWordsActive
import com.aurora.player.model.MAX_NAMED_QUEUES
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.normalizeQueueName
import com.aurora.player.model.performGestureAction
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.TagEditorDialog
import com.aurora.player.ui.library.rememberArtworkBitmap
import com.aurora.player.ui.theme.LocalAnimationsEnabled
import com.aurora.player.ui.theme.LocalCoverShape
import com.aurora.player.ui.theme.LocalDynamicColor
import com.aurora.player.ui.theme.LocalPlayerBackground
import com.aurora.player.ui.theme.contrastOn
import com.aurora.player.playback.AudioEffects
import com.aurora.player.ui.theme.coverShapeToCornerPercent
import com.aurora.player.ui.theme.dominantColor
import com.aurora.player.ui.theme.relativeLuminance
import com.aurora.player.util.LrcLine
import com.aurora.player.util.LrcParser
import com.aurora.player.util.SongDetails
import com.aurora.player.util.codecDisplayName
import com.aurora.player.util.shareVerse
import com.aurora.player.online.searchItunes
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.rememberRemoteBitmap
import java.io.IOException
import com.aurora.player.model.StreamQuality
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.RadioButton

/**
 * Aurora Player — reproductor a pantalla completa, cableado.
 * Todo el estado viene del MediaController (MainActivity): posición
 * real, play/pausa, anterior/siguiente, aleatorio y repetición.
 * Letras sincronizadas elegantes + visualizador sutil: Fase 1b
 * (apagados por defecto, no se fingen aquí).
 */

/** Temporizador de apagado (ETAPA 5): OFF por defecto, nunca se activa solo. */
enum class SleepTimerOption(val label: String, val minutes: Int) {
    OFF("Apagado", 0),
    MIN_15("15 minutos", 15),
    MIN_30("30 minutos", 30),
    MIN_45("45 minutos", 45),
    MIN_60("60 minutos", 60),
    END_OF_SONG("Al terminar la canción", -1),
}

/**
 * Gesto de cambio de pista: deslizamiento horizontal sobre la portada.
 * Devuelve 1 = siguiente (deslizar a la izquierda), -1 = anterior
 * (deslizar a la derecha), 0 = no hay gesto suficiente.
 */
fun swipeDirection(totalPx: Float, threshold: Float = 100f): Int = when {
    totalPx < -threshold -> 1
    totalPx > threshold -> -1
    else -> 0
}

/**
 * AUDITORÍA (2026-11-09): la velocidad se mostraba con "%.2g", que
 * recorta a 2 cifras significativas: 1.25x se leía "1.2x". Con 2
 * decimales y ceros recortados, cada velocidad se lee como es.
 */
fun formatSpeed(speed: Float): String =
    String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')

@Composable
fun PlayerScreen(
    title: String?,
    artist: String?,
    album: String?,
    artworkUri: Uri?,
    canvasUrl: String? = null,
    songUri: Uri? = null,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEq: () -> Unit,
    speed: Float = 1f,
    onCycleSpeed: () -> Unit = {},
    // RONDA 6: tono independiente de la velocidad (1x = sin cambio).
    pitch: Float = 1f,
    onCyclePitch: () -> Unit = {},
    queueTitles: List<String> = emptyList(),
    queueIndex: Int = 0,
    onQueueClick: (Int) -> Unit = {},
    // RONDA 2 (2026-11-09): la cola solo se podía tocar para saltar.
    // Phonograph/Retro permiten reordenarla y quitar pistas: ahora
    // cada fila sube, baja o se quita.
    onQueueRemove: (Int) -> Unit = {},
    onQueueMove: (Int, Int) -> Unit = { _, _ -> },
    // SPEC 005 FR-007: colas guardadas con nombre.
    savedQueues: List<NamedQueueWithCount> = emptyList(),
    savedQueueMessage: String? = null,
    onSaveQueue: (String) -> Unit = {},
    onLoadSavedQueue: (Long) -> Unit = {},
    onDeleteSavedQueue: (Long) -> Unit = {},
    onSavedQueueMessageConsumed: () -> Unit = {},
    lyrics: List<LrcLine> = emptyList(),
    // RONDA 153: si la búsqueda online falló (sin conexión), la letra
    // no existe como "sin letras": se avisa del fallo en vez de decir
    // que la canción no tiene letra.
    lyricsError: Boolean = false,
    translatedLyrics: List<String> = emptyList(),
    // RONDA 2 (2026-11-09): línea de calidad real (FLAC/kbps/kHz) y
    // si el modo online está encendido (para explicar por qué no hay
    // letras cuando no las hay).
    qualityText: String? = null,
    onlineMode: Boolean = false,
    // Slice 5 paso 5: passthrough Atmos (Ajustes > Sonido). Sin él,
    // la pista JOC se decodifica normal y el aviso no sale.
    atmosPassthrough: Boolean = true,
    sleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    onSleepTimerSelected: (SleepTimerOption) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    // MENÚ ⋮ (2026-11-11, como BitChord): la fila superior (Me
    // gusta / No me gusta / Compartir) y las acciones de la pista
    // actual. Lo que ya se muestra como botón (Favorito, Letras,
    // Ecualizador) NO se duplica en el menú. Dormir vive solo en el
    // menú (desde 2026-11-11 ya no tiene botón propio).
    isOnline: Boolean = false,
    isDisliked: Boolean = false,
    isCached: Boolean = false,
    songPath: String? = null,
    // Ficha completa (copia BitChord): detalles reales + canción real
    // de la biblioteca. Null cuando suena online o aún no cargó.
    songDetails: SongDetails? = null,
    currentSong: Song? = null,
    onShareSong: () -> Unit = {},
    onPlayNextCurrent: () -> Unit = {},
    onAddToQueueCurrent: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onShowVideo: () -> Unit = {},
    onImproveQuality: () -> Unit = {},
    onDownload: () -> Unit = {},
    preselectedDownloadQuality: StreamQuality = StreamQuality.HIGH,
    onDownloadPermanent: (StreamQuality?) -> Unit = {},
    onRemoveFromCache: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onToggleDislike: () -> Unit = {},
    onShareErrorLog: () -> Unit = {},
    playlists: List<PlaylistEntity> = emptyList(),
    onAddToPlaylist: (Long) -> Unit = {},
    onCreatePlaylistAndAdd: (String) -> Unit = {},
    lyricsOffsetMs: Int = 0,
    onLyricsOffsetChange: (Int) -> Unit = {},
    onSaveLyrics: (List<LrcLine>) -> Unit = {},
    // Slice 6 (2026-11-12): tras guardar etiquetas, la biblioteca se
    // recarga para que los cambios se vean (MainActivity).
    onTagsSaved: () -> Unit = {},
    // Slice 6 paso 5b: canciones del mismo álbum para aplicar las
    // etiquetas a todo el álbum desde el editor.
    albumSongs: List<Song> = emptyList(),
    // Gestos con interruptor real en Ajustes (AUDITORÍA 2026-11-09).
    swipeEnabled: Boolean = true,
    doubleTapEnabled: Boolean = true,
    // Fase 2 — gestos configurables: qué hace el doble toque.
    doubleTapAction: GestureAction = GestureAction.FAVORITE,
    // Fase 2 — modos de karaoke: cuándo se colorean las palabras.
    karaokeMode: KaraokeMode = KaraokeMode.FULLSCREEN_ONLY,
    // Fase 2 app — Cast: nombre del dispositivo al que se está
    // enviando (null = sin sesión activa).
    castDeviceName: String? = null,
    modifier: Modifier = Modifier,
) {
    // REFINAMIENTO (2026-11-10, como BitChord): "A continuación"
    // visible al abrir el reproductor, no escondida tras el botón.
    var showQueue by remember { mutableStateOf(true) }
    var showSavedQueues by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    // MENÚ ⋮ (2026-11-11, como BitChord).
    var songMenuOpen by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    var itunesCoverUrl by remember(title, artist) { mutableStateOf<String?>(null) }
    // FORMA DE ONDA (Fase 2 app): picos de la canción local actual,
    // de caché o medidos en segundo plano al cambiar de pista. Las
    // pistas online (https) no se miden: nunca se dibuja una onda
    // inventada ni se descarga el audio entero para medirlo.
    var waveformPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    val playerContext = LocalContext.current
    // Slice 5 pasos 3-4: soporte Atmos del dispositivo, calculado
    // una sola vez por composición (la sonda toca AudioCapabilities
    // y la lista de códecs; no en cada frame ni en cada pista).
    // Se pasa al diálogo de información y al aviso del reproductor.
    val atmosSupported = remember(playerContext) {
        detectAtmosSupport(playerContext) == AtmosSupport.ATMOS
    }
    LaunchedEffect(songUri) {
        try {
            waveformPeaks = emptyList()
            val uri = songUri ?: return@LaunchedEffect
            if (uri.scheme != "content" && uri.scheme != "file") return@LaunchedEffect
            val key = uri.toString()
            val cached = WaveformCache.read(playerContext, key)
            if (cached != null) {
                waveformPeaks = cached
            } else {
                val measured = measureWaveform(playerContext, uri)
                if (measured != null) {
                    WaveformCache.write(playerContext, key, measured)
                    waveformPeaks = measured
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 2026-11-11 (crash al tocar una canción): que un fallo
            // midiendo la onda nunca cierre el reproductor.
            com.aurora.player.util.ErrorLog.log(playerContext, "Forma de onda", e)
        }
    }
    val lyricsListState = rememberLazyListState()
    // RONDA 2 (2026-11-13, P2): el desplazamiento automático seguía
    // la línea que suena aunque el usuario hubiera bajado a leer otra
    // parte; un arrastre manual pausa el seguimiento y el botón
    // "Seguir la letra" lo reanuda. Solo el arrastre del usuario
    // pausa: collectIsDraggedAsState no se dispara con el scroll
    // programado del propio efecto. Al cambiar de canción se vuelve
    // a seguir desde el principio.
    var lyricsFollow by remember { mutableStateOf(true) }
    val lyricsUserDragging by lyricsListState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(lyricsUserDragging) {
        if (lyricsUserDragging) lyricsFollow = false
    }
    LaunchedEffect(songUri) { lyricsFollow = true }
    val screenScrollState = rememberScrollState()
    var showLyricsEditor by remember { mutableStateOf(false) }
    // CANVAS (Fase 2, 2026-11-10, como BitChord): con el video
    // en bucle sonando, los controles se ocultan solos a los 5 s
    // sin tocar nada; un toque en la portada los vuelve a
    // mostrar. Sin Canvas (o en pausa) todo siempre visible.
    var canvasControlsVisible by remember { mutableStateOf(true) }
    var canvasHideKey by remember { mutableStateOf(0) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (canvasUrl != null && !canvasControlsVisible) 0f else 1f,
        label = "canvasControls",
    )
    LaunchedEffect(canvasUrl, isPlaying, canvasHideKey) {
        if (canvasUrl != null && isPlaying) {
            delay(5000)
            canvasControlsVisible = false
        } else {
            canvasControlsVisible = true
        }
    }
    val currentLine = LrcParser.currentLineIndex(lyrics, positionMs + lyricsOffsetMs)
    // RENDIMIENTO (spec 004): sin animaciones, la letra salta de línea
    // al instante en vez de desplazarse animada.
    val animationsEnabled = LocalAnimationsEnabled.current
    LaunchedEffect(showLyrics, currentLine, songUri, lyricsFollow) {
        if (showLyrics && lyricsFollow) {
            // RONDA 47: antes de la primera línea currentLine es -1 y
            // no se desplazaba nada, así que la letra podía abrirse en
            // la posición de la canción anterior; ahora se va al inicio.
            // RONDA 55: songUri en las claves — al cambiar de canción
            // con el mismo currentLine (-1/0 al inicio), el efecto no se
            // relanzaba y la lista quedaba en la posición vieja.
            // RONDA ARREGLOS 2026-11-13: la línea que suena quedaba
            // pegada arriba de la lista; ahora se dejan dos líneas
            // por encima, para que quede en el centro de la vista
            // (como BitChord) y se vea el contexto.
            val target = if (currentLine >= 0) (currentLine - 2).coerceAtLeast(0) else 0
            if (animationsEnabled) lyricsListState.animateScrollToItem(target)
            else lyricsListState.scrollToItem(target)
        }
    }
    // COLA (2026-11-10): al abrirla, la pantalla baja sola hasta
    // ella. Antes solo asomaba la fila cortada abajo.
    LaunchedEffect(showQueue) {
        if (showQueue) {
            if (animationsEnabled) screenScrollState.animateScrollTo(screenScrollState.maxValue)
            else screenScrollState.scrollTo(screenScrollState.maxValue)
        }
    }
    // DISEÑO PREMIUM (spec 003): fondo y acento siguen a la portada
    // de la canción que suena. Sin portada (o con el interruptor
    // apagado) manda el acento elegido en Ajustes, como siempre.
    val useDynamicColor = LocalDynamicColor.current
    val playerBackground = LocalPlayerBackground.current
    val coverBitmap = rememberArtworkBitmap(artworkUri, songUri, sizePx = 1024)
    val itunesCoverBitmap = rememberRemoteBitmap(itunesCoverUrl)

    LaunchedEffect(title, artist, onlineMode, coverBitmap, canvasUrl) {
        if (!onlineMode || canvasUrl != null || coverBitmap != null) {
            itunesCoverUrl = null
            return@LaunchedEffect
        }
        val queryTitle = title.orEmpty()
        val queryArtist = artist.orEmpty()
        try {
            val res = searchItunes("$queryArtist $queryTitle", limit = 1)
            itunesCoverUrl = res?.firstOrNull {
                (it.title.contains(queryTitle, ignoreCase = true) || queryTitle.contains(it.title, ignoreCase = true)) &&
                (it.artist.contains(queryArtist, ignoreCase = true) || queryArtist.contains(it.artist, ignoreCase = true))
            }?.artworkUrl600
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: IOException) {
            itunesCoverUrl = null
        }
    }
    // Fondo difuminado (2026-11-10, como BitChord): la misma portada
    // a 64 px, estirada a pantalla completa (el escalado bilineal la
    // difumina solo). Carga aparte de la grande (otra clave de caché).
    val coverBlurBitmap = rememberArtworkBitmap(artworkUri, songUri, sizePx = 64)
    val dynamicAccent = remember(useDynamicColor, coverBitmap) {
        if (useDynamicColor) coverBitmap?.let { dominantColor(it) } else null
    }
    val accent = dynamicAccent ?: MaterialTheme.colorScheme.primary
    val onAccent = if (dynamicAccent != null) contrastOn(dynamicAccent)
    else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        when (playerBackground) {
            PlayerBackground.COVER -> if (coverBlurBitmap != null) {
                // FONDO DIFUMINADO (2026-11-10, como BitChord): la
                // portada a 64 px estirada a pantalla completa, a
                // opacidad completa; el degradado a negro de abajo
                // deja leer el texto y los controles.
                Image(
                    bitmap = coverBlurBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PlayerBackground.GRADIENT -> if (dynamicAccent != null) {
                // REDISEÑO (2026-11-10): el degradado era tan suave
                // (0.38) que no se veía y el reproductor parecía plano
                // y negro. Ahora baja del color vivo de la portada.
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                dynamicAccent.copy(alpha = 0.72f),
                                dynamicAccent.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
                )
            }
            PlayerBackground.SOLID -> {}
        }
        // Sobre la portada de fondo, un degradado a negro para que el
        // texto y los controles se lean siempre.
        // RONDA LETRAS 2026-11-11 (brillo): el velo era 0.35 arriba /
        // TRANSPARENTE en el centro / 0.8 abajo, y el título y el
        // artista van justo en el centro: con una portada clara se
        // veían lavados (captura "Fuze (Kotori Remix)"). Ahora el
        // velo es oscuro en toda la pantalla.
        // UI-2 (2026-11-11): el velo era fijo (0.6/0.55/0.85) y con
        // portadas oscuras apagaba el color para nada; con portadas
        // claras hacía falta oscuro (RONDA LETRAS: el título se
        // lavaba). Ahora se gradúa por la luminosidad del color
        // dominante de la portada: oscura → velo ligero, clara →
        // velo fuerte (nunca más flojo que el probado con claras).
        val veilLum = remember(coverBlurBitmap) {
            coverBlurBitmap?.let { dominantColor(it) }?.let { relativeLuminance(it) } ?: 0.5f
        }
        val veilTop = 0.35f + 0.3f * veilLum
        val veilMid = 0.3f + 0.3f * veilLum
        val veilBottom = 0.6f + 0.3f * veilLum
        if (playerBackground == PlayerBackground.COVER && coverBlurBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = veilTop),
                            MaterialTheme.colorScheme.background.copy(alpha = veilMid),
                            MaterialTheme.colorScheme.background.copy(alpha = veilBottom),
                        ),
                    ),
                ),
            )
        }
    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(screenScrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }
            Box {
                IconButton(onClick = { songMenuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                }
                SongOptionsMenu(
                    expanded = songMenuOpen,
                    onDismiss = { songMenuOpen = false },
                    title = title,
                    artist = artist,
                    album = album,
                    isFavorite = isFavorite,
                    isDisliked = isDisliked,
                    isOnline = isOnline,
                    isCached = isCached,
                    accent = accent,
                    onToggleFavorite = onToggleFavorite,
                    onToggleDislike = onToggleDislike,
                    onShareSong = onShareSong,
                    onImproveQuality = onImproveQuality,
                    onShowVideo = onShowVideo,
                    onStartRadio = onStartRadio,
                    onPlayNextCurrent = onPlayNextCurrent,
                    onAddToQueueCurrent = onAddToQueueCurrent,
                    onDownload = onDownload,
                    onRemoveFromCache = onRemoveFromCache,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onShowSongInfo = { showSongInfo = true },
                    onShowAddToPlaylist = { showAddToPlaylist = true },
                    onOpenSleepMenu = { sleepMenuOpen = true },
                    onOpenLyrics = { showLyrics = true },
                    onShareErrorLog = onShareErrorLog,
                    onShowDownloadDialog = { showDownloadDialog = true },
                )
                // MENÚ ⋮ (2026-11-11): Dormir ya no tiene botón propio;
                // su desplegable vive aquí, anclado al ⋮.
                DropdownMenu(
                    expanded = sleepMenuOpen,
                    onDismissRequest = { sleepMenuOpen = false },
                ) {
                    SleepTimerOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (option == sleepTimer) "✓ ${option.label}" else option.label,
                                )
                            },
                            onClick = {
                                onSleepTimerSelected(option)
                                sleepMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        // MENÚ ⋮ Stage 2: añadir la pista actual a una lista.
        if (showAddToPlaylist) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { showAddToPlaylist = false },
                onAddToPlaylist = { id ->
                    showAddToPlaylist = false
                    onAddToPlaylist(id)
                },
                onCreatePlaylistAndAdd = { name ->
                    showAddToPlaylist = false
                    onCreatePlaylistAndAdd(name)
                },
            )
        }
        // MENÚ ⋮: información de la canción. Sin canción no hay
        // ficha: no se abre un diálogo vacío.
        if (showSongInfo && (title != null || currentSong != null)) {
            SongInfoDialog(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                qualityText = qualityText,
                songPath = songPath,
                isOnline = isOnline,
                song = currentSong,
                details = songDetails,
                atmosSupported = atmosSupported,
                onEditTags = { showSongInfo = false; showTagEditor = true },
                onDismiss = { showSongInfo = false },
            )
        }
        // Slice 6 (2026-11-12): editor de etiquetas de la canción
        // local. Al guardar, la biblioteca se recarga (onTagsSaved).
        if (showTagEditor && currentSong != null && !isOnline) {
            TagEditorDialog(
                song = currentSong,
                onSaved = { showTagEditor = false; onTagsSaved() },
                onDismiss = { showTagEditor = false },
                onlineMode = onlineMode,
                albumSongs = albumSongs,
            )
        }
        if (showDownloadDialog) {
            DownloadQualityDialog(
                preselected = preselectedDownloadQuality,
                onConfirm = { q ->
                    showDownloadDialog = false
                    onDownloadPermanent(q)
                },
                onDismiss = { showDownloadDialog = false },
            )
        }
        if (castDeviceName != null) {
            Text(
                "Enviando a $castDeviceName",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (title == null) {
            Spacer(Modifier.height(64.dp))
            Text("No suena nada todavía", style = MaterialTheme.typography.titleLarge)
            Text(
                "Elige una canción en Biblioteca y sonará aquí.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        // Portada grande con sombra y la forma elegida en Ajustes.
        // Gestos (ideas de Retro/Gramophone): deslizar a los lados
        // cambia de pista y doble toque marca la canción como favorita.
        // RONDA 5 (2026-11-09): el umbral del gesto era 100 px fijos:
        // en un teléfono denso son ~6 mm (cambia de pista sin querer)
        // y en uno poco denso ~16 mm. Ahora son 56 dp reales.
        val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
        // Fase 1b: el AudioManager se lee AQUÍ, en el cuerpo del
        // composable. Ni dentro de pointerInput ni dentro de
        // remember { } se puede leer LocalContext.current: el
        // cálculo de remember NO es contexto @Composable (eso era
        // el error del run #205 intento 2). Se lee fuera y se pasa
        // como clave de remember.
        val context = LocalContext.current
        val audioManager = remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        // Ronda 4: vibración al doble toque y posición actual
        // (rememberUpdatedState: dentro de pointerInput la posición
        // quedaría obsoleta si se leyera el parámetro directo).
        val haptics = LocalHapticFeedback.current
        val latestPositionMs by rememberUpdatedState(positionMs)
        val latestDurationMs by rememberUpdatedState(durationMs)
        val latestOnSeek by rememberUpdatedState(onSeek)
        val coverCorner = RoundedCornerShape(coverShapeToCornerPercent(LocalCoverShape.current))
        // Canvas de Spotify (Fase 2): cuando la canción tiene
        // Canvas, el vídeo en bucle ocupa el lugar de la portada;
        // si no, la portada de siempre. Los gestos (deslizar, doble
        // toque, volumen) viven en la caja, iguales para los dos,
        // para no duplicar la cadena.
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                // Resplandor con el color de la portada (spec 003).
                .shadow(24.dp, coverCorner, ambientColor = accent.copy(alpha = 0.5f), spotColor = accent.copy(alpha = 0.5f))
                .pointerInput(swipeEnabled) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!swipeEnabled) {
                                dragTotal = 0f
                                return@detectHorizontalDragGestures
                            }
                            when (swipeDirection(dragTotal, swipeThresholdPx)) {
                                1 -> onNext()
                                -1 -> onPrevious()
                            }
                            dragTotal = 0f
                        },
                        // RONDA 86: si el gesto se cancela (otro gesto
                        // se lo queda, se interrumpe el toque), el
                        // total acumulado se tira: si no, quedaba para
                        // el siguiente deslizamiento y uno corto podía
                        // cambiar de canción sin querer.
                        onDragCancel = { dragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                    )
                }
                .pointerInput(doubleTapEnabled, doubleTapAction) {
                    detectTapGestures(
                        // Fase 2 — gestos configurables: el doble
                        // toque hace lo que el usuario eligió.
                        onDoubleTap = {
                            if (!doubleTapEnabled) return@detectTapGestures
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            // RONDA 2 (2026-11-13, P2): la acción vive
                            // en performGestureAction (GestureAction.kt):
                            // un valor nuevo del enum sin rama no compila.
                            performGestureAction(
                                action = doubleTapAction,
                                onNext = onNext,
                                onPrevious = onPrevious,
                                onPlayPause = onPlayPause,
                                onToggleFavorite = onToggleFavorite,
                                onFastForward = {
                                    // Ronda 4: ±10 s; con duración
                                    // desconocida no se recorta.
                                    val target = latestPositionMs + 10_000L
                                    latestOnSeek(if (latestDurationMs > 0) target.coerceAtMost(latestDurationMs) else target)
                                },
                                onRewind = { latestOnSeek((latestPositionMs - 10_000L).coerceAtLeast(0L)) },
                                onToggleMute = {
                                    val action = if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                                        AudioManager.ADJUST_UNMUTE
                                    } else {
                                        AudioManager.ADJUST_MUTE
                                    }
                                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, action, AudioManager.FLAG_SHOW_UI)
                                },
                            )
                        },
                        onTap = { canvasControlsVisible = true; canvasHideKey++ },
                    )
                }
        ) {
            if (canvasUrl != null) {
                CanvasVideo(
                    url = canvasUrl,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize().clip(coverCorner),
                )
            } else if (itunesCoverBitmap != null) {
                Image(
                    bitmap = itunesCoverBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(coverCorner),
                )
            } else {
                ArtworkImage(
                    uri = artworkUri,
                    songUri = songUri,
                    letter = title.take(1).uppercase(),
                    sizePx = 1024,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Visualizador real (Fase 2): espectro bajo la portada, con
        // el color del visualizador del tema. Solo cuando hay datos
        // de verdad; en pausa o sin captura no se dibuja nada.
        val visualizerBars = AudioEffects.visualizerBars
        if (visualizerBars.isNotEmpty()) {
            VisualizerBarsView(
                bars = visualizerBars,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(
                // UI-2 (2026-11-11): 26.sp ExtraBold se sentía pesado
                // y poco refinado; 24.sp mantiene la jerarquía.
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (artist != null) {
            Text(
                artist,
                style = MaterialTheme.typography.bodyLarge.copy(
                    // UI-2 (2026-11-11): el artista baja de tamaño y
                    // de contraste para que el título mande.
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Rediseño: el nombre del artista abre su perfil.
                // heightIn(48.dp) para que el toque mida lo mínimo (R-03).
                modifier = Modifier.padding(top = 4.dp)
                    .heightIn(min = 48.dp)
                    .clickable { onOpenArtist() }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Abrir perfil de $artist"
                    },
            )
        }
        if (album != null) {
            Text(
                album,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (qualityText != null) {
            Text(
                qualityText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Slice 5 paso 4: aviso corto cuando suena Atmos de verdad:
        // en offload el ecualizador (y ReplayGain/balance) no se
        // aplican a esta pista. Solo local: YouTube Music no tiene
        // Atmos.
        if (songDetails?.isAtmosJoc == true && atmosSupported && atmosPassthrough && !isOnline) {
            Text(
                "Dolby Atmos · sin ecualizador en esta pista",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Vista previa de la letra (2026-11-10, como BitChord): la
        // línea que suena bajo el título; tocar abre las letras.
        // Fase 2 — modos de karaoke: con "Siempre", la vista previa
        // también colorea las palabras; si no, texto plano como antes.
        val currentLyricLine = lyrics.getOrNull(currentLine)
        val currentLyric = currentLyricLine?.text
        if (!showLyrics && currentLyric != null) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { showLyrics = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentLyricLine != null && currentLyricLine.words.isNotEmpty() &&
                    karaokeWordsActive(karaokeMode, fullscreen = false)
                ) {
                    Text(
                        karaokeLineText(currentLyricLine, positionMs, lyricsOffsetMs, accent),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Text(
                        currentLyric,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                // Fase 2 — compartir verso: el verso que suena, como
                // texto, a donde el usuario quiera. Sin verso (línea
                // vacía), no hay nada que compartir.
                IconButton(
                    onClick = { shareVerse(playerContext, currentLyric, title, artist) },
                    enabled = currentLyric.isNotBlank(),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Compartir verso",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Ver letras",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlayerSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            waveformPeaks = waveformPeaks,
            accent = accent,
            onSeek = onSeek,
            songUri = songUri,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).alpha(controlsAlpha),
        )
        // UI-2 (2026-11-11): más aire alrededor del grupo principal
        // (play/pausa, anterior, siguiente, aleatorio, repetir).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).alpha(controlsAlpha),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatorio",
                    modifier = Modifier.size(28.dp),
                    tint = if (shuffleEnabled) {
                        accent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
            }
            // Botón principal grande y relleno: no se puede confundir
            // con los demás controles ni pasar desapercibido.
            Box(
                modifier = Modifier.size(72.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(40.dp),
                    tint = onAccent,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    },
                    contentDescription = "Repetir",
                    modifier = Modifier.size(28.dp),
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        accent
                    },
                )
            }
        }
        // REDISEÑO (2026-11-10): cada acción lleva su nombre
        // debajo del icono. Antes había dos filas separadas (iconos
        // arriba, textos abajo) y los nombres no caían bajo su icono:
        // el temporizador parecía llamarse "Cola".
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).alpha(controlsAlpha),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onToggleFavorite() },
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Favorito",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { showLyrics = !showLyrics },
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = "Letras",
                    tint = if (showLyrics) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Letras",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpenEq() },
            ) {
                Icon(Icons.Filled.Equalizer, contentDescription = "Ecualizador")
                Text(
                    "Ecualizador",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // MENÚ ⋮ (2026-11-11): Dormir se mudó al menú ⋮ y su hueco
            // lo ocupa el Chromecast (el botón ya no está en la cabecera).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CastButton(size = 24.dp)
                Text(
                    "Chromecast",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // ETAPA 4: velocidad real y cola visible (tocar salta a esa pista).
        // RONDA 6: tono independiente, al lado de la velocidad.
        // UI-2 (2026-11-11): velocidad/tono/cola son acciones
        // secundarias: se separan visualmente del grupo principal.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCycleSpeed) {
                Text("Velocidad ${formatSpeed(speed)}x", maxLines = 1)
            }
            TextButton(onClick = onCyclePitch) {
                Text("Tono ${formatSpeed(pitch)}x", maxLines = 1)
            }
            if (queueTitles.isNotEmpty()) {
                TextButton(onClick = { showQueue = !showQueue }) {
                    Text(
                        if (showQueue) "Ocultar cola" else "Cola (${queueTitles.size})",
                        maxLines = 1,
                    )
                }
            }
            if (queueTitles.isNotEmpty() || savedQueues.isNotEmpty()) {
                TextButton(onClick = { showSavedQueues = true }) {
                    Text("Colas", maxLines = 1)
                }
            }
        }
        if (showQueue && queueTitles.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                // RONDA CAZA 2026-11-13 (P2): clave estable por fila
                // (índice + título: la cola puede repetir la misma
                // canción). Sin ella, al quitar una pista la marca de
                // "sonando" se quedaba en la fila equivocada.
                itemsIndexed(queueTitles, key = { index, queueTitle -> "$index:$queueTitle" }) { index, queueTitle ->
                    val isCurrent = index == queueIndex
                    ListItem(
                        headlineContent = {
                            Text(
                                "${index + 1}. $queueTitle",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) accent
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onQueueMove(index, index - 1) },
                                    enabled = index > 0,
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Subir en la cola",
                                    )
                                }
                                IconButton(
                                    onClick = { onQueueMove(index, index + 1) },
                                    enabled = index < queueTitles.size - 1,
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Bajar en la cola",
                                    )
                                }
                                IconButton(onClick = { onQueueRemove(index) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Quitar de la cola",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onQueueClick(index) },
                    )
                }
            }
        }
    }
    }
    // LETRAS A PANTALLA COMPLETA (2026-11-10, como BitChord): la
    // vista de letras ya no es una seccion dentro del reproductor,
    // es su propia pantalla con Canvas de fondo y transporte.
    if (showLyrics) {
        LyricsFullScreen(
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            songUri = songUri,
            canvasUrl = canvasUrl,
            coverBlurBitmap = coverBlurBitmap,
            lyrics = lyrics,
            lyricsError = lyricsError,
            translatedLyrics = translatedLyrics,
            showTranslation = showTranslation,
            onToggleTranslation = { showTranslation = !showTranslation },
            currentLine = currentLine,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            lyricsOffsetMs = lyricsOffsetMs,
            onLyricsOffsetChange = onLyricsOffsetChange,
            onEditLyrics = { showLyricsEditor = true },
            onToggleFavorite = onToggleFavorite,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            // Fase 2 — modos de karaoke.
            karaokeMode = karaokeMode,
            onSeek = onSeek,
            onClose = { showLyrics = false },
            onlineMode = onlineMode,
            accent = accent,
            onAccent = onAccent,
            listState = lyricsListState,
            followLyrics = lyricsFollow,
            onResumeFollowLyrics = { lyricsFollow = true },
        )
    }
    // Fase 1b: editor de letras (.lrc).
    if (showLyricsEditor) {
        LyricsEditorDialog(
            lyrics = lyrics,
            positionMs = positionMs,
            onDismiss = { showLyricsEditor = false },
            onSave = { edited ->
                onSaveLyrics(edited)
                showLyricsEditor = false
            },
        )
    }
    // SPEC 005 FR-007: diálogo de colas guardadas.
    if (showSavedQueues) {
        SavedQueuesDialog(
            savedQueues = savedQueues,
            queueSize = queueTitles.size,
            message = savedQueueMessage,
            onSaveQueue = onSaveQueue,
            onLoadSavedQueue = onLoadSavedQueue,
            onDeleteSavedQueue = onDeleteSavedQueue,
            onDismiss = {
                showSavedQueues = false
                onSavedQueueMessageConsumed()
            },
        )
    }
}

/**
 * SPEC 005 FR-007: colas guardadas con nombre. Tocar una cola la
 * carga como cola activa (reemplaza la actual); mantener pulsada
 * pregunta antes de borrarla. Guardar con un nombre que ya existe
 * reemplaza esa cola.
 */
@Composable
private fun SavedQueuesDialog(
    savedQueues: List<NamedQueueWithCount>,
    queueSize: Int,
    message: String?,
    onSaveQueue: (String) -> Unit,
    onLoadSavedQueue: (Long) -> Unit,
    onDeleteSavedQueue: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<NamedQueueWithCount?>(null) }
    val dateFormat = remember {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colas guardadas (${savedQueues.size}/$MAX_NAMED_QUEUES)") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la cola") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // RONDA 92: con 20 colas el botón se apagaba siempre,
                // también para actualizar una cola que ya existe (la
                // ronda 18 lo permite en MainActivity). Si el nombre
                // ya existe, se puede guardar encima.
                val normalizedName = normalizeQueueName(name)
                val nameExists = normalizedName != null &&
                    savedQueues.any { it.queue.name.equals(normalizedName, ignoreCase = true) }
                TextButton(
                    onClick = {
                        normalizedName?.let {
                            onSaveQueue(it)
                            name = ""
                        }
                    },
                    enabled = normalizedName != null &&
                        queueSize > 0 && (savedQueues.size < MAX_NAMED_QUEUES || nameExists),
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Guardar cola actual") }
                if (savedQueues.size >= MAX_NAMED_QUEUES && !nameExists) {
                    Text(
                        "Ya tienes $MAX_NAMED_QUEUES colas guardadas; borra una para guardar otra.",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                message?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }
                if (savedQueues.isEmpty()) {
                    Text(
                        "Aún no hay colas guardadas. Ponle un nombre arriba y guarda la cola que suena.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        // RONDA CAZA 2026-11-13 (P2): clave estable por
                        // id de la cola guardada (dos colas pueden
                        // llamarse igual).
                        itemsIndexed(savedQueues, key = { _, item -> item.queue.id }) { _, item ->
                            ListItem(
                                headlineContent = { Text(item.queue.name) },
                                supportingContent = {
                                    Text(
                                        "${item.songCount} canciones · " +
                                            dateFormat.format(Date(item.queue.updatedAt)),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = { onLoadSavedQueue(item.queue.id) },
                                    onLongClick = { toDelete = item },
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
    val itemToDelete = toDelete
    if (itemToDelete != null) {
        val item = itemToDelete
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Borrar cola") },
            text = { Text("¿Borrar «${item.queue.name}» con sus ${item.songCount} canciones?") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    onDeleteSavedQueue(item.queue.id)
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Texto de una línea con karaoke palabra por palabra (Fase 1b):
 * ya cantadas en acento, la que suena en acento y negrita, las que
 * faltan apagadas. Lo usan la vista a pantalla completa y (modo
 * "Siempre") la vista previa del reproductor.
 */
@Composable
private fun karaokeLineText(
    line: LrcLine,
    positionMs: Long,
    lyricsOffsetMs: Int,
    accent: Color,
): AnnotatedString {
    val wordIndex = LrcParser.currentWordIndex(line, positionMs + lyricsOffsetMs)
    return buildAnnotatedString {
        line.words.forEachIndexed { wi, word ->
            if (wi > 0) append(" ")
            withStyle(
                SpanStyle(
                    color = if (wi <= wordIndex) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (wi == wordIndex) FontWeight.Bold
                    else FontWeight.Normal,
                ),
            ) { append(word.text) }
        }
    }
}

/**
 * RONDA LENTITUD (2026-11-11): la barra de progreso con sus tiempos.
 * El estado del arrastre (sliderPosition) vive AQUÍ, no en la
 * pantalla: antes cada movimiento del dedo recomponía TODA la
 * pantalla del reproductor (portada, cola, letras...) a 60 Hz y
 * recorrer la música se sentía super lento. Ahora el arrastre solo
 * recompone esta barra; la pantalla solo se actualiza con el tick
 * de 500 ms y al soltar se hace un solo seek.
 */
@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    waveformPeaks: List<Float>,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    // R180 (2026-11-11): el arrastre vivía en un remember sin clave:
    // si la canción cambiaba a mitad del arrastre, al soltar se hacía
    // seek en la canción NUEVA con la posición de la VIEJA. La clave
    // es la canción (mismo patrón que LaunchedEffect(songUri)).
    songUri: Uri? = null,
) {
    var sliderPosition by remember(songUri) { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0L)
    val shownPosition = sliderPosition ?: positionMs.coerceIn(0L, duration).toFloat()
    Column(modifier = modifier) {
        WaveformSeekBar(
            peaks = waveformPeaks,
            value = shownPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                sliderPosition?.let { onSeek(it.toLong()) }
                sliderPosition = null
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            accent = accent,
            dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        )
        // UI-2 (2026-11-11): los tiempos iban pegados a la forma de
        // onda; 4 dp de aire y el transcurrido en acento (la
        // posición se lee sin saturar la barra de color).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                TimeFormat.formatDuration(shownPosition.toLong()),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = accent,
            )
            Text(
                TimeFormat.formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lineas de la letra (sincronizadas + karaoke palabra por palabra +
 * traduccion debajo). Compartida por la vista a pantalla completa;
 * antes vivia incrustada en el reproductor.
 */
@Composable
private fun LyricsLines(
    lyrics: List<LrcLine>,
    translatedLyrics: List<String>,
    showTranslation: Boolean,
    currentLine: Int,
    positionMs: Long,
    lyricsOffsetMs: Int,
    accent: Color,
    listState: LazyListState,
    fullscreen: Boolean = false,
    // Fase 2 — modos de karaoke: cuándo se colorean las palabras.
    karaokeMode: KaraokeMode = KaraokeMode.FULLSCREEN_ONLY,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        // Ronda 6-fluidez (Codex #4): clave estable por línea
        // (índice + tiempo: las letras no se reordenan, y dos
        // líneas pueden compartir el mismo tiempo).
        itemsIndexed(
            lyrics,
            key = { index, line -> "$index|${line.timeMs}" },
        ) { index, line ->
            val isCurrent = index == currentLine
            // RONDA LETRAS 2026-11-11 (como BitChord): alineadas a la
            // izquierda, la que suena grande y en blanco, su traducción
            // justo debajo en pequeño, y aire generoso entre grupos.
            // UI-2 (2026-11-11): la línea que suena con más aire y
            // las cercanas atenuadas de forma gradual por distancia
            // (antes todas las demás tenían el mismo gris).
            val lineFade = when {
                isCurrent || currentLine < 0 -> 1f
                kotlin.math.abs(index - currentLine) == 1 -> 0.7f
                kotlin.math.abs(index - currentLine) == 2 -> 0.55f
                else -> 0.4f
            }
            // RONDA ARREGLOS 2026-11-13: más aire entre líneas (las
            // letras se veían amontonadas) y la que suena más grande
            // también dentro del reproductor, no solo a pantalla
            // completa (como BitChord).
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = if (isCurrent) 20.dp else 12.dp)
                    .alpha(lineFade),
            ) {
                // Fase 2 — modos de karaoke: las palabras solo se
                // colorean cuando el modo lo permite en esta vista.
                if (isCurrent && line.words.isNotEmpty() &&
                    karaokeWordsActive(karaokeMode, fullscreen)
                ) {
                    Text(
                        karaokeLineText(line, positionMs, lyricsOffsetMs, accent),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        style = if (fullscreen) {
                            MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp)
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        color = if (isCurrent) {
                            if (fullscreen) Color.White else accent
                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (isCurrent) {
                            if (fullscreen) {
                                MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp)
                            } else {
                                MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp)
                            }
                        } else if (fullscreen) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = if (isCurrent) FontWeight.Bold
                        else FontWeight.Normal,
                    )
                }
                // Traduccion bajo la linea original, en pequeno; la de
                // la linea que suena en blanco suave (como BitChord),
                // las demas en gris. Solo si hay una y es distinta.
                val translation = translatedLyrics.getOrNull(index)
                if (showTranslation && translation != null &&
                    translation.isNotBlank() && translation != line.text
                ) {
                    Text(
                        translation,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        textAlign = TextAlign.Start,
                        color = if (isCurrent && fullscreen) {
                            Color.White.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = if (isCurrent && fullscreen) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                    )
                }
            }
        }
    }
}

/**
 * LETRAS A PANTALLA COMPLETA (2026-11-10, como BitChord): mini
 * cabecera (portada pequena + titulo/artista + favorito + cerrar),
 * Canvas en movimiento de fondo si lo hay (si no, la portada
 * difuminada), letras grandes con la traduccion debajo y el
 * transporte abajo. Sustituye a la lista dentro del reproductor.
 */
@Composable
private fun LyricsFullScreen(
    title: String?,
    artist: String?,
    artworkUri: Uri?,
    songUri: Uri?,
    canvasUrl: String?,
    coverBlurBitmap: Bitmap?,
    lyrics: List<LrcLine>,
    lyricsError: Boolean = false,
    translatedLyrics: List<String>,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    currentLine: Int,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    lyricsOffsetMs: Int,
    onLyricsOffsetChange: (Int) -> Unit,
    onEditLyrics: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    // Fase 2 — modos de karaoke.
    karaokeMode: KaraokeMode = KaraokeMode.FULLSCREEN_ONLY,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onlineMode: Boolean,
    accent: Color,
    onAccent: Color,
    listState: LazyListState,
    followLyrics: Boolean = true,
    onResumeFollowLyrics: () -> Unit = {},
) {
    // FORMA DE ONDA (Fase 2 app): picos de la canción local actual,
    // de caché o medidos en segundo plano al cambiar de pista. Las
    // pistas online (https) no se miden: nunca se dibuja una onda
    // inventada ni se descarga el audio entero para medirlo.
    var waveformPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    val playerContext = LocalContext.current
    LaunchedEffect(songUri) {
        try {
            waveformPeaks = emptyList()
            val uri = songUri ?: return@LaunchedEffect
            if (uri.scheme != "content" && uri.scheme != "file") return@LaunchedEffect
            val key = uri.toString()
            val cached = WaveformCache.read(playerContext, key)
            if (cached != null) {
                waveformPeaks = cached
            } else {
                val measured = measureWaveform(playerContext, uri)
                if (measured != null) {
                    WaveformCache.write(playerContext, key, measured)
                    waveformPeaks = measured
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 2026-11-11 (crash al tocar una canción): que un fallo
            // midiendo la onda nunca cierre el reproductor.
            com.aurora.player.util.ErrorLog.log(playerContext, "Forma de onda", e)
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (canvasUrl != null) {
            CanvasVideo(url = canvasUrl, isPlaying = isPlaying, modifier = Modifier.fillMaxSize())
        } else if (coverBlurBitmap != null) {
            Image(
                bitmap = coverBlurBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Velo oscuro para que las letras se lean sobre el Canvas o
        // la portada, igual que en BitChord.
        // RONDA LETRAS 2026-11-11 (brillo): el velo anterior era
        // 0.55 arriba / TRANSPARENTE en el centro / 0.85 abajo — justo
        // donde van las letras no había nada y el brillo de una
        // portada clara se comía el texto blanco. Ahora el velo es
        // oscuro en toda la pantalla (0.78 arriba, 0.72 en el centro,
        // 0.88 abajo): la portada se sigue viendo difuminada detrás,
        // pero el texto siempre se lee.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                    ),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Mini cabecera: portada + titulo/artista + favorito + cerrar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    uri = artworkUri,
                    letter = title?.firstOrNull()?.toString() ?: "\u266a",
                    size = 44.dp,
                    songUri = songUri,
                    sizePx = 144,
                )
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                ) {
                    Text(
                        title ?: "Sin título",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Fase 2 — compartir verso (igual que en la vista
                // previa): la línea que suena ahora mismo.
                val currentLineText = lyrics.getOrNull(currentLine)?.text
                IconButton(
                    onClick = {
                        if (!currentLineText.isNullOrBlank()) {
                            shareVerse(playerContext, currentLineText, title, artist)
                        }
                    },
                    enabled = !currentLineText.isNullOrBlank(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Compartir verso")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar letras")
                }
            }
            if (lyrics.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (lyricsError) {
                        "No se pudo buscar la letra. Comprueba tu conexión e inténtalo otra vez."
                    } else if (onlineMode) {
                        "Sin letras para esta canción."
                    } else {
                        "Sin letras para esta canción. Activa el Modo online en Ajustes para buscarlas en internet, o pon un archivo .lrc junto a la canción."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Ajuste de sincronizacion + traduccion, como en la
                // vista anterior: mismo comportamiento, nueva casa.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onLyricsOffsetChange((lyricsOffsetMs - 100).coerceIn(-5000, 5000)) },
                    ) { Text("\u2212100 ms") }
                    Text(
                        if (lyricsOffsetMs == 0) "En tiempo"
                        else "${if (lyricsOffsetMs > 0) "+" else ""}$lyricsOffsetMs ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onLyricsOffsetChange((lyricsOffsetMs + 100).coerceIn(-5000, 5000)) },
                    ) { Text("+100 ms") }
                    TextButton(
                        onClick = onEditLyrics,
                    ) { Text("Editar") }
                }
                if (translatedLyrics.isNotEmpty()) {
                    TextButton(
                        onClick = onToggleTranslation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (showTranslation) "Traducida al Español"
                            else "Traducir al español",
                        )
                    }
                }
                if (!followLyrics) {
                    TextButton(
                        onClick = onResumeFollowLyrics,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Seguir la letra") }
                }
                LyricsLines(
                    lyrics = lyrics,
                    translatedLyrics = translatedLyrics,
                    showTranslation = showTranslation,
                    currentLine = currentLine,
                    positionMs = positionMs,
                    lyricsOffsetMs = lyricsOffsetMs,
                    accent = accent,
                    listState = listState,
                    fullscreen = true,
                    karaokeMode = karaokeMode,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            // Transporte abajo, como en BitChord: barra + anterior /
            // play / siguiente.
            PlayerSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                waveformPeaks = waveformPeaks,
                accent = accent,
                onSeek = onSeek,
                songUri = songUri,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
                }
                Box(
                    modifier = Modifier.size(72.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(40.dp),
                        tint = onAccent,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

/**
 * MENÚ ⋮ (2026-11-11, como BitChord): las opciones de la pista
 * actual. La fila superior (Me gusta / No me gusta / Compartir)
 * imita la de BitChord; el resto son filas normales. Lo que ya se
 * muestra como botón en el reproductor (Favorito, Letras,
 * Ecualizador) no se duplica aquí. Dormir vive solo en el menú
 * (desde 2026-11-11 ya no tiene botón propio).
 */
@Composable
private fun SongOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String?,
    artist: String?,
    album: String?,
    isFavorite: Boolean,
    isDisliked: Boolean,
    isOnline: Boolean,
    isCached: Boolean,
    accent: Color,
    onToggleFavorite: () -> Unit,
    onToggleDislike: () -> Unit,
    onShareSong: () -> Unit,
    onImproveQuality: () -> Unit,
    onShowVideo: () -> Unit,
    onShowAddToPlaylist: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNextCurrent: () -> Unit,
    onAddToQueueCurrent: () -> Unit,
    onDownload: () -> Unit,
    onShowDownloadDialog: () -> Unit,
    onRemoveFromCache: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onShowSongInfo: () -> Unit,
    onOpenSleepMenu: () -> Unit,
    onOpenLyrics: () -> Unit,
    onShareErrorLog: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuTopAction(
                icon = Icons.Filled.Favorite,
                label = "Me gusta",
                tinted = isFavorite,
                accent = accent,
                onClick = { onDismiss(); onToggleFavorite() },
            )
            MenuTopAction(
                icon = Icons.Filled.HeartBroken,
                label = "No me gusta",
                tinted = isDisliked,
                accent = accent,
                onClick = { onDismiss(); onToggleDislike() },
            )
            MenuTopAction(
                icon = Icons.Filled.Share,
                label = "Compartir",
                tinted = false,
                accent = accent,
                onClick = { onDismiss(); onShareSong() },
            )
        }
        HorizontalDivider()
        if (isOnline) {
            DropdownMenuItem(
                text = { Text("Mejorar calidad") },
                leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                onClick = { onDismiss(); onImproveQuality() },
            )
            DropdownMenuItem(
                text = { Text("Ver vídeo") },
                leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                onClick = { onDismiss(); onShowVideo() },
            )
        }
        // MENÚ ⋮ Stage 2: las listas solo guardan música local (sus
        // canciones son ids de MediaStore); un tema online no tiene
        // id local y no cabe en una lista.
        if (!isOnline) {
            DropdownMenuItem(
                text = { Text("Añadir a la lista") },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                onClick = { onDismiss(); onShowAddToPlaylist() },
            )
        }
        DropdownMenuItem(
            text = { Text("Iniciar radio") },
            leadingIcon = { Icon(Icons.Filled.Radio, contentDescription = null) },
            onClick = { onDismiss(); onStartRadio() },
        )
        DropdownMenuItem(
            text = { Text("Reproducir después") },
            leadingIcon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
            onClick = { onDismiss(); onPlayNextCurrent() },
        )
        DropdownMenuItem(
            text = { Text("Agregar a la cola") },
            leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
            onClick = { onDismiss(); onAddToQueueCurrent() },
        )
        if (isOnline && !isCached) {
            DropdownMenuItem(
                text = { Text("Descargar a la caché") },
                leadingIcon = { Icon(Icons.Filled.Cached, contentDescription = null) },
                onClick = { onDismiss(); onDownload() },
            )
        }
        if (isOnline) {
            DropdownMenuItem(
                text = { Text("Descargar…") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = { onDismiss(); onShowDownloadDialog() },
            )
        }
        if (isOnline && isCached) {
            DropdownMenuItem(
                text = { Text("Quitar de la caché") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { onDismiss(); onRemoveFromCache() },
            )
        }
        if (album != null) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Abrir álbum")
                        Text(
                            album,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                onClick = { onDismiss(); onOpenAlbum() },
            )
        }
        if (artist != null) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Abrir artista")
                        Text(
                            artist,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                onClick = { onDismiss(); onOpenArtist() },
            )
        }
        DropdownMenuItem(
            text = { Text("Información de la canción") },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = { onDismiss(); onShowSongInfo() },
        )
        DropdownMenuItem(
            text = { Text("Temporizador") },
            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
            onClick = { onDismiss(); onOpenSleepMenu() },
        )
        DropdownMenuItem(
            text = { Text("Lyrics offset") },
            leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
            onClick = { onDismiss(); onOpenLyrics() },
        )
        DropdownMenuItem(
            text = { Text("Copiar registro") },
            leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
            onClick = { onDismiss(); onShareErrorLog() },
        )
    }
}
 
@Composable
private fun DownloadQualityDialog(
    preselected: StreamQuality,
    onConfirm: (StreamQuality?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<StreamQuality?>(preselected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descargar") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { selected = null },
                ) {
                    RadioButton(
                        selected = selected == null,
                        onClick = { selected = null },
                    )
                    Text("La mejor disponible", modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { selected = StreamQuality.HIGH },
                ) {
                    RadioButton(
                        selected = selected == StreamQuality.HIGH,
                        onClick = { selected = StreamQuality.HIGH },
                    )
                    Text("Alta", modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { selected = StreamQuality.MEDIUM },
                ) {
                    RadioButton(
                        selected = selected == StreamQuality.MEDIUM,
                        onClick = { selected = StreamQuality.MEDIUM },
                    )
                    Text("Media", modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { selected = StreamQuality.LOW },
                ) {
                    RadioButton(
                        selected = selected == StreamQuality.LOW,
                        onClick = { selected = StreamQuality.LOW },
                    )
                    Text("Baja", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Descargar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
 
/** Una acción de la fila superior del menú ⋮ (icono + nombre debajo). */
@Composable
private fun MenuTopAction(
    icon: ImageVector,
    label: String,
    tinted: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (tinted) accent else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** MENÚ ⋮: ficha completa de la pista actual (como BitChord, con código propio).
 * Solo se muestra la fila cuando el dato existe de verdad: sin dato no
 * hay fila, nunca un invento ni un marcador. El aspecto es el de Aurora
 * (AMOLED, SongInfoRow), no una copia visual de BitChord. Con tantas
 * filas, el contenido hace scroll dentro del diálogo. */
@Composable
private fun SongInfoDialog(
    title: String?,
    artist: String?,
    album: String?,
    durationMs: Long,
    qualityText: String?,
    songPath: String?,
    isOnline: Boolean,
    song: Song?,
    details: SongDetails?,
    atmosSupported: Boolean = false,
    onEditTags: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                // Slice 6: editar etiquetas solo en canciones locales
                // (una online no tiene archivo que etiquetar).
                if (onEditTags != null && song != null && !isOnline) {
                    TextButton(onClick = onEditTags) { Text("Editar etiquetas") }
                }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        title = { Text("Información de la canción") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Nombre de archivo y ubicación: de la ruta real.
                songPath?.takeIf { it.isNotBlank() }?.let { path ->
                    SongInfoRow("Nombre de archivo", path.substringAfterLast('/'))
                    SongInfoRow("Ubicación", path)
                }
                title?.let { SongInfoRow("Título", it) }
                artist?.let { SongInfoRow("Artista", it) }
                album?.let { SongInfoRow("Álbum", it) }
                song?.albumArtist?.takeIf { it.isNotBlank() }?.let { SongInfoRow("Artista de álbum", it) }
                (song?.composer ?: details?.composer)?.takeIf { it.isNotBlank() }?.let { SongInfoRow("Compositor", it) }
                (details?.genre ?: song?.genre)?.takeIf { it.isNotBlank() }?.let { SongInfoRow("Género", it) }
                details?.writer?.takeIf { it.isNotBlank() }?.let { SongInfoRow("Letrista", it) }
                song?.takeIf { it.trackNumber > 0 }?.let { SongInfoRow("Número de pista", it.trackNumber.toString()) }
                song?.takeIf { it.discNumber > 1 }?.let { SongInfoRow("Número de disco", it.discNumber.toString()) }
                song?.year?.let { SongInfoRow("Año", it.toString()) }
                SongInfoRow("Duración", formatInfoDuration(durationMs))
                // Calidad: la línea real (códec/bitrate/muestreo) y,
                // si el archivo es sin pérdida, se dice tal cual.
                qualityText?.let { text ->
                    val full = if (song?.isLossless == true) "$text · Sin pérdida" else text
                    SongInfoRow("Calidad", full)
                }
                // Slice 5 paso 3: "Dolby Atmos" solo cuando el archivo
                // es E-AC-3 JOC de verdad Y el dispositivo lo soporta.
                // Un eac3 sin JOC no es Atmos, y sin soporte en el
                // dispositivo no se promete nada. Solo local: YouTube
                // Music no tiene Atmos.
                if (details?.isAtmosJoc == true && atmosSupported && !isOnline) {
                    SongInfoRow("Audio espacial", "Dolby Atmos")
                }
                details?.bitrateKbps?.let { SongInfoRow("Velocidad de bits", "$it kbps") }
                details?.sampleRateHz?.let { SongInfoRow("Frecuencia", "$it Hz") }
                details?.bitDepth?.let { SongInfoRow("Profundidad de bits", "$it") }
                if (!isOnline && song != null) {
                    SongInfoRow("Formato", codecDisplayName(song.mimeType, song.path))
                }
                details?.channelCount?.let { SongInfoRow("Canal", it.toString()) }
                song?.takeIf { it.sizeBytes > 0 }?.let { SongInfoRow("Tamaño", formatInfoFileSize(it.sizeBytes)) }
                song?.takeIf { it.dateAdded > 0 }?.let { SongInfoRow("Fecha agregada a biblioteca", formatInfoDate(it.dateAdded)) }
                song?.takeIf { it.dateModified > 0 }?.let { SongInfoRow("Fecha modificada", formatInfoDate(it.dateModified)) }
                song?.lastPlayedAt?.let { SongInfoRow("Fecha de última reproducción", formatInfoDate(it)) }
                if (isOnline) {
                    SongInfoRow("Origen", "YouTube Music")
                }
            }
        },
    )
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatInfoDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return String.format(java.util.Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

/** Fecha legible en el idioma del teléfono ("16 ene 2026, 12:47"). */
private fun formatInfoDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

/** Tamaño legible con la coma decimal del idioma del teléfono. */
private fun formatInfoFileSize(bytes: Long): String {
    val locale = Locale.getDefault()
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(locale, "%.1f KB", bytes / 1024.0)
        else -> String.format(locale, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * MENÚ ⋮ Stage 2 (2026-11-11): añadir la pista actual a una lista.
 * Solo música local: las listas guardan ids de MediaStore y un tema
 * online no tiene id local (la fila no se muestra cuando suena online).
 */
@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a la lista") },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text("Aún no tienes listas: crea una abajo.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            DropdownMenuItem(
                                text = { Text(playlist.name) },
                                leadingIcon = {
                                    Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                                },
                                onClick = { onAddToPlaylist(playlist.id) },
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nueva lista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = { onCreatePlaylistAndAdd(newName.trim()) },
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
