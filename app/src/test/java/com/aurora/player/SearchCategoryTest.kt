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
}
