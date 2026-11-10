package com.aurora.player.library

import com.aurora.player.model.Song
import kotlin.random.Random

/**
 * Auto-DJ / Automix (Fase 2 app): elige la siguiente canción cuando
 * se acaba la cola, con canciones PARECIDAS a la que suena ahora.
 * Orden de preferencia, todo con datos reales de Song:
 * 1) mismo artista, 2) mismo género, 3) mismo álbum,
 * 4) las más escuchadas de la biblioteca.
 * Nunca repite la canción actual ni las ya sonadas en esta sesión
 * (recentlyPlayedIds). Con Random inyectado la elección es
 * comprobable en tests; en la app se usa un Random normal.
 * Si no queda nada elegible, devuelve null (se para, no se inventa).
 */
fun autoDjNext(
    current: Song?,
    songs: List<Song>,
    recentlyPlayedIds: Set<Long> = emptySet(),
    random: Random = Random.Default,
): Song? {
    val pool = songs.filter { it.id != current?.id && it.id !in recentlyPlayedIds }
    if (pool.isEmpty()) return null
    val byArtist = current?.let { c -> pool.filter { it.artist == c.artist } }.orEmpty()
    if (byArtist.isNotEmpty()) return byArtist.random(random)
    val byGenre = current?.genre?.let { g -> pool.filter { it.genre == g } }.orEmpty()
    if (byGenre.isNotEmpty()) return byGenre.random(random)
    val byAlbum = current?.let { c -> pool.filter { it.album == c.album } }.orEmpty()
    if (byAlbum.isNotEmpty()) return byAlbum.random(random)
    val mostPlayed = pool.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
    if (mostPlayed.isNotEmpty()) return mostPlayed.first()
    return pool.random(random)
}
