package com.aurora.player.model

/**
 * Calidad de streaming online (BitChord, 2026-11-10): la calidad
 * elige el ORDEN de la escalera de itags de YouTube. Los MB/h son
 * el coste real del bitrate (kbps * 3600 / 8 / 1024).
 *
 * Honesto: YouTube no ofrece audio sin perdida, asi que no hay
 * tier "Sin perdida". Alta es Opus ~160 kbps, lo mejor que devuelve
 * InnerTube; si ese itag no esta, cae al siguiente sin quedarse
 * sin audio.
 */
enum class StreamQuality(
    val label: String,
    val description: String,
    val preferredItags: List<Long>,
) {
    HIGH(
        "Alta",
        "Opus ~160 kbps · ~72 MB/h",
        listOf(251L, 140L, 139L),
    ),
    MEDIUM(
        "Media",
        "AAC ~128 kbps · ~58 MB/h",
        listOf(140L, 251L, 139L),
    ),
    LOW(
        "Baja",
        "AAC ~48 kbps · ~22 MB/h",
        listOf(139L, 140L, 251L),
    ),
}
