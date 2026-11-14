package com.aurora.player

import com.aurora.player.online.JsonParseException
import com.aurora.player.online.JsonParser
import com.aurora.player.online.JsonValue
import com.aurora.player.online.long
import com.aurora.player.online.obj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * RONDA 2 (2026-11-13, P2): tests del parser JSON propio. El
 * parseNumber ya es estricto desde la caza (regex de gramática +
 * finito), pero no tenía test que lo fijara: "1.2.3" o "1e999"
 * (Infinity) podían volver a colar como número en una refactorización.
 */
class JsonTest {

    @Test
    fun `numeros invalidos tiran JsonParseException`() {
        for (bad in listOf("1.2.3", "1e999", "1e+", "01", "1.", ".5", "1e", "--1", "1..2", "NaN")) {
            assertThrows(JsonParseException::class.java) { JsonParser.parse(bad) }
        }
    }

    @Test
    fun `numeros validos se leen con su valor`() {
        assertEquals(123.0, (JsonParser.parse("123") as JsonValue.Num).value, 0.0)
        assertEquals(-1500.0, (JsonParser.parse("-1.5e3") as JsonValue.Num).value, 0.0)
        assertEquals(0.5, (JsonParser.parse("0.5") as JsonValue.Num).value, 0.0)
        assertEquals(0.0, (JsonParser.parse("0") as JsonValue.Num).value, 0.0)
        assertEquals(1000.0, (JsonParser.parse("1e3") as JsonValue.Num).value, 0.0)
    }

    @Test
    fun `long lee el texto crudo y no pierde enteros de 64 bits`() {
        // 2^53 + 1: como Double se redondea a 2^53; el raw lo salva.
        val root = JsonParser.parse("{\"id\":9007199254740993}")
        assertEquals(9_007_199_254_740_993L, root.obj("id").long())
        assertEquals("9007199254740993", (root.obj("id") as JsonValue.Num).raw)
    }
}
