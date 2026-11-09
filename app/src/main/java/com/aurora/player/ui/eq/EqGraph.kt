package com.aurora.player.ui.eq

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.freqToX

/**
 * RONDA 7 P1: gráfica de respuesta en frecuencias de una curva de EQ.
 * Eje X logarítmico con las frecuencias REALES de las bandas del
 * dispositivo (nunca inventadas; si no hay, las bandas se reparten
 * por igual); eje Y -15..+15 dB. La curva es la que suena: los puntos
 * son los niveles reales de la curva y la línea los une con suavizado.
 */
@Composable
fun EqResponseGraph(
    levelsDb: List<Float>,
    frequenciesHz: List<Int>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val left = 34.dp.toPx()
        val right = 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun yFor(db: Float): Float = top + (1f - (db + 15f) / 30f) * h
        fun xFor(i: Int): Float {
            val n = levelsDb.size
            if (n <= 1) return left
            val freq = frequenciesHz.getOrNull(i)
            return if (freq != null && frequenciesHz.size >= 2) {
                left + freqToX(freq, frequenciesHz.first(), frequenciesHz.last()) * w
            } else {
                left + (i.toFloat() / (n - 1)) * w
            }
        }
        // Rejilla horizontal (+15, 0, -15).
        val gridColor = onSurface.copy(alpha = 0.15f)
        for (db in listOf(15f, 0f, -15f)) {
            drawLine(gridColor, Offset(left, yFor(db)), Offset(size.width - right, yFor(db)), strokeWidth = 1.dp.toPx())
        }
        // Etiquetas dB a la izquierda.
        val labelPaint = Paint().apply {
            color = onSurface.copy(alpha = 0.6f).toArgb()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.RIGHT
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("+15", left - 4.dp.toPx(), yFor(15f) + 4.dp.toPx(), labelPaint)
            canvas.nativeCanvas.drawText("0", left - 4.dp.toPx(), yFor(0f) + 4.dp.toPx(), labelPaint)
            canvas.nativeCanvas.drawText("-15", left - 4.dp.toPx(), yFor(-15f) + 4.dp.toPx(), labelPaint)
        }
        if (levelsDb.isEmpty()) return@Canvas
        // Puntos de la curva.
        val points = levelsDb.indices.map { i -> Offset(xFor(i), yFor(levelsDb[i])) }
        // Curva suave por los puntos (curvas cuadráticas por puntos medios).
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val mid = Offset((points[i - 1].x + points[i].x) / 2f, (points[i - 1].y + points[i].y) / 2f)
                quadraticBezierTo(points[i - 1].x, points[i - 1].y, mid.x, mid.y)
            }
            lineTo(points.last().x, points.last().y)
        }
        // Relleno bajo la curva.
        val fillPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, yFor(-15f))
            lineTo(points[0].x, yFor(-15f))
            close()
        }
        drawPath(fillPath, accent.copy(alpha = 0.18f))
        drawPath(path, accent, style = Stroke(width = 3.dp.toPx()))
        for (p in points) drawCircle(accent, radius = 4.dp.toPx(), center = p)
        // Etiquetas de frecuencia bajo cada banda.
        val freqPaint = Paint().apply {
            color = onSurface.copy(alpha = 0.6f).toArgb()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.CENTER
        }
        drawIntoCanvas { canvas ->
            for (i in levelsDb.indices) {
                val hz = frequenciesHz.getOrNull(i) ?: continue
                val label = if (hz >= 1000) "${hz / 1000}k" else "$hz"
                canvas.nativeCanvas.drawText(label, xFor(i), size.height - 6.dp.toPx(), freqPaint)
            }
        }
    }
}
