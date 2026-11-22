package senada.catalog.youtube

// Respons Innertube asli dari FEmusic_home, FEmusic_explore, FEmusic_charts, dan mood yang dipangkas tanpa data pribadi atau token pelacakan.

internal const val REAL_HOME_PAGE_1 = """
{
 "responseContext": {
  "visitorData": "VISITOR_HOME_1"
 },
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
                "text": "Mix lagu"
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/qVb_uk5JJ-FyTIoESoeL080jtUIIvjdN-aQbRtUTREMMsenXd-txpbgtYaoYi5t3G7h_GBwT79zD5ppkEA=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/qVb_uk5JJ-FyTIoESoeL080jtUIIvjdN-aQbRtUTREMMsenXd-txpbgtYaoYi5t3G7h_GBwT79zD5ppkEA=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "overlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "BLQPNaBklGA",
                    "playlistId": "RDAMVMBLQPNaBklGA",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Negoro Angin",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "BLQPNaBklGA",
                      "playlistId": "RDAMVMBLQPNaBklGA",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                       }
                      }
                     }
                    }
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
                    "text": "Denny Caknan",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "UCrXRY_7SVVmc6TykwhRGUNQ",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                       }
                      }
                     }
                    }
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "136 jt pemutaran"
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
                    "text": "Negoro Angin",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_Td7gsVHlRUa",
                      "params": "ggMEgAL5BQ%3D%3D",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                       }
                      }
                     }
                    }
                   }
                  ]
                 }
                }
               }
              ],
              "playlistItemData": {
               "videoId": "BLQPNaBklGA"
              }
             }
            },
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/xrGDyYO3umAVFdsdyIM2G451xiAxCD6haJkCQel6TQlqE-XsEUCGsj_Q5Er4YjFpjWqv-_Ze-VaPPL0j=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/xrGDyYO3umAVFdsdyIM2G451xiAxCD6haJkCQel6TQlqE-XsEUCGsj_Q5Er4YjFpjWqv-_Ze-VaPPL0j=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "overlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "5F28ye50-Kc",
                    "playlistId": "RDAMVM5F28ye50-Kc",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Teh Hijau",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "5F28ye50-Kc",
                      "playlistId": "RDAMVM5F28ye50-Kc",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                       }
                      }
                     }
                    }
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
                    "text": "Tulus",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "UC_DHlXllTSMB8pTC38_leFg",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                       }
                      }
                     }
                    }
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "246 jt pemutaran"
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
                    "text": "Teh Hijau",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_RVAbNZdUasD",
                      "params": "ggMEgAL5BQ%3D%3D",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                       }
                      }
                     }
                    }
                   }
                  ]
                 }
                }
               }
              ],
              "playlistItemData": {
               "videoId": "5F28ye50-Kc"
              }
             }
            }
           ],
           "numItemsPerColumn": "4"
          }
         },
         {
          "musicCarouselShelfRenderer": {
           "header": {
            "musicCarouselShelfBasicHeaderRenderer": {
             "title": {
              "runs": [
               {
                "text": "Kesedihan musim hujan"
               }
              ]
             },
             "strapline": {
              "runs": [
               {
                "text": "LAGU-LAGU UNTUK HARI YANG MENDUNG 🌧"
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/SputWd885kz_aO2OvtnClqkxR1m7C5vm6xX1q2uMeacMWDuThdzGxrolwyUrvAQx5QIvYFTO8cGk2tg=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/SputWd885kz_aO2OvtnClqkxR1m7C5vm6xX1q2uMeacMWDuThdzGxrolwyUrvAQx5QIvYFTO8cGk2tg=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Jam Galau",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_nQ7zJT5SkLuI4COHNLXeDYvjY8XQpz--U",
                   "params": "ggMEgALjBA%3D%3D",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Tiara Andini"
                },
                {
                 "text": ", "
                },
                {
                 "text": "Mahalini"
                },
                {
                 "text": ", "
                },
                {
                 "text": "Fabio Asher"
                },
                {
                 "text": ", "
                },
                {
                 "text": "Judika"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLRDCLAK5uy_nQ7zJT5SkLuI4COHNLXeDYvjY8XQpz--U",
                "params": "ggMEgALjBA%3D%3D",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "RDCLAK5uy_nQ7zJT5SkLuI4COHNLXeDYvjY8XQpz--U"
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/XHf4UUHtHELewPe_BDelWFv-aKvSurwoBAmqDNBBpnze9VylxlnxL1cbW9JKJFQiSJf42ukmPbZ5LQ5N=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/XHf4UUHtHELewPe_BDelWFv-aKvSurwoBAmqDNBBpnze9VylxlnxL1cbW9JKJFQiSJf42ukmPbZ5LQ5N=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Lagu Lama Sakitnya Sama",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_nojW3TJbkI-lX_ZQvuxxTlJCanmWH7p8M",
                   "params": "ggMEgALjBA%3D%3D",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Ungu"
                },
                {
                 "text": ", "
                },
                {
                 "text": "Peterpan"
                },
                {
                 "text": ", "
                },
                {
                 "text": "Bunga Citra Lestari"
                },
                {
                 "text": ", "
                },
                {
                 "text": "Virgoun"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLRDCLAK5uy_nojW3TJbkI-lX_ZQvuxxTlJCanmWH7p8M",
                "params": "ggMEgALjBA%3D%3D",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "RDCLAK5uy_nojW3TJbkI-lX_ZQvuxxTlJCanmWH7p8M"
                   }
                  }
                 }
                }
               }
              }
             }
            }
           ]
          }
         },
         {
          "musicTastebuilderShelfRenderer": {
           "thumbnail": {
            "musicTastebuilderShelfThumbnailRenderer": {
             "thumbnail": {
              "thumbnails": [
               {
                "url": "https://www.youtube.com/img/music/tastebuilder/shelf_thumbnail/v6/ID_248_X_76.png",
                "width": 248,
                "height": 76
               },
               {
                "url": "https://www.youtube.com/img/music/tastebuilder/shelf_thumbnail/v6/ID_372_X_114.png",
                "width": 372,
                "height": 114
               },
               {
                "url": "https://www.youtube.com/img/music/tastebuilder/shelf_thumbnail/v6/ID_496_X_152.png",
                "width": 496,
                "height": 152
               },
               {
                "url": "https://www.youtube.com/img/music/tastebuilder/shelf_thumbnail/v6/ID_744_X_228.png",
                "width": 744,
                "height": 228
               },
               {
                "url": "https://www.youtube.com/img/music/tastebuilder/shelf_thumbnail/v6/ID_992_X_304.png",
                "width": 992,
                "height": 304
               }
              ]
             }
            }
           },
           "primaryText": {
            "runs": [
             {
              "text": "Beri tahu kami artis yang Anda suka"
             }
            ]
           },
           "secondaryText": {
            "runs": [
             {
              "text": "Kami akan membuat pengalaman menikmati video khusus untuk kamu"
             }
            ]
           },
           "actionButton": {
            "buttonRenderer": {
             "style": "STYLE_PRIMARY",
             "text": {
              "runs": [
               {
                "text": "Ayo mulai"
               }
              ]
             },
             "navigationEndpoint": {
              "modalEndpoint": {
               "modal": {
                "modalWithTitleAndButtonRenderer": {
                 "title": {
                  "runs": [
                   {
                    "text": "Punya artis favorit?"
                   }
                  ]
                 },
                 "content": {
                  "runs": [
                   {
                    "text": "Beri tahu kami artis yang Anda sukai dan kami akan meningkatkan rekomendasi untuk Anda"
                   }
                  ]
                 },
                 "button": {
                  "buttonRenderer": {
                   "style": "STYLE_BLUE_TEXT",
                   "isDisabled": false,
                   "text": {
                    "runs": [
                     {
                      "text": "Login"
                     }
                    ]
                   },
                   "navigationEndpoint": {
                    "signInEndpoint": {
                     "hack": true
                    }
                   }
                  }
                 }
                }
               }
              }
             }
            }
           },
           "isVisible": true
          }
         }
        ],
        "continuations": [
         {
          "nextContinuationData": {
           "continuation": "HOME_PAGE_2_TOKEN"
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
"""

