package com.aurora.player.util

import com.aurora.player.online.JsonValue
import kotlin.math.floor

/**
 * Aurora Player — escritor JSON (SPEC 005, FR-004, respaldo).
 *
 * `online/Json.kt` es solo parser; esto es su compañero: serializa un
 * JsonValue a texto JSON válido que ese mismo parser vuelve a leer
 * (ida y vuelta probada con tests). Puro, sin Android: corre en los
 * tests unitarios de la JVM. Escapa comillas, barras, controles y
 * \uXXXX; los números enteros salen sin decimales (123, no 123.0).
 */

/** JsonValue -> texto JSON. */
fun JsonValue.toJsonString(): String = buildString { appendJson(this@toJsonString) }

private fun StringBuilder.appendJson(v: JsonValue) {
    when (v) {
        is JsonValue.Obj -> {
            append('{')
            var first = true
            for ((key, value) in v.map) {
                if (!first) append(',')
                first = false
                append('"').append(key.jsonEscaped()).append("\":")
                appendJson(value)
            }
            append('}')
        }
        is JsonValue.Arr -> {
            append('[')
            var first = true
            for (item in v.items) {
                if (!first) append(',')
                first = false
                appendJson(item)
            }
            append(']')
        }
        is JsonValue.Str -> append('"').append(v.value.jsonEscaped()).append('"')
        is JsonValue.Num -> append(formatNumber(v.value))
        is JsonValue.Bool -> append(if (v.value) "true" else "false")
        JsonValue.Null -> append("null")
    }
}

private fun formatNumber(d: Double): String {
    // Entero dentro de rango seguro: sin decimales ni exponente.
    if (d == floor(d) && d >= -9.0e15 && d <= 9.0e15) return d.toLong().toString()
    return d.toString()
}

/** Escapa una cadena para ir entre comillas en JSON. */
fun String.jsonEscaped(): String = buildString {
    for (c in this@jsonEscaped) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
