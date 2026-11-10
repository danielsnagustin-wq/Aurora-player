package com.aurora.player.lyrics

/**
 * Precarga de letras (idea de BitChord, 2026-11-09): mientras suena
 * una canción, la letra de la siguiente se busca en segundo plano
 * para que ya esté en la caché antes de que empiece a sonar.
 *
 * Devuelve el índice de la siguiente canción, o null si no hay
 * siguiente: fin de la cola sin repetir, o una cola de una sola
 * canción que se repite a sí misma (precargarse a sí misma no
 * tiene sentido).
 */
fun nextIndexToPrefetch(currentIndex: Int, count: Int, repeatAll: Boolean): Int? {
    if (count <= 0 || currentIndex < 0 || currentIndex >= count) return null
    val next = currentIndex + 1
    if (next < count) return next
    if (repeatAll && count > 1) return 0
    return null
}
