package com.aurora.player.ui.stats

import com.aurora.player.data.PlayHistoryEntity
import com.aurora.player.model.Song
import java.util.Calendar

/**
 * RONDA 7 P3: Tu año — lógica pura (sin Android), testeable.
 * Todo sale del historial real (play_history, 30 s por escucha): nada
 * inventado, nada en la nube. El año se delimita por playedAt en
 * [1 de enero 00:00, 1 de enero del año siguiente).
 */
data class YearStats(
    val year: Int,
    val totalPlays: Int,
    val totalListenedMs: Long,
    val topSongs: List<Pair<Song, Int>>,
    val topArtists: List<Pair<String, Int>>,
    val topGenres: List<Pair<String, Int>>,
    val distinctDays: Int,
    val firstPlayedAt: Long?,
    val lastPlayedAt: Long?,
    /** Top 10 canciones del año (que sigan en la biblioteca): para la playlist. */
    val topSongIds: List<Long>,
)

/** Inicio y fin (excluido) de un año, en hora local del teléfono. */
fun yearRange(year: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.set(year + 1, Calendar.JANUARY, 1, 0, 0, 0)
    val end = cal.timeInMillis
    return start to end
}

/**
 * Resume un año a partir de su historial (ya filtrado a escuchas de
 * 30 s o más) y de las canciones que siguen en la biblioteca.
 * Las canciones borradas del teléfono no aparecen en los tops ni en
 * la playlist, pero sus reproducciones sí cuentan en los totales.
 */
fun computeYearStats(
    year: Int,
    history: List<PlayHistoryEntity>,
    songsById: Map<Long, Song>,
): YearStats {
    val dayMs = 24L * 60 * 60 * 1000
    val bySong = history.groupingBy { it.songId }.eachCount()
    val topSongs = bySong.entries
        .sortedByDescending { it.value }
        .take(5)
        .mapNotNull { (id, count) -> songsById[id]?.let { it to count } }
    val artistCounts = mutableMapOf<String, Int>()
    val genreCounts = mutableMapOf<String, Int>()
    for ((songId, count) in bySong) {
        val song = songsById[songId] ?: continue
        artistCounts[song.artist] = (artistCounts[song.artist] ?: 0) + count
        val genre = song.genre?.takeIf { it.isNotBlank() } ?: "Sin género"
        genreCounts[genre] = (genreCounts[genre] ?: 0) + count
    }
    return YearStats(
        year = year,
        totalPlays = history.size,
        totalListenedMs = history.sumOf { it.durationListenedMs },
        topSongs = topSongs,
        topArtists = artistCounts.entries.sortedByDescending { it.value }.take(5)
            .map { it.key to it.value },
        topGenres = genreCounts.entries.sortedByDescending { it.value }.take(3)
            .map { it.key to it.value },
        distinctDays = history.map { it.playedAt / dayMs }.toSet().size,
        firstPlayedAt = history.minOfOrNull { it.playedAt },
        lastPlayedAt = history.maxOfOrNull { it.playedAt },
        topSongIds = bySong.entries
            .sortedByDescending { it.value }
            .filter { songsById.containsKey(it.key) }
            .take(10)
            .map { it.key },
    )
}
