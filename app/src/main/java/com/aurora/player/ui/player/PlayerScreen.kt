package com.aurora.player.ui.player

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.media3.common.Player
import com.aurora.player.model.PlayerBackground
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.rememberArtworkBitmap
import com.aurora.player.ui.theme.LocalCoverShape
import com.aurora.player.ui.theme.LocalDynamicColor
import com.aurora.player.ui.theme.LocalPlayerBackground
import com.aurora.player.ui.theme.contrastOn
import com.aurora.player.ui.theme.coverShapeToCornerPercent
import com.aurora.player.ui.theme.dominantColor
import com.aurora.player.util.LrcLine
import com.aurora.player.util.LrcParser
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
    lyrics: List<LrcLine> = emptyList(),
    sleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    onSleepTimerSelected: (SleepTimerOption) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    lyricsOffsetMs: Int = 0,
    onLyricsOffsetChange: (Int) -> Unit = {},
    // Gestos con interruptor real en Ajustes (AUDITORÍA 2026-11-09).
    swipeEnabled: Boolean = true,
    doubleTapEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    // Posición mientras se arrastra la barra: se busca UNA sola vez al
    // soltar, no en cada paso del arrastre (antes se pedían decenas de
    // seeks por segundo y el sonido se entrecortaba).
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val lyricsListState = rememberLazyListState()
    val currentLine = LrcParser.currentLineIndex(lyrics, positionMs + lyricsOffsetMs)
    LaunchedEffect(showLyrics, currentLine) {
        if (showLyrics && currentLine >= 0) {
            lyricsListState.animateScrollToItem(currentLine)
        }
    }
    // DISEÑO PREMIUM (spec 003): fondo y acento siguen a la portada
    // de la canción que suena. Sin portada (o con el interruptor
    // apagado) manda el acento elegido en Ajustes, como siempre.
    val useDynamicColor = LocalDynamicColor.current
    val playerBackground = LocalPlayerBackground.current
    val coverBitmap = rememberArtworkBitmap(artworkUri, songUri)
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
            PlayerBackground.COVER -> if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.32f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PlayerBackground.GRADIENT -> if (dynamicAccent != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                dynamicAccent.copy(alpha = 0.38f),
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
        if (playerBackground == PlayerBackground.COVER && coverBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
            )
        }
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { sleepMenuOpen = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Temporizador de apagado",
                            tint = if (sleepTimer == SleepTimerOption.OFF) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                accent
                            },
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
                IconButton(onClick = onOpenEq) {
                    Icon(Icons.Filled.Equalizer, contentDescription = "Ecualizador")
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) accent
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
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
        val coverCorner = RoundedCornerShape(coverShapeToCornerPercent(LocalCoverShape.current))
        ArtworkImage(
            uri = artworkUri,
            songUri = songUri,
            letter = title.take(1).uppercase(),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                // Resplandor con el color de la portada (spec 003).
                .shadow(24.dp, coverCorner, ambientColor = accent.copy(alpha = 0.45f), spotColor = accent.copy(alpha = 0.45f))
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
                .pointerInput(doubleTapEnabled) {
                    detectTapGestures(
                        onDoubleTap = { if (doubleTapEnabled) onToggleFavorite() },
                    )
                },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            listOfNotNull(artist, album).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val duration = durationMs.coerceAtLeast(0L)
        val shownPosition = sliderPosition ?: positionMs.coerceIn(0L, duration).toFloat()
        Slider(
            value = shownPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                sliderPosition?.let { onSeek(it.toLong()) }
                sliderPosition = null
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(TimeFormat.formatDuration(shownPosition.toLong()))
            Text(TimeFormat.formatDuration(duration))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
        // ETAPA 4: velocidad real y cola visible (tocar salta a esa pista).
        // RONDA 6: tono independiente, al lado de la velocidad.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCycleSpeed) {
                Text("Velocidad ${formatSpeed(speed)}x")
            }
            TextButton(onClick = onCyclePitch) {
                Text("Tono ${formatSpeed(pitch)}x")
            }
            if (queueTitles.isNotEmpty()) {
                TextButton(onClick = { showQueue = !showQueue }) {
                    Text(if (showQueue) "Ocultar cola" else "Cola (${queueTitles.size})")
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
        // ETAPA 5: letras sincronizadas del .lrc de la canción.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showLyrics = !showLyrics }) {
                Text(if (showLyrics) "Ocultar letras" else "Letras")
            }
        }
        if (showLyrics) {
            if (lyrics.isEmpty()) {
                Text(
                    "Sin letras para esta canción.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Ajuste de sincronización: el .lrc puede venir unos
                // ms desplazado; se corrige por canción y se guarda.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onLyricsOffsetChange((lyricsOffsetMs - 100).coerceIn(-5000, 5000)) },
                    ) { Text("−100 ms") }
                    Text(
                        if (lyricsOffsetMs == 0) "En tiempo"
                        else "${if (lyricsOffsetMs > 0) "+" else ""}$lyricsOffsetMs ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onLyricsOffsetChange((lyricsOffsetMs + 100).coerceIn(-5000, 5000)) },
                    ) { Text("+100 ms") }
                }
                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isCurrent = index == currentLine
                        Text(
                            line.text,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            textAlign = TextAlign.Center,
                            color = if (isCurrent) accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = if (isCurrent) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
    }
}
