package com.aurora.player

import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.buildWatchBody
import com.aurora.player.online.parseWatchPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPlaylistTest {

    private val tabbedFixture = """
{
  "contents": {
    "singleColumnMusicWatchNextResultsRenderer": {
      "tabbedRenderer": {
        "watchNextTabbedResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "musicQueueRenderer": {
                    "content": {
                      "playlistPanelRenderer": {
                        "contents": [
                          {
                            "playlistPanelVideoRenderer": {
                              "videoId": "dQw4w9WgXcQ",
                              "title": {
                                "runs": [
                                  {
                                    "text": "Canción semilla"
                                  }
                                ]
                              },
                              "longBylineText": {
                                "runs": [
                                  {
                                    "text": "Artista Uno"
                                  },
                                  {
                                    "text": " • "
                                  },
                                  {
                                    "text": "Álbum Uno"
                                  }
                                ]
                              },
                              "lengthText": {
                                "runs": [
                                  {
                                    "text": "3:24"
                                  }
                                ]
                              },
                              "thumbnail": {
                                "thumbnails": [
                                  {
                                    "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=small",
                                    "width": 60,
                                    "height": 60
                                  },
                                  {
                                    "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                                    "width": 544,
                                    "height": 544
                                  }
                                ]
                              }
                            }
                          },
                          {
                            "playlistPanelVideoRenderer": {
                              "videoId": "abc123def45",
                              "title": {
                                "runs": [
                                  {
                                    "text": "Parecida uno"
                                  }
                                ]
                              },
                              "longBylineText": {
                                "runs": [
                                  {
                                    "text": "Artista Dos"
                                  },
                                  {
                                    "text": " • "
                                  },
                                  {
                                    "text": "Álbum Dos"
                                  }
                                ]
                              },
                              "lengthText": {
                                "runs": [
                                  {
                                    "text": "4:05"
                                  }
                                ]
                              },
                              "thumbnail": {
                                "musicThumbnailRenderer": {
                                  "thumbnail": {
                                    "thumbnails": [
                                      {
                                        "url": "https://i.ytimg.com/vi/abc123def45/hqdefault.jpg?sqp=small",
                                        "width": 60,
                                        "height": 60
                                      },
                                      {
                                        "url": "https://i.ytimg.com/vi/abc123def45/hqdefault.jpg",
                                        "width": 544,
                                        "height": 544
                                      }
                                    ]
                                  }
                                }
                              }
                            }
                          },
                          {
                            "playlistPanelVideoRenderer": {
                              "title": {
                                "runs": [
                                  {
                                    "text": "Sin videoId"
                                  }
                                ]
                              },
                              "longBylineText": {
                                "runs": [
                                  {
                                    "text": "Nadie"
                                  }
                                ]
                              }
                            }
                          },
                          {
                            "playlistPanelVideoRenderer": {
                              "videoId": "zzz000blank1",
                              "title": {
                                "runs": [
                                  {
                                    "text": "   "
                                  }
                                ]
                              },
                              "longBylineText": {
                                "runs": [
                                  {
                                    "text": "Artista Tres"
                                  }
                                ]
                              },
                              "lengthText": {
                                "runs": [
                                  {
                                    "text": "1:00"
                                  }
                                ]
                              }
                            }
                          },
                          {
                            "playlistPanelVideoRenderer": {
                              "videoId": "xyz789uvw01",
                              "title": {
                                "runs": [
                                  {
                                    "text": "Parecida dos"
                                  }
                                ]
                              },
                              "longBylineText": {
                                "runs": [
                                  {
                                    "text": "Artista Cuatro"
                                  }
                                ]
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              }
            }
          ]
        }
      }
    }
  }
}
    """.trimIndent()

    private val oldShapeFixture = """
{
  "contents": {
    "singleColumnMusicWatchNextResults": {
      "playlist": {
        "playlist": {
          "contents": [
            {
              "playlistPanelVideoRenderer": {
                "videoId": "old11122233",
                "title": {
                  "runs": [
                    {
                      "text": "Vieja forma"
                    }
                  ]
                },
                "longBylineText": {
                  "runs": [
                    {
                      "text": "Artista Viejo"
                    },
                    {
                      "text": " • "
                    },
                    {
                      "text": "Álbum Viejo"
                    }
                  ]
                },
                "lengthText": {
                  "runs": [
                    {
                      "text": "2:30"
                    }
                  ]
                },
                "thumbnail": {
                  "thumbnails": [
                    {
                      "url": "https://i.ytimg.com/vi/old11122233/hqdefault.jpg?sqp=small",
                      "width": 60,
                      "height": 60
                    },
                    {
                      "url": "https://i.ytimg.com/vi/old11122233/hqdefault.jpg",
                      "width": 544,
                      "height": 544
                    }
                  ]
                }
              }
            }
          ]
        }
      }
    }
  }
}
    """.trimIndent()

    // RONDA 2 (2026-11-13, P1, n=924): YouTube envuelve a veces cada
    // ítem en playlistPanelVideoWrapperRenderer (con primaryRenderer
    // dentro); este fixture fija que el parser lo desenvuelve (n=849).
    private val wrappedFixture = """
{
  "contents": {
    "singleColumnMusicWatchNextResultsRenderer": {
      "tabbedRenderer": {
        "watchNextTabbedResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "musicQueueRenderer": {
                    "content": {
                      "playlistPanelRenderer": {
                        "contents": [
                          {
                            "playlistPanelVideoWrapperRenderer": {
                              "primaryRenderer": {
                                "playlistPanelVideoRenderer": {
                                  "videoId": "wrap999",
                                  "title": {
                                    "runs": [
                                      {
                                        "text": "Envuelta"
                                      }
                                    ]
                                  },
                                  "longBylineText": {
                                    "runs": [
                                      {
                                        "text": "Artista Envuelto"
                                      },
                                      {
                                        "text": " • "
                                      },
                                      {
                                        "text": "Álbum Envuelto"
                                      }
                                    ]
                                  },
                                  "lengthText": {
                                    "runs": [
                                      {
                                        "text": "2:00"
                                      }
                                    ]
                                  },
                                  "thumbnail": {
                                    "thumbnails": [
                                      {
                                        "url": "https://i.ytimg.com/vi/wrap999/hqdefault.jpg?sqp=small",
                                        "width": 60,
                                        "height": 60
                                      },
                                      {
                                        "url": "https://i.ytimg.com/vi/wrap999/hqdefault.jpg",
                                        "width": 544,
                                        "height": 544
                                      }
                                    ]
                                  }
                                }
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              }
            }
          ]
        }
      }
    }
  }
}
    """.trimIndent()

    @Test
    fun parseaRadioTabbed() {
        val tracks = parseWatchPlaylist(tabbedFixture)
        assertEquals(3, tracks.size)
        val seed = tracks[0]
        assertEquals("dQw4w9WgXcQ", seed.videoId)
        assertEquals("Canción semilla", seed.title)
        assertEquals("Artista Uno", seed.artist)
        assertEquals("Álbum Uno", seed.album)
        assertEquals(204L, seed.durationSec)
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            seed.thumbnailUrl,
        )
        val second = tracks[1]
        assertEquals("abc123def45", second.videoId)
        assertEquals("Parecida uno", second.title)
        assertEquals("Artista Dos", second.artist)
        assertEquals(245L, second.durationSec)
        assertEquals(
            "https://i.ytimg.com/vi/abc123def45/hqdefault.jpg",
            second.thumbnailUrl,
        )
        val third = tracks[2]
        assertEquals("xyz789uvw01", third.videoId)
        assertEquals("Parecida dos", third.title)
        assertEquals("Artista Cuatro", third.artist)
        assertNull(third.album)
        assertNull(third.durationSec)
    }

    @Test
    fun parseaRadioFormaAntigua() {
        val tracks = parseWatchPlaylist(oldShapeFixture)
        assertEquals(1, tracks.size)
        assertEquals("old11122233", tracks[0].videoId)
        assertEquals("Vieja forma", tracks[0].title)
        assertEquals("Artista Viejo", tracks[0].artist)
        assertEquals(150L, tracks[0].durationSec)
    }

    @Test
    fun parseaRadioEnvuelta() {
        val tracks = parseWatchPlaylist(wrappedFixture)
        assertEquals(1, tracks.size)
        val only = tracks[0]
        assertEquals("wrap999", only.videoId)
        assertEquals("Envuelta", only.title)
        assertEquals("Artista Envuelto", only.artist)
        assertEquals("Álbum Envuelto", only.album)
        assertEquals(120L, only.durationSec)
        assertEquals(
            "https://i.ytimg.com/vi/wrap999/hqdefault.jpg",
            only.thumbnailUrl,
        )
    }

    @Test
    fun jsonInvalidoDaVacio() {
        assertEquals(emptyList<OnlineTrack>(), parseWatchPlaylist("{ no es json"))
    }

    @Test
    fun jsonSinEstructuraDaVacio() {
        assertEquals(emptyList<OnlineTrack>(), parseWatchPlaylist("{\"contents\": {}}"))
    }

    @Test
    fun cuerpoWatchLlevaRadio() {
        val body = buildWatchBody("dQw4w9WgXcQ")
        assertTrue(body.contains("\"playlistId\":\"RDAMVMdQw4w9WgXcQ\""))
        assertTrue(body.contains("\"params\":\"wAEB\""))
        assertTrue(body.contains("\"videoId\":\"dQw4w9WgXcQ\""))
    }
}
