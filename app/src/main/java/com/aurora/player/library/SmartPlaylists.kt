package com.aurora.player.library

import com.aurora.player.model.Song

/**
 * Listas inteligentes (Fase 2 app): listas que se arman solas a
 * partir de datos REALES de la biblioteca (reproducciones,
 * favoritos, fecha de alta, última escucha). No hay lista que
 * necesite un dato que Song no tenga: la de "más tiempo escuchado"
 * no existe aquí porque el tiempo escuchado vive en SongStatsRow,
 * no en Song (se haría aparte, nunca con un número inventado).
 * Función pura: misma entrada, misma salida; el límite recorta.
 */
enum class SmartPlaylistKind(val title: String) {
    MOST_PLAYED("Más escuchadas"),
    FAVORITES("Favoritas"),
    RECENTLY_ADDED("Añadidas recientemente"),
    NEVER_PLAYED("Sin escuchar"),
    RECENTLY_PLAYED("Escuchadas recientemente"),
}

fun smartPlaylistSongs(
    kind: SmartPlaylistKind,
    songs: List<Song>,
    limit: Int = 50,
): List<Song> {
    val filtered = when (kind) {
        SmartPlaylistKind.MOST_PLAYED ->
            songs.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
        SmartPlaylistKind.FAVORITES ->
            songs.filter { it.isFavorite }
        SmartPlaylistKind.RECENTLY_ADDED ->
            songs.sortedByDescending { it.dateAdded }
        SmartPlaylistKind.NEVER_PLAYED ->
            songs.filter { it.playCount == 0 }
        SmartPlaylistKind.RECENTLY_PLAYED ->
            songs.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
    }
    return filtered.take(limit.coerceAtLeast(0))
}
