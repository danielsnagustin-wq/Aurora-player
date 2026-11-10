package com.aurora.player

import com.aurora.player.online.GoogleAccountAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAccountAuthTest {
    @Test
    fun bearerHeaderUsesAuthorization() {
        val headers = GoogleAccountAuth.buildBearerHeaders("abc123")
        assertEquals("Bearer abc123", headers["Authorization"])
        assertEquals(1, headers.size)
    }

    @Test
    fun bearerHeaderTrimsToken() {
        val headers = GoogleAccountAuth.buildBearerHeaders("  abc123  ")
        assertEquals("Bearer abc123", headers["Authorization"])
    }

    @Test
    fun youtubeScopeIsOAuth2Youtube() {
        assertTrue(GoogleAccountAuth.YOUTUBE_SCOPE.startsWith("oauth2:"))
        assertTrue(GoogleAccountAuth.YOUTUBE_SCOPE.contains("youtube"))
    }
}
