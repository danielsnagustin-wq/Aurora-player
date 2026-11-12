package com.aurora.player

import com.aurora.player.model.SearchCategory
import com.aurora.player.model.matchesSearchQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aurora Player — tests de las categorías de búsqueda (spec 005, FR-005). */
class SearchCategoryTest {

    @Test
    fun `todo busca en titulo artista album y genero`() {
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "luna", SearchCategory.ALL))
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "mar", SearchCategory.ALL))
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "rock", SearchCategory.ALL))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "jazz", SearchCategory.ALL))
    }

    @Test
    fun `canciones solo mira el titulo`() {
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "sol", SearchCategory.SONGS))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "luna", SearchCategory.SONGS))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "mar", SearchCategory.SONGS))
    }

    @Test
    fun `artistas solo mira el artista`() {
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "luna", SearchCategory.ARTISTS))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "sol", SearchCategory.ARTISTS))
    }

    @Test
    fun `albumes solo mira el album`() {
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "mar", SearchCategory.ALBUMS))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "sol", SearchCategory.ALBUMS))
    }

    @Test
    fun `generos solo mira el genero y sin genero cuenta`() {
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "rock", SearchCategory.GENRES))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "sol", SearchCategory.GENRES))
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", null, "sin género", SearchCategory.GENRES))
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "", "sin género", SearchCategory.GENRES))
        assertFalse(matchesSearchQuery("Sol", "Luna", "Mar", null, "rock", SearchCategory.GENRES))
    }

    @Test
    fun `busqueda vacia coincide con todo y no distingue mayusculas`() {
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "", SearchCategory.SONGS))
        assertTrue(matchesSearchQuery("Sol", "Luna", "Mar", "Rock", "SOL", SearchCategory.SONGS))
    }

    @Test
    fun `la busqueda no distingue acentos`() {
        // Ronda 23: "cancion" encuentra "Canción" en cualquier campo.
        assertTrue(matchesSearchQuery("Canción", "José", "Música", "Pop", "cancion", SearchCategory.SONGS))
        assertTrue(matchesSearchQuery("Canción", "José", "Música", "Pop", "jose", SearchCategory.ARTISTS))
        assertTrue(matchesSearchQuery("Canción", "José", "Música", "Pop", "musica", SearchCategory.ALBUMS))
        assertTrue(matchesSearchQuery("Canción", "José", "Música", "Pop", "cancion", SearchCategory.ALL))
        // Y al revés: la consulta con acento encuentra el texto sin él.
        assertTrue(matchesSearchQuery("Cancion", "Jose", "Musica", "Pop", "canción", SearchCategory.SONGS))
        // Lo que no coincide sigue sin coincidir.
        assertFalse(matchesSearchQuery("Canción", "José", "Música", "Pop", "cantar", SearchCategory.SONGS))
    }

    @Test
    fun `la busqueda pliega letras que NFD no descompone`() {
        // RONDA 169: ł, ø, đ, ß, æ y œ no se descomponen en NFD y
        // el plegado de acentos las dejaba distintas.
        assertTrue(matchesSearchQuery("Łódź", "X", "Y", "Pop", "lodz", SearchCategory.SONGS))
        assertTrue(matchesSearchQuery("Straße", "X", "Y", "Pop", "strasse", SearchCategory.SONGS))
        assertTrue(matchesSearchQuery("Søren", "X", "Y", "Pop", "soren", SearchCategory.SONGS))
        assertTrue(matchesSearchQuery("Đorđe", "X", "Y", "Pop", "dorde", SearchCategory.SONGS))
        assertTrue(matchesSearchQuery("Æsop", "X", "Y", "Pop", "aesop", SearchCategory.SONGS))
        assertFalse(matchesSearchQuery("Łódź", "X", "Y", "Pop", "varsovia", SearchCategory.SONGS))
    }
}
