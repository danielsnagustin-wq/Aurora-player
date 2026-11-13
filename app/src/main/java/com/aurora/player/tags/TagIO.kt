package com.aurora.player.tags

import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag

/**
 * Etiquetas de una canción (las que edita el usuario).
 * Todo es opcional: sin dato, no sale (R-17).
 */
data class SongTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val track: Int? = null,
    val trackTotal: Int? = null,
    val disc: Int? = null,
    val discTotal: Int? = null,
    val lyricist: String? = null,
    val comment: String? = null,
)

/**
 * Lectura y escritura de etiquetas con jaudiotagger (3.0.1, LGPL).
 * Solo trabaja sobre un File (el temporal del paso 2); nunca toca
 * MediaStore ni la biblioteca. Bloqueante: llamar desde IO.
 */
object TagIO {

    /** Lee las etiquetas de [file]; null si el archivo no se puede leer. */
    fun read(file: File): SongTags? {
        val tag = try {
            AudioFileIO.read(file).tag
        } catch (_: Exception) {
            return null
        } ?: return SongTags()
        return SongTags(
            title = tag.firstOrNull(FieldKey.TITLE),
            artist = tag.firstOrNull(FieldKey.ARTIST),
            albumArtist = tag.firstOrNull(FieldKey.ALBUM_ARTIST),
            album = tag.firstOrNull(FieldKey.ALBUM),
            composer = tag.firstOrNull(FieldKey.COMPOSER),
            genre = tag.firstOrNull(FieldKey.GENRE),
            year = tag.firstOrNull(FieldKey.YEAR)?.toIntOrNull()
                ?: tag.firstOrNull(FieldKey.ALBUM_YEAR)?.toIntOrNull(),
            track = tag.firstOrNull(FieldKey.TRACK)?.trackNumber(),
            trackTotal = tag.firstOrNull(FieldKey.TRACK_TOTAL)?.toIntOrNull()
                ?: tag.firstOrNull(FieldKey.TRACK)?.trackTotal(),
            disc = tag.firstOrNull(FieldKey.DISC_NO)?.toIntOrNull(),
            discTotal = tag.firstOrNull(FieldKey.DISC_TOTAL)?.toIntOrNull(),
            lyricist = tag.firstOrNull(FieldKey.LYRICIST),
            comment = tag.firstOrNull(FieldKey.COMMENT),
        )
    }

    /**
     * Escribe [tags] en [file]. Los campos null se dejan como están
     * (no se borran: el borrado explícito es trabajo del paso 3).
     * Devuelve false si el archivo no se pudo leer o escribir.
     */
    fun write(file: File, tags: SongTags): Boolean {
        val audioFile = try {
            AudioFileIO.read(file)
        } catch (_: Exception) {
            return false
        }
        val tag = try {
            audioFile.tagOrCreateAndSetDefault()
        } catch (_: Exception) {
            return false
        }
        tag.setOrSkip(FieldKey.TITLE, tags.title)
        tag.setOrSkip(FieldKey.ARTIST, tags.artist)
        tag.setOrSkip(FieldKey.ALBUM_ARTIST, tags.albumArtist)
        tag.setOrSkip(FieldKey.ALBUM, tags.album)
        tag.setOrSkip(FieldKey.COMPOSER, tags.composer)
        tag.setOrSkip(FieldKey.GENRE, tags.genre)
        tag.setOrSkip(FieldKey.YEAR, tags.year?.toString())
        tag.setOrSkip(FieldKey.TRACK, tags.track?.toString())
        tag.setOrSkip(FieldKey.TRACK_TOTAL, tags.trackTotal?.toString())
        tag.setOrSkip(FieldKey.DISC_NO, tags.disc?.toString())
        tag.setOrSkip(FieldKey.DISC_TOTAL, tags.discTotal?.toString())
        tag.setOrSkip(FieldKey.LYRICIST, tags.lyricist)
        tag.setOrSkip(FieldKey.COMMENT, tags.comment)
        return try {
            AudioFileIO.write(audioFile)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * getFirst devuelve "" o lanza KeyNotFoundException si la clave no
 * existe en este tipo de tag. Vacío es lo mismo que ausente (R-17).
 */
private fun Tag.firstOrNull(key: FieldKey): String? {
    val value = try {
        getFirst(key)
    } catch (_: Exception) {
        return null
    }
    return value.takeIf { it.isNotBlank() }
}

/**
 * Escribe el campo si hay valor; si el tipo de tag no lo soporta,
 * se ignora sin romper el resto de la escritura.
 */
private fun Tag.setOrSkip(key: FieldKey, value: String?) {
    if (value == null) return
    try {
        setField(key, value)
    } catch (_: Exception) {
        // Clave no soportada por este formato: se ignora.
    }
}

/** "3" o "3/12" -> 3. */
private fun String.trackNumber(): Int? =
    substringBefore('/').trim().toIntOrNull()

/** "3/12" -> 12; "3" -> null. */
private fun String.trackTotal(): Int? =
    if (contains('/')) substringAfter('/').trim().toIntOrNull() else null
