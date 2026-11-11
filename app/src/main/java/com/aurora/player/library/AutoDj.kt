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
    // Ronda 17: un artista/género/álbum VACÍO no es una coincidencia:
    // sin esta guarda, una canción sin metadatos "coincidía" con
    // todas las demás sin metadatos y la radio sonaba a ciegas.
    val byArtist = current?.let { c ->
        if (c.artist.isBlank()) emptyList() else pool.filter { it.artist == c.artist }
    }.orEmpty()
    if (byArtist.isNotEmpty()) return byArtist.random(random)
    val byGenre = current?.genre?.takeIf { it.isNotBlank() }?.let { g ->
        pool.filter { it.genre == g }
    }.orEmpty()
    if (byGenre.isNotEmpty()) return byGenre.random(random)
    val byAlbum = current?.let { c ->
        if (c.album.isBlank()) emptyList() else pool.filter { it.album == c.album }
    }.orEmpty()
    if (byAlbum.isNotEmpty()) return byAlbum.random(random)
    val mostPlayed = pool.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
    if (mostPlayed.isNotEmpty()) return mostPlayed.first()
    return pool.random(random)
}

/**
 * Auto-DJ en tanda: encadena [autoDjNext] hasta [count] canciones.
 * Cada canción elegida pasa a ser la "actual" de la siguiente
 * elección (la tanda suena coherente: de una parecida a la
 * siguiente parecida) y se añade a los excluidos, así la tanda
 * nunca repite. Si el pool se agota, la tanda sale más corta.
 */
fun autoDjQueue(
    current: Song?,
    songs: List<Song>,
    count: Int = 10,
    recentlyPlayedIds: Set<Long> = emptySet(),
    random: Random = Random.Default,
): List<Song> {
    val picked = mutableListOf<Song>()
    val excluded = recentlyPlayedIds.toMutableSet()
    var cursor = current
    repeat(count) {
        val next = autoDjNext(cursor, songs, excluded, random) ?: return picked
        picked += next
        excluded += next.id
        cursor = next
    }
    return picked
}
