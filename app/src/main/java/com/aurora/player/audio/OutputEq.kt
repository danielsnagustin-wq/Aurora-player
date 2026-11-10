package com.aurora.player.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * EQ por salida (Fase 2 audio): cada salida de audio puede tener su
 * propio EQ — el altavoz no suena igual que unos auriculares, y unos
 * auriculares Bluetooth no suenan igual que otros.
 *
 * La clasificación es pura (recibe los tipos de AudioDeviceInfo como
 * enteros, porque en los tests JVM las constantes de Android son 0):
 *   8  = TYPE_BLUETOOTH_A2DP, 7 = TYPE_BLUETOOTH_SCO
 *   3  = TYPE_WIRED_HEADPHONES, 4 = TYPE_WIRED_HEADSET,
 *   5  = TYPE_LINE_ANALOG, 6 = TYPE_LINE_DIGITAL
 *   11 = TYPE_USB_DEVICE, 12 = TYPE_USB_ACCESSORY, 22 = TYPE_USB_HEADSET
 *   2  = TYPE_BUILTIN_SPEAKER
 * Prioridad cuando hay varias salidas a la vez (pasa con BT conectado):
 * Bluetooth > cable > USB > altavoz.
 */
enum class OutputRoute { SPEAKER, WIRED, BLUETOOTH, USB, OTHER }

fun classifyOutputRoute(deviceTypes: List<Int>): OutputRoute {
    // Literales AOSP a propósito: en los tests JVM las constantes de
    // AudioDeviceInfo valen 0, así que comparar contra ellas rompería
    // los tests. 8 = BT A2DP, 7 = BT SCO, 3/4 = cable, 5/6 = línea,
    // 11/12/22 = USB, 2 = altavoz.
    if (deviceTypes.any { it == 8 || it == 7 }) return OutputRoute.BLUETOOTH
    if (deviceTypes.any { it == 3 || it == 4 || it == 5 || it == 6 }) return OutputRoute.WIRED
    if (deviceTypes.any { it == 11 || it == 12 || it == 22 }) return OutputRoute.USB
    if (deviceTypes.any { it == 2 }) return OutputRoute.SPEAKER
    return OutputRoute.OTHER
}

/**
 * Clave de guardado de una salida: "speaker" | "wired" | "usb" |
 * "other" | "bluetooth:<nombre del aparato>". El nombre se limpia
 * para que la clave sea segura en DataStore; dos aparatos del mismo
 * modelo comparten EQ, que es lo que se quiere.
 */
fun routeKey(route: OutputRoute, deviceName: String?): String {
    if (route != OutputRoute.BLUETOOTH) return route.name.lowercase()
    val clean = (deviceName ?: "")
        .lowercase()
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
        .joinToString("")
        .trim('_')
        .take(40)
    return if (clean.isEmpty()) "bluetooth" else "bluetooth:$clean"
}

/** Etiqueta honesta de la salida para la pantalla de EQ. */
fun routeLabel(route: OutputRoute, deviceName: String?): String = when (route) {
    OutputRoute.SPEAKER -> "Altavoz del teléfono"
    OutputRoute.WIRED -> "Auriculares con cable"
    OutputRoute.BLUETOOTH -> if (deviceName.isNullOrBlank()) "Bluetooth" else "Bluetooth · $deviceName"
    OutputRoute.USB -> if (deviceName.isNullOrBlank()) "USB" else "USB · $deviceName"
    OutputRoute.OTHER -> "Otra salida"
}

/**
 * Salida de audio actual según AudioManager: no necesita ningún
 * permiso (productName de AudioDeviceInfo no exige BLUETOOTH_CONNECT).
 * Devuelve la ruta y el nombre del aparato cuando lo hay.
 */
fun currentOutputRoute(context: Context): Pair<OutputRoute, String?> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return OutputRoute.OTHER to null
    val devices = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    } catch (_: Exception) {
        emptyArray()
    }
    val route = classifyOutputRoute(devices.map { it.type })
    val name = when (route) {
        OutputRoute.BLUETOOTH -> devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }?.productName?.toString()?.takeIf { it.isNotBlank() }
        OutputRoute.WIRED -> devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }?.productName?.toString()?.takeIf { it.isNotBlank() }
        OutputRoute.USB -> devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }?.productName?.toString()?.takeIf { it.isNotBlank() }
        else -> null
    }
    return route to name
}
