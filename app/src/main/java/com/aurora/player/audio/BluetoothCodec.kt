package com.aurora.player.audio

/**
 * Códec Bluetooth A2DP (Fase 2 audio): nombres legibles para los
 * tipos de BluetoothCodecConfig (constantes AOSP, literales porque
 * en los tests JVM las constantes de Android valen 0).
 *
 * OJO: los tipos AOSP son 0-based (SBC = 0, AAC = 1, aptX = 2,
 * aptX HD = 3, LDAC = 4, aptX Adaptive = 5, aptX TWS+ = 6,
 * LC3 = 7, Opus = 8). La tabla anterior era 1-based y nombraba
 * mal todos los códecs menos LC3 (ronda 9). LHDC no tiene
 * constante AOSP: es un códec de fabricante y esta API no lo
 * distingue, así que no se inventa.
 *
 * La lectura del códec activo es API oculta (getCodecStatus) y se
 * hace por reflexión con fallback honesto; este archivo solo nombra
 * lo que el sistema devuelve, nunca inventa un códec.
 */
fun bluetoothCodecName(codecType: Int): String = when (codecType) {
    0 -> "SBC"
    1 -> "AAC"
    2 -> "aptX"
    3 -> "aptX HD"
    4 -> "LDAC"
    5 -> "aptX Adaptive"
    6 -> "aptX TWS+"
    7 -> "LC3"
    8 -> "Opus"
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
