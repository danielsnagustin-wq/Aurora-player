package com.aurora.player.audio

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Códec Bluetooth A2DP activo (Fase 2 audio): qué códec usa el
 * auricular conectado ahora mismo (LDAC, aptX, AAC, SBC...).
 *
 * La lectura es API oculta (BluetoothA2dp.getCodecStatus) y se hace
 * por reflexión: si el sistema no la deja, devuelve null y la UI
 * dice "Códec no disponible", nunca se inventa uno. Necesita el
 * permiso BLUETOOTH_CONNECT (Android 12+); sin él, null también.
 * Solo se llama al abrir el detalle de salida, nunca en segundo plano.
 */
data class BtCodecInfo(
    val codecType: Int,
    val sampleRateHz: Int?,
    val bitsPerSample: Int?,
) {
    fun displayLine(): String =
        bluetoothCodecLine(codecType, sampleRateHz, bitsPerSample)
}

/** Constante SAMPLE_RATE_* de BluetoothCodecConfig -> Hz. */
fun btSampleRateConstantToHz(value: Int): Int? = when (value) {
    1 -> 44100
    2 -> 48000
    4 -> 88200
    8 -> 96000
    16 -> 176400
    32 -> 192000
    else -> null
}

/** Constante BITS_PER_SAMPLE_* de BluetoothCodecConfig -> bits. */
fun btBitsConstantToBits(value: Int): Int? = when (value) {
    1 -> 16
    2 -> 24
    4 -> 32
    else -> null
}

/**
 * Ronda 16: con varios A2DP conectados, el primero no tiene por qué
 * ser el que suena. Se prefiere el dispositivo cuya dirección aparece
 * entre las salidas activas de AudioManager; si ninguno coincide (o
 * la lectura falla), se usa el primero (comportamiento anterior).
 */
private fun pickActiveBtDevice(
    context: Context,
    devices: List<BluetoothDevice>,
): BluetoothDevice? {
    if (devices.isEmpty()) return null
    if (devices.size == 1) return devices.first()
    val activeAddresses = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.mapNotNull { it.address }
            ?.toSet()
            .orEmpty()
    } catch (_: Exception) {
        emptySet()
    }
    return devices.firstOrNull { it.address in activeAddresses } ?: devices.first()
}

suspend fun readActiveBtCodec(context: Context): BtCodecInfo? =
    withContext(Dispatchers.IO) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext null
            }
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext null
            val a2dp = suspendCancellableCoroutine<BluetoothA2dp?> { cont ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (cont.isActive) cont.resume(proxy as? BluetoothA2dp)
                    }

                    override fun onServiceDisconnected(profile: Int) {}
                }
                val ok = adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
                if (!ok && cont.isActive) cont.resume(null)
            } ?: return@withContext null
            try {
                val devices: List<BluetoothDevice> = try {
                    a2dp.connectedDevices
                } catch (_: Exception) {
                    emptyList()
                }
                val device: BluetoothDevice =
                    pickActiveBtDevice(context, devices) ?: return@withContext null
                val status = try {
                    a2dp.javaClass
                        .getMethod("getCodecStatus", BluetoothDevice::class.java)
                        .invoke(a2dp, device)
                } catch (_: Exception) {
                    null
                } ?: return@withContext null
                val config = try {
                    status.javaClass.getMethod("getCodecConfig").invoke(status)
                } catch (_: Exception) {
                    null
                } ?: return@withContext null
                val codecType = try {
                    config.javaClass.getMethod("getCodecType").invoke(config) as? Int
                } catch (_: Exception) {
                    null
                } ?: return@withContext null
                val sampleRate = try {
                    config.javaClass.getMethod("getSampleRate").invoke(config) as? Int
                } catch (_: Exception) {
                    null
                }?.let { btSampleRateConstantToHz(it) }
                val bits = try {
                    config.javaClass.getMethod("getBitsPerSample").invoke(config) as? Int
                } catch (_: Exception) {
                    null
                }?.let { btBitsConstantToBits(it) }
                BtCodecInfo(codecType, sampleRate, bits)
            } finally {
                try {
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            null
        }
    }
