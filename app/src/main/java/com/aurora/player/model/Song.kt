package com.aurora.player.model

import android.net.Uri

/**
 * Aurora Player — modelo de canción.
 * Fuente de verdad: MediaStore (Etapa 1) y, en Fase 1b, TagLib para
 * multi-artista / disc numbers / ReplayGain leídos del archivo.
 * Nada inventado: si un campo no existe en el archivo, es null o vacío.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val uri: Uri,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val trackNumber: Int = 0,
    val discNumber: Int = 1, // Fase 1b: leído del tag; por defecto 1, no inventado
    val genre: String? = null,
    val year: Int? = null,
    val bitrate: Int? = null, // Fase 1b: del extractor; null = no verificado
    val sampleRateHz: Int? = null, // Fase 1b: del extractor; null = no verificado
    val albumArtist: String? = null, // Android 11+: columna ALBUM_ARTIST
    val composer: String? = null, // Android 11+: columna COMPOSER
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
) {
    val isLossless: Boolean
        get() = mimeType in setOf("audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav")
}

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val year: Int? = null,
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** true = playlist-archivo (M3U/XSPF en disco, Gramophone-style); false = interna (Room). */
    val isFileBased: Boolean = false,
)
