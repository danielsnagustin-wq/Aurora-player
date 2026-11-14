package com.aurora.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.Song
import com.aurora.player.util.ErrorLog
import java.util.Locale

/**
 * Aurora Player — biblioteca local con MediaStore (Etapa 1).
 * Reglas:
 * - Solo archivos de audio reales (IS_MUSIC = 1) y duración >= 5 s por
 *   defecto (como Gramophone: oculta tonos/notificaciones y pistas
 *   ultracortas; el mínimo es configurable en Ajustes).
 * - Sin escanear a mano con File.listFiles como fuente principal.
 * - Sin permiso de internet: todo local.
 */
/** URI de la portada de un álbum en MediaStore (pura, sin repositorio). */
fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        albumId,
    )

class MusicRepository(private val context: Context) {

    /**
     * Carga con reintento: primero con las columnas de Android 11+ y,
     * si algún dispositivo no las conoce (la consulta lanza
     * IllegalArgumentException), sin ellas. Un fallo total devuelve
     * lista vacía y se registra, nunca cuelga la biblioteca.
     * Ronda 31: el fallo total ya no es silencioso — queda en
     * [lastLoadError] para que la biblioteca pueda decirle al
     * usuario que el escaneo falló en vez de "no hay canciones".
     */
    var lastLoadError: String? = null
        private set

    fun loadSongs(minDurationMs: Long = 5_000L): List<Song> {
        return try {
            loadSongsWithProjection(minDurationMs, extended = true).also {
                lastLoadError = null
            }
        } catch (e: IllegalArgumentException) {
            ErrorLog.log(context, "Escaneo sin columnas nuevas", e)
            try {
                loadSongsWithProjection(minDurationMs, extended = false).also {
                    lastLoadError = null
                }
            } catch (e2: Exception) {
                ErrorLog.log(context, "Cargar biblioteca", e2)
                lastLoadError = "No se pudo escanear la biblioteca. Revisa el permiso de música e inténtalo otra vez."
                emptyList()
            }
        } catch (e: Exception) {
            ErrorLog.log(context, "Cargar biblioteca", e)
            lastLoadError = "No se pudo escanear la biblioteca. Revisa el permiso de música e inténtalo otra vez."
            emptyList()
        }
    }

    private fun loadSongsWithProjection(
        minDurationMs: Long,
        extended: Boolean,
    ): List<Song> {
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
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
        )
        if (extended && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
        // RONDA 124: query() puede devolver null sin lanzar nada (el
        // proveedor falla) y el ?.use lo tragaba: biblioteca vacía SIN
        // error aunque hubiera música. Un cursor nulo es un fallo de
        // escaneo y va por el camino de error de loadSongs().
        val cursor = context.contentResolver.query(uri, projection.toTypedArray(), selection, selectionArgs, sortOrder)
            ?: throw IllegalStateException("MediaStore devolvió un cursor nulo al escanear la biblioteca")
        cursor.use { cursor ->
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
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
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
                        dateModified = cursor.getLong(modifiedCol) * 1_000L,
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
                // RONDA 169: el artista del álbum es albumArtist
                // (MediaStore, columna de Android 11+): en una
                // recopilación el albumArtist es "Varios artistas"
                // y cada pista tiene su cantante. Antes se ponía el
                // artista de la primera canción y el álbum quedaba
                // asociado a quien no era. Sin albumArtist (API
                // vieja o columna vacía), la primera pista.
                artist = list.firstNotNullOfOrNull { it.albumArtist?.takeIf { a -> a.isNotBlank() } }
                    ?: list.first().artist,
                songCount = list.size,
                year = list.mapNotNull { it.year }.minOrNull(),
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

    fun loadArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }.map { (name, list) ->
            Artist(
                id = name.hashCode().toLong(),
                name = name,
                songCount = list.size,
                albumCount = list.distinctBy { it.albumId }.size,
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

    // La portada vive como función de nivel superior albumArtUri
    // (línea 22, pura, sin repositorio). NO añadir aquí un método
    // con el mismo nombre: dentro de la clase ese nombre resuelve
    // al propio método y se llama a sí mismo en bucle infinito
    // (StackOverflowError en el teléfono, 2026-11-10).
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
