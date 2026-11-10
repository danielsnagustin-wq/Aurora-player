package com.aurora.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.CastMediaControlIntent

/**
 * Cast (Fase 2 app), Serie A: el proveedor de opciones que el
 * framework de Cast busca en el manifest (meta-data
 * OPTIONS_PROVIDER_CLASS_NAME).
 *
 * Receptor: el receptor multimedia por defecto de Google
 * (DEFAULT_MEDIA_RECEIVER_APPLICATION_ID). No hay receptor propio:
 * el Chromecast o la TV reproduce el audio directamente desde la
 * URL del stream, sin app receptora a medida.
 *
 * Esto solo DECLARA las opciones; nada inicializa Cast hasta que
 * la UI pide el CastContext (botón de Cast, Serie B). Sin
 * dispositivos cerca, el botón simplemente no muestra nada.
 */
class AuroraCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? =
        null
}
