package com.aurora.player.ui.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aurora.player.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.delay
import com.aurora.player.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * RETIRADA (2026-11-10 13:24, orden del usuario): la animación de
 * inicio con brillo/polvo se quitó; la app entra directo a la
 * biblioteca. Este archivo ya no se usa desde ningún sitio.
 * Aurora Player — inicio propio con animación (3 s, corta).
 * SIN NOMBRE: solo la figura, EN POLVO SUAVE (sin bloques).
 * ANIMACIÓN DE MOVIMIENTO de verdad, no balanceo en el sitio:
 * la figura ENTRA VOLANDO desde la derecha, luego SUBE mientras
 * se convierte en polvo azul suave (lógica de Child of Light:
 * los oculis): se deshace desde los bordes hacia adentro, con
 * polvo denso que sale de su silueta, y un viento suave de
 * DERECHA a IZQUIERDA se lo lleva hasta dejar la pantalla
 * limpia. Tocar la pantalla salta la animación.
 * Brillitos AZULES, no blancos. En el video el pelo y el
 * vestido además ondean (franjas); en la app el movimiento es
 * el viaje + la subida + el polvo.
 *
 * Fases (3 s): 0-0,8 s entra volando desde la derecha;
 * 0,8-1,5 s vuela en el centro; 1,5-2,5 s sube y se hace polvo
 * suave; 2,5-3 s el viento barre el polvo de derecha a
 * izquierda y todo se apaga. Al terminar, onFinished abre la
 * biblioteca.
 */
private data class DustParticle(
    val x: Float, // 0..1 relativo al ancho
    val y: Float, // 0..1 relativo al alto
    val r: Float, // radio base en px
    val color: Color,
    val start: Float, // progreso en el que aparece
    val drift: Float, // cuánto lo arrastra el viento (fracción del ancho)
    val wobble: Float, // vaivén vertical en px
    val phase: Float, // fase del parpadeo de luciérnaga
    val flick: Float, // velocidad del parpadeo
    val maxA: Float = 0.6f, // alfa máxima del grano
)

private fun buildParticles(): List<DustParticle> {
    val random = Random(42)
    // Brillitos AZULES, no blancos: solo azul cielo/oculi.
    val colors = listOf(
        Color(0xFF9FD8F2), // azul cielo (oculi)
        Color(0xFF99DCFF), // llama azul de Igniculus
        Color(0xFF7FB8FF), // azul oculi
        Color(0xFF6FC8FF), // azul brillante
        Color(0xFF5FB8FF), // azul intenso
    )
    val list = ArrayList<DustParticle>()
    // Polvo AZUL SUAVE y denso: 450 granos, más grandes. Se
    // deshace desde los BORDES hacia adentro: el inicio depende
    // de la distancia al centro (bordes antes).
    repeat(450) {
        val x = 0.5f + (random.nextFloat() - 0.5f) * 0.28f
        val y = 0.5f + (random.nextFloat() - 0.5f) * 0.24f
        val dx = (x - 0.5f) / 0.15f
        val dy = (y - 0.42f) / 0.19f
        val dist = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
        list += DustParticle(
            x = x,
            y = y,
            r = 1.2f + random.nextFloat() * 1.8f,
            color = colors[random.nextInt(colors.size)],
            start = 0.45f + dist * 0.20f + random.nextFloat() * 0.05f,
            drift = 0.25f + random.nextFloat() * 0.55f,
            wobble = (random.nextFloat() - 0.5f) * 22f,
            phase = random.nextFloat() * 6.28f,
            flick = 4f + random.nextFloat() * 5f,
            maxA = 0.95f,
        )
    }
    // Oculis: pocos y tenues.
    repeat(24) {
        list += DustParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            r = 1f + random.nextFloat() * 1.1f,
            color = colors[random.nextInt(colors.size)],
            start = 0.12f + random.nextFloat() * 0.25f,
            drift = 0.20f + random.nextFloat() * 0.35f,
            wobble = (random.nextFloat() - 0.5f) * 18f,
            phase = random.nextFloat() * 6.28f,
            flick = 3f + random.nextFloat() * 4f,
            maxA = 0.6f,
        )
    }
    return list
}

