package com.aurora.player

import com.aurora.player.online.YouTubeMusicAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YouTube Music — firma SAPISIDHASH y lectura de cookies (2026-11-10).
 * Lógica pura sin Android: la firma es SHA-1 de "<segundos> <sapisid>
 * https://music.youtube.com", con el vector precalculado abajo.
 */
class YouTubeMusicAuthTest {

    @Test
    fun `SHA-1 conocido`() {
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            YouTubeMusicAuth.sha1Hex("abc"),
        )
    }

    @Test
    fun `SAPISIDHASH con segundos fijos da el vector precalculado`() {
        val hash = YouTubeMusicAuth.buildSapisidHash(
            sapisid = "test-sapisid-123",
            timestampSec = 1750000000L,
        )
        assertEquals(
            "SAPISIDHASH 1750000000_a430908cd347edf854e14eba6d7380dab14a18c3",
            hash,
        )
    }

    @Test
    fun `SAPISIDHASH vacio da null`() {
        assertNull(YouTubeMusicAuth.buildSapisidHash(""))
        assertNull(YouTubeMusicAuth.buildSapisidHash("   "))
    }

    @Test
    fun `extractCookie encuentra SAPISID entre varias cookies`() {
        val cookies = "SID=aaa; SAPISID=test-sapisid-123; HSID=bbb"
        assertEquals("test-sapisid-123", YouTubeMusicAuth.extractCookie(cookies, "SAPISID"))
        assertEquals("aaa", YouTubeMusicAuth.extractCookie(cookies, "SID"))
        assertNull(YouTubeMusicAuth.extractCookie(cookies, "NOPE"))
        assertNull(YouTubeMusicAuth.extractCookie("", "SAPISID"))
    }

    @Test
    fun `buildAuthHeaders sin SAPISID da null, con SAPISID trae las tres`() {
        assertNull(YouTubeMusicAuth.buildAuthHeaders("SID=aaa; HSID=bbb"))
        val headers = YouTubeMusicAuth.buildAuthHeaders("SID=aaa; SAPISID=test-sapisid-123")
        assertTrue(headers != null)
        assertTrue(headers!!.containsKey("Cookie"))
        assertTrue(headers["Authorization"]!!.startsWith("SAPISIDHASH "))
        assertEquals("https://music.youtube.com", headers["X-Origin"])
    }
}
