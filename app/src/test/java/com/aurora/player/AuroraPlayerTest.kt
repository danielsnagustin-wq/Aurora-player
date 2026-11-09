package com.aurora.player

import com.aurora.player.alarm.AlarmSource
import com.aurora.player.alarm.decodeAlarmSource
import com.aurora.player.alarm.describeNextAlarm
import com.aurora.player.alarm.encodeAlarmSource
import com.aurora.player.alarm.formatAlarmTime
import com.aurora.player.alarm.nextAlarmTime
import com.aurora.player.audio.BassPref
import com.aurora.player.audio.EnergyPref
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.audio.TreblePref
import com.aurora.player.audio.clampGainDb
import com.aurora.player.audio.dbToLinear
import com.aurora.player.audio.freqToX
import com.aurora.player.audio.parseReplayGainDb
import com.aurora.player.audio.selectReplayGainDb
import com.aurora.player.audio.suggestCurve
import com.aurora.player.data.PlayHistoryEntity
import com.aurora.player.data.trackAndDisc
import com.aurora.player.ui.player.formatSpeed
import com.aurora.player.ui.player.swipeDirection
import com.aurora.player.ui.stats.SongInfo
import com.aurora.player.ui.stats.computeYearStats
import com.aurora.player.ui.stats.yearRange
import com.aurora.player.util.LrcLine
import com.aurora.player.util.LrcParser
import com.aurora.player.util.M3u
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.buildMetadataUpdate
import com.aurora.player.util.codecDisplayName
import com.aurora.player.lyrics.LyricsProvider
import com.aurora.player.lyrics.OnlineLyrics
import com.aurora.player.lyrics.buildLrclibGetUrl
import com.aurora.player.lyrics.buildLrclibSearchUrl
import com.aurora.player.lyrics.extractJsonString
import com.aurora.player.lyrics.fetchLyricsFirst
import com.aurora.player.lyrics.parseLrclibResponse
import com.aurora.player.lyrics.plainToLrcLines
import com.aurora.player.lyrics.resolveLyrics
import com.aurora.player.online.AudioFormat
import com.aurora.player.online.JsonParseException
import com.aurora.player.online.JsonParser
import com.aurora.player.online.PLAYER_CLIENTS
import com.aurora.player.online.SEARCH_CLIENT
import com.aurora.player.online.buildPlayerBody
import com.aurora.player.online.buildSearchBody
import com.aurora.player.online.jsonEscape
import com.aurora.player.online.parseDurationToSec
import com.aurora.player.online.parsePlayerFormats
import com.aurora.player.online.parseSearchResults
import com.aurora.player.online.selectAudioFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aurora Player — tests unitarios básicos (Etapa 1).
 * Sin Android: solo lógica pura (M3U, tiempo, presets de EQ).
 * Los tests de MediaStore / Room / ExoPlayer van en androidTest o
 * con Robolectric en un paso posterior; no se fingen aquí.
 */
class AuroraPlayerTest {

    @Test
    fun `formato de duracion 0 es 0-00`() {
        assertEquals("0:00", TimeFormat.formatDuration(0))
    }

    @Test
    fun `formato de duracion 90 segundos es 1-30`() {
        assertEquals("1:30", TimeFormat.formatDuration(90_000))
    }

    @Test
    fun `formato de duracion 61 minutos`() {
        assertEquals("61:05", TimeFormat.formatDuration(3_665_000))
    }

    @Test
    fun `tiempo total 90 minutos`() {
        assertEquals("1h 30min", TimeFormat.formatTotalListened(5_400_000))
    }

    @Test
    fun `M3U se construye y se vuelve a leer`() {
        val entries = listOf(
            M3u.Entry(200, "Artista - Canción", "/Music/cancion.mp3"),
            M3u.Entry(180, "Otro - Tema", "tema.flac"),
        )
        val text = M3u.build(entries)
        val parsed = M3u.parse(text)
        assertEquals(2, parsed.size)
        assertEquals("/Music/cancion.mp3", parsed[0].path)
        assertEquals(200L, parsed[0].seconds)
        assertEquals("tema.flac", parsed[1].path)
    }

    @Test
    fun `M3U ignora directivas desconocidas y lineas vacias`() {
        val parsed = M3u.parse("#EXTM3U\n#EXTINF:120,A - B\n\n/song.mp3\n#UNKNOWN:1\n")
        assertEquals(1, parsed.size)
        assertEquals("/song.mp3", parsed[0].path)
    }