@Composable
fun StartScreen(onFinished: () -> Unit = {}) {
    // RENDIMIENTO (spec 004): sin animaciones, la figura se muestra
    // estática (p = 0.5, visible del todo, sin moverse) y sigue a los
    // 600 ms, en vez de la animación de 3 s.
    val animationsEnabled = LocalAnimationsEnabled.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            progress.animateTo(1f, animationSpec = tween(3000, easing = LinearEasing))
        } else {
            progress.snapTo(0.5f)
            delay(600)
        }
        onFinished()
    }
    val p = progress.value
    // Viento SUAVE de derecha a izquierda: entra a los 2,5 s y barre todo.
    val wind = ((p - 0.83f) / 0.17f).coerceIn(0f, 1f)
    val particles = remember { buildParticles() }

    // MOVIMIENTO de verdad: entra volando desde la derecha
    // (0-0,8 s) y SUBE mientras se convierte en polvo (1,5-2,5 s).
    val travelX = when {
        p < 0.27f -> {
            val t = p / 0.27f
            140f * (1f - (1f - (1f - t) * (1f - t)))
        }
        p < 0.50f -> 0f
        else -> -20f * ((p - 0.50f) / 0.50f).coerceIn(0f, 1f)
    }
    val riseY = if (p < 0.50f) 0f
        else -40f * ((p - 0.50f) / 0.33f).coerceIn(0f, 1f)

    // La figura aparece volando (0-0,8 s), se sostiene y se
    // convierte en polvo SUAVE subiendo (1,5-2,5 s).
    val logoAlpha = when {
        p < 0.27f -> p / 0.27f
        p < 0.50f -> 1f
        else -> 1f - ((p - 0.50f) / 0.33f).coerceIn(0f, 1f)
    }
    val logoScale = 0.97f + 0.03f * (p / 0.27f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onFinished() },
    ) {
        // RONDA PULIDO (2026-11-09): la luz morada detrás de la
        // figura se quitó (el usuario la pidió fuera). La animación
        // (vuelo, polvo) queda igual.
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // SIN NOMBRE: solo la figura, que se convierte en polvo.
            // CAUSA DEL CIERRE (2026-11-09): painterResource NO puede
            // cargar un icono adaptativo (ic_launcher.xml): solo acepta
            // VectorDrawable o imágenes (PNG/JPG/WEBP). Se usa el PNG
            // del frente del icono (ic_launcher_foreground).
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "Aurora Player",
                modifier = Modifier.size(160.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                        translationX = travelX
                        translationY = riseY
                    },
            )
        }
        // Polvo y viento, por encima de la figura: el polvo sale de ella.
        Canvas(Modifier.fillMaxSize()) {
            // El viento estira el polvo suave en horizontal (se deshace).
            val stretch = 1f + wind * 2f
            for (pt in particles) {
                if (p < pt.start) continue
                val fadeIn = ((p - pt.start) / 0.12f).coerceIn(0f, 1f)
                // Parpadeo LENTO y suave de luciérnaga.
                val flicker = 0.75f + 0.25f *
                    sin(p * pt.flick * 6.28f + pt.phase)
                val alpha = (fadeIn * flicker * (1f - wind) * pt.maxA)
                    .coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                // Viento a la IZQUIERDA: x disminuye con wind.
                val x = pt.x * size.width - wind * pt.drift * size.width
                val y = pt.y * size.height +
                    sin((p * 4f + pt.x * 10f)) * pt.wobble * (1f - wind)
                // Polvo SUAVE en 3 capas, sin borde duro: halo amplio
                // muy tenue + halo medio + núcleo pequeño. Nada de
                // círculos sólidos (parecían rueditas).
                val r = pt.r * (0.7f + 0.3f * fadeIn)
                fun softDot(scale: Float, a: Float) {
                    drawOval(
                        color = pt.color,
                        topLeft = Offset(
                            x - r * scale * stretch,
                            y - r * scale,
                        ),
                        size = Size(r * scale * 2f * stretch, r * scale * 2f),
                        alpha = (alpha * a).coerceIn(0f, 1f),
                    )
                }
                softDot(3.5f, 0.07f)
                softDot(2.0f, 0.18f)
                softDot(1.0f, 0.85f)
            }
            // Ráfagas de viento, casi invisibles: líneas que cruzan
            // de derecha a izquierda.
            if (wind > 0f) {
                repeat(4) { i ->
                    val y = (i + 0.5f) / 4f * size.height + (i % 2) * 12f
                    val offset = (1.2f - (wind * 1.2f - 0.2f + i * 0.22f)) % 1.2f
                    val x = offset * size.width - 0.2f * size.width
                    drawLine(
                        color = Color(0xFF87CEEB),
                        start = Offset(x, y),
                        end = Offset(x - 160f, y),
                        strokeWidth = 1f,
                        // Aparecen con el viento y se apagan al final.
                        alpha = 0.06f * sin(wind * PI.toFloat()),
                    )
                }
            }
        }
    }
}
