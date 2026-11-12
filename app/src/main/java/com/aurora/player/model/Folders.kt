package com.aurora.player.model

/**
 * Aurora Player — carpetas (SPEC 005, FR-006). Funciones puras
 * sobre rutas de archivo, testeables sin Android.
 *
 * Las rutas son absolutas (MediaStore DATA). La navegación es por
 * cadena de carpeta: la raíz es el prefijo común de todas las
 * canciones (p. ej. /storage/emulated/0) y cada nivel muestra sus
 * subcarpetas directas + sus canciones directas.
 */

data class FolderEntry(val path: String, val name: String, val songCount: Int)

/** Carpeta padre de una ruta. "/a/b/c.mp3" -> "/a/b"; "/a.mp3" -> "/". */
fun parentPath(path: String): String {
    val idx = path.lastIndexOf('/')
    return if (idx <= 0) "/" else path.substring(0, idx)
}

/**
 * Carpeta común a todas las rutas (por segmentos, no por texto:
 * "/a/b" y "/a/bc" comparten "/a", no "/a/b"). Vacío si no hay
 * ninguna en común o no hay rutas.
 */
fun commonFolderPrefix(paths: List<String>): String {
    if (paths.isEmpty()) return ""
    val dirs = paths.map(::parentPath)
    var prefix = dirs[0]
    for (dir in dirs.drop(1)) {
        while (prefix.isNotEmpty() && dir != prefix && !dir.startsWith("$prefix/")) {
            val cut = prefix.lastIndexOf('/')
            prefix = if (cut <= 0) "" else prefix.substring(0, cut)
        }
        if (prefix.isEmpty()) break
    }
    return prefix
}

/** Subcarpetas directas de `current`, con su nº de canciones (recursivo). */
fun childFolders(paths: List<String>, current: String): List<FolderEntry> {
    val base = if (current.isEmpty()) "/" else "$current/"
    val counts = linkedMapOf<String, Int>()
    for (path in paths) {
        if (!path.startsWith(base)) continue
        val rest = path.removePrefix(base)
        val slash = rest.indexOf('/')
        if (slash < 0) continue // canción directa en current
        val child = base + rest.substring(0, slash)
        counts[child] = (counts[child] ?: 0) + 1
    }
    return counts.entries.map { (path, count) ->
        FolderEntry(path, path.substringAfterLast('/'), count)
    }.sortedBy { it.name.lowercase() }
}

/** true si la canción está directamente en `current` (sin bajar). */
fun isDirectlyInFolder(path: String, current: String): Boolean =
    parentPath(path) == current

/**
 * Id de árbol del selector de carpetas (SAF) a ruta de archivo:
 * "primary:Music/Rock" -> "/storage/emulated/0/Music/Rock";
 * "1234-5678:Music" -> "/storage/1234-5678/Music" (tarjeta SD).
 * Null si el id no tiene la forma "volumen:ruta".
 */
fun treeDocumentIdToPath(treeDocumentId: String): String? {
    val colon = treeDocumentId.indexOf(':')
    if (colon < 0) return null
    val volume = treeDocumentId.substring(0, colon)
    val rest = treeDocumentId.substring(colon + 1)
    if (volume.isEmpty()) return null
    val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    return if (rest.isEmpty()) base else "$base/$rest"
}

/** true si la canción está dentro de una carpeta excluida (o es ella). */
fun isInExcludedFolder(path: String, excluded: Set<String>): Boolean {
    val dir = parentPath(path)
    return excluded.any { dir == it || dir.startsWith("$it/") }
}

/**
 * ESCANEAR (2026-11-11): true si la canción está dentro de una de
 * las carpetas a escanear. Vacío = se escanea todo (sin filtro).
 */
fun isInIncludedFolder(path: String, included: Set<String>): Boolean {
    if (included.isEmpty()) return true
    val dir = parentPath(path)
    return included.any { dir == it || dir.startsWith("$it/") }
}
