package com.aurora.player.util

/**
 * Aurora Player — conversiones de color puras (SPEC 007, 2026-11-09).
 * El editor de personalización trabaja en HEX, RGB y HSL: estas
 * funciones son Kotlin puro, testeables sin Android.
 */
fun hexToRgb(hex: String): Triple<Int, Int, Int>? {
    val clean = hex.trim().removePrefix("#")
    val six = when (clean.length) {
        3 -> clean.map { "$it$it" }.joinToString("")
        6 -> clean
        else -> return null
    }
    return try {
        Triple(
            six.substring(0, 2).toInt(16),
            six.substring(2, 4).toInt(16),
            six.substring(4, 6).toInt(16),
        )
    } catch (_: NumberFormatException) {
        null
    }
}

fun rgbToHex(r: Int, g: Int, b: Int): String =
    "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

/** RGB (0..255) -> HSL: h en 0..360, s y l en 0..1. */
fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        rf -> ((gf - bf) / d + if (gf < bf) 6f else 0f) * 60f
        gf -> ((bf - rf) / d + 2f) * 60f
        else -> ((rf - gf) / d + 4f) * 60f
    }
    return Triple(h, s, l)
}

/** HSL (h 0..360, s/l 0..1) -> RGB (0..255). */
fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    if (sat == 0f) {
        val v = (light * 255f).toInt()
        return Triple(v, v, v)
    }
    val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
    val p = 2f * light - q
    fun channel(t: Float): Int {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        val c = when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
        return (c * 255f).toInt().coerceIn(0, 255)
    }
    return Triple(
        channel(hue / 360f + 1f / 3f),
        channel(hue / 360f),
        channel(hue / 360f - 1f / 3f),
    )
}