    @Test
    fun `presets de EQ tienen 10 bandas y estan en rango`() {
        assertEquals(35, EqualizerManager.PRESETS.size)
        for (preset in EqualizerManager.PRESETS) {
            assertEquals(10, preset.levelsDb.size)
            assertTrue(preset.levelsDb.all { it in -15f..15f })
        }
        assertTrue(EqualizerManager.PRESETS.all { it.levelsDb[0] == 0f }.not())
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            EqualizerManager.PRESETS.first { it.name == "Plano" }.levelsDb)
    }

    @Test
    fun `LRC parsea lineas con tiempo e ignora etiquetas`() {
        val lines = LrcParser.parse("[ti:Título]\n[00:01.00]Primera\n[00:05.50]Segunda\n[ar:Artista]\n")
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals("Primera", lines[0].text)
        assertEquals(5500L, lines[1].timeMs)
        assertEquals("Segunda", lines[1].text)
    }

    @Test
    fun `LRC ordena por tiempo y la linea actual es la ultima pasada`() {
        val lines = LrcParser.parse("[00:10.00]Diez\n[00:02.00]Dos\n[00:05.00]Cinco\n")
        assertEquals("Dos", lines[0].text)
        assertEquals(0, LrcParser.currentLineIndex(lines, 3000))
        assertEquals(1, LrcParser.currentLineIndex(lines, 7000))
        assertEquals(-1, LrcParser.currentLineIndex(lines, 1000))
    }

    @Test
    fun `TRACK 1003 es pista 3 disco 1`() {
        assertEquals(3 to 1, trackAndDisc(1003, 0, 0))
    }

    @Test
    fun `columnas modernas mandan sobre TRACK`() {
        assertEquals(5 to 2, trackAndDisc(2005, 5, 2))
    }

    @Test
    fun `sin dato de pista el disco es 1`() {
        assertEquals(0 to 1, trackAndDisc(0, 0, 0))
    }

    @Test
    fun `editor solo escribe campos con valor`() {
        val out = buildMetadataUpdate("X", "", "", "", "", "", "", "", "", true)
        assertEquals(1, out.size)
        assertEquals("X", out["title"])
    }

    @Test
    fun `editor combina pista y disco en TRACK`() {
        val out = buildMetadataUpdate("", "", "", "", "", "", "", "3", "2", true)
        assertEquals(2003, out["track"])
        assertEquals(3, out["cd_track_number"])
        assertEquals(2, out["disc_number"])
    }

    @Test
    fun `sin API 30 no hay columnas nuevas`() {
        val out = buildMetadataUpdate("", "", "Y", "", "Rock", "", "", "5", "1", false)
        assertTrue(!out.containsKey("album_artist"))
        assertTrue(!out.containsKey("genre"))
        assertTrue(!out.containsKey("cd_track_number"))
        assertEquals(1005, out["track"])
    }

    @Test
    fun `ano invalido no se escribe`() {
        assertTrue(!buildMetadataUpdate("", "", "", "", "", "", "abc", "", "", true).containsKey("year"))
        assertTrue(!buildMetadataUpdate("", "", "", "", "", "", "0", "", "", true).containsKey("year"))
        assertEquals(2020, buildMetadataUpdate("", "", "", "", "", "", "2020", "", "", true)["year"])
    }

    @Test
    fun `deslizar a la izquierda es siguiente y a la derecha anterior`() {
        assertEquals(1, swipeDirection(-150f))
        assertEquals(-1, swipeDirection(150f))
        assertEquals(0, swipeDirection(50f))
        assertEquals(0, swipeDirection(-99f))
    }

    @Test
    fun `la velocidad se lee con sus decimales reales`() {
        assertEquals("1", formatSpeed(1f))
        assertEquals("1.25", formatSpeed(1.25f))
        assertEquals("1.5", formatSpeed(1.5f))
        assertEquals("0.75", formatSpeed(0.75f))
        assertEquals("2", formatSpeed(2f))
    }

    // RONDA 6: ReplayGain real (funciones puras del procesador).

    @Test
    fun `la etiqueta ReplayGain se lee como numero`() {
        assertEquals(-6.23f, parseReplayGainDb("-6.23 dB")!!, 0.001f)
        assertEquals(0f, parseReplayGainDb("0 dB")!!, 0.001f)
        assertEquals(3.5f, parseReplayGainDb("+3.5 dB")!!, 0.001f)
        assertNull(parseReplayGainDb("sin numero"))
    }

    @Test
    fun `el modo ReplayGain elige la ganancia correcta`() {
        assertEquals(0f, selectReplayGainDb("off", -6f, -8f, 0f), 0.001f)
        assertEquals(-6f, selectReplayGainDb("track", -6f, -8f, 0f), 0.001f)
        assertEquals(-8f, selectReplayGainDb("album", -6f, -8f, 0f), 0.001f)
        assertEquals(-6f, selectReplayGainDb("smart", -6f, -8f, 0f), 0.001f)
        assertEquals(-8f, selectReplayGainDb("smart", null, -8f, 0f), 0.001f)
        assertEquals(0f, selectReplayGainDb("smart", null, null, 0f), 0.001f)
        assertEquals(-4f, selectReplayGainDb("track", -6f, null, 2f), 0.001f)
    }

    @Test
    fun `la ganancia se recorta para no distorsionar`() {
        assertEquals(12f, clampGainDb(20f), 0.001f)
        assertEquals(-30f, clampGainDb(-40f), 0.001f)
        assertEquals(12f, selectReplayGainDb("track", 10f, null, 5f), 0.001f)
        assertEquals(1.0, dbToLinear(0f), 0.001)
        assertEquals(0.1, dbToLinear(-20f), 0.001)
    }

    @Test
    fun `LRC con varias etiquetas en una linea da una linea por tiempo`() {
        val lines = LrcParser.parse("[00:01.00][00:05.00]coro\n[ti:Prueba]\n[00:10.00]fin")
        assertEquals(3, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals("coro", lines[0].text)
        assertEquals(5000L, lines[1].timeMs)
        assertEquals("coro", lines[1].text)
        assertEquals(10000L, lines[2].timeMs)
        assertEquals("fin", lines[2].text)
    }

    @Test
    fun `el nombre del codec se lee en claro`() {
        assertEquals("FLAC", codecDisplayName("audio/flac"))
        assertEquals("MP3", codecDisplayName("audio/mpeg"))
        assertEquals("AAC", codecDisplayName("audio/mp4"))
        assertEquals("WAV", codecDisplayName("audio/x-wav"))
        assertEquals("audio/xyz", codecDisplayName("audio/xyz"))
    }

    // RONDA 7 P1: asistente de EQ.
    @Test
    fun `el asistente propone mas graves en electronica`() {
        val curve = suggestCurve("Electrónica", BassPref.MORE, TreblePref.BALANCED, EnergyPref.NORMAL)
        assertEquals(10, curve.size)
        assertEquals(11f, curve[0], 0.001f) // base 7 + 4
        assertEquals(7f, curve[9], 0.001f) // base 7, sin cambio de agudos
    }

    @Test
    fun `la curva del asistente nunca pasa de 15 dB`() {
        val curve = suggestCurve("Graves", BassPref.MORE, TreblePref.MORE, EnergyPref.STRONG)
        assertTrue(curve.all { it in -15f..15f })
        assertEquals(15f, curve[0], 0.001f) // 8 + 4 = 12, x1.25 = 15
    }

    @Test
    fun `la posicion en frecuencia es logaritmica y creciente`() {
        assertEquals(0f, freqToX(60, 60, 14000), 0.001f)
        assertEquals(1f, freqToX(14000, 60, 14000), 0.001f)
        assertTrue(freqToX(1000, 60, 14000) > freqToX(100, 60, 14000))
    }

    // RONDA 7 P3: Tu año.
    @Test
    fun `el rango de un anio empieza el 1 de enero a las cero`() {
        val (start, end) = yearRange(2026)
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = start
        assertEquals(2026, cal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.JANUARY, cal.get(java.util.Calendar.MONTH))
        assertEquals(1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY))
        cal.timeInMillis = end
        assertEquals(2027, cal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.JANUARY, cal.get(java.util.Calendar.MONTH))
    }

    @Test
    fun `tu anio resume el historial del anio`() {
        val (start, end) = yearRange(2026)
        val dayMs = 24L * 60 * 60 * 1000
        val songs = mapOf(
            1L to SongInfo("A", "X", "Electrónica"),
            2L to SongInfo("B", "Y", "Rock"),
        )
        val history = listOf(
            PlayHistoryEntity(1, start + 5 * dayMs, 60000),
            PlayHistoryEntity(1, start + 6 * dayMs, 60000),
            PlayHistoryEntity(2, start + 6 * dayMs, 45000),
            PlayHistoryEntity(1, start - dayMs, 60000), // 2025: fuera del año
        )
        val inYear = history.filter { it.playedAt in start until end }
        val stats = computeYearStats(2026, inYear, songs)
        assertEquals(3, stats.totalPlays)
        assertEquals(165000L, stats.totalListenedMs)
        assertEquals("A", stats.topSongs[0].first.title)
        assertEquals(2, stats.topSongs[0].second)
        assertEquals("X", stats.topArtists[0].first)
        assertEquals("Electrónica", stats.topGenres[0].first)
        assertEquals(2, stats.distinctDays)
        assertEquals(listOf(1L, 2L), stats.topSongIds)
    }

    @Test
    fun `tu anio cuenta canciones borradas solo en los totales`() {
        val (start, _) = yearRange(2026)
        val stats = computeYearStats(
            2026,
            listOf(PlayHistoryEntity(99, start + 1000, 60000)),
            emptyMap(),
        )
        assertEquals(1, stats.totalPlays)
        assertTrue(stats.topSongs.isEmpty())
        assertTrue(stats.topSongIds.isEmpty())
    }

    // RONDA 7 P4: alarma musical.
    @Test
    fun `la alarma de las 7 30 suena hoy si aun no paso`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.JANUARY, 15, 6, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val now = cal.timeInMillis
        val next = nextAlarmTime(7, 30, now)
        cal.timeInMillis = next
        assertEquals(15, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(7, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(java.util.Calendar.MINUTE))
        assertEquals("Sonará hoy a las 07:30", describeNextAlarm(7, 30, now))
    }

    @Test
    fun `la alarma de las 7 30 suena manana si ya paso`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.JANUARY, 15, 8, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val now = cal.timeInMillis
        val next = nextAlarmTime(7, 30, now)
        cal.timeInMillis = next
        assertEquals(16, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(7, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals("Sonará mañana a las 07:30", describeNextAlarm(7, 30, now))
    }

    @Test
    fun `la hora de la alarma se escribe con cero`() {
        assertEquals("07:30", formatAlarmTime(7, 30))
        assertEquals("00:05", formatAlarmTime(0, 5))
    }

    @Test
    fun `la fuente de la alarma se guarda y se lee igual`() {
        assertEquals(AlarmSource.ShuffleAll, decodeAlarmSource(encodeAlarmSource(AlarmSource.ShuffleAll)))
        assertEquals(
            AlarmSource.Song(123, "Mi canción"),
            decodeAlarmSource(encodeAlarmSource(AlarmSource.Song(123, "Mi canción"))),
        )
        assertEquals(
            AlarmSource.Playlist(5, "Favoritas"),
            decodeAlarmSource(encodeAlarmSource(AlarmSource.Playlist(5, "Favoritas"))),
        )
        assertEquals(AlarmSource.ShuffleAll, decodeAlarmSource("roto"))
        assertEquals(AlarmSource.ShuffleAll, decodeAlarmSource(null))
        assertEquals(AlarmSource.ShuffleAll, decodeAlarmSource(""))
    }

    // RONDA 7 P2: letras online (LRCLIB).

    private class FakeLyricsProvider(
        override val name: String,
        private val result: OnlineLyrics?,
        private val fail: Boolean = false,
    ) : LyricsProvider {
        override suspend fun fetch(
            track: String,
            artist: String,
            album: String?,
            durationSec: Long,
        ): OnlineLyrics? {
            if (fail) throw RuntimeException("caído")
            return result
        }
    }

    @Test
    fun buildLrclibGetUrlCodificaTituloArtistaAlbumYDuracion() {
        assertEquals(
            "https://lrclib.net/api/get?track_name=Caf%C3%A9+del+Mar&artist_name=Energy+52&album_name=Caf%C3%A9+del+Mar&duration=234",
            buildLrclibGetUrl("Café del Mar", "Energy 52", "Café del Mar", 234),
        )
        assertEquals(
            "https://lrclib.net/api/get?track_name=Blue&artist_name=Eiffel+65",
            buildLrclibGetUrl("Blue", "Eiffel 65", null, 0),
        )
    }

    @Test
    fun buildLrclibSearchUrlCodificaLaConsulta() {
        assertEquals(
            "https://lrclib.net/api/search?q=Blue+Eiffel+65",
            buildLrclibSearchUrl("Blue Eiffel 65"),
        )
    }

    @Test
    fun extractJsonStringLeeCampoConEscapesYNull() {
        val json = """{"syncedLyrics":"[00:01.00]Hola \"mundo\"\nsegunda línea","plainLyrics":null}"""
        assertEquals("[00:01.00]Hola \"mundo\"\nsegunda línea", extractJsonString(json, "syncedLyrics"))
        assertNull(extractJsonString(json, "plainLyrics"))
        assertNull(extractJsonString(json, "noExiste"))
        assertNull(extractJsonString("""{"a": 5}""", "a"))
    }

    @Test
    fun parseLrclibResponseAceptaSnakeCaseYDescartaVacios() {
        val snake = """{"synced_lyrics":"[00:01.00]Hola","plain_lyrics":"Hola"}"""
        val parsed = parseLrclibResponse(snake)
        assertEquals("[00:01.00]Hola", parsed?.syncedLrc)
        assertEquals("Hola", parsed?.plainText)
        assertNull(parseLrclibResponse("""{"syncedLyrics":"","plainLyrics":""}"""))
        assertNull(parseLrclibResponse("""{"trackName":"Blue"}"""))
    }

    @Test
    fun plainToLrcLinesConservaOrdenYSinSincronizar() {
        val lines = plainToLrcLines("uno\ndos\n\ntres")
        assertEquals(listOf("uno", "dos", "tres"), lines.map { it.text })
        assertEquals(listOf(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE), lines.map { it.timeMs })
    }

    @Test
    fun resolveLyricsPriorizaElLrcLocal() {
        val lines = resolveLyrics(
            "[00:01.00]Local",
            OnlineLyrics("[00:01.00]Online", "Online"),
        )
        assertEquals(listOf("Local"), lines.map { it.text })
    }

    @Test
    fun resolveLyricsUsaLaOnlineSincronizadaSiNoHayLocal() {
        val lines = resolveLyrics(null, OnlineLyrics("[00:02.00]Online", null))
        assertEquals(listOf("Online"), lines.map { it.text })
        assertEquals(2000L, lines.first().timeMs)
    }

    @Test
    fun resolveLyricsUsaLaOnlineSinTiemposComoUltimoRecurso() {
        val lines = resolveLyrics(null, OnlineLyrics(null, "verso uno\nverso dos"))
        assertEquals(listOf("verso uno", "verso dos"), lines.map { it.text })
        assertEquals(Long.MAX_VALUE, lines.first().timeMs)
        assertEquals(emptyList<LrcLine>(), resolveLyrics(null, null))
    }

    @Test
    fun fetchLyricsFirstPruebaProveedoresEnOrdenYSaltaErrores() = runBlocking {
        val providers = listOf(
            FakeLyricsProvider("Caido", null, fail = true),
            FakeLyricsProvider("Vacio", null),
            FakeLyricsProvider("Bueno", OnlineLyrics(null, "letra")),
        )
        assertEquals("letra", fetchLyricsFirst(providers, "Blue", "Eiffel 65", null, 0)?.plainText)
        assertNull(
            fetchLyricsFirst(
                listOf(FakeLyricsProvider("Vacio", null)),
                "Blue", "Eiffel 65", null, 0,
            ),
        )
    }

    // 002-online-youtube: JSON mínimo + InnerTube.

    @Test
    fun jsonParserLeeObjetosAnidadosYEscapes() {
        val parsed = JsonParser.parse(
            """{"a":{"b":[1,2.5,"x\ny","é"],"c":true,"d":null},"e":"A"}""",
        )
        assertEquals("x\ny", parsed.obj("a")?.obj("b")?.arr()?.get(2)?.str())
        assertEquals("é", parsed.obj("a")?.obj("b")?.arr()?.get(3)?.str())
        assertEquals(2.5, parsed.obj("a")?.obj("b")?.arr()?.get(1)?.long()?.toDouble() ?: 0.0, 0.001)
        assertEquals(true, parsed.obj("a")?.obj("c")?.bool())
        assertEquals(null, parsed.obj("a")?.obj("d")?.str())
        assertEquals("A", parsed.obj("e")?.str())
    }

    @Test(expected = JsonParseException::class)
    fun jsonParserRechazaJsonRoto() {
        JsonParser.parse("""{"a": [1, 2}""")
    }

    @Test
    fun jsonEscapeEscapaComillasYBarra() {
        assertEquals("a\\\"b\\\\c", jsonEscape("a\"b\\c"))
        assertEquals("línea1\\nlínea2", jsonEscape("línea1\nlínea2"))
    }

    @Test
    fun buildSearchBodyLlevaQueryYClienteWebRemix() {
        val body = buildSearchBody("Blue \"Eiffel\" 65")
        assertEquals(true, body.contains("WEB_REMIX"))
        assertEquals(true, body.contains("Blue \\\"Eiffel\\\" 65"))
        assertEquals(true, body.contains("EgWKAQIIAWoQEAMQBBAJEAoQGAgAEEAMQBg%3D%3D"))
    }

    @Test
    fun buildPlayerBodyLlevaVideoIdYCliente() {
        val body = buildPlayerBody(PLAYER_CLIENTS.first(), "abc123")
        assertEquals(true, body.contains("\"videoId\":\"abc123\""))
        assertEquals(true, body.contains("ANDROID_VR"))
        assertEquals(true, body.contains("contentCheckOk"))
    }

    @Test
    fun parseDurationToSecConvierte() {
        assertEquals(204L, parseDurationToSec("3:24"))
        assertEquals(3723L, parseDurationToSec("1:02:03"))
        assertNull(parseDurationToSec("hola"))
        assertNull(parseDurationToSec("3"))
        assertNull(parseDurationToSec(""))
    }

    @Test
    fun parseSearchResultsExtraeCanciones() {
        val json = """{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Blue"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Eiffel 65"},{"text":" • "},{"text":"Europop"},{"text":" • "},{"text":"3:24"}]}}}],"playlistItemData":{"videoId":"abc123"},"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/small"},{"url":"https://i.ytimg.com/big"}]}}}}}]}}]}}}}]}}}"""
        val results = parseSearchResults(json)
        assertEquals(1, results.size)
        val track = results.first()
        assertEquals("abc123", track.videoId)
        assertEquals("Blue", track.title)
        assertEquals("Eiffel 65", track.artist)
        assertEquals("Europop", track.album)
        assertEquals(204L, track.durationSec)
        assertEquals("https://i.ytimg.com/big", track.thumbnailUrl)
        assertEquals(emptyList<com.aurora.player.online.OnlineTrack>(), parseSearchResults("no es json"))
        assertEquals(emptyList<com.aurora.player.online.OnlineTrack>(), parseSearchResults("""{"contents":{}}"""))
    }

    @Test
    fun parsePlayerFormatsDescartaCifradosYVideo() {
        val json = """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[{"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","signatureCipher":"s=xxx","bitrate":160000},{"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","url":"https://example.com/140","bitrate":128000},{"itag":22,"mimeType":"video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"","url":"https://example.com/22","bitrate":2000000}]}}"""
        val formats = parsePlayerFormats(json)
        assertEquals(listOf(140L), formats?.map { it.itag })
        assertEquals("https://example.com/140", formats?.first()?.url)
        assertEquals(emptyList<AudioFormat>(), parsePlayerFormats("""{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[{"itag":251,"mimeType":"audio/webm","signatureCipher":"s=xxx","bitrate":160000}]}}"""))
        assertNull(parsePlayerFormats("""{"playabilityStatus":{"status":"ERROR","reason":"No disponible"}}"""))
    }

    @Test
    fun selectAudioFormatPrefiereOpusLuegoAac() {
        val opus = AudioFormat(251, "audio/webm", "https://x/251", 160000)
        val aac = AudioFormat(140, "audio/mp4", "https://x/140", 128000)
        val low = AudioFormat(139, "audio/mp4", "https://x/139", 48000)
        assertEquals(opus, selectAudioFormat(listOf(aac, opus, low)))
        assertEquals(aac, selectAudioFormat(listOf(low, aac)))
        assertEquals(low, selectAudioFormat(listOf(low)))
        assertNull(selectAudioFormat(emptyList()))
    }

    @Test
    fun playerClientsEnOrdenYCompletos() {
        assertEquals("WEB_REMIX", SEARCH_CLIENT.name)
        assertEquals("67", SEARCH_CLIENT.id)
        assertEquals("ANDROID_VR", PLAYER_CLIENTS.first().name)
        for (client in PLAYER_CLIENTS) {
            assertEquals(true, client.name.isNotBlank())
            assertEquals(true, client.id.isNotBlank())
            assertEquals(true, client.version.isNotBlank())
            assertEquals(true, client.host.isNotBlank())
        }
    }
}
