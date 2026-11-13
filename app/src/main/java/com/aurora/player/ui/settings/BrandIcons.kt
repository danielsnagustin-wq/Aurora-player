package com.aurora.player.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
            moveTo(w * 0.38f, h * 0.30f)
            lineTo(w * 0.38f, h * 0.70f)
            lineTo(w * 0.72f, h * 0.50f)
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
        // Tres curvas paralelas, la de arriba más larga, con la
        // inclinación del logo. RONDA ARREGLOS 2026-11-13: antes eran
        // trozos de círculo (drawArc) y salían dos "cejas" recortadas
        // arriba; los arcos del logo son curvas suaves, así que van
        // como Bézier cuadráticas (verificado el dibujo antes de
        // pushear).
        rotate(degrees = -18f, pivot = center) {
            val curves = listOf(
                Triple(Offset(0.18f, 0.42f), Offset(0.50f, 0.18f), Offset(0.82f, 0.42f)),
                Triple(Offset(0.22f, 0.54f), Offset(0.50f, 0.32f), Offset(0.78f, 0.54f)),
                Triple(Offset(0.26f, 0.66f), Offset(0.50f, 0.46f), Offset(0.74f, 0.66f)),
            )
            for ((start, control, end) in curves) {
                val path = Path().apply {
                    moveTo(size.width * start.x, size.height * start.y)
                    quadraticBezierTo(
                        size.width * control.x, size.height * control.y,
                        size.width * end.x, size.height * end.y,
                    )
                }
                drawPath(path, color = Color.Black, style = style)
            }
        }
    }
}

/**
 * SoundCloud: círculo naranja con su nube blanca (dos bultos y base
 * redondeada). La nube del logo lleva además unas barras de sonido a
 * la izquierda; aquí va solo la nube, que es lo reconocible.
 */
@Composable
fun SoundCloudIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(color = Color(0xFFFF5500), radius = size.minDimension / 2f, center = center)
        val w = size.width
        val h = size.height
        val cloud = Color.White
        // RONDA ARREGLOS 2026-11-13: la nube era un bulto pequeño que
        // no se leía como nube. Ahora es más grande, con los dos
        // bultos bien separados (verificado el dibujo antes de pushear).
        drawCircle(color = cloud, radius = w * 0.23f, center = Offset(w * 0.60f, h * 0.40f))
        drawCircle(color = cloud, radius = w * 0.17f, center = Offset(w * 0.36f, h * 0.46f))
        // Base redondeada de la nube.
        drawRoundRect(
            color = cloud,
            topLeft = Offset(w * 0.22f, h * 0.46f),
            size = Size(w * 0.58f, h * 0.22f),
            cornerRadius = CornerRadius(h * 0.10f),
        )
    }
}
