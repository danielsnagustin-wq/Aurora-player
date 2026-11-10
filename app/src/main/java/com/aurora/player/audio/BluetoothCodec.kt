package com.aurora.player.audio

/**
 * Códec Bluetooth A2DP (Fase 2 audio): nombres legibles para los
 * tipos de BluetoothCodecConfig (constantes AOSP, literales porque
 * en los tests JVM las constantes de Android valen 0).
 *
 * La lectura del códec activo es API oculta (getCodecStatus) y se
 * hace por reflexión con fallback honesto; este archivo solo nombra
 * lo que el sistema devuelve, nunca inventa un códec.
 */
fun bluetoothCodecName(codecType: Int): String = when (codecType) {
    1 -> "SBC"
    2 -> "AAC"
    3 -> "aptX"
    4 -> "aptX HD"
    5 -> "LDAC"
    6 -> "LHDC"
    7 -> "LC3"
    else -> "Desconocido"
}

/** "LDAC · 96 kHz · 24 bits" o solo lo que se sepa (null = sin dato). */
fun bluetoothCodecLine(codecType: Int, sampleRateHz: Int?, bitsPerSample: Int?): String {
    val name = bluetoothCodecName(codecType)
    val parts = mutableListOf(name)
    if (sampleRateHz != null && sampleRateHz > 0) {
        parts.add(if (sampleRateHz >= 1000) "${sampleRateHz / 1000} kHz" else "$sampleRateHz Hz")
    }
    if (bitsPerSample != null && bitsPerSample > 0) {
        parts.add("$bitsPerSample bits")
    }
    return parts.joinToString(" · ")
}
