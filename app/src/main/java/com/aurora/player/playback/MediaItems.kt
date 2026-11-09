package com.aurora.player.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Song

/**
 * Construye el MediaItem de una canción (metadatos + portada real).
 * Antes vivía como función privada en MainActivity; la alarma también
 * lo necesita desde el servicio, así que ahora es compartida.
 * La ruta viaja en el MediaItem: las letras (.lrc) se leen directas,
 * sin escanear toda la biblioteca al cambiar de pista.
 */
fun songToMediaItem(context: Context, song: Song): MediaItem =
    MediaItem.Builder()
        .setUri(song.uri)
        .setMediaId(song.id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(MusicRepository(context).albumArtUri(song.albumId))
                .setExtras(Bundle().apply { putString("aurora_path", song.path) })
                .build(),
        )
        .build()
