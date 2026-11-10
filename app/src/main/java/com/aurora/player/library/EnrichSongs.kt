package com.aurora.player.library

import com.aurora.player.data.SongStatsRow
import com.aurora.player.model.Song

/**
 * Canciones con sus datos reales de uso: favorito, nº de
 * reproducciones y última escucha (de Room). Es lo que alimentan
 * las listas inteligentes y el Auto-DJ: sin esto, "Más escuchadas"
 * no sabría nada. Sin estadísticas, la canción cuenta como 0
 * escuchas y sin última escucha. (LibraryViewModel.enrichedSongs
 * hace lo mismo en línea; unificar cuando la Serie A esté en verde.)
 */
fun enrichSongs(
    songs: List<Song>,
    favoriteIds: Set<Long>,
    statsById: Map<Long, SongStatsRow>,
): List<Song> = songs.map { song ->
    val row = statsById[song.id]
    song.copy(
        isFavorite = song.id in favoriteIds,
        playCount = row?.plays ?: 0,
        lastPlayedAt = row?.lastPlayed,
    )
}