internal const val REAL_HOME_PAGE_2 = """
{
 "continuationContents": {
  "sectionListContinuation": {
   "contents": [
    {
     "musicCarouselShelfRenderer": {
      "header": {
       "musicCarouselShelfBasicHeaderRenderer": {
        "title": {
         "runs": [
          {
           "text": "Rilis baru",
           "navigationEndpoint": {
            "browseEndpoint": {
             "browseId": "FEmusic_new_releases_albums",
             "params": "ggMEgALSAg%3D%3D"
            }
           }
          }
         ]
        }
       }
      },
      "contents": [
       {
        "musicTwoRowItemRenderer": {
         "thumbnailRenderer": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/qEO5sJHxoosJctUZS6aoDfQfqdS0GO91HibNTR8UZNhtiMiVMyRztHSUtYEw8lpd0f95O9tgEaL-7S9b=w226-h226-l90-rj",
              "width": 226,
              "height": 226
             },
             {
              "url": "https://yt3.googleusercontent.com/qEO5sJHxoosJctUZS6aoDfQfqdS0GO91HibNTR8UZNhtiMiVMyRztHSUtYEw8lpd0f95O9tgEaL-7S9b=w544-h544-l90-rj",
              "width": 544,
              "height": 544
             }
            ]
           }
          }
         },
         "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
         "title": {
          "runs": [
           {
            "text": "Mustahil",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "MPREb_z43rR5FKyfe",
              "params": "ggMvGilPTEFLNXV5X2xCMkJOOVJlTTE1MHhCeW5QN0hzdEJIaVFEMkV6Umx0b4AC0gI%3D",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ALBUM"
               }
              }
             }
            }
           }
          ]
         },
         "subtitle": {
          "runs": [
           {
            "text": "Single"
           },
           {
            "text": " • "
           },
           {
            "text": "Robi DA7",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "UCj55SwkWwuzitcC6Px4KeQQ",
              "params": "ggMEgALSAg%3D%3D",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ARTIST"
               }
              }
             }
            }
           }
          ]
         },
         "navigationEndpoint": {
          "browseEndpoint": {
           "browseId": "MPREb_z43rR5FKyfe",
           "params": "ggMvGilPTEFLNXV5X2xCMkJOOVJlTTE1MHhCeW5QN0hzdEJIaVFEMkV6Umx0b4AC0gI%3D",
           "browseEndpointContextSupportedConfigs": {
            "browseEndpointContextMusicConfig": {
             "pageType": "MUSIC_PAGE_TYPE_ALBUM"
            }
           }
          }
         },
         "thumbnailOverlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchPlaylistEndpoint": {
               "playlistId": "OLAK5uy_lB2BN9ReM150xBynP7HstBHiQD2EzRlto"
              }
             }
            }
           }
          }
         }
        }
       },
       {
        "musicTwoRowItemRenderer": {
         "thumbnailRenderer": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/J0FPYLReGuQClp9yvfI1iY4TziEhns9zvdaC43U1BFZoZ_uYsA6XEqbgSE8WPhT2ugnRXxHeeh7DyfUWVg=w226-h226-l90-rj",
              "width": 226,
              "height": 226
             },
             {
              "url": "https://yt3.googleusercontent.com/J0FPYLReGuQClp9yvfI1iY4TziEhns9zvdaC43U1BFZoZ_uYsA6XEqbgSE8WPhT2ugnRXxHeeh7DyfUWVg=w544-h544-l90-rj",
              "width": 544,
              "height": 544
             }
            ]
           }
          }
         },
         "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
         "title": {
          "runs": [
           {
            "text": "Kusuma Wijaya",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "MPREb_TNLPMqdmtGX",
              "params": "ggMvGilPTEFLNXV5X21WTHN5clhiVGRhWnRPbHI1X2RGbk1sMHlqVHhOZUtiSYAC0gI%3D",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ALBUM"
               }
              }
             }
            }
           }
          ]
         },
         "subtitle": {
          "runs": [
           {
            "text": "Single"
           },
           {
            "text": " • "
           },
           {
            "text": "Silvy Kumalasari",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "UCqCKzfIthBwRgHJubG4izsw",
              "params": "ggMEgALSAg%3D%3D",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ARTIST"
               }
              }
             }
            }
           }
          ]
         },
         "navigationEndpoint": {
          "browseEndpoint": {
           "browseId": "MPREb_TNLPMqdmtGX",
           "params": "ggMvGilPTEFLNXV5X21WTHN5clhiVGRhWnRPbHI1X2RGbk1sMHlqVHhOZUtiSYAC0gI%3D",
           "browseEndpointContextSupportedConfigs": {
            "browseEndpointContextMusicConfig": {
             "pageType": "MUSIC_PAGE_TYPE_ALBUM"
            }
           }
          }
         },
         "thumbnailOverlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchPlaylistEndpoint": {
               "playlistId": "OLAK5uy_mVLsyrXbTdaZtOlr5_dFnMl0yjTxNeKbI"
              }
             }
            }
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
           "text": "Lagu trending untuk kamu"
          }
         ]
        }
       }
      },
      "contents": [
       {
        "musicResponsiveListItemRenderer": {
         "thumbnail": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/q9eCXwz3e0na2saRIQHAi-u5flaqQD61w15xkn4Q4t5zwIJI6GDRvd_x_61W1ONZR7nEo7NtPkBzYB7Y=w60-h60-l90-rj",
              "width": 60,
              "height": 60
             },
             {
              "url": "https://yt3.googleusercontent.com/q9eCXwz3e0na2saRIQHAi-u5flaqQD61w15xkn4Q4t5zwIJI6GDRvd_x_61W1ONZR7nEo7NtPkBzYB7Y=w120-h120-l90-rj",
              "width": 120,
              "height": 120
             }
            ]
           }
          }
         },
         "overlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchEndpoint": {
               "videoId": "dWTkgzOmvWc",
               "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": {
                 "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                }
               }
              }
             }
            }
           }
          }
         },
         "flexColumns": [
          {
           "musicResponsiveListItemFlexColumnRenderer": {
            "text": {
             "runs": [
              {
               "text": "Mimpi Manis",
               "navigationEndpoint": {
                "watchEndpoint": {
                 "videoId": "dWTkgzOmvWc",
                 "playerParams": "0gcJCTUC0OBS9m0t",
                 "watchEndpointMusicSupportedConfigs": {
                  "watchEndpointMusicConfig": {
                   "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                  }
                 }
                }
               }
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
               "text": "Jeyma Monica",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "UCFAoslxAx04SAVEDrJpnTlQ",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                  }
                 }
                }
               }
              },
              {
               "text": " • "
              },
              {
               "text": "1 jt pemutaran"
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
               "text": "Mimpi Manis",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "MPREb_5dbmalxyBRf",
                 "params": "ggMEgAL6CA%3D%3D",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                  }
                 }
                }
               }
              }
             ]
            }
           }
          }
         ],
         "playlistItemData": {
          "videoId": "dWTkgzOmvWc"
         }
        }
       },
       {
        "musicResponsiveListItemRenderer": {
         "thumbnail": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/NmZCNW8oNNghjYJtTaTav2pIJkuBlBFhnInEsjklq4iK6NhiDuMIi-nIIXfbZ0jQRyu5OFYfF-drwPaB=w60-h60-l90-rj",
              "width": 60,
              "height": 60
             },
             {
              "url": "https://yt3.googleusercontent.com/NmZCNW8oNNghjYJtTaTav2pIJkuBlBFhnInEsjklq4iK6NhiDuMIi-nIIXfbZ0jQRyu5OFYfF-drwPaB=w120-h120-l90-rj",
              "width": 120,
              "height": 120
             }
            ]
           }
          }
         },
         "overlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchEndpoint": {
               "videoId": "ADSPDO-it-Q",
               "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": {
                 "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                }
               }
              }
             }
            }
           }
          }
         },
         "flexColumns": [
          {
           "musicResponsiveListItemFlexColumnRenderer": {
            "text": {
             "runs": [
              {
               "text": "Ndoro Ayu (feat. Sadewok)",
               "navigationEndpoint": {
                "watchEndpoint": {
                 "videoId": "ADSPDO-it-Q",
                 "watchEndpointMusicSupportedConfigs": {
                  "watchEndpointMusicConfig": {
                   "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                  }
                 }
                }
               }
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
               "text": "Silvy Kumalasari",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "UCqCKzfIthBwRgHJubG4izsw",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                  }
                 }
                }
               }
              },
              {
               "text": " • "
              },
              {
               "text": "966 rb pemutaran"
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
               "text": "Ndoro Ayu",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "MPREb_VF6XWfR9273",
                 "params": "ggMEgAL6CA%3D%3D",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                  }
                 }
                }
               }
              }
             ]
            }
           }
          }
         ],
         "playlistItemData": {
          "videoId": "ADSPDO-it-Q"
         }
        }
       }
      ],
      "numItemsPerColumn": "4"
     }
    }
   ],
   "continuations": [
    {
     "nextContinuationData": {
      "continuation": "HOME_PAGE_3_TOKEN"
     }
    }
   ]
  }
 }
}
"""

