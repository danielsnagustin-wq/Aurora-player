package com.aurora.player.online

/**
 * Caché de lo escuchado de YouTube (Fase 2, 2026-11-10, como BitChord).
 *
 * La primera vez que suena una canción online se baja su audio y se
 * guarda en cacheDir/stream-cache; la siguiente vez suena del disco,
 * sin gastar datos. La descarga escribe a un archivo .part y solo al
 * terminar lo renombra: un archivo final siempre está completo (un
 * corte a medias nunca se da por bueno). Cuando el total pasa del
 * límite, se borran primero los que llevan más tiempo sin sonar
 * (LRU); nada se borra mientras suena.
 *
 * Esta clase es lógica pura (sin Android) para poder probarla en JVM:
 * recibe la lista de archivos y dice cuáles borrar. El acceso a disco
 * vive en el llamador.
 */
data class CachedStream(
    val videoId: String,
    val itag: Int,
    val quality: StreamQuality,
    val sizeBytes: Long,
    val lastUsedMs: Long,
    val fileName: String,
)

/** Límite por defecto de la caché: 500 MB. */
const val DEFAULT_STREAM_CACHE_MAX_BYTES: Long = 500L * 1024L * 1024L

/** Extensión real del contenedor que da cada itag (251 es Opus en WebM). */
fun streamExtensionForItag(itag: Int): String = when (itag) {
    251 -> "webm"
    140, 139 -> "m4a"
    else -> "bin"
}

/** Nombre de archivo en caché para un video + itag. */
fun streamCacheFileName(videoId: String, itag: Int): String =
    "${videoId}_$itag.${streamExtensionForItag(itag)}"

/** Nombre temporal mientras se descarga (nunca se reproduce). */
fun streamCachePartFileName(videoId: String, itag: Int): String =
    streamCacheFileName(videoId, itag) + ".part"

/** Tamaño total de la caché. */
fun totalCacheSize(files: List<CachedStream>): Long =
    files.sumOf { it.sizeBytes.coerceAtLeast(0L) }

/**
 * Qué archivos borrar para volver bajo [maxBytes], los más viejos
 * sin usar primero. Devuelve la lista a borrar (vacía si ya cabe).
 * Nunca borra nada si el límite es 0 o negativo (sin límite).
 */
fun filesToEvict(files: List<CachedStream>, maxBytes: Long): List<CachedStream> {
    if (maxBytes <= 0L) return emptyList()
    val total = totalCacheSize(files)
    if (total <= maxBytes) return emptyList()
    var freed = 0L
    val toDelete = mutableListOf<CachedStream>()
    for (file in files.sortedBy { it.lastUsedMs }) {
        if (total - freed <= maxBytes) break
        toDelete.add(file)
        freed += file.sizeBytes.coerceAtLeast(0L)
    }
    return toDelete
}
