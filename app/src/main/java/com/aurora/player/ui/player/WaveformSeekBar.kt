package com.aurora.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Forma de onda (Fase 2 app): la barra de búsqueda del reproductor
 * dibujada con la onda real de la canción. La onda va debajo y el
 * Slider de Compose (con las pistas transparentes) sigue llevando
 * toda la interacción: arrastrar y tocar para buscar, con el mismo
 * comportamiento de siempre. La parte ya sonada va en el color de
 * acento; la que falta, atenuada. Sin picos (aún sin medir o fallo)
 * no se dibuja nada: el Slider queda solo, como antes.
 */
@Composable
fun WaveformSeekBar(
    peaks: List<Float>,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    dimmed: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(48.dp)) {
        if (peaks.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val barWidth = size.width / peaks.size
                val playedX = value.coerceIn(valueRange.start, valueRange.endInclusive)
                    .let { (it - valueRange.start) / (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f) } * size.width
                peaks.forEachIndexed { index, peak ->
                    val x = index * barWidth
                    val barHeight = (peak.coerceIn(0f, 1f) * size.height * 0.9f).coerceAtLeast(2f)
                    val top = (size.height - barHeight) / 2f
                    drawRoundRect(
                        color = if (x + barWidth / 2f <= playedX) accent else dimmed,
                        topLeft = Offset(x + barWidth * 0.15f, top),
                        size = Size(barWidth * 0.7f, barHeight),
                        cornerRadius = CornerRadius(barWidth * 0.35f, barWidth * 0.35f),
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
}