internal const val REAL_HOME_PAGE_3 = """
{
 "continuationContents": {
  "sectionListContinuation": {
   "contents": [
    {
     "musicCarouselShelfRenderer": {
      "header": {
       "musicCarouselShelfBasicHeaderRenderer": {
        "title": {
         "runs": [
          {
           "text": "Hits Indonesia."
          }
         ]
        },
        "strapline": {
         "runs": [
          {
           "text": "LAGU HITS INDONESIA TERBAIK SAAT INI"
          }
         ]
        }
       }
      },
      "contents": [
       {
        "musicTwoRowItemRenderer": {
         "thumbnailRenderer": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/9x1gMqfBeTsbbs_hn50JXmWhq0G0Vj3dwB4y1vUMt1XckCi0VSNlEmW_gdpXXIYrX_5_d1cgpoQfsQ=w226-h226-l90-rj",
              "width": 226,
              "height": 226
             },
             {
              "url": "https://yt3.googleusercontent.com/9x1gMqfBeTsbbs_hn50JXmWhq0G0Vj3dwB4y1vUMt1XckCi0VSNlEmW_gdpXXIYrX_5_d1cgpoQfsQ=w544-h544-l90-rj",
              "width": 544,
              "height": 544
             }
            ]
           }
          }
         },
         "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
         "title": {
          "runs": [
           {
            "text": "Lagu-lagu Terpopuler Tahun 2026 (Sejauh Ini)",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "VLRDCLAK5uy_krPEmv9sFxy7EIWsT89irAfv-GpVd0Ho8",
              "params": "ggMEgAKPAw%3D%3D",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
               }
              }
             }
            }
           }
          ]
         },
         "subtitle": {
          "runs": [
           {
            "text": "Justin Bieber"
           },
           {
            "text": ", "
           },
           {
            "text": "dia"
           },
           {
            "text": ", "
           },
           {
            "text": "Bernadya"
           },
           {
            "text": ", "
           },
           {
            "text": "Tasya Rosmala"
           }
          ]
         },
         "navigationEndpoint": {
          "browseEndpoint": {
           "browseId": "VLRDCLAK5uy_krPEmv9sFxy7EIWsT89irAfv-GpVd0Ho8",
           "params": "ggMEgAKPAw%3D%3D",
           "browseEndpointContextSupportedConfigs": {
            "browseEndpointContextMusicConfig": {
             "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
            }
           }
          }
         },
         "thumbnailOverlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchPlaylistEndpoint": {
               "playlistId": "RDCLAK5uy_krPEmv9sFxy7EIWsT89irAfv-GpVd0Ho8"
              }
             }
            }
           }
          }
         }
        }
       },
       {
        "musicTwoRowItemRenderer": {
         "thumbnailRenderer": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/O9b621d6gn6mXEmPBA_RW6-nlcjMTeEZIOpUXREj7n0Mk2aOsCVrbMTvXZroLJ-IEHwJYTD4ICST38i9=w226-h226-l90-rj",
              "width": 226,
              "height": 226
             },
             {
              "url": "https://yt3.googleusercontent.com/O9b621d6gn6mXEmPBA_RW6-nlcjMTeEZIOpUXREj7n0Mk2aOsCVrbMTvXZroLJ-IEHwJYTD4ICST38i9=w544-h544-l90-rj",
              "width": 544,
              "height": 544
             }
            ]
           }
          }
         },
         "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
         "title": {
          "runs": [
           {
            "text": "Hot Koplo",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "VLRDCLAK5uy_nOwEIYxGx84rpgRE384JIBHM1xMfEzhho",
              "params": "ggMEgAKPAw%3D%3D",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
               }
              }
             }
            }
           }
          ]
         },
         "subtitle": {
          "runs": [
           {
            "text": "Ajeng Febria"
           },
           {
            "text": ", "
           },
           {
            "text": "Difarina Indra Adella"
           },
           {
            "text": ", "
           },
           {
            "text": "Laila Ayu"
           },
           {
            "text": ", "
           },
           {
            "text": "Fendik Adella"
           }
          ]
         },
         "navigationEndpoint": {
          "browseEndpoint": {
           "browseId": "VLRDCLAK5uy_nOwEIYxGx84rpgRE384JIBHM1xMfEzhho",
           "params": "ggMEgAKPAw%3D%3D",
           "browseEndpointContextSupportedConfigs": {
            "browseEndpointContextMusicConfig": {
             "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
            }
           }
          }
         },
         "thumbnailOverlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchPlaylistEndpoint": {
               "playlistId": "RDCLAK5uy_nOwEIYxGx84rpgRE384JIBHM1xMfEzhho"
              }
             }
            }
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
           "text": "Cover dan remix"
          }
         ]
        }
       }
      },
      "contents": [
       {
        "musicResponsiveListItemRenderer": {
         "thumbnail": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/hTs6leowBJYmxmoHXsbzQqVmGdzwWhLNeUnFdnciKIiYSkyiaCvJIlUKiL1mz2fjjHUoeToSluV2m0TL=w60-h60-l90-rj",
              "width": 60,
              "height": 60
             },
             {
              "url": "https://yt3.googleusercontent.com/hTs6leowBJYmxmoHXsbzQqVmGdzwWhLNeUnFdnciKIiYSkyiaCvJIlUKiL1mz2fjjHUoeToSluV2m0TL=w120-h120-l90-rj",
              "width": 120,
              "height": 120
             }
            ]
           }
          }
         },
         "overlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchEndpoint": {
               "videoId": "eMORGws_SWM",
               "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": {
                 "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                }
               }
              }
             }
            }
           }
          }
         },
         "flexColumns": [
          {
           "musicResponsiveListItemFlexColumnRenderer": {
            "text": {
             "runs": [
              {
               "text": "Pulung",
               "navigationEndpoint": {
                "watchEndpoint": {
                 "videoId": "eMORGws_SWM",
                 "watchEndpointMusicSupportedConfigs": {
                  "watchEndpointMusicConfig": {
                   "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                  }
                 }
                }
               }
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
               "text": "Denny Caknan",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "UCrXRY_7SVVmc6TykwhRGUNQ",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                  }
                 }
                }
               }
              },
              {
               "text": " • "
              },
              {
               "text": "10 jt pemutaran"
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
               "text": "Pulung",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "MPREb_fU4jJNaJF1N",
                 "params": "ggMEgAKPCg%3D%3D",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                  }
                 }
                }
               }
              }
             ]
            }
           }
          }
         ],
         "playlistItemData": {
          "videoId": "eMORGws_SWM"
         }
        }
       },
       {
        "musicResponsiveListItemRenderer": {
         "thumbnail": {
          "musicThumbnailRenderer": {
           "thumbnail": {
            "thumbnails": [
             {
              "url": "https://yt3.googleusercontent.com/p5fiwlhLCznW8hpHjP58lWSGMK84OtyqY2jl1NrJ1QkqsqXOs3F59DY_cxoAikKXaEmFx_K_kZlTdibQ=w60-h60-l90-rj",
              "width": 60,
              "height": 60
             },
             {
              "url": "https://yt3.googleusercontent.com/p5fiwlhLCznW8hpHjP58lWSGMK84OtyqY2jl1NrJ1QkqsqXOs3F59DY_cxoAikKXaEmFx_K_kZlTdibQ=w120-h120-l90-rj",
              "width": 120,
              "height": 120
             }
            ]
           }
          }
         },
         "overlay": {
          "musicItemThumbnailOverlayRenderer": {
           "content": {
            "musicPlayButtonRenderer": {
             "playNavigationEndpoint": {
              "watchEndpoint": {
               "videoId": "vu1HJ2CCQy4",
               "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": {
                 "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                }
               }
              }
             }
            }
           }
          }
         },
         "flexColumns": [
          {
           "musicResponsiveListItemFlexColumnRenderer": {
            "text": {
             "runs": [
              {
               "text": "Di Keheningan Malam Ku Bersujud",
               "navigationEndpoint": {
                "watchEndpoint": {
                 "videoId": "vu1HJ2CCQy4",
                 "watchEndpointMusicSupportedConfigs": {
                  "watchEndpointMusicConfig": {
                   "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                  }
                 }
                }
               }
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
               "text": "Santri Metal Family",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "UCiN686IhaDazffTO_JfCsjw",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                  }
                 }
                }
               }
              },
              {
               "text": " • "
              },
              {
               "text": "6,8 jt pemutaran"
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
               "text": "Di Keheningan Malam Ku Bersujud",
               "navigationEndpoint": {
                "browseEndpoint": {
                 "browseId": "MPREb_VneBqSCs8Zr",
                 "params": "ggMEgAKPCg%3D%3D",
                 "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                   "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                  }
                 }
                }
               }
              }
             ]
            }
           }
          }
         ],
         "playlistItemData": {
          "videoId": "vu1HJ2CCQy4"
         }
        }
       }
      ],
      "numItemsPerColumn": "4"
     }
    }
   ]
  }
 }
}
"""

