package com.aurora.player.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aurora.player.model.StreamQuality

/**
 * Calidad por conexión (copia BitChord, slice 3): con Wi-Fi se usa la
 * calidad de Wi-Fi; con datos móviles (o si no se puede comprobar la
 * red) se usa la de móvil, para no gastar datos sin querer.
 */
fun effectiveStreamQuality(
    isWifi: Boolean,
    wifiQuality: StreamQuality,
    mobileQuality: StreamQuality,
): StreamQuality = if (isWifi) wifiQuality else mobileQuality

/**
 * True si la red activa es Wi-Fi. False sin red, con datos móviles o
 * si no se puede comprobar. Necesita ACCESS_NETWORK_STATE (permiso
 * normal, sin pedir nada al usuario).
 */
fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
