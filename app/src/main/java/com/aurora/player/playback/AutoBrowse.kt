package com.aurora.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aurora.player.model.Song

/**
 * Android Auto (Fase 2 app): el árbol que ve el coche.
 *
 * Auto no usa la pantalla del teléfono: pide una raíz navegable y
 * sus hijos a la MediaLibrarySession. La raíz tiene cuatro carpetas
 * (Canciones, Favoritas, Álbumes, Artistas); cada álbum y cada
 * artista es una carpeta con sus canciones. Todo lo que es árbol
 * (ids, títulos, filtros) es puro y probado; el servicio solo añade
 * los MediaItem con su portada real.
 */
data class AutoNode(val id: String, val title: String)

object AutoBrowse {
    const val ROOT_ID = "aurora_root"
    const val SONGS_ID = "canciones"
    const val FAVORITES_ID = "favoritas"
    const val ALBUMS_ID = "albumes"
    const val ARTISTS_ID = "artistas"

    fun rootNodes(): List<AutoNode> = listOf(
        AutoNode(SONGS_ID, "Canciones"),
        AutoNode(FAVORITES_ID, "Favoritas"),
        AutoNode(ALBUMS_ID, "Álbumes"),
        AutoNode(ARTISTS_ID, "Artistas"),
    )

    fun albumNodes(songs: List<Song>): List<AutoNode> =
        songs.distinctBy { it.albumId }
            .sortedBy { it.album.lowercase() }
            .map { AutoNode("album:${it.albumId}", it.album.ifBlank { "Sin álbum" }) }

    fun artistNodes(songs: List<Song>): List<AutoNode> =
        songs.distinctBy { it.artist }
            .sortedBy { it.artist.lowercase() }
            .map { AutoNode("artist:${it.artist}", it.artist.ifBlank { "Sin artista" }) }

    /** Las canciones de un nodo: carpeta de canciones, favoritas, un álbum o un artista. Vacía si el id no existe. */
    fun songsForNode(nodeId: String, songs: List<Song>, favoriteIds: Set<Long>): List<Song> =
        when {
            nodeId == SONGS_ID -> songs
            nodeId == FAVORITES_ID -> songs.filter { it.id in favoriteIds }
            nodeId.startsWith("album:") -> {
                val albumId = nodeId.removePrefix("album:").toLongOrNull() ?: return emptyList()
                songs.filter { it.albumId == albumId }
            }
            nodeId.startsWith("artist:") -> {
                val artist = nodeId.removePrefix("artist:")
                songs.filter { it.artist == artist }
            }
            else -> emptyList()
        }

    /** Una carpeta del árbol: navegable, nunca reproducible. */
    fun nodeMediaItem(node: AutoNode): MediaItem =
        MediaItem.Builder()
            .setMediaId(node.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(node.title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    /** Las canciones de un nodo, listas para sonar en el coche (mismo MediaItem que en el teléfono, marcado reproducible). */
    fun songMediaItems(context: Context, songs: List<Song>): List<MediaItem> =
        songs.map { song ->
            val base = songToMediaItem(context, song)
            base.buildUpon()
                .setMediaMetadata(base.mediaMetadata.buildUpon().setIsPlayable(true).build())
                .build()
        }
}
