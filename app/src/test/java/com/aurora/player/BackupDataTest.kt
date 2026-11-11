package com.aurora.player

import com.aurora.player.data.BackupData
import com.aurora.player.data.BackupEqPreset
import com.aurora.player.data.BackupHistoryEntry
import com.aurora.player.data.BackupPlaylist
import com.aurora.player.data.BackupQueue
import com.aurora.player.data.BackupSongRef
import com.aurora.player.data.BackupValidation
import com.aurora.player.data.matchSongs
import com.aurora.player.data.toJsonString
import com.aurora.player.data.validateBackup
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.ThemeSettings
import com.aurora.player.online.JsonParser
import com.aurora.player.online.JsonValue
import com.aurora.player.online.obj
import com.aurora.player.online.str
import com.aurora.player.util.toJsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aurora Player — tests del respaldo (spec 005, FR-004, lógica pura). */
class BackupDataTest {

    private fun sampleData(): BackupData = BackupData(
        createdAtMs = 1_700_000_000_000L,
        playlists = listOf(
            BackupPlaylist(
                "Mis favoritas \"del\" finde",
                1_699_000_000_000L,
                1_699_500_000_000L,
                listOf(
                    BackupSongRef(11, "/Music/rock/canción 1.mp3"),
                    BackupSongRef(22, "/Music/pop/canción\\2.flac"),
                ),
            ),
        ),
        favorites = listOf(BackupSongRef(11, "/Music/rock/canción 1.mp3")),
        history = listOf(
            BackupHistoryEntry(BackupSongRef(11, "/Music/rock/canción 1.mp3"), 1_699_900_000_000L, 45_000L),
        ),
        theme = ThemeSettings.DEFAULT.copy(accentHex = "#75D6CE", cornerRadiusDp = 4),
        player = PlayerSettings.DEFAULT.copy(channelBalance = -0.5f, crossfadeSeconds = 3),
        queue = BackupQueue(
            listOf(BackupSongRef(11, "/Music/rock/canción 1.mp3")),
            index = 0,
            positionMs = 12_345L,
        ),
        eqPresets = listOf(BackupEqPreset("Mi preset", listOf(1.5f, 0f, -2.5f))),
    )

    @Test
    fun `escritor JSON escapa comillas barras y saltos`() {
        val v = JsonValue.Obj(
            linkedMapOf(
                "a" to JsonValue.Str("di \"hola\"\ncon \\ barra"),
                "b" to JsonValue.Num(123.0),
                "c" to JsonValue.Num(0.5),
                "d" to JsonValue.Bool(true),
                "e" to JsonValue.Null,
            ),
        )
        val text = v.toJsonString()
        assertEquals(
            "{\"a\":\"di \\\"hola\\\"\\ncon \\\\ barra\",\"b\":123,\"c\":0.5,\"d\":true,\"e\":null}",
            text,
        )
        // El parser propio lo vuelve a leer igual.
        val back = JsonParser.parse(text)
        assertEquals("di \"hola\"\ncon \\ barra", back.obj("a").str())
    }

    @Test
    fun `ida y vuelta del respaldo conserva todo`() {
        val original = sampleData()
        val result = validateBackup(original.toJsonString())
        assertTrue(result is BackupValidation.Valid)
        val data = (result as BackupValidation.Valid).data
        assertEquals(1_700_000_000_000L, data.createdAtMs)
        assertEquals("Mis favoritas \"del\" finde", data.playlists[0].name)
        assertEquals(11L, data.playlists[0].songs[0].id)
        assertEquals("/Music/pop/canción\\2.flac", data.playlists[0].songs[1].path)
        assertEquals(1, data.favorites.size)
        assertEquals(45_000L, data.history[0].durationListenedMs)
        assertEquals("#75D6CE", data.theme.accentHex)
        assertEquals(4, data.theme.cornerRadiusDp)
        assertEquals(-0.5f, data.player.channelBalance)
        assertEquals(3, data.player.crossfadeSeconds)
        assertEquals(12_345L, data.queue.positionMs)
        assertEquals("Mi preset", data.eqPresets[0].name)
        assertEquals(1.5f, data.eqPresets[0].levelsDb[0])
        assertEquals(-2.5f, data.eqPresets[0].levelsDb[2])
    }

