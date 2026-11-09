package com.aurora.player.model

/**
 * Aurora Player — categorías de búsqueda (SPEC 005, FR-005).
 *
 * Como Poweramp: la búsqueda tiene un ámbito. "Todo" busca en
 * título, artista, álbum y género; las demás solo en su campo.
 * El género en blanco se busca como "Sin género", igual que en la
 * pestaña Géneros. Todo puro y testeable sin Android.
 */
enum class SearchCategory(val label: String) {
    ALL("Todo"),
    SONGS("Canciones"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    GENRES("Géneros"),
}

fun matchesSearchQuery(
    title: String,
    artist: String,
    album: String,
    genre: String?,
    q: String,
    category: SearchCategory,
): Boolean {
    if (q.isBlank()) return true
    val genreName = genre?.takeIf { it.isNotBlank() } ?: "Sin género"
    return when (category) {
        SearchCategory.ALL -> title.contains(q, ignoreCase = true) ||
            artist.contains(q, ignoreCase = true) ||
            album.contains(q, ignoreCase = true) ||
            genreName.contains(q, ignoreCase = true)
        SearchCategory.SONGS -> title.contains(q, ignoreCase = true)
        SearchCategory.ARTISTS -> artist.contains(q, ignoreCase = true)
        SearchCategory.ALBUMS -> album.contains(q, ignoreCase = true)
        SearchCategory.GENRES -> genreName.contains(q, ignoreCase = true)
    }
}