internal const val REAL_HOME_EN_PAGE_1 = """
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
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/xrGDyYO3umAVFdsdyIM2G451xiAxCD6haJkCQel6TQlqE-XsEUCGsj_Q5Er4YjFpjWqv-_Ze-VaPPL0j=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/xrGDyYO3umAVFdsdyIM2G451xiAxCD6haJkCQel6TQlqE-XsEUCGsj_Q5Er4YjFpjWqv-_Ze-VaPPL0j=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "overlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "5F28ye50-Kc",
                    "playlistId": "RDAMVM5F28ye50-Kc",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Teh Hijau",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "5F28ye50-Kc",
                      "playlistId": "RDAMVM5F28ye50-Kc",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                       }
                      }
                     }
                    }
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
                    "text": "Tulus",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "UC_DHlXllTSMB8pTC38_leFg",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                       }
                      }
                     }
                    }
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "246M plays"
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
                    "text": "Teh Hijau",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_RVAbNZdUasD",
                      "params": "ggMEgAL5BQ%3D%3D",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                       }
                      }
                     }
                    }
                   }
                  ]
                 }
                }
               }
              ],
              "playlistItemData": {
               "videoId": "5F28ye50-Kc"
              }
             }
            },
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/qVb_uk5JJ-FyTIoESoeL080jtUIIvjdN-aQbRtUTREMMsenXd-txpbgtYaoYi5t3G7h_GBwT79zD5ppkEA=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/qVb_uk5JJ-FyTIoESoeL080jtUIIvjdN-aQbRtUTREMMsenXd-txpbgtYaoYi5t3G7h_GBwT79zD5ppkEA=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "overlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "BLQPNaBklGA",
                    "playlistId": "RDAMVMBLQPNaBklGA",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Negoro Angin",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "BLQPNaBklGA",
                      "playlistId": "RDAMVMBLQPNaBklGA",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                       }
                      }
                     }
                    }
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
                    "text": "Denny Caknan",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "UCrXRY_7SVVmc6TykwhRGUNQ",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                       }
                      }
                     }
                    }
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "136M plays"
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
                    "text": "Negoro Angin",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_Td7gsVHlRUa",
                      "params": "ggMEgAL5BQ%3D%3D",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                       }
                      }
                     }
                    }
                   }
                  ]
                 }
                }
               }
              ],
              "playlistItemData": {
               "videoId": "BLQPNaBklGA"
              }
             }
            }
           ],
           "numItemsPerColumn": "4"
          }
         },
         {
          "musicCarouselShelfRenderer": {
           "header": {
            "musicCarouselShelfBasicHeaderRenderer": {
             "title": {
              "runs": [
               {
                "text": "Trending community playlists"
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/PR-EXqPEf4aHFFLHknd2II-rvvVINdq7mS_eaHe4NEX11w2P6N8SztkPlouSzvGX6gtuMeeJXro=s192",
                   "width": 192,
                   "height": 192
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/PR-EXqPEf4aHFFLHknd2II-rvvVINdq7mS_eaHe4NEX11w2P6N8SztkPlouSzvGX6gtuMeeJXro=s576",
                   "width": 576,
                   "height": 576
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "santai",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLPLF3h4hNV9dlgGs8M4x6AXGgn53qlzHF2J",
                   "params": "ggMEgALDCg%3D%3D",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Irudiana00",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCfZc4orAwn8WDIfP88eISGA",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                    }
                   }
                  }
                 }
                },
                {
                 "text": " • "
                },
                {
                 "text": "5.3M views"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLPLF3h4hNV9dlgGs8M4x6AXGgn53qlzHF2J",
                "params": "ggMEgALDCg%3D%3D",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "PLF3h4hNV9dlgGs8M4x6AXGgn53qlzHF2J"
                   }
                  }
                 }
                }
               }
              },
              "thumbnailCornerOverlay": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.ggpht.com/ytc/AIdro_ngWkOMbEXdG4nEj85mKcRLlMzP6OhU0ve7ivK-VlrQgk_6=s48-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 48,
                   "height": 48
                  },
                  {
                   "url": "https://yt3.ggpht.com/ytc/AIdro_ngWkOMbEXdG4nEj85mKcRLlMzP6OhU0ve7ivK-VlrQgk_6=s96-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 96,
                   "height": 96
                  },
                  {
                   "url": "https://yt3.ggpht.com/ytc/AIdro_ngWkOMbEXdG4nEj85mKcRLlMzP6OhU0ve7ivK-VlrQgk_6=s48-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 48,
                   "height": 48
                  },
                  {
                   "url": "https://yt3.ggpht.com/ytc/AIdro_ngWkOMbEXdG4nEj85mKcRLlMzP6OhU0ve7ivK-VlrQgk_6=s96-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 96,
                   "height": 96
                  }
                 ]
                },
                "onTap": {
                 "browseEndpoint": {
                  "browseId": "UCfZc4orAwn8WDIfP88eISGA",
                  "params": "ggMEgALDCg%3D%3D",
                  "browseEndpointContextSupportedConfigs": {
                   "browseEndpointContextMusicConfig": {
                    "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.ggpht.com/2908vJ0ZJUho_pXI5ZdX1068GnxX9sp2G8uFN7FqWTsKUNsxuMnCMo8FITvAI7Hapw0ebmmA6Q=s192",
                   "width": 192,
                   "height": 192
                  },
                  {
                   "url": "https://yt3.ggpht.com/2908vJ0ZJUho_pXI5ZdX1068GnxX9sp2G8uFN7FqWTsKUNsxuMnCMo8FITvAI7Hapw0ebmmA6Q=s576",
                   "width": 576,
                   "height": 576
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Kompilasi Lagu Pop Indonesia 90an",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLPLw3xXmiGk7179fZ_q7f3YgmuD05U7ClHr",
                   "params": "ggMEgALDCg%3D%3D",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Dadang Rustiawan",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCnGvEucMqFKWn2fSHeL7afA",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                    }
                   }
                  }
                 }
                },
                {
                 "text": " • "
                },
                {
                 "text": "18M views"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLPLw3xXmiGk7179fZ_q7f3YgmuD05U7ClHr",
                "params": "ggMEgALDCg%3D%3D",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "PLw3xXmiGk7179fZ_q7f3YgmuD05U7ClHr"
                   }
                  }
                 }
                }
               }
              },
              "thumbnailCornerOverlay": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.ggpht.com/3JNnFQyx6l9-1S7H4Zoil5Mmyfw3vEGSBfk3xvGTqyV-tN57sXWsw4Oh0u-fH0IEmgFfZZvkeQ=s48-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 48,
                   "height": 48
                  },
                  {
                   "url": "https://yt3.ggpht.com/3JNnFQyx6l9-1S7H4Zoil5Mmyfw3vEGSBfk3xvGTqyV-tN57sXWsw4Oh0u-fH0IEmgFfZZvkeQ=s96-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 96,
                   "height": 96
                  },
                  {
                   "url": "https://yt3.ggpht.com/3JNnFQyx6l9-1S7H4Zoil5Mmyfw3vEGSBfk3xvGTqyV-tN57sXWsw4Oh0u-fH0IEmgFfZZvkeQ=s48-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 48,
                   "height": 48
                  },
                  {
                   "url": "https://yt3.ggpht.com/3JNnFQyx6l9-1S7H4Zoil5Mmyfw3vEGSBfk3xvGTqyV-tN57sXWsw4Oh0u-fH0IEmgFfZZvkeQ=s96-c-k-c0x00000000-no-cc-rj-rp",
                   "width": 96,
                   "height": 96
                  }
                 ]
                },
                "onTap": {
                 "browseEndpoint": {
                  "browseId": "UCnGvEucMqFKWn2fSHeL7afA",
                  "params": "ggMEgALDCg%3D%3D",
                  "browseEndpointContextSupportedConfigs": {
                   "browseEndpointContextMusicConfig": {
                    "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                   }
                  }
                 }
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
"""

