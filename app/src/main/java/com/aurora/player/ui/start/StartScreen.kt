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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.player.R
import kotlin.math.sin
import kotlin.random.Random

/**
 * Aurora Player — inicio propio con animación (~5 s).
 * Lógica de Child of Light: Aurora se disuelve en polvo luminoso
 * (los oculis: chispas de luz) y un viento que sopla de izquierda
 * a derecha los arrastra y los borra hasta dejar la pantalla
 * limpia. Tocar la pantalla salta la animación.
 *
 * Fases: 0-1 s aparece Aurora con su brillo; 1-2,75 s los oculis
 * flotan; 2,75-3,75 s la figura se disuelve en polvo; 3,5-5 s el
 * viento barre el polvo de izquierda a derecha y todo se apaga.
 * Al terminar, onFinished abre la biblioteca.
 */
private data class DustParticle(
    val x: Float, // 0..1 relativo al ancho
    val y: Float, // 0..1 relativo al alto
    val r: Float, // radio base en px
    val color: Color,
    val start: Float, // progreso en el que aparece
    val drift: Float, // cuánto lo arrastra el viento (fracción del ancho)
    val wobble: Float, // vaivén vertical en px
)

private fun buildParticles(): List<DustParticle> {
    val random = Random(42)
    val colors = listOf(
        Color(0xFF8CCBEB), // hielo
        Color(0xFFA99ACD), // lavanda
        Color(0xFF75D6CE), // turquesa
        Color(0xFFDCE8F5), // plata
        Color(0xFF7FD4FF), // llama azul de Igniculus
    )
    val list = ArrayList<DustParticle>()
    // Polvo de la figura: concentrado donde está el logo (centro).
    repeat(140) {
        list += DustParticle(
            x = 0.5f + (random.nextFloat() - 0.5f) * 0.28f,
            y = 0.5f + (random.nextFloat() - 0.5f) * 0.24f,
            r = 2f + random.nextFloat() * 5f,
            color = colors[random.nextInt(colors.size)],
            start = 0.45f + random.nextFloat() * 0.2f,
            drift = 0.25f + random.nextFloat() * 0.55f,
            wobble = (random.nextFloat() - 0.5f) * 30f,
        )
    }
    // Oculis flotando por toda la pantalla (aparecen antes).
    repeat(24) {
        list += DustParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            r = 1.5f + random.nextFloat() * 2.5f,
            color = colors[random.nextInt(colors.size)],
            start = 0.15f + random.nextFloat() * 0.3f,
            drift = 0.2f + random.nextFloat() * 0.4f,
            wobble = (random.nextFloat() - 0.5f) * 20f,
        )
    }
    return list
}

@Composable
fun StartScreen(onFinished: () -> Unit = {}) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(5000, easing = LinearEasing))
        onFinished()
    }
    val p = progress.value
    // Viento de izquierda a derecha: entra a los 3,5 s y barre todo.
    val wind = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
    val particles = remember { buildParticles() }

    // La figura aparece (0-0,75 s), se sostiene y se disuelve (2,5-3,25 s).
    val logoAlpha = when {
        p < 0.15f -> p / 0.15f
        p < 0.5f -> 1f
        else -> 1f - ((p - 0.5f) / 0.15f).coerceIn(0f, 1f)
    }
    val logoScale = 0.96f + 0.04f * (p / 0.2f).coerceIn(0f, 1f)
    // Los textos aparecen con la figura y el viento se los lleva.
    val textAlpha = ((p - 0.1f) / 0.15f).coerceIn(0f, 1f) *
        (1f - ((p - 0.7f) / 0.2f).coerceIn(0f, 1f))
    val glowAlpha = 0.5f * logoAlpha

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onFinished() },
    ) {
        // Brillo detrás de la figura, que se apaga con ella.
        Box(
            modifier = Modifier.fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8CCBEB),
                            Color(0xFFA99ACD),
                            Color.Transparent,
                        ),
                    ),
                )
                .graphicsLayer { alpha = glowAlpha },
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "Aurora Player",
                modifier = Modifier.size(160.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                    },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Aurora Player",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "El amor por la música no tiene que tener precio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
        }
        // Polvo y viento, por encima de la figura: el polvo sale de ella.
        Canvas(Modifier.fillMaxSize()) {
            for (pt in particles) {
                if (p < pt.start) continue
                val fadeIn = ((p - pt.start) / 0.08f).coerceIn(0f, 1f)
                val alpha = (fadeIn * (1f - wind)).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                val x = pt.x * size.width + wind * pt.drift * size.width
                val y = pt.y * size.height +
                    sin((p * 6f + pt.x * 10f)) * pt.wobble * (1f - wind)
                drawCircle(
                    color = pt.color,
                    radius = pt.r * (0.6f + 0.4f * fadeIn),
                    center = Offset(x, y),
                    alpha = alpha,
                )
            }
            // Ráfagas de viento: líneas que cruzan de izquierda a derecha.
            if (wind > 0f) {
                repeat(8) { i ->
                    val y = (i + 0.5f) / 8f * size.height + (i % 3) * 12f
                    val offset = (wind * 1.4f - 0.2f + i * 0.13f) % 1.2f
                    val x = offset * size.width - 0.2f * size.width
                    drawLine(
                        color = Color(0xFFDCE8F5),
                        start = Offset(x, y),
                        end = Offset(x + 140f, y),
                        strokeWidth = 2f,
                        alpha = 0.18f * wind * (1f - wind * 0.6f),
                    )
                }
            }
        }
    }
}
