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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.aurora.player.audio.WaveformCache
import com.aurora.player.audio.measureWaveform
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.aurora.player.model.karaokeWordsActive
import com.aurora.player.model.MAX_NAMED_QUEUES
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.normalizeQueueName
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.rememberArtworkBitmap
import com.aurora.player.ui.theme.LocalAnimationsEnabled
import com.aurora.player.ui.theme.LocalCoverShape
import com.aurora.player.ui.theme.LocalDynamicColor
import com.aurora.player.ui.theme.LocalPlayerBackground
import com.aurora.player.ui.theme.contrastOn
import com.aurora.player.playback.AudioEffects
import com.aurora.player.ui.theme.coverShapeToCornerPercent
import com.aurora.player.ui.theme.dominantColor
import com.aurora.player.util.LrcLine
import com.aurora.player.util.LrcParser
import com.aurora.player.util.shareVerse
import com.aurora.player.util.TimeFormat

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
 * Gesto de volumen: deslizamiento vertical sobre la portada.
 * Cada umbral recorrido es un paso de volumen (negativo = subir,
 * el dedo sube; positivo = bajar, el dedo baja). 0 = sin paso.
 */
fun volumeStepsForDrag(totalPx: Float, threshold: Float): Int =
    (totalPx / threshold).toInt()

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
    translatedLyrics: List<String> = emptyList(),
    // RONDA 2 (2026-11-09): línea de calidad real (FLAC/kbps/kHz) y
    // si el modo online está encendido (para explicar por qué no hay
    // letras cuando no las hay).
    qualityText: String? = null,
    onlineMode: Boolean = false,
    sleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    onSleepTimerSelected: (SleepTimerOption) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    lyricsOffsetMs: Int = 0,
    onLyricsOffsetChange: (Int) -> Unit = {},
    onSaveLyrics: (List<LrcLine>) -> Unit = {},
    // Gestos con interruptor real en Ajustes (AUDITORÍA 2026-11-09).
    swipeEnabled: Boolean = true,
    doubleTapEnabled: Boolean = true,
    // Fase 2 — gestos configurables: qué hace el doble toque.
    doubleTapAction: GestureAction = GestureAction.FAVORITE,
    // Fase 2 — modos de karaoke: cuándo se colorean las palabras.
    karaokeMode: KaraokeMode = KaraokeMode.FULLSCREEN_ONLY,
    volumeGestureEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showSavedQueues by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    var dragTotalY by remember { mutableStateOf(0f) }
    // Posición mientras se arrastra la barra: se busca UNA sola vez al
    // soltar, no en cada paso del arrastre (antes se pedían decenas de
    // seeks por segundo y el sonido se entrecortaba).
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    // FORMA DE ONDA (Fase 2 app): picos de la canción local actual,
    // de caché o medidos en segundo plano al cambiar de pista. Las
    // pistas online (https) no se miden: nunca se dibuja una onda
    // inventada ni se descarga el audio entero para medirlo.
    var waveformPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    val playerContext = LocalContext.current
    LaunchedEffect(songUri) {
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
    }
    val lyricsListState = rememberLazyListState()
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
    LaunchedEffect(showLyrics, currentLine) {
        if (showLyrics && currentLine >= 0) {
            if (animationsEnabled) lyricsListState.animateScrollToItem(currentLine)
            else lyricsListState.scrollToItem(currentLine)
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
        if (playerBackground == PlayerBackground.COVER && coverBlurBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
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
        val coverCorner = RoundedCornerShape(coverShapeToCornerPercent(LocalCoverShape.current))
        // Canvas de Spotify (Fase 2): cuando la canción tiene
        // Canvas, el vídeo en bucle ocupa el lugar de la portada;
        // si no, la portada de siempre. Los gestos (deslizar, doble
        // toque, volumen) viven en la caja, iguales para los dos,
        // para no duplicar la cadena.
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                // Resplandor con el color de la portada (spec 003).
                .shadow(32.dp, coverCorner, ambientColor = accent.copy(alpha = 0.7f), spotColor = accent.copy(alpha = 0.7f))
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
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                    )
                }
                .pointerInput(doubleTapEnabled, doubleTapAction) {
                    detectTapGestures(
                        // Fase 2 — gestos configurables: el doble
                        // toque hace lo que el usuario eligió.
                        onDoubleTap = {
                            if (!doubleTapEnabled) return@detectTapGestures
                            when (doubleTapAction) {
                                GestureAction.FAVORITE -> onToggleFavorite()
                                GestureAction.PLAY_PAUSE -> onPlayPause()
                                GestureAction.NEXT -> onNext()
                                GestureAction.PREVIOUS -> onPrevious()
                            }
                        },
                        onTap = { canvasControlsVisible = true; canvasHideKey++ },
                    )
                }
                .pointerInput(volumeGestureEnabled) {
                    // Fase 1b: deslizar vertical sobre la portada
                    // sube/baja el volumen; la barra del sistema
                    // (FLAG_SHOW_UI) es la que da la respuesta visual.
                    detectVerticalDragGestures(
                        onDragEnd = { dragTotalY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (!volumeGestureEnabled) return@detectVerticalDragGestures
                            dragTotalY += dragAmount
                            val steps = volumeStepsForDrag(dragTotalY, swipeThresholdPx)
                            if (steps != 0) {
                                val direction = if (steps > 0) AudioManager.ADJUST_LOWER
                                else AudioManager.ADJUST_RAISE
                                repeat(kotlin.math.abs(steps)) {
                                    audioManager.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        direction,
                                        AudioManager.FLAG_SHOW_UI,
                                    )
                                }
                                dragTotalY -= steps * swipeThresholdPx
                            }
                        },
                    )
                },
        ) {
            if (canvasUrl != null) {
                CanvasVideo(
                    url = canvasUrl,
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
                fontSize = 26.sp,
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
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
        val duration = durationMs.coerceAtLeast(0L)
        val shownPosition = sliderPosition ?: positionMs.coerceIn(0L, duration).toFloat()
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
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).alpha(controlsAlpha),
        )
        Row(
            modifier = Modifier.fillMaxWidth().alpha(controlsAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                TimeFormat.formatDuration(shownPosition.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                TimeFormat.formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).alpha(controlsAlpha),
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
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { sleepMenuOpen = true },
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = "Temporizador de apagado",
                        tint = if (sleepTimer == SleepTimerOption.OFF) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            accent
                        },
                    )
                    Text(
                        "Dormir",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
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
        // ETAPA 4: velocidad real y cola visible (tocar salta a esa pista).
        // RONDA 6: tono independiente, al lado de la velocidad.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
            LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                itemsIndexed(queueTitles) { index, queueTitle ->
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
                TextButton(
                    onClick = {
                        normalizeQueueName(name)?.let {
                            onSaveQueue(it)
                            name = ""
                        }
                    },
                    enabled = normalizeQueueName(name) != null &&
                        queueSize > 0 && savedQueues.size < MAX_NAMED_QUEUES,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Guardar cola actual") }
                if (savedQueues.size >= MAX_NAMED_QUEUES) {
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
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        itemsIndexed(savedQueues) { _, item ->
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
    if (toDelete != null) {
        val item = toDelete!!
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
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentLine
            Column(modifier = Modifier.fillMaxWidth()) {
                // Fase 2 — modos de karaoke: las palabras solo se
                // colorean cuando el modo lo permite en esta vista.
                if (isCurrent && line.words.isNotEmpty() &&
                    karaokeWordsActive(karaokeMode, fullscreen)
                ) {
                    Text(
                        karaokeLineText(line, positionMs, lyricsOffsetMs, accent),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else {
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        color = if (isCurrent) {
                            if (fullscreen) Color.White else accent
                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (isCurrent) {
                            MaterialTheme.typography.titleLarge
                        } else if (fullscreen) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = if (isCurrent && fullscreen) FontWeight.Bold
                        else FontWeight.Normal,
                    )
                }
                // Traduccion bajo la linea original, en pequeno y
                // apagada; solo si hay una y es distinta.
                val translation = translatedLyrics.getOrNull(index)
                if (showTranslation && translation != null &&
                    translation.isNotBlank() && translation != line.text
                ) {
                    Text(
                        translation,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
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
) {
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    // FORMA DE ONDA (Fase 2 app): picos de la canción local actual,
    // de caché o medidos en segundo plano al cambiar de pista. Las
    // pistas online (https) no se miden: nunca se dibuja una onda
    // inventada ni se descarga el audio entero para medirlo.
    var waveformPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    val playerContext = LocalContext.current
    LaunchedEffect(songUri) {
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
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (canvasUrl != null) {
            CanvasVideo(url = canvasUrl, modifier = Modifier.fillMaxSize())
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
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
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
                    if (onlineMode) {
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
            val duration = durationMs.coerceAtLeast(0L)
            val shownPosition = sliderPosition ?: positionMs.coerceIn(0L, duration).toFloat()
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
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    TimeFormat.formatDuration(shownPosition.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    TimeFormat.formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
