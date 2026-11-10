package com.aurora.player.cast

/**
 * Cast (Fase 2 app), Serie A: decisiones puras sobre qué se puede
 * enviar al Chromecast y con qué tipo de contenido.
 *
 * Límite honesto de la v1: solo las pistas ONLINE (YouTube Music,
 * mediaId "yt:<videoId>") se pueden enviar, porque el Chromecast
 * descarga el audio directamente desde la URL del stream. Las
 * pistas LOCALES viven en el teléfono (content://) y el Chromecast
 * no puede alcanzarlas; para ellas haría falta un servidor HTTP
 * local en el teléfono, que es otra ronda. Nunca se finge que una
 * pista local se está enviando.
 */
object CastMedia {

    /** Solo las pistas online (mediaId "yt:…") tienen URL alcanzable por el Chromecast. */
    fun isCastableOnlineTrack(mediaId: String): Boolean =
        mediaId.startsWith("yt:")

    /**
     * Tipo MIME del stream según el itag de YouTube Music:
     * 251 es Opus en WebM; 140/139 son AAC en MP4. Cualquier otro
     * itag de audio se declara MP4 (el receptor lo rechaza si no
     * lo es, nunca se inventa otro formato).
     */
    fun contentTypeForItag(itag: Long): String =
        when (itag) {
            251L -> "audio/webm"
            else -> "audio/mp4"
        }

    /**
     * Tipo de contenido de una URL de stream de YouTube Music, sin
     * resolver nada más: las URLs de googlevideo llevan el itag como
     * parámetro (`itag=251` / `itag=140`); si falta, se mira el
     * `mime=` (audio%2Fwebm / audio%2Fmp4); si no hay ninguno, MP4.
     */
    fun contentTypeForStreamUrl(streamUrl: String): String {
        val itag = streamUrl.substringAfter("itag=", "")
            .substringBefore("&").toLongOrNull()
        if (itag != null) return contentTypeForItag(itag)
        val mime = streamUrl.substringAfter("mime=", "")
            .substringBefore("&")
        return if (mime.contains("webm", ignoreCase = true)) "audio/webm" else "audio/mp4"
    }
}
