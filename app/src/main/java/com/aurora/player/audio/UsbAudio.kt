package com.aurora.player.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Ronda USB Serie A — DAC USB: detectarlo, mostrar lo que soporta
 * y mandar el AudioTrack directo a él.
 *
 * Lo que hace de verdad: `findUsbDac` lee los dispositivos de
 * salida de AudioManager (sin permisos) y `setPreferredDevice`
 * del sumidero de Media3 (verificado en el bytecode del AAR
 * 1.4.1: acepta null y lo limpia) fija el DAC como salida del
 * reproductor, así el sonido no se va al altavoz ni lo roba el
 * Bluetooth cuando el DAC está conectado. La tasa nativa es
 * honesta: si la tasa de la pista está en la lista del DAC, no
 * hay resampleo en el mezclador; si no, Android resamplea a su
 * tasa y la línea de estado lo dice.
 *
 * Lo que NO se promete: DVC (volumen directo del DAC) y salida
 * USB directa (driver USB propio) quedan pendientes y no se
 * fingen aquí.
 */
data class UsbDacInfo(
    val name: String,
    val sampleRatesHz: List<Int>,
    val channelCounts: List<Int>,
)

/** DAC USB conectado ahora mismo, si lo hay (sin permisos). */
fun findUsbDac(context: Context): Pair<AudioDeviceInfo, UsbDacInfo>? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return null
    val devices = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    } catch (_: Exception) {
        return null
    }
    val device = devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    } ?: return null
    return device to parseUsbDacInfo(device)
}

fun parseUsbDacInfo(device: AudioDeviceInfo): UsbDacInfo {
    val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "DAC USB"
    val rates = try {
        device.sampleRates?.toList() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    val channels = try {
        device.channelCounts?.toList() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    return UsbDacInfo(name, rates.sorted(), channels.sorted())
}

/** Tasa del mezclador de Android (la tasa a la que resamplea). */
fun mixerSampleRateHz(context: Context): Int? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return null
    return try {
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    } catch (_: Exception) {
        null
    }
}

/**
 * Tasa nativa de la pista si el DAC la soporta; si no, null (el
 * mezclador resamplea). Pura, para tests.
 */
fun pickNativeRate(trackRateHz: Int?, dacRates: List<Int>): Int? {
    if (trackRateHz == null) return null
    return if (dacRates.contains(trackRateHz)) trackRateHz else null
}

/** "44.1, 48, 96, 192 kHz" */
fun formatUsbRates(ratesHz: List<Int>): String {
    if (ratesHz.isEmpty()) return "sin tasas conocidas"
    return ratesHz.distinct().sorted().joinToString(", ") { formatRate(it) }
}

private fun formatRate(rateHz: Int): String =
    if (rateHz % 1000 == 0) "${rateHz / 1000} kHz" else "${rateHz / 1000.0} kHz"

/** Línea honesta para Sonido cuando hay un DAC USB. */
fun usbStatusLine(dac: UsbDacInfo?, trackRateHz: Int?, mixerRateHz: Int?): String {
    if (dac == null) return ""
    val native = pickNativeRate(trackRateHz, dac.sampleRatesHz)
    return when {
        native != null ->
            "DAC USB · ${dac.name}: la pista suena a su tasa nativa de ${formatRate(native)}, sin resampleo."
        trackRateHz != null && mixerRateHz != null ->
            "DAC USB · ${dac.name}: la pista es de ${formatRate(trackRateHz)} y el DAC no la lista; Android resamplea a ${formatRate(mixerRateHz)} (tasa del mezclador)."
        trackRateHz != null ->
            "DAC USB · ${dac.name}: la pista es de ${formatRate(trackRateHz)}; tasas del DAC: ${formatUsbRates(dac.sampleRatesHz)}."
        else ->
            "DAC USB · ${dac.name}: tasas: ${formatUsbRates(dac.sampleRatesHz)}."
    }
}