internal const val REAL_EXPLORE_PAGE = """
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
          "gridRenderer": {
           "items": [
            {
             "musicNavigationButtonRenderer": {
              "buttonText": {
               "runs": [
                {
                 "text": "Rilis baru"
                }
               ]
              },
              "clickCommand": {
               "browseEndpoint": {
                "browseId": "FEmusic_new_releases"
               }
              }
             }
            },
            {
             "musicNavigationButtonRenderer": {
              "buttonText": {
               "runs": [
                {
                 "text": "Tangga lagu"
                }
               ]
              },
              "clickCommand": {
               "browseEndpoint": {
                "browseId": "FEmusic_charts",
                "params": "sgYPRkVtdXNpY19leHBsb3Jl"
               }
              }
             }
            },
            {
             "musicNavigationButtonRenderer": {
              "buttonText": {
               "runs": [
                {
                 "text": "Jenis musik & suasana"
                }
               ]
              },
              "clickCommand": {
               "browseEndpoint": {
                "browseId": "FEmusic_moods_and_genres"
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
                "text": "Album & single terbaru",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "FEmusic_new_releases_albums"
                 }
                }
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/3U1YJ120eVts75oSJ3vn2M2JNgDRc6wV78_nOkzVn_c28-xKaCMcs3AjmUdiacpn3UBWGMBmBabPOe8=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/3U1YJ120eVts75oSJ3vn2M2JNgDRc6wV78_nOkzVn_c28-xKaCMcs3AjmUdiacpn3UBWGMBmBabPOe8=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Medley Pulkam (From 'Pulang Kampung')",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_zCtiJUqzyoI",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Single"
                },
                {
                 "text": " • "
                },
                {
                 "text": "Vanessa Zee",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCKGfdi8DojPXS5pzwSfuIhg",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_zCtiJUqzyoI",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "OLAK5uy_k0QJHa5KEe5V_IZn_j4bdJ6AK_FZrhKWo"
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/OntSJbRRhN-s0z4-StuOrsCCkudmqmY_bqdOsGjMsidOHwtXDD7Kvqos3j8uYsJECeeBwcyH8YN9RMyk5w=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/OntSJbRRhN-s0z4-StuOrsCCkudmqmY_bqdOsGjMsidOHwtXDD7Kvqos3j8uYsJECeeBwcyH8YN9RMyk5w=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Kamu Satu Satunya",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_ZqnVeFIfN1p",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Single"
                },
                {
                 "text": " • "
                },
                {
                 "text": "Murama Najwa",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCDwtJWlVw0M0c7gBZmqRmPg",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_ZqnVeFIfN1p",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "OLAK5uy_lb_9aLccqdFeF75BuzSwovCWGTeb25epM"
                   }
                  }
                 }
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
                "text": "Jenis musik & suasana",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "FEmusic_moods_and_genres"
                 }
                }
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicNavigationButtonRenderer": {
              "buttonText": {
               "runs": [
                {
                 "text": "Bepergian"
                }
               ]
              },
              "clickCommand": {
               "browseEndpoint": {
                "browseId": "FEmusic_moods_and_genres_category",
                "params": "ggMPOg1uX044Z2o5WERLckpU"
               }
              }
             }
            },
            {
             "musicNavigationButtonRenderer": {
              "buttonText": {
               "runs": [
                {
                 "text": "Fokus"
                }
               ]
              },
              "clickCommand": {
               "browseEndpoint": {
                "browseId": "FEmusic_moods_and_genres_category",
                "params": "ggMPOg1uX0NvNGNhWThMYWRh"
               }
              }
             }
            },
            {
             "musicNavigationButtonRenderer": {
              "buttonText": {
               "runs": [
                {
                 "text": "Gaming"
                }
               ]
              },
              "clickCommand": {
               "browseEndpoint": {
                "browseId": "FEmusic_moods_and_genres_category",
                "params": "ggMPOg1uX3NmUVV4Vzl3WGQ0"
               }
              }
             }
            }
           ],
           "numItemsPerColumn": "4"
          }
         },
         {
          "musicCarouselShelfRenderer": {
           "header": {
            "musicCarouselShelfBasicHeaderRenderer": {
             "title": {
              "runs": [
               {
                "text": "Trending",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "VLOLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                  "browseEndpointContextSupportedConfigs": {
                   "browseEndpointContextMusicConfig": {
                    "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                   }
                  }
                 }
                }
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://i.ytimg.com/vi/2RFN2BAiNTk/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3noglBxk3oHlMzfKM_OYgkcU3GxCA",
                   "width": 400,
                   "height": 225
                  }
                 ]
                }
               }
              },
              "overlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "2RFN2BAiNTk",
                    "playlistId": "OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "MIMPI MANIS - Jeyma Monica (Official Live Music Video) Bak Serumpun Sumpah Janji Berdua",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "2RFN2BAiNTk",
                      "playlistId": "OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                       }
                      }
                     }
                    }
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
                    "text": "Jeyma Monica",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "UCFAoslxAx04SAVEDrJpnTlQ",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                       }
                      }
                     }
                    }
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "1 jt x ditonton"
                   }
                  ]
                 }
                }
               },
               {}
              ],
              "playlistItemData": {
               "videoId": "2RFN2BAiNTk"
              },
              "customIndexColumn": {
               "musicCustomIndexColumnRenderer": {
                "text": {
                 "runs": [
                  {
                   "text": "1"
                  }
                 ]
                }
               }
              }
             }
            },
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/1oIZ4E7u4S3i33KqxtOsxC3Wg72Zv15b6MHgTNlmz81ebfJ0HWiZ5Z1DabQ561I8sfjfWMQ4lKFXijg=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/1oIZ4E7u4S3i33KqxtOsxC3Wg72Zv15b6MHgTNlmz81ebfJ0HWiZ5Z1DabQ561I8sfjfWMQ4lKFXijg=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "overlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "U35DD_XDVus",
                    "playlistId": "OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Kita Lewati Berdua",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "U35DD_XDVus",
                      "playlistId": "OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                       }
                      }
                     }
                    }
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
                    "text": "OVERNIGHT",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "UCsZgq1f1IU91knmyGHIhDdg",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                       }
                      }
                     }
                    }
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
                    "text": "Kita Lewati Berdua",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_aTeXFRBSij8",
                      "browseEndpointContextSupportedConfigs": {
                       "browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                       }
                      }
                     }
                    }
                   }
                  ]
                 }
                }
               }
              ],
              "playlistItemData": {
               "videoId": "U35DD_XDVus"
              },
              "customIndexColumn": {
               "musicCustomIndexColumnRenderer": {
                "text": {
                 "runs": [
                  {
                   "text": "5"
                  }
                 ]
                }
               }
              }
             }
            }
           ],
           "numItemsPerColumn": "4"
          }
         },
         {
          "musicCarouselShelfRenderer": {
           "header": {
            "musicCarouselShelfBasicHeaderRenderer": {
             "title": {
              "runs": [
               {
                "text": "Video musik terbaru",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "FEmusic_new_releases_videos"
                 }
                }
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://i.ytimg.com/vi/iaX1qQxj514/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3kd_lyLAKizDGOghXBS-zPCyAr1SA",
                   "width": 400,
                   "height": 225
                  },
                  {
                   "url": "https://i.ytimg.com/vi/iaX1qQxj514/hq720.jpg?sqp=-oaymwEKCKAGEMIDIABIWg&rs=AMzJL3lINxrc45Vi3Y_0bsx_a95kCLGP6A",
                   "width": 800,
                   "height": 450
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9",
              "title": {
               "runs": [
                {
                 "text": "Bulan Separuh (Official Music Video)"
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Aruma",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCFk-qfmwoMq3kf6PWhqTdtw",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                    }
                   }
                  }
                 }
                },
                {
                 "text": " • "
                },
                {
                 "text": "15 rb x ditonton"
                }
               ]
              },
              "navigationEndpoint": {
               "watchEndpoint": {
                "videoId": "iaX1qQxj514",
                "watchEndpointMusicSupportedConfigs": {
                 "watchEndpointMusicConfig": {
                  "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "iaX1qQxj514",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                     }
                    }
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://i.ytimg.com/vi/Q7itd_vuSBI/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3kU1ZdL6zS6M8IkKW9Qf6InrJGg5Q",
                   "width": 400,
                   "height": 225
                  },
                  {
                   "url": "https://i.ytimg.com/vi/Q7itd_vuSBI/hq720.jpg?sqp=-oaymwEKCKAGEMIDIABIWg&rs=AMzJL3kVaBpvlcPfjBsCcSrgwkoOmrRoxQ",
                   "width": 800,
                   "height": 450
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9",
              "title": {
               "runs": [
                {
                 "text": "Kaleb J - Tanda Cinta (Official Music Video)"
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "PASSION VIBE",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCRwJAgR_2qsUwY4stKQjBNw",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                    }
                   }
                  }
                 }
                },
                {
                 "text": " • "
                },
                {
                 "text": "98 rb x ditonton"
                }
               ]
              },
              "navigationEndpoint": {
               "watchEndpoint": {
                "videoId": "Q7itd_vuSBI",
                "watchEndpointMusicSupportedConfigs": {
                 "watchEndpointMusicConfig": {
                  "musicVideoType": "MUSIC_VIDEO_TYPE_UGC"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "Q7itd_vuSBI",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_UGC"
                     }
                    }
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://i.ytimg.com/vi/i_ZUw4sB7RE/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nu296P-NXuRNCFntAetc8PAM50Xw",
                   "width": 400,
                   "height": 225
                  },
                  {
                   "url": "https://i.ytimg.com/vi/i_ZUw4sB7RE/hq720.jpg?sqp=-oaymwEKCKAGEMIDIABIWg&rs=AMzJL3kvqXX3POj2sWQqSvxhf-VvXFN91g",
                   "width": 800,
                   "height": 450
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9",
              "title": {
               "runs": [
                {
                 "text": "Vini - The Mirage (Afro House Keinemusik Extended Mix)",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPEDi_ZUw4sB7RE",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "6 mnt, 35 dtk"
                },
                {
                 "text": " • "
                },
                {
                 "text": "Dj Vini",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCvr3mbEYif_FcwwAUzS-VNA",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPEDi_ZUw4sB7RE",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchEndpoint": {
                    "videoId": "i_ZUw4sB7RE",
                    "watchEndpointMusicSupportedConfigs": {
                     "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
                     }
                    }
                   }
                  }
                 }
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
"""

