package com.aurora.player.lyrics

/**
 * Orden de los proveedores de letras (Fase 2 — usuario 2026-11-09,
 * hoja "Elegir proveedor" como BitChord). El primero que encuentre
 * la letra gana; si falla, pasa al siguiente. Funciones puras, sin
 * Android, para probarlas en la JVM.
 */
val DEFAULT_PROVIDER_ORDER: List<String> = listOf("LRCLIB", "BetterLyrics", "BiniLyrics")

/** Nombres conocidos -> instancia del proveedor. */
fun providerByName(name: String): LyricsProvider? = when (name) {
    "LRCLIB" -> LrclibProvider()
    "BetterLyrics" -> BetterLyricsProvider()
    "BiniLyrics" -> BiniLyricsProvider()
    else -> null
}

/**
 * Parsea el CSV guardado: solo nombres conocidos, sin duplicados,
 * y los que falten se añaden al final en el orden por defecto
 * (así un proveedor nuevo nunca se pierde).
 */
fun parseProviderOrder(csv: String?): List<String> {
    val known = DEFAULT_PROVIDER_ORDER.toSet()
    val ordered = csv.orEmpty().split(",")
        .map { it.trim() }
        .filter { it in known }
        .distinct()
    return ordered + DEFAULT_PROVIDER_ORDER.filter { it !in ordered }
}

fun providerOrderToCsv(order: List<String>): String =
    parseProviderOrder(order.joinToString(",")).joinToString(",")

/** Instancias en el orden guardado, listas para loadOnlineLyrics. */
fun orderedProviders(csv: String?): List<LyricsProvider> =
    parseProviderOrder(csv).mapNotNull { providerByName(it) }

/** Mueve un proveedor una posición (arriba/abajo) en el orden. */
fun moveProvider(order: List<String>, name: String, up: Boolean): List<String> {
    val clean = parseProviderOrder(order.joinToString(",")).toMutableList()
    val index = clean.indexOf(name)
    if (index < 0) return clean
    val target = if (up) index - 1 else index + 1
    if (target < 0 || target >= clean.size) return clean
    val moved = clean.removeAt(index)
    clean.add(target, moved)
    return clean
}
