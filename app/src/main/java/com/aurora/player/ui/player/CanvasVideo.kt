package com.aurora.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Canvas de Spotify (Fase 2, como BitChord): vídeo en bucle, sin
 * sonido, que ocupa el lugar de la portada cuando la canción tiene
 * Canvas. Reproductor propio y silenciado (la música sale del
 * reproductor principal); se crea con la URL y se libera al salir
 * o al cambiar de canción. El vídeo se recorta para llenar el
 * cuadrado de la portada (ZOOM).
 */
@Composable
fun CanvasVideo(url: String, isPlaying: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = isPlaying
        }
    }
    // R180 (2026-11-11): el Canvas arrancaba con playWhenReady = true
    // y nadie lo paraba nunca: el vídeo seguía en bucle aunque la
    // música estuviera en pausa. Ahora sigue al reproductor principal.
    LaunchedEffect(isPlaying) { player.playWhenReady = isPlaying }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier,
    )
}