internal const val REAL_CHARTS_PAGE = """
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
          "musicShelfRenderer": {}
         },
         {
          "musicCarouselShelfRenderer": {
           "header": {
            "musicCarouselShelfBasicHeaderRenderer": {
             "title": {
              "runs": [
               {
                "text": "Tangga lagu video"
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/B5l1KRZyKxC5u-u1OTKYEkrDoTXH9Yjn0ZjL9DQvETR16tx6V-RCGeY8ue3a78iCGvNokcS057g=s192",
                   "width": 192,
                   "height": 192
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/B5l1KRZyKxC5u-u1OTKYEkrDoTXH9Yjn0ZjL9DQvETR16tx6V-RCGeY8ue3a78iCGvNokcS057g=s576",
                   "width": 576,
                   "height": 576
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Trending 20 Indonesia",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLOLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Tangga lagu"
                },
                {
                 "text": " • "
                },
                {
                 "text": "Tangga Lagu YouTube",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "FEmusic_charts",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLOLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo"
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/aJCmCHakAJJhitHkCvVLXFy7uADtzPx-p8Rfa0gGj-6VV8Ugxq0INcZhS2L-V-21D4d8_C-vLI8=s192",
                   "width": 192,
                   "height": 192
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/aJCmCHakAJJhitHkCvVLXFy7uADtzPx-p8Rfa0gGj-6VV8Ugxq0INcZhS2L-V-21D4d8_C-vLI8=s576",
                   "width": 576,
                   "height": 576
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Video Musik Teratas Harian - Indonesia",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLPL4fGSI1pDJn4lW6MJA2q37CfnraqAizqE",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Tangga lagu"
                },
                {
                 "text": " • "
                },
                {
                 "text": "Tangga Lagu YouTube",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "FEmusic_charts",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLPL4fGSI1pDJn4lW6MJA2q37CfnraqAizqE",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "PL4fGSI1pDJn4lW6MJA2q37CfnraqAizqE"
                   }
                  }
                 }
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
                "text": "Artis teratas"
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://lh3.googleusercontent.com/woWOuYcLDhSRRyFn6kNHJtaiGpD3dW2SnYZanXVdqe96T9Qs1Bt1GchfV73HsIJq2R013krAX3DyLA=w60-h60-p-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://lh3.googleusercontent.com/woWOuYcLDhSRRyFn6kNHJtaiGpD3dW2SnYZanXVdqe96T9Qs1Bt1GchfV73HsIJq2R013krAX3DyLA=w120-h120-p-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Denny Caknan"
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
                    "text": "7,13 jt subscriber"
                   }
                  ]
                 }
                }
               }
              ],
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "UCrXRY_7SVVmc6TykwhRGUNQ",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                 }
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
                 "iconType": "ARROW_CHART_NEUTRAL"
                },
                "iconColorStyle": "CUSTOM_INDEX_COLUMN_ICON_COLOR_STYLE_GREY"
               }
              }
             }
            },
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://lh3.googleusercontent.com/49jlarJQA3SqQRrZZODYQjRZwj7e-duxZWwF3taTj8TxgSRw_U_ebvVPvVPAU0KxPeNw76xWZw17ejiN=w60-h60-p-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://lh3.googleusercontent.com/49jlarJQA3SqQRrZZODYQjRZwj7e-duxZWwF3taTj8TxgSRw_U_ebvVPvVPAU0KxPeNw76xWZw17ejiN=w120-h120-p-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                }
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Yeni Inka"
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
                    "text": "1,18 jt subscriber"
                   }
                  ]
                 }
                }
               }
              ],
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "UCyxP1IFj5sTYMV8EtpdlbRg",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                 }
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
                },
                "iconColorStyle": "CUSTOM_INDEX_COLUMN_ICON_COLOR_STYLE_GREY"
               }
              }
             }
            }
           ],
           "numItemsPerColumn": "4"
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
"""

