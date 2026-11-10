package com.aurora.player

import com.aurora.player.online.parseCharts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Charts de YouTube Music: el fixture es JSON mínimo escrito a mano
 * con la forma documentada (singleColumnBrowseResultsRenderer →
 * sectionListRenderer → carruseles; la primera sección es el país y
 * se salta; VL = lista, musicResponsiveListItemRenderer = artista).
 */
class ChartsTest {

    private val fixture = """
{
  "contents": {
    "singleColumnBrowseResultsRenderer": {
      "tabs": [
        {
          "tabRenderer": {
            "content": {
              "sectionListRenderer": {
                "contents": [
                  {
                    "musicShelfRenderer": {
                      "contents": []
                    }
                  },
                  {
                    "musicCarouselShelfRenderer": {
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Top vídeos",
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "VLPLvideos"
                                    }
                                  }
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/v.jpg"
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        },
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Top vídeos diario",
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "VLPLdaily"
                                    }
                                  }
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/d.jpg"
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                      ]
                    }
                  },
                  {
                    "musicCarouselShelfRenderer": {
                      "contents": [
                        {
                          "musicResponsiveListItemRenderer": {
                            "flexColumns": [
                              {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                  "text": {
                                    "runs": [
                                      {
                                        "text": "Artista Uno"
                                      }
                                    ]
                                  }
                                }
                              },
                              {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                  "text": {
                                    "runs": [
                                      {
                                        "text": "9.6M suscriptores"
                                      }
                                    ]
                                  }
                                }
                              }
                            ],
                            "navigationEndpoint": {
                              "browseEndpoint": {
                                "browseId": "UCuno"
                              }
                            },
                            "thumbnail": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/a1.jpg"
                                    }
                                  ]
                                }
                              }
                            },
                            "customIndexColumn": {
                              "musicCustomIndexColumnRenderer": {
                                "text": {
                                  "runs": [
                                    {
                                      "text": "1"
                                    }
                                  ]
                                },
                                "icon": {
                                  "iconType": "ARROW_DROP_UP"
                                }
                              }
                            }
                          }
                        },
                        {
                          "musicResponsiveListItemRenderer": {
                            "flexColumns": [
                              {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                  "text": {
                                    "runs": [
                                      {
                                        "text": "Artista Dos"
                                      }
                                    ]
                                  }
                                }
                              },
                              {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                  "text": {
                                    "runs": [
                                      {
                                        "text": "2.1M suscriptores"
                                      }
                                    ]
                                  }
                                }
                              }
                            ],
                            "navigationEndpoint": {
                              "browseEndpoint": {
                                "browseId": "UCdos"
                              }
                            },
                            "thumbnail": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/a2.jpg"
                                    }
                                  ]
                                }
                              }
                            },
                            "customIndexColumn": {
                              "musicCustomIndexColumnRenderer": {
                                "text": {
                                  "runs": [
                                    {
                                      "text": "2"
                                    }
                                  ]
                                },
                                "icon": {
                                  "iconType": "ARROW_CHART_NEUTRAL"
                                }
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        }
      ]
    }
  }
}
    """.trimIndent()

    @Test
    fun playlists_quitanElPrefijoVL() {
        val charts = parseCharts(fixture)
        assertEquals(2, charts.videoPlaylists.size)
        assertEquals("Top vídeos", charts.videoPlaylists[0].title)
        assertEquals("PLvideos", charts.videoPlaylists[0].playlistId)
        assertEquals("https://img/v.jpg", charts.videoPlaylists[0].thumbnailUrl)
        assertEquals("PLdaily", charts.videoPlaylists[1].playlistId)
    }

    @Test
    fun artistas_conPuestoTendenciaYSuscriptores() {
        val charts = parseCharts(fixture)
        assertEquals(2, charts.artists.size)
        assertEquals("Artista Uno", charts.artists[0].title)
        assertEquals("UCuno", charts.artists[0].browseId)
        assertEquals("9.6M", charts.artists[0].subscribers)
        assertEquals("1", charts.artists[0].rank)
        assertEquals("sube", charts.artists[0].trend)
        assertEquals("igual", charts.artists[1].trend)
        assertEquals("2", charts.artists[1].rank)
    }

    @Test
    fun jsonRoto_oSinSecciones_devuelveVacio() {
        assertTrue(parseCharts("{").videoPlaylists.isEmpty())
        assertTrue(parseCharts("{}").artists.isEmpty())
        assertTrue(parseCharts("{\"contents\":{}}").videoPlaylists.isEmpty())
    }

    @Test
    fun artistaSinPuesto_niTendencia_quedaEnNull() {
        val json = """
{
  "contents": {
    "singleColumnBrowseResultsRenderer": {
      "tabs": [
        {
          "tabRenderer": {
            "content": {
              "sectionListRenderer": {
                "contents": [
                  {
                    "musicShelfRenderer": {
                      "contents": []
                    }
                  },
                  {
                    "musicCarouselShelfRenderer": {
                      "contents": [
                        {
                          "musicResponsiveListItemRenderer": {
                            "flexColumns": [
                              {
                                "musicResponsiveListItemFlexColumnRenderer": {
                                  "text": {
                                    "runs": [
                                      {
                                        "text": "Sin Puesto"
                                      }
                                    ]
                                  }
                                }
                              }
                            ],
                            "thumbnail": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": []
                                }
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        }
      ]
    }
  }
}
        """.trimIndent()
        val charts = parseCharts(json)
        assertEquals(1, charts.artists.size)
        assertNull(charts.artists[0].rank)
        assertNull(charts.artists[0].trend)
        assertNull(charts.artists[0].subscribers)
        assertTrue(charts.videoPlaylists.isEmpty())
    }
}
