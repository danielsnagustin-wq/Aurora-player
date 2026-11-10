package com.aurora.player.util

import android.content.Context
import com.aurora.player.audio.DsdInfo
import com.aurora.player.audio.dopCarrierRateHz
import com.aurora.player.audio.dopLabel
import com.aurora.player.audio.isDsdFile
import com.aurora.player.audio.parseDffHeader
import com.aurora.player.audio.parseDsfHeader
import com.aurora.player.model.Song

/**
 * Ronda USB Serie B — leer la cabecera DSD de un archivo de la
 * biblioteca (solo los primeros 4 KB, por el ContentResolver) y
 * devolver su info real: formato, tasa y canales. Sin permisos
 * nuevos: el archivo ya está en la biblioteca del usuario. Si no
 * se puede leer o no es un DSF/DFF válido, null (nunca se
 * inventa una tasa).
 */
fun loadDsdInfo(context: Context, song: Song): DsdInfo? {
    if (!isDsdFile(song.path, song.mimeType)) return null
    val header = try {
        context.contentResolver.openInputStream(song.uri)?.use { input ->
            val buf = ByteArray(4096)
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n <= 0) break
                read += n
            }
            buf.copyOf(read)
        } ?: return null
    } catch (_: Exception) {
        return null
    }
    return parseDsfHeader(header) ?: parseDffHeader(header)
}

/**
 * Línea de calidad para un DSD: "DSD64 · 176.4 kHz (DoP)". La
 * tasa es la portadora DoP, que es la que vería el DAC; la nota
 * entre paréntesis recuerda que es DoP, no PCM.
 */
fun dsdQualityLine(info: DsdInfo): String {
    val carrierKhz = dopCarrierRateHz(info.sampleRateHz) / 1000.0
    val carrier = if (carrierKhz % 1.0 == 0.0) {
        "${carrierKhz.toInt()} kHz"
    } else {
        "$carrierKhz kHz"
    }
    return "${dopLabel(info.sampleRateHz)} · $carrier (DoP)"
}
