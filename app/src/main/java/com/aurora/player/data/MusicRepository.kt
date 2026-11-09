package com.aurora.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.Song

/**
 * Aurora Player — biblioteca local con MediaStore (Etapa 1).
 * Reglas:
 * - Solo archivos de audio reales (IS_MUSIC = 1) y duración >= 5 s por
 *   defecto (como Gramophone: oculta tonos/notificaciones y pistas
 *   ultracortas; el mínimo es configurable en Ajustes).
 * - Sin escanear a mano con File.listFiles como fuente principal.
 * - Sin permiso de internet: todo local.
 */
class MusicRepository(private val context: Context) {

    fun loadSongs(minDurationMs: Long = 5_000L): List<Song> {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // Columnas de Android 11+ (ALBUM_ARTIST, COMPOSER, GENRE,
        // CD_TRACK_NUMBER, DISC_NUMBER): solo se piden en API 30+,
        // en versiones viejas la consulta fallaría si se incluyen.
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projection += MediaStore.Audio.Media.ALBUM_ARTIST
            projection += MediaStore.Audio.Media.COMPOSER
            projection += MediaStore.Audio.Media.GENRE
            projection += MediaStore.Audio.Media.CD_TRACK_NUMBER
            projection += MediaStore.Audio.Media.DISC_NUMBER
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND " +
            "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        context.contentResolver.query(uri, projection.toTypedArray(), selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val composerCol = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
                val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val cdTrackCol = cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                val discCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val trackRaw = cursor.getInt(trackCol)
                    val cdTrack = if (cdTrackCol >= 0) cursor.getInt(cdTrackCol) else 0
                    val discNum = if (discCol >= 0) cursor.getInt(discCol) else 0
                    val (trackNumber, discNumber) = trackAndDisc(trackRaw, cdTrack, discNum)
                    songs += Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Sin título",
                        artist = cursor.getString(artistCol) ?: "Artista desconocido",
                        album = cursor.getString(albumCol) ?: "Álbum desconocido",
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = cursor.getLong(durationCol),
                        path = cursor.getString(dataCol) ?: "",
                        uri = ContentUris.withAppendedId(uri, id),
                        mimeType = cursor.getString(mimeCol) ?: "",
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAdded = cursor.getLong(addedCol) * 1_000L,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        year = cursor.getInt(yearCol).takeIf { it > 0 },
                        genre = if (genreCol >= 0) cursor.getString(genreCol) else null,
                        albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null,
                        composer = if (composerCol >= 0) cursor.getString(composerCol) else null,
                    )
                }
            }
        return songs
    }

    fun loadAlbums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }.map { (albumId, list) ->
            Album(
                id = albumId,
                name = list.first().album,
                artist = list.first().artist,
                songCount = list.size,
                year = list.mapNotNull { it.year }.minOrNull(),
            )
        }.sortedBy { it.name.lowercase() }

    fun loadArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }.map { (name, list) ->
            Artist(
                id = name.hashCode().toLong(),
                name = name,
                songCount = list.size,
                albumCount = list.distinctBy { it.albumId }.size,
            )
        }.sortedBy { it.name.lowercase() }

    /** Portada del álbum vía MediaStore; null = sin portada (se muestra inicial, nunca una ajena). */
    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )
}

/**
 * Pista y disco de una canción: las columnas modernas (CD_TRACK_NUMBER,
 * DISC_NUMBER, Android 11+) mandan; si no existen, TRACK codifica
 * disco*1000 + pista. Sin dato -> pista 0, disco 1.
 */
fun trackAndDisc(trackRaw: Int, cdTrackNumber: Int, discNumber: Int): Pair<Int, Int> {
    val track = when {
        cdTrackNumber > 0 -> cdTrackNumber
        trackRaw > 0 -> trackRaw % 1000
        else -> 0
    }
    val disc = when {
        discNumber > 0 -> discNumber
        trackRaw > 0 -> (trackRaw / 1000).takeIf { it > 0 } ?: 1
        else -> 1
    }
    return track to disc
}
