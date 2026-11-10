package com.aurora.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Visualizador real (Fase 2 audio): dibuja las barras que publica
 * el servicio desde la FFT de la sesión de audio. Barras
 * redondeadas, color del tema (visualizerHex). Si la lista viene
 * vacía (sin captura o en pausa), no dibuja nada: nunca se finge
 * animación con datos inventados.
 */
@Composable
fun VisualizerBarsView(
    bars: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (bars.isEmpty()) return@Canvas
        val barWidth = size.width / bars.size
        bars.forEachIndexed { i, level ->
            val height = (level * size.height).coerceAtLeast(if (level > 0f) 2.dp.toPx() else 0f)
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = i * barWidth + barWidth * 0.15f,
                    y = size.height - height,
                ),
                size = Size(barWidth * 0.7f, height),
                cornerRadius = CornerRadius(barWidth * 0.35f),
            )
        }
    }
}