internal const val REAL_MOOD_CATEGORY_PAGE = """
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
                "text": "Musik asyik",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "FEmusic_moods_and_genres_category",
                  "params": "ggMPOg1uX0xiVWFObW1JSHhJ"
                 }
                }
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/TALQkjUiJF2lNPNzYAFFAvJfJPiLHtSHF2YbtXKLA84h-ZaN7X8scDed7OTAiFgKx6CZ4K2ZAZyqUUw=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/TALQkjUiJF2lNPNzYAFFAvJfJPiLHtSHF2YbtXKLA84h-ZaN7X8scDed7OTAiFgKx6CZ4K2ZAZyqUUw=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Feel Good Hip Hop & R&B",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_mkEwQuegHYB8_aAzBO8Q__6gGoaFblISw",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Playlist"
                },
                {
                 "text": " • "
                },
                {
                 "text": "YouTube Music"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLRDCLAK5uy_mkEwQuegHYB8_aAzBO8Q__6gGoaFblISw",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "RDCLAK5uy_mkEwQuegHYB8_aAzBO8Q__6gGoaFblISw"
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/LdHZKfih_bCyAVi4k2xuN5gG8EdDd7RA9eQKiC03slIJm5FnJwG3OfKgxy4szcjxBCrFz1R6BLPEyEU=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/LdHZKfih_bCyAVi4k2xuN5gG8EdDd7RA9eQKiC03slIJm5FnJwG3OfKgxy4szcjxBCrFz1R6BLPEyEU=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Bubble Pop",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_lGEOjy5U8xV41C8_LyqNnAZKOH6sGyutI",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Playlist"
                },
                {
                 "text": " • "
                },
                {
                 "text": "YouTube Music"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLRDCLAK5uy_lGEOjy5U8xV41C8_LyqNnAZKOH6sGyutI",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "RDCLAK5uy_lGEOjy5U8xV41C8_LyqNnAZKOH6sGyutI"
                   }
                  }
                 }
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
                "text": "Relaxing commute",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "FEmusic_moods_and_genres_category",
                  "params": "ggMPOg1uX1BWQ1BmMXRONGhS"
                 }
                }
               }
              ]
             }
            }
           },
           "contents": [
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/-LPVsIXuLax56VZfxpiuLADIl7RT2Hov69wOjzY0avWtZoElKbi9gM-KafzFUm7ljSe3hzDA0XtvtO8=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/-LPVsIXuLax56VZfxpiuLADIl7RT2Hov69wOjzY0avWtZoElKbi9gM-KafzFUm7ljSe3hzDA0XtvtO8=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Unplugged Commute",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_mxcaNVxJiVbP_IThqc98V5cbjqfovF36I",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Playlist"
                },
                {
                 "text": " • "
                },
                {
                 "text": "YouTube Music"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLRDCLAK5uy_mxcaNVxJiVbP_IThqc98V5cbjqfovF36I",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "RDCLAK5uy_mxcaNVxJiVbP_IThqc98V5cbjqfovF36I"
                   }
                  }
                 }
                }
               }
              }
             }
            },
            {
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/IyDHT-NAoZtq2LAYVm9oxHHwjqmpyZkoLfd3x0uhFsv3cqA_5sKWtKBURr5bqXkx7lvcDPg_pxk_4sI=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/IyDHT-NAoZtq2LAYVm9oxHHwjqmpyZkoLfd3x0uhFsv3cqA_5sKWtKBURr5bqXkx7lvcDPg_pxk_4sI=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
              "title": {
               "runs": [
                {
                 "text": "Vibes on Deck",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_m_hXTIKF2MjTVBpLZMeSRk6Wr81x2BhJQ",
                   "browseEndpointContextSupportedConfigs": {
                    "browseEndpointContextMusicConfig": {
                     "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "subtitle": {
               "runs": [
                {
                 "text": "Playlist"
                },
                {
                 "text": " • "
                },
                {
                 "text": "YouTube Music"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLRDCLAK5uy_m_hXTIKF2MjTVBpLZMeSRk6Wr81x2BhJQ",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                 }
                }
               }
              },
              "thumbnailOverlay": {
               "musicItemThumbnailOverlayRenderer": {
                "content": {
                 "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": {
                   "watchPlaylistEndpoint": {
                    "playlistId": "RDCLAK5uy_m_hXTIKF2MjTVBpLZMeSRk6Wr81x2BhJQ"
                   }
                  }
                 }
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
"""
