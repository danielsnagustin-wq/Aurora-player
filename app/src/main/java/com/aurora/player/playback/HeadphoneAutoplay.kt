package com.aurora.player.playback

import android.bluetooth.BluetoothA2dp
import android.content.Intent

/**
 * Fase 1b — reproducir al conectar auriculares/BT (opt-in, OFF).
 *
 * La decisión es pura (solo constantes de acción y enteros): auriculares
 * con cable (ACTION_HEADSET_PLUG con state == 1) o salida de audio
 * Bluetooth A2DP conectada (ACTION_CONNECTION_STATE_CHANGED con
 * STATE_CONNECTED). No se usa BluetoothDevice: leer su clase exige el
 * permiso BLUETOOTH_CONNECT (API 31+) y la app no lo pide; el estado
 * A2DP del intent no necesita ningún permiso.
 *
 * ACL_CONNECTED no se escucha: también dispara con relojes y otros
 * aparatos que no son salida de audio; A2DP + cable cubren las
 * salidas reales (auriculares, altavoz, coche).
 */
fun isAudioOutputConnected(action: String?, headsetState: Int, a2dpState: Int): Boolean =
    when (action) {
        Intent.ACTION_HEADSET_PLUG ->
            headsetState == 1
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
            a2dpState == BluetoothA2dp.STATE_CONNECTED
        else -> false
    }
