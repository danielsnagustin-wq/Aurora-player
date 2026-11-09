package com.aurora.player

import com.aurora.player.model.normalizeQueueName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Aurora Player — tests de colas guardadas (spec 005, FR-007). */
class NamedQueueTest {

    @Test
    fun `nombre se limpia y se recorta`() {
        assertEquals("Mi cola", normalizeQueueName("  Mi cola  "))
        assertEquals("a".repeat(40), normalizeQueueName("a".repeat(50)))
    }

    @Test
    fun `nombre vacio es nulo`() {
        assertNull(normalizeQueueName(""))
        assertNull(normalizeQueueName("   "))
    }
}