    @Test
    fun `archivo que no es JSON se rechaza con motivo`() {
        val result = validateBackup("esto no es json {")
        assertTrue(result is BackupValidation.Invalid)
        assertTrue((result as BackupValidation.Invalid).reason.contains("JSON"))
    }

    @Test
    fun `archivo de otra app se rechaza`() {
        val result = validateBackup("{\"app\":\"Otro Player\",\"version\":1}")
        assertTrue(result is BackupValidation.Invalid)
        assertTrue((result as BackupValidation.Invalid).reason.contains("Aurora Player"))
    }

    @Test
    fun `version distinta se rechaza`() {
        val text = sampleData().toJsonString().replace("\"version\":1", "\"version\":2")
        val result = validateBackup(text)
        assertTrue(result is BackupValidation.Invalid)
        assertTrue((result as BackupValidation.Invalid).reason.contains("Versión"))
    }

    @Test
    fun `sin listas se rechaza`() {
        val text = sampleData().toJsonString().replace("\"playlists\":[{", "\"listas\":[{")
        val result = validateBackup(text)
        assertTrue(result is BackupValidation.Invalid)
    }

    @Test
    fun `entrada danada en una lista invalida todo el respaldo`() {
        // RONDA 36: un favorito sin ruta ya no se descarta en silencio:
        // el archivo entero se rechaza y no se restaura nada, para no
        // borrar los datos reales al restaurar.
        val broken = sampleData().toJsonString().replace(
            "\"favorites\":[{\"id\":11,\"path\":\"/Music/rock/canción 1.mp3\"}]",
            "\"favorites\":[{\"id\":11}]",
        )
        val result = validateBackup(broken)
        assertTrue(result is BackupValidation.Invalid)
    }

    @Test
    fun `campos de lista o cola incompletos invalidan todo el respaldo`() {
        // RONDA 46: el escritor siempre escribe createdAt/updatedAt,
        // durationListenedMs y los tres campos de la cola; si faltan,
        // el archivo está dañado y ya no se completan con ceros ni con
        // una cola vacía.
        val base = sampleData().toJsonString()
        val sinDuracion = base.replace(",\"durationListenedMs\":45000", "")
        assertTrue(validateBackup(sinDuracion) is BackupValidation.Invalid)
        val sinIndiceCola = base.replace(",\"index\":0", "")
        assertTrue(validateBackup(sinIndiceCola) is BackupValidation.Invalid)
    }

    @Test
    fun `ajustes invalidos caen a valores por defecto sin romper`() {
        val base = sampleData().copy(
            theme = ThemeSettings.DEFAULT.copy(accentHex = "#75D6CE"),
            player = PlayerSettings.DEFAULT.copy(crossfadeSeconds = 3),
        )
        val broken = base.toJsonString()
            .replace("\"accentHex\":\"#75D6CE\"", "\"accentHex\":\"no-es-un-hex\"")
            .replace("\"crossfadeSeconds\":3", "\"crossfadeSeconds\":99")
            .replace("\"themeMode\":\"AMOLED\"", "\"themeMode\":\"MODO_INVENTADO\"")
        val result = validateBackup(broken)
        assertTrue(result is BackupValidation.Valid)
        val data = (result as BackupValidation.Valid).data
        assertEquals("#8CCBEB", data.theme.accentHex)
        assertEquals(12, data.player.crossfadeSeconds)
        assertEquals(ThemeSettings.DEFAULT.themeMode, data.theme.themeMode)
    }

    @Test
    fun `emparejar por id luego por ruta y resto ausente`() {
        val refs = listOf(
            BackupSongRef(11, "/a/1.mp3"), // por id
            BackupSongRef(99, "/b/2.mp3"), // por ruta (id cambió)
            BackupSongRef(77, "/c/3.mp3"), // ausente
        )
        val library = listOf(11L to "/a/1.mp3", 55L to "/b/2.mp3")
        val (found, missing) = matchSongs(refs, library)
        assertEquals(2, found.size)
        assertEquals(11L, found[0].first)
        assertEquals(55L, found[1].first) // id nuevo de la biblioteca
        assertEquals(1, missing.size)
        assertEquals(77L, missing[0].id)
    }
}
