package com.aurora.player.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.captionLineAt
import com.aurora.player.util.LrcLine
import kotlinx.coroutines.delay

/**
 * Vídeo de YouTube a pantalla completa (Fase 2 app, Namida),
 * Serie B.
 *
 * El vídeo suena por el reproductor de siempre (el mismo
 * MediaController): la PlayerView le pone la imagen y los controles
 * de reproducir/pausa/buscar. Al cerrar, el vídeo se quita y el
 * audio sigue en el mini-reproductor, como cerrar el vídeo de
 * cualquier app. Los subtítulos van en pantalla, sincronizados con
 * la posición; sin subtítulos, simplemente no se muestra nada.
 */
@Composable
fun VideoPlayerDialog(
    track: OnlineTrack,
    captions: List<LrcLine>,
    controller: MediaController?,
    onDismiss: () -> Unit,
) {
    var positionMs by remember { mutableStateOf(0L) }
    LaunchedEffect(controller) {
        while (true) {
            positionMs = controller?.currentPosition ?: 0L
            delay(200)
        }
    }
    val caption = captionLineAt(captions, positionMs)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (controller != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = controller
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "El reproductor no está listo.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            ) {
                Text(
                    track.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (track.artist.isNotBlank()) {
                    Text(
                        track.artist,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cerrar vídeo",
                    tint = Color.White,
                )
            }
            caption?.let {
                Text(
                    it.text,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
