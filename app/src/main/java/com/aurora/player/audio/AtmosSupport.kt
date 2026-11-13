package com.aurora.player.audio

import android.content.Context
import android.media.MediaCodecList
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities

/**
 * Slice 5 (Dolby Atmos real, 2026-11-12): Atmos en Android es
 * E-AC-3 JOC (Dolby Digital Plus con Atmos) o AC-4, pasado al
 * hardware por AudioTrack (offload); nunca se decodifica con
 * MediaCodec y nunca se simula. YouTube Music NO tiene Atmos: la
 * única fuente real son archivos locales con E-AC-3 JOC.
 *
 * Detección ANTES de ofrecer nada: si el dispositivo no lo
 * soporta, la función no aparece (R-24). La licencia Dolby
 * (DAX3) decide en el teléfono del usuario; un "no soportado"
 * también es un resultado válido, no un fracaso.
 *
 * Verificado en los AAR reales de Media3 1.4.1: C.ENCODING_E_AC3,
 * C.ENCODING_E_AC3_JOC y C.ENCODING_AC4 existen en media3-common,
 * y AudioCapabilities vive en androidx.media3.exoplayer.audio
 * (NO en common.audio como decía el plan inicial).
 */
enum class AtmosSupport {
    /** Ni DD+ ni Atmos: el dispositivo no pasa estos bitstreams. */
    NONE,

    /** Dolby Digital Plus (E-AC-3) sin la extensión Atmos. */
    DD_PLUS,

    /** E-AC-3 JOC o AC-4: el dispositivo puede pasar Atmos. */
    ATMOS,
}

/**
 * Mapeo puro (testeable en JVM): qué nivel de soporte hay con lo
 * que reporta el dispositivo. Matiz honesto: un decodificador
 * AC-4 solo dice que el dispositivo lee AC-4, que PUEDE llevar
 * Atmos; la insignia de una pista concreta exige además el JOC
 * real en el archivo (paso 2), no solo el códec.
 */
internal fun mapAtmosSupport(
    supportsEAc3: Boolean,
    supportsEAc3Joc: Boolean,
    supportsAc4: Boolean,
): AtmosSupport = when {
    supportsEAc3Joc || supportsAc4 -> AtmosSupport.ATMOS
    supportsEAc3 -> AtmosSupport.DD_PLUS
    else -> AtmosSupport.NONE
}

/**
 * Detección del dispositivo, una vez al arrancar (no por pista).
 * AudioCapabilities cubre E-AC-3 y E-AC-3 JOC; AC-4 no tiene
 * constante de encoding usable aquí y se busca por MediaCodecList.
 * Cualquier fallo (sin servicio de audio, sin códecs) es NONE,
 * nunca un crash.
 *
 * Se caza Throwable, no solo Exception, a propósito: en JVM el
 * mero hecho de tocar AudioCapabilities tira
 * ExceptionInInitializerError (un Error) porque su inicializador
 * estático construye un android.util.SparseArray, que en los stubs
 * de los tests tira RuntimeException("Stub!") (reproducido en
 * local 2026-11-13, run 43427589515). En el teléfono real
 * SparseArray existe y esto no pasa; aquí la detección es
 * best-effort y cualquier fallo vale NONE.
 */
fun detectAtmosSupport(context: Context): AtmosSupport {
    val caps = try {
        AudioCapabilities.getCapabilities(context)
    } catch (_: Throwable) {
        null
    }
    val supportsEAc3 = caps?.supportsEncoding(C.ENCODING_E_AC3) == true
    val supportsJoc = caps?.supportsEncoding(C.ENCODING_E_AC3_JOC) == true
    val supportsAc4 = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codec ->
            !codec.isEncoder && codec.supportedTypes.any {
                it.equals("audio/ac4", ignoreCase = true)
            }
        }
    } catch (_: Throwable) {
        false
    }
    return mapAtmosSupport(supportsEAc3, supportsJoc, supportsAc4)
}
