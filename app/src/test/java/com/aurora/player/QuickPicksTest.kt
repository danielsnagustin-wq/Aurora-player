package com.aurora.player

import com.aurora.player.online.parseQuickPicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selección rápida: los fixtures se generan con json.dumps (lección
 * #324: nunca escribir JSON anidado a mano, una llave de más tumba
 * los tests). Forma documentada por ytmusicapi: fila con título
 * "Quick picks" o "Selección rápida", ítems musicTwoRowItemRenderer
 * con videoId en watchEndpoint.
 */
class QuickPicksTest {

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
                    "musicCarouselShelfRenderer": {
                      "header": {
                        "musicCarouselShelfBasicHeaderRenderer": {
                          "title": {
                            "runs": [
                              {
                                "text": "Your morning music"
                              }
                            ]
                          }
                        }
                      },
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Otra"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Alguien"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "vid000"
                              }
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/o.jpg"
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
                      "header": {
                        "musicCarouselShelfBasicHeaderRenderer": {
                          "title": {
                            "runs": [
                              {
                                "text": "Quick picks"
                              }
                            ]
                          }
                        }
                      },
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Gravity"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "yetep"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "EludZd6lfts"
                              }
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/g.jpg"
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
                                  "text": "Feel Good"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "L3V3LS"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "bR5l0hJDnX8"
                              }
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/f.jpg"
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
                      "header": {
                        "musicCarouselShelfBasicHeaderRenderer": {
                          "title": {
                            "runs": [
                              {
                                "text": "New albums"
                              }
                            ]
                          }
                        }
                      },
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Disco"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Banda"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "vid111"
                              }
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

    private val spanishFixture = """
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
                    "musicCarouselShelfRenderer": {
                      "header": {
                        "musicCarouselShelfBasicHeaderRenderer": {
                          "title": {
                            "runs": [
                              {
                                "text": "Selección rápida"
                              }
                            ]
                          }
                        }
                      },
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Canción"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Cantante"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "vid999"
                              }
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/c.jpg"
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
    fun soloLaFilaQuickPicks() {
        val picks = parseQuickPicks(fixture)
        assertEquals(2, picks.size)
        assertEquals("EludZd6lfts", picks[0].videoId)
        assertEquals("Gravity", picks[0].title)
        assertEquals("yetep", picks[0].artist)
        assertEquals("https://img/g.jpg", picks[0].thumbnailUrl)
        assertEquals("bR5l0hJDnX8", picks[1].videoId)
    }

    @Test
    fun tituloEnEspanol_tambienVale() {
        val picks = parseQuickPicks(spanishFixture)
        assertEquals(1, picks.size)
        assertEquals("Canción", picks[0].title)
        assertEquals("vid999", picks[0].videoId)
    }

    @Test
    fun sinFilaQuickPicks_oJsonRoto_devuelveVacio() {
        assertTrue(parseQuickPicks("{}").isEmpty())
        assertTrue(parseQuickPicks("{").isEmpty())
        val sinFila = """
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
                    "musicCarouselShelfRenderer": {
                      "header": {
                        "musicCarouselShelfBasicHeaderRenderer": {
                          "title": {
                            "runs": [
                              {
                                "text": "Other"
                              }
                            ]
                          }
                        }
                      },
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "X"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "A"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "v1"
                              }
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://img/x.jpg"
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
        assertTrue(parseQuickPicks(sinFila).isEmpty())
    }

    @Test
    fun itemSinVideoId_seIgnora() {
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
                    "musicCarouselShelfRenderer": {
                      "header": {
                        "musicCarouselShelfBasicHeaderRenderer": {
                          "title": {
                            "runs": [
                              {
                                "text": "Quick picks"
                              }
                            ]
                          }
                        }
                      },
                      "contents": [
                        {
                          "musicTwoRowItemRenderer": {
                            "title": {
                              "runs": [
                                {
                                  "text": "Sin vídeo"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Nadie"
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": []
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
                                  "text": "Con vídeo"
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Alguien"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Álbum"
                                }
                              ]
                            },
                            "navigationEndpoint": {
                              "watchEndpoint": {
                                "videoId": "vid777"
                              }
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
        val picks = parseQuickPicks(json)
        assertEquals(1, picks.size)
        assertEquals("vid777", picks[0].videoId)
    }
}
