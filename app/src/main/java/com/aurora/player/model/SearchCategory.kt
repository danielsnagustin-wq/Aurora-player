package com.aurora.player.model

import java.text.Normalizer

/**
 * Aurora Player — categorías de búsqueda (SPEC 005, FR-005).
 *
 * Como Poweramp: la búsqueda tiene un ámbito. "Todo" busca en
 * título, artista, álbum y género; las demás solo en su campo.
 * El género en blanco se busca como "Sin género", igual que en la
 * pestaña Géneros. Todo puro y testeable sin Android.
 *
 * Ronda 23: la búsqueda no distingue mayúsculas NI acentos —
 * "cancion" encuentra "Canción", "jose" encuentra "José".
 * Se normaliza con NFD y se quitan las marcas diacríticas de
 * ambos lados antes de comparar.
 */
enum class SearchCategory(val label: String) {
    ALL("Todo"),
    SONGS("Canciones"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    GENRES("Géneros"),
}

private fun foldAccents(s: String): String =
    // RONDA 169: hay letras que NFD no descompone (no tienen marca
    // que quitar): ł, ø, đ, ß, æ, œ. Sin este mapa, buscar "lodz"
    // no encontraba "Łódź" y "strasse" no encontraba "Straße".
    Normalizer.normalize(
        s.replace("ß", "ss").replace("Æ", "ae").replace("æ", "ae")
            .replace("Œ", "oe").replace("œ", "oe")
            .replace('ł', 'l').replace('Ł', 'l')
            .replace('ø', 'o').replace('Ø', 'o')
            .replace('đ', 'd').replace('Đ', 'd'),
        Normalizer.Form.NFD,
    )
        // RONDA 145: era solo el bloque InCombiningDiacriticalMarks
        // (U+0300-U+036F): las tildes de bloques extendidos (p. ej.
        // U+1AB0) no se quitaban y la búsqueda fallaba. \p{Mn} quita
        // TODAS las marcas sin espaciado, en cualquier bloque.
        .replace(Regex("\\p{Mn}+"), "")

private fun containsFolded(haystack: String, needle: String): Boolean =
    foldAccents(haystack).contains(foldAccents(needle), ignoreCase = true)

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
        SearchCategory.ALL -> containsFolded(title, q) ||
            containsFolded(artist, q) ||
            containsFolded(album, q) ||
            containsFolded(genreName, q)
        SearchCategory.SONGS -> containsFolded(title, q)
        SearchCategory.ARTISTS -> containsFolded(artist, q)
        SearchCategory.ALBUMS -> containsFolded(album, q)
        SearchCategory.GENRES -> containsFolded(genreName, q)
    }
}
