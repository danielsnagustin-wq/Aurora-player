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
 */
class LyricsProviderOrderTest {

    @Test
    fun `por defecto los tres en orden`() {
        assertEquals(listOf("LRCLIB", "BetterLyrics", "BiniLyrics"), parseProviderOrder(null))
        assertEquals(listOf("LRCLIB", "BetterLyrics", "BiniLyrics"), parseProviderOrder(""))
    }

    @Test
    fun `respeta el orden guardado`() {
        assertEquals(
            listOf("BiniLyrics", "LRCLIB", "BetterLyrics"),
            parseProviderOrder("BiniLyrics,LRCLIB,BetterLyrics"),
        )
    }

    @Test
    fun `descarta desconocidos y duplicados y anade los que faltan`() {
        assertEquals(
            listOf("BetterLyrics", "LRCLIB", "BiniLyrics"),
            parseProviderOrder("BetterLyrics,Inventado,BetterLyrics"),
        )
    }

    @Test
    fun `csv redondo`() {
        assertEquals(
            "BiniLyrics,LRCLIB,BetterLyrics",
            providerOrderToCsv(listOf("BiniLyrics", "LRCLIB", "BetterLyrics")),
        )
    }

    @Test
    fun `mover arriba y abajo`() {
        val order = listOf("LRCLIB", "BetterLyrics", "BiniLyrics")
        assertEquals(listOf("BetterLyrics", "LRCLIB", "BiniLyrics"), moveProvider(order, "BetterLyrics", true))
        assertEquals(listOf("LRCLIB", "BiniLyrics", "BetterLyrics"), moveProvider(order, "BetterLyrics", false))
        // Los extremos no se mueven.
        assertEquals(order, moveProvider(order, "LRCLIB", true))
        assertEquals(order, moveProvider(order, "BiniLyrics", false))
        assertEquals(order, moveProvider(order, "Inventado", true))
    }

    @Test
    fun `orderedProviders devuelve instancias en orden`() {
        val providers = orderedProviders("BiniLyrics,LRCLIB,BetterLyrics")
        assertEquals(listOf("BiniLyrics", "LRCLIB", "BetterLyrics"), providers.map { it.name })
        assertTrue(orderedProviders("Inventado").map { it.name } == DEFAULT_PROVIDER_ORDER)
    }
}
