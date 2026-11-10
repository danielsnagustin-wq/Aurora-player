package com.aurora.player.playback

/**
 * Fase 1b — crossfade de 2 reproductores: ¿se solapa la pista siguiente?
 * Funciones puras y testeables; no tocan reproductores. El monitor de
 * posición de PlaybackService decide CUÁNDO (quedan ≤ ventana ms);
 * esto decide SI (ajuste, cola, repetición, álbum).
 */
fun shouldOverlapCrossfade(
    crossfadeSeconds: Int,
    hasNext: Boolean,
    repeatOne: Boolean,
    currentAlbum: String?,
    nextAlbum: String?,
    gaplessAlbumException: Boolean,
): Boolean {
    if (crossfadeSeconds <= 0) return false
    if (!hasNext) return false
    if (repeatOne) return false
    if (gaplessAlbumException) {
        val current = currentAlbum?.trim().orEmpty()
        val next = nextAlbum?.trim().orEmpty()
        if (current.isNotEmpty() && current == next) return false
    }
    return true
}

/**
 * Ventana real de solape: el ajuste (1..12 s) recortado a la duración
 * de la pista siguiente, para no solapar más de lo que dura.
 */
fun overlapWindowMs(crossfadeSeconds: Int, nextDurationMs: Long): Long {
    val windowMs = crossfadeSeconds.coerceIn(1, 12) * 1000L
    return if (nextDurationMs > 0) minOf(windowMs, nextDurationMs) else windowMs
}
