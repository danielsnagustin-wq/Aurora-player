package com.aurora.player.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * RONDA ICONOS (2026-11-11): los iconos de marca de YouTube Music y
 * Spotify, dibujados a mano en código (la app no tiene res/drawable):
 * en "Cuenta e integraciones" salían una nota de música genérica y
 * una persona genérica. Son las marcas de los servicios, no arte
 * propio: solo identifican la cuenta que se conecta.
 */

/** YouTube Music: círculo rojo con el triángulo de play blanco. */
@Composable
fun YouTubeMusicIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(color = Color(0xFFFF0000), radius = radius, center = center)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.40f, h * 0.32f)
            lineTo(w * 0.40f, h * 0.68f)
            lineTo(w * 0.70f, h * 0.50f)
            close()
        }
        drawPath(path, color = Color.White)
    }
}

/** Spotify: círculo verde con sus tres arcos negros inclinados. */
@Composable
fun SpotifyIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(color = Color(0xFF1DB954), radius = size.minDimension / 2f, center = center)
        val stroke = size.minDimension * 0.075f
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Tres arcos paralelos, el de arriba más largo, con la
        // inclinación del logo.
        rotate(degrees = -18f, pivot = center) {
            // (centro vertical relativo, diámetro relativo)
            val arcs = listOf(
                -0.14f to 0.95f,
                0.0f to 0.85f,
                0.14f to 0.75f,
            )
            for ((dy, diameterRatio) in arcs) {
                val diameter = size.width * diameterRatio
                drawArc(
                    color = Color.Black,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(cx - diameter / 2f, cy + size.height * dy - diameter / 2f),
                    size = Size(diameter, diameter),
                    style = style,
                )
            }
        }
    }
}
