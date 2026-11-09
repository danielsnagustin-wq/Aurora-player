package com.aurora.player.online

/**
 * RONDA 7 (002-online-youtube): parser JSON mínimo, Kotlin puro.
 * InnerTube devuelve JSON anidado enorme y org.json es stub en los
 * unit tests (lección de P2 con LRCLIB), así que el parseo de
 * respuestas de YouTube pasa por aquí: testeable sin Android.
 * Cubre objetos, arrays, strings con escapes (\uXXXX incluido),
 * números, booleanos y null. Lanza JsonParseException si está roto.
 */
sealed interface JsonValue {
    data class Obj(val map: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

class JsonParseException(message: String) : Exception(message)

/** Navegación segura: null si el camino no existe o no es del tipo. */
fun JsonValue?.obj(key: String): JsonValue? = (this as? JsonValue.Obj)?.map?.get(key)
fun JsonValue?.arr(): List<JsonValue>? = (this as? JsonValue.Arr)?.items
fun JsonValue?.str(): String? = (this as? JsonValue.Str)?.value
fun JsonValue?.long(): Long? = (this as? JsonValue.Num)?.value?.toLong()
fun JsonValue?.bool(): Boolean? = (this as? JsonValue.Bool)?.value

object JsonParser {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonParseException("Basura tras el valor JSON")
        return value
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonParseException("Fin inesperado del JSON")
            return when (val ch = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> {
                    expect("true")
                    JsonValue.Bool(true)
                }
                'f' -> {
                    expect("false")
                    JsonValue.Bool(false)
                }
                'n' -> {
                    expect("null")
                    JsonValue.Null
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw JsonParseException("Carácter inesperado '$ch' en $pos")
            }
        }

        private fun expect(word: String) {
            if (!text.startsWith(word, pos)) throw JsonParseException("Se esperaba '$word' en $pos")
            pos += word.length
        }

        private fun parseObject(): JsonValue.Obj {
            pos++ // {
            val map = linkedMapOf<String, JsonValue>()
            skipWhitespace()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return JsonValue.Obj(map)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[pos] != '"') throw JsonParseException("Se esperaba una clave en $pos")
                val key = parseString()
                skipWhitespace()
                if (atEnd() || text[pos] != ':') throw JsonParseException("Se esperaba ':' en $pos")
                pos++
                map[key] = parseValue()
                skipWhitespace()
                if (atEnd()) throw JsonParseException("Objeto sin cerrar")
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonValue.Obj(map)
                    }
                    else -> throw JsonParseException("Se esperaba ',' o '}' en $pos")
                }
            }
        }

        private fun parseArray(): JsonValue.Arr {
            pos++ // [
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return JsonValue.Arr(items)
            }
            while (true) {
                items += parseValue()
                skipWhitespace()
                if (atEnd()) throw JsonParseException("Array sin cerrar")
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("Se esperaba ',' o ']' en $pos")
                }
            }
        }

        private fun parseString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos]
                when {
                    ch == '"' -> {
                        pos++
                        return sb.toString()
                    }
                    ch == '\\' && pos + 1 < text.length -> {
                        when (val e = text[pos + 1]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 5 >= text.length) throw JsonParseException("Escape \\u roto")
                                sb.append(
                                    text.substring(pos + 2, pos + 6).toIntOrNull(16)?.toChar()
                                        ?: throw JsonParseException("Escape \\u inválido"),
                                )
                                pos += 4
                            }
                            else -> throw JsonParseException("Escape inválido '\\$e'")
                        }
                        pos += 2
                    }
                    else -> {
                        sb.append(ch)
                        pos++
                    }
                }
            }
            throw JsonParseException("Cadena sin cerrar")
        }

        private fun parseNumber(): JsonValue.Num {
            val start = pos
            if (pos < text.length && text[pos] == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val raw = text.substring(start, pos)
            return JsonValue.Num(raw.toDoubleOrNull() ?: throw JsonParseException("Número inválido '$raw'"))
        }
    }
}
