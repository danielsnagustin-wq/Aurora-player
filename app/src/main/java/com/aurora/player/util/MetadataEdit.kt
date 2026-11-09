package com.aurora.player.util

import android.provider.MediaStore

/**
 * Aurora Player — cambios de metadatos (editor de Detalles).
 *
 * La lógica es pura y testeable: recibe los campos del editor y
 * devuelve el mapa columna -> valor que se escribe en MediaStore.
 * Solo se incluyen los campos con valor real: un campo vacío no
 * borra el dato que ya tiene la canción. Las columnas de Android 11+
 * (artista del álbum, género, compositor, pista y disco modernos)
 * solo se incluyen cuando api30 es true; en versiones viejas el
 * proveedor no las conoce y el update fallaría.
 */
fun buildMetadataUpdate(
    title: String,
    artist: String,
    albumArtist: String,
    album: String,
    genre: String,
    composer: String,
    year: String,
    track: String,
    disc: String,
    api30: Boolean,
): Map<String, Any> {
    val out = mutableMapOf<String, Any>()
    val cleanTitle = title.trim()
    if (cleanTitle.isNotEmpty()) out[MediaStore.Audio.Media.TITLE] = cleanTitle
    val cleanArtist = artist.trim()
    if (cleanArtist.isNotEmpty()) out[MediaStore.Audio.Media.ARTIST] = cleanArtist
    val cleanAlbum = album.trim()
    if (cleanAlbum.isNotEmpty()) out[MediaStore.Audio.Media.ALBUM] = cleanAlbum
    year.trim().toIntOrNull()?.takeIf { it > 0 }?.let {
        out[MediaStore.Audio.Media.YEAR] = it
    }
    val trackNum = track.trim().toIntOrNull()?.takeIf { it > 0 }
    val discNum = disc.trim().toIntOrNull()?.takeIf { it > 0 } ?: 1
    if (trackNum != null) {
        out[MediaStore.Audio.Media.TRACK] = discNum * 1000 + trackNum
    }
    if (api30) {
        val cleanAlbumArtist = albumArtist.trim()
        if (cleanAlbumArtist.isNotEmpty()) {
            out[MediaStore.Audio.Media.ALBUM_ARTIST] = cleanAlbumArtist
        }
        val cleanGenre = genre.trim()
        if (cleanGenre.isNotEmpty()) out[MediaStore.Audio.Media.GENRE] = cleanGenre
        val cleanComposer = composer.trim()
        if (cleanComposer.isNotEmpty()) out[MediaStore.Audio.Media.COMPOSER] = cleanComposer
        if (trackNum != null) {
            out[MediaStore.Audio.Media.CD_TRACK_NUMBER] = trackNum
            out[MediaStore.Audio.Media.DISC_NUMBER] = discNum
        }
    }
    return out
}
