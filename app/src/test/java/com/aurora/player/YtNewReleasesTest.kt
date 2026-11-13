package com.aurora.player

import com.aurora.player.online.parseNewReleasesFromHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtNewReleasesTest {

    private val homeFixture = """
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
                                  "text": "Otra cosa",
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "MPREzzz"
                                    }
                                  }
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
                                  "text": "Single"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "2025"
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://i.ytimg.com/vi/qqq/hqdefault.jpg?small",
                                      "width": 60,
                                      "height": 60
                                    },
                                    {
                                      "url": "https://i.ytimg.com/vi/qqq/hqdefault.jpg",
                                      "width": 540,
                                      "height": 540
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
                                "text": "New releases"
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
                                  "text": "Disco fresco",
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "MPREabc123"
                                    }
                                  }
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Artista Uno"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Album"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "2026"
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://i.ytimg.com/vi/aaa/hqdefault.jpg?small",
                                      "width": 60,
                                      "height": 60
                                    },
                                    {
                                      "url": "https://i.ytimg.com/vi/aaa/hqdefault.jpg",
                                      "width": 540,
                                      "height": 540
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
                                  "text": "Sencillo nuevo",
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "MPREdef456"
                                    }
                                  }
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Artista Dos"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Single"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "2026"
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://i.ytimg.com/vi/bbb/hqdefault.jpg?small",
                                      "width": 60,
                                      "height": 60
                                    },
                                    {
                                      "url": "https://i.ytimg.com/vi/bbb/hqdefault.jpg",
                                      "width": 540,
                                      "height": 540
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
                                  "text": "   ",
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "MPREblank"
                                    }
                                  }
                                }
                              ]
                            },
                            "subtitle": {
                              "runs": [
                                {
                                  "text": "Artista Tres"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "Single"
                                },
                                {
                                  "text": " • "
                                },
                                {
                                  "text": "2026"
                                }
                              ]
                            },
                            "thumbnailRenderer": {
                              "musicThumbnailRenderer": {
                                "thumbnail": {
                                  "thumbnails": [
                                    {
                                      "url": "https://i.ytimg.com/vi/ccc/hqdefault.jpg?small",
                                      "width": 60,
                                      "height": 60
                                    },
                                    {
                                      "url": "https://i.ytimg.com/vi/ccc/hqdefault.jpg",
                                      "width": 540,
                                      "height": 540
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
    fun parseaEstanteNewReleases() {
        val releases = parseNewReleasesFromHome(homeFixture)
        assertEquals(2, releases.size)
        val first = releases[0]
        assertEquals("MPREabc123", first.browseId)
        assertEquals("Disco fresco", first.title)
        assertEquals("Artista Uno", first.artist)
        assertEquals(2026, first.year)
        assertEquals("Album", first.kind)
        assertEquals("https://i.ytimg.com/vi/aaa/hqdefault.jpg", first.thumbnailUrl)
        val second = releases[1]
        assertEquals("MPREdef456", second.browseId)
        assertEquals("Sencillo nuevo", second.title)
        assertEquals("Artista Dos", second.artist)
        assertEquals("Single", second.kind)
    }

    @Test
    fun sinEstanteDaVacio() {
        assertTrue(parseNewReleasesFromHome("{\"contents\": {}}").isEmpty())
        assertTrue(parseNewReleasesFromHome("{ no es json").isEmpty())
    }
}
