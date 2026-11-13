package com.aurora.player

import com.aurora.player.online.buildBrowseBody
import com.aurora.player.online.parseAlbumTracks
import com.aurora.player.online.parseArtistBrowseId
import com.aurora.player.online.parseArtistPage
import com.aurora.player.online.parseArtistReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Página de artista en YouTube Music (R8b): el channelId sale del run
 * del artista en los resultados de búsqueda, y la página (browse)
 * trae canciones top, álbumes, sencillos y vídeos con años reales.
 * Los fixtures son sintéticos (estructura documentada, no respuestas
 * reales copiadas): si YouTube cambia la forma, estos tests no lo
 * cazan; lo caza el teléfono.
 */
class ArtistPageTest {

    private val searchJson = """
{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
{"musicResponsiveListItemRenderer":{"flexColumns":[
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"STONED TO DEATH"}]}}},
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Stoned Level","navigationEndpoint":{"browseEndpoint":{"browseId":"UCstoned123"}}},{"text":" • "},{"text":"Muerte"}]}}}
],"navigationEndpoint":{"watchEndpoint":{"videoId":"vid1"}}}},
{"musicResponsiveListItemRenderer":{"flexColumns":[
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Otra canción"}]}}},
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Otro Artista","navigationEndpoint":{"browseEndpoint":{"browseId":"UCotro999"}}}]}}}
],"navigationEndpoint":{"watchEndpoint":{"videoId":"vid2"}}}}
]}}]}}}]}}}
    """.trimIndent()

    private val pageJson = """
{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Canciones principales"}]}}},"contents":[
{"musicResponsiveListItemRenderer":{"flexColumns":[
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"STONED TO DEATH"}]}}},
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Stoned Level"},{"text":" • "},{"text":"3:24"}]}}}
],"navigationEndpoint":{"watchEndpoint":{"videoId":"vid9"}}}}
]}}},
{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Álbumes"}]}}},"contents":[
{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"STONED TO DEATH","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREalbum1"}}}]},"subtitle":{"runs":[{"text":"Álbum"},{"text":" • "},{"text":"2025"}]},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/album.jpg"}]}}}}}
]}}},
{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Singles y EPs"}]},"moreContentButton":{"buttonRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"MPADsingles","params":"paramsRecency"}}}}}},"contents":[
{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Killers In The Street","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREsingle1"}}}]},"subtitle":{"runs":[{"text":"Single"},{"text":" • "},{"text":"2026"}]},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/single.jpg"}]}}}}}
]}}},
{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Vídeos"}]}}},"contents":[
{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"RIOT - Back To Back"}]},"subtitle":{"runs":[{"text":"Dubstep uNk"},{"text":" • "},{"text":"24 K visualizaciones"}]},"navigationEndpoint":{"watchEndpoint":{"videoId":"vidV1"}},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/video.jpg"}]}}}}}
]}}}
]}}}]}}}
    """.trimIndent()

    @Test
    fun browseIdExactoDelArtista() {
        assertEquals("UCstoned123", parseArtistBrowseId(searchJson, "Stoned Level"))
    }

    @Test
    fun browseIdSinCoincidenciaUsaElPrimero() {
        assertEquals("UCstoned123", parseArtistBrowseId(searchJson, "Nadie"))
    }

    @Test
    fun browseIdSinUcEsNull() {
        val sinUc = """{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"X"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Y"}]}}}],"navigationEndpoint":{"watchEndpoint":{"videoId":"v"}}}}]}}]}}}]}}}"""
        assertNull(parseArtistBrowseId(sinUc, "Y"))
    }

    @Test
    fun paginaExtraeLasCuatroSecciones() {
        val page = parseArtistPage(pageJson)!!
        assertEquals(listOf("STONED TO DEATH"), page.topSongs.map { it.title })
        assertEquals(204L, page.topSongs[0].durationSec)
        assertEquals(1, page.albums.size)
        assertEquals("MPREalbum1", page.albums[0].browseId)
        assertEquals(2025, page.albums[0].year)
        assertEquals("Álbum", page.albums[0].kind)
        assertEquals(1, page.singles.size)
        assertEquals("MPREsingle1", page.singles[0].browseId)
        assertEquals(2026, page.singles[0].year)
        assertEquals("Single", page.singles[0].kind)
        assertEquals("MPADsingles", page.singlesBrowseId)
        assertEquals("paramsRecency", page.singlesParams)
        assertNull(page.albumsBrowseId)
        assertEquals(1, page.videos.size)
        assertEquals("vidV1", page.videos[0].videoId)
        assertTrue(page.videos[0].subtitle?.contains("visualizaciones") == true)
    }

    @Test
    fun paginaSinSeccionesEsVaciaNoNull() {
        val page = parseArtistPage("{}")!!
        assertTrue(page.topSongs.isEmpty())
        assertTrue(page.albums.isEmpty())
        assertTrue(page.singles.isEmpty())
        assertTrue(page.videos.isEmpty())
    }

    @Test
    fun paginaJsonRotoEsNull() {
        assertNull(parseArtistPage("esto no es json"))
    }

    @Test
    fun lanzamientosDeUnaSeccion() {
        val json = """
{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Singles"}]}}},"contents":[
{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"A"}]},"subtitle":{"runs":[{"text":"Single"},{"text":" • "},{"text":"2026"}]}}},
{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"B"}]},"subtitle":{"runs":[{"text":"EP"},{"text":" • "},{"text":"2024"}]}}}
]}}}
]}}}]}}}
        """.trimIndent()
        val releases = parseArtistReleases(json)
        assertEquals(listOf("A", "B"), releases.map { it.title })
        assertEquals(listOf(2026, 2024), releases.map { it.year })
    }

    @Test
    fun albumTracksExtraeCanciones() {
        val json = """
{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
{"musicShelfRenderer":{"contents":[
{"musicResponsiveListItemRenderer":{"flexColumns":[
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Uno"}]}}},
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Stoned Level"},{"text":" • "},{"text":"2:10"}]}}}
],"navigationEndpoint":{"watchEndpoint":{"videoId":"t1"}}}},
{"musicResponsiveListItemRenderer":{"flexColumns":[
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Dos"}]}}},
{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Stoned Level"},{"text":" • "},{"text":"3:00"}]}}}
],"navigationEndpoint":{"watchEndpoint":{"videoId":"t2"}}}}
]}}}
]}}}]}}}
        """.trimIndent()
        val tracks = parseAlbumTracks(json)!!
        assertEquals(listOf("Uno", "Dos"), tracks.map { it.title })
        assertEquals(listOf(130L, 180L), tracks.map { it.durationSec })
    }

    @Test
    fun albumTracksJsonRotoEsNull() {
        assertNull(parseAlbumTracks("roto"))
    }

    @Test
    fun buildBrowseBodyLlevaBrowseIdYParams() {
        val body = buildBrowseBody("MPADsingles", "paramsRecency")
        assertTrue(body.contains("\"browseId\":\"MPADsingles\""))
        assertTrue(body.contains("\"params\":\"paramsRecency\""))
        assertTrue(buildBrowseBody("UCx").contains("\"browseId\":\"UCx\""))
    }
}
