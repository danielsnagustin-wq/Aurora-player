package com.aurora.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/**
 * Aurora Player — color dominante de la portada (spec 003, diseño premium).
 *
 * Sin dependencias nuevas (sin Palette): se muestrean los píxeles de la
 * portada (≤512 px) y se elige el tono más frecuente entre los píxeles
 * con color de verdad (saturados y ni muy oscuros ni casi blancos). El
 * color se devuelve ya con saturación y brillo mínimos para que sirva
 * de acento y se lea sobre negro. Sin portada o sin color útil, null:
 * el que llama usa el acento elegido por el usuario.
 *
 * El núcleo es puro (`dominantColorFromPixels`, IntArray ARGB) para
 * poder probarlo en unit tests sin Bitmap (stub en JVM).
 */
object CoverColors {

    /**
     * Color dominante de una lista de píxeles ARGB. null si no hay
     * ningún píxel con color aprovechable (todo gris/negro/blanco).
     */
    fun dominantColorFromPixels(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        // 12 cubos de tono (30° cada uno). Acumulamos peso = saturación
        // y la suma de RGB para promediar el color del cubo ganador.
        val buckets = Array(12) { Bucket() }
        val step = maxOf(1, pixels.size / 4096)
        var i = 0
        while (i < pixels.size) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            if (a < 128) {
                i += step
                continue // transparente: no es color de portada
            }
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val value = max / 255f
            val saturation = if (max == 0) 0f else delta / max.toFloat()
            // Sin color (gris) o demasiado oscuro/claro: no sirve de acento.
            if (saturation < 0.25f || value < 0.2f || value > 0.98f && saturation < 0.5f) {
                i += step
                continue
            }
            val hue = hueOf(r, g, b) // 0..360
            val bucket = ((hue / 30f).toInt()).coerceIn(0, 11)
            buckets[bucket].add(r, g, b, saturation)
            i += step
        }
        val best = buckets.withIndex()
            .maxByOrNull { (_, b) -> b.weight }
            ?.takeIf { (_, b) -> b.count > 0 }
            ?: return null
        val (r, g, b) = best.value.average()
        return boostedArgb(r, g, b)
    }

    /** Tono 0..360 a partir de RGB 0..255. */
    fun hueOf(r: Int, g: Int, b: Int): Float {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        if (delta == 0f) return 0f
        val hue = when (max) {
            rf -> 60f * (((gf - bf) / delta) % 6f)
            gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        return (hue + 360f) % 360f
    }

    /**
     * Sube la saturación y el brillo mínimos del color promediado, para
     * que el acento no salga apagado ni casi negro. Devuelve ARGB opaco.
     */
    fun boostedArgb(r: Int, g: Int, b: Int): Int {
        val max = maxOf(r, g, b).coerceAtLeast(1)
        val min = minOf(r, g, b)
        val sat = (max - min) / max.toFloat()
        val boost = if (sat < 0.55f) 1f + (0.55f - sat) else 1f
        var nr = (r * boost).toInt().coerceIn(0, 255)
        var ng = (g * boost).toInt().coerceIn(0, 255)
        var nb = (b * boost).toInt().coerceIn(0, 255)
        // Brillo mínimo: el canal mayor, al menos 150.
        val m2 = maxOf(nr, ng, nb)
        if (m2 < 150) {
            val lift = 150 - m2
            nr = (nr + lift).coerceIn(0, 255)
            ng = (ng + lift).coerceIn(0, 255)
            nb = (nb + lift).coerceIn(0, 255)
        }
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private class Bucket {
        var count = 0
        var weight = 0f
        private var sumR = 0L
        private var sumG = 0L
        private var sumB = 0L

        fun add(r: Int, g: Int, b: Int, saturation: Float) {
            count++
            weight += saturation
            sumR += r
            sumG += g
            sumB += b
        }

        fun average(): Triple<Int, Int, Int> = Triple(
            (sumR / count).toInt(),
            (sumG / count).toInt(),
            (sumB / count).toInt(),
        )
    }
}

/** Color dominante de un Bitmap de portada, como Color de Compose. */
fun dominantColor(bitmap: Bitmap): Color? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    return try {
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        CoverColors.dominantColorFromPixels(pixels)?.let { Color(it) }
    } catch (_: Exception) {
        null
    }
}

/** Aclara u oscurece un color multiplicando sus canales (0..1 = oscuro). */
fun Color.scaled(factor: Float): Color = copy(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
)
