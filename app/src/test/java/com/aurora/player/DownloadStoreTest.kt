package com.aurora.player

import com.aurora.player.data.DownloadRecord
import com.aurora.player.data.DownloadStatus
import com.aurora.player.data.decodeDownloadRecord
import com.aurora.player.data.encodeDownloadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStoreTest {

    private val record = DownloadRecord(
        videoId = "abc123",
        title = "Runaway",
        artist = "AURORA",
        sizeBytes = 4_123_456L,
        downloadedAtMs = 1_791_234_567_890L,
        status = DownloadStatus.READY,
        uri = "content://media/external/downloads/42",
    )

    @Test
    fun encodeDecodeRoundTripConservaTodo() {
        val decoded = decodeDownloadRecord(encodeDownloadRecord(record))!!
        assertEquals(record, decoded)
    }

    @Test
    fun encodeDecodeSinUriDaNull() {
        val sinUri = record.copy(uri = null)
        val decoded = decodeDownloadRecord(encodeDownloadRecord(sinUri))!!
        assertEquals(sinUri, decoded)
        assertNull(decoded.uri)
    }

    @Test
    fun encodeUsaSeparadorUnitarioYSieteCampos() {
        val encoded = encodeDownloadRecord(record)
        assertEquals(7, encoded.split('\u001F').size)
    }

    @Test
    fun decodeDevuelveNullConCamposIncompletos() {
        assertNull(decodeDownloadRecord("a\u001Fb\u001Fc"))
        assertNull(decodeDownloadRecord(""))
    }

    @Test
    fun decodeDevuelveNullConNumerosOEstadoInvalidos() {
        val base = encodeDownloadRecord(record)
        val conSizeMalo = base.replace("4123456", "no-numero")
        assertNull(decodeDownloadRecord(conSizeMalo))
        val conEstadoMalo = base.replace("READY", "DESCONOCIDO")
        assertNull(decodeDownloadRecord(conEstadoMalo))
    }

    @Test
    fun decodeAceptaTituloConBarraVertical() {
        val conBarra = record.copy(title = "A | B")
        assertEquals(conBarra, decodeDownloadRecord(encodeDownloadRecord(conBarra))!!)
    }
}
