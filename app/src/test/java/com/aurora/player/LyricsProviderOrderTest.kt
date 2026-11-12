package com.aurora.player

import com.aurora.player.lyrics.DEFAULT_PROVIDER_ORDER
import com.aurora.player.lyrics.moveProvider
import com.aurora.player.lyrics.orderedProviders
import com.aurora.player.lyrics.parseProviderOrder
import com.aurora.player.lyrics.providerOrderToCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orden de proveedores de letras (Fase 2): parseo, guardado y
 * reordenación. Puro, sin Android, para probarlo en la JVM.
 *
 * Desde 2026-11-12 el orden por defecto tiene OCHO proveedores
 * (LRCLIB, BetterLyrics, BiniLyrics, LyricsOvh, QQMusic, Unison,
 * LiriQo, Kugou) y parseProviderOrder añade al final los que falten
 * en el CSV guardado, así que ningún proveedor nuevo se pierde.
 */
class LyricsProviderOrderTest {

    private val defaultOrder = listOf(
        "LRCLIB", "BetterLyrics", "BiniLyrics", "LyricsOvh",
        "QQMusic", "Unison", "LiriQo", "Kugou",
    )

    @Test
    fun `por defecto los ocho en orden`() {
        assertEquals(defaultOrder, parseProviderOrder(null))
        assertEquals(defaultOrder, parseProviderOrder(""))
    }

    @Test
    fun `respeta el orden guardado`() {
        assertEquals(
            listOf(
                "BiniLyrics", "LRCLIB", "BetterLyrics", "LyricsOvh",
                "QQMusic", "Unison", "LiriQo", "Kugou",
            ),
            parseProviderOrder("BiniLyrics,LRCLIB,BetterLyrics"),
        )
    }

    @Test
    fun `descarta desconocidos y duplicados y anade los que faltan`() {
        assertEquals(
            listOf(
                "BetterLyrics", "LRCLIB", "BiniLyrics", "LyricsOvh",
                "QQMusic", "Unison", "LiriQo", "Kugou",
            ),
            parseProviderOrder("BetterLyrics,Inventado,BetterLyrics"),
        )
    }

    @Test
    fun `csv redondo`() {
        assertEquals(
            "BiniLyrics,LRCLIB,BetterLyrics,LyricsOvh,QQMusic,Unison,LiriQo,Kugou",
            providerOrderToCsv(listOf("BiniLyrics", "LRCLIB", "BetterLyrics")),
        )
    }

    @Test
    fun `mover arriba y abajo`() {
        val order = defaultOrder
        assertEquals(
            listOf(
                "BetterLyrics", "LRCLIB", "BiniLyrics", "LyricsOvh",
                "QQMusic", "Unison", "LiriQo", "Kugou",
            ),
            moveProvider(order, "BetterLyrics", true),
        )
        assertEquals(
            listOf(
                "LRCLIB", "BiniLyrics", "BetterLyrics", "LyricsOvh",
                "QQMusic", "Unison", "LiriQo", "Kugou",
            ),
            moveProvider(order, "BetterLyrics", false),
        )
        // Los extremos no se mueven.
        assertEquals(order, moveProvider(order, "LRCLIB", true))
        assertEquals(order, moveProvider(order, "Kugou", false))
        assertEquals(order, moveProvider(order, "Inventado", true))
    }

    @Test
    fun `orderedProviders devuelve instancias en orden`() {
        val providers = orderedProviders("BiniLyrics,LRCLIB,BetterLyrics")
        assertEquals(
            listOf(
                "BiniLyrics", "LRCLIB", "BetterLyrics", "LyricsOvh",
                "QQMusic", "Unison", "LiriQo", "Kugou",
            ),
            providers.map { it.name },
        )
        assertTrue(orderedProviders("Inventado").map { it.name } == DEFAULT_PROVIDER_ORDER)
    }
}
