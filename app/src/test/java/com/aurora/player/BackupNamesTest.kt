package com.aurora.player

import com.aurora.player.util.backupFileName
import com.aurora.player.util.backupFolderDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BackupNamesTest {

    @Test
    fun nombreLlevaFechaYHora() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 10, 9, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // La zona horaria del teléfono manda en el nombre; solo se
        // exige el formato, no la hora exacta en UTC.
        val name = backupFileName(cal.timeInMillis)
        assertTrue(name.startsWith("aurora-player-respaldo-"))
        assertTrue(name.endsWith(".json"))
        assertEquals(5, name.split("-").size)
    }

    @Test
    fun carpetaMuestraSuNombre() {
        assertEquals(
            "Music",
            backupFolderDisplayName("content://com.android.externalstorage.documents/tree/primary%3AMusic"),
        )
        assertEquals(
            "Download/Respaldos",
            backupFolderDisplayName("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FRespaldos"),
        )
        assertEquals("", backupFolderDisplayName(""))
    }
}
