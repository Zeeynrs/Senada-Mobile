package senada.catalog.youtube

// Respons Innertube asli yang dipangkas tanpa data pribadi atau token pelacakan.

internal const val SEARCH_ARTISTS_PAGE_1 = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_ARTIST_SEARCH"
 },
 "contents": {
  "tabbedSearchResultsRenderer": {
   "tabs": [
    {
     "tabRenderer": {
      "title": "YT Music",
      "selected": true,
      "content": {
       "sectionListRenderer": {
        "contents": [
         {
          "musicShelfRenderer": {
           "title": {
            "runs": [
             {
              "text": "Artis"
             }
            ]
           },
           "contents": [
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/h8P1jEIZLM8lkMxNA6Nbq98b43wcqllJSNmcZTCRPAB-F6rG_0Nqw5w7fwou0PN1QGwSW5viwWD5NV0=w60-h60-p-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/h8P1jEIZLM8lkMxNA6Nbq98b43wcqllJSNmcZTCRPAB-F6rG_0Nqw5w7fwou0PN1QGwSW5viwWD5NV0=w120-h120-p-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                },
                "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_CIRCLE",
                "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FILL"
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Tulus"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               },
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Artis"
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "54,3 jt audiens bulanan"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "UC_DHlXllTSMB8pTC38_leFg",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                 }
                }
               }
              },
              "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_TALL"
             }
            },
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/8b9igTyScWDq6oJISHYvY27FDL4bd1zdHd0Xz_-e7su6kr2zphprMDaIS3ZGNiv16HLZ32XPWwwuP1VB=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/8b9igTyScWDq6oJISHYvY27FDL4bd1zdHd0Xz_-e7su6kr2zphprMDaIS3ZGNiv16HLZ32XPWwwuP1VB=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  }
                 ]
                },
                "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_CIRCLE",
                "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FILL"
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Tulus M"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               },
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Artis"
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "8,65 rb subscriber"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "UC7Q4a29g7y4eqsN1FpBYU8g",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                 }
                }
               }
              },
              "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_TALL"
             }
            }
           ],
           "continuations": [
            {
             "nextContinuationData": {
              "continuation": "ARTIST_PAGE_2_TOKEN"
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

internal const val SEARCH_ALBUMS_PAGE_1 = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_ALBUM_SEARCH"
 },
 "contents": {
  "tabbedSearchResultsRenderer": {
   "tabs": [
    {
     "tabRenderer": {
      "title": "YT Music",
      "selected": true,
      "content": {
       "sectionListRenderer": {
        "contents": [
         {
          "musicShelfRenderer": {
           "title": {
            "runs": [
             {
              "text": "Album"
             }
            ]
           },
           "contents": [
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/QpgC2ZqMYzFgdGwgDdOB3FLwKtYIzE0LUrk81-XbzQ1rJ3wIiDcJgJoEuwFMeBW3EeRztuSj4bUafdwt=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/QpgC2ZqMYzFgdGwgDdOB3FLwKtYIzE0LUrk81-XbzQ1rJ3wIiDcJgJoEuwFMeBW3EeRztuSj4bUafdwt=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/QpgC2ZqMYzFgdGwgDdOB3FLwKtYIzE0LUrk81-XbzQ1rJ3wIiDcJgJoEuwFMeBW3EeRztuSj4bUafdwt=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/QpgC2ZqMYzFgdGwgDdOB3FLwKtYIzE0LUrk81-XbzQ1rJ3wIiDcJgJoEuwFMeBW3EeRztuSj4bUafdwt=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                },
                "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_UNSPECIFIED",
                "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FIT"
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Pamit"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               },
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Single"
                   },
                   {
                    "text": " • "
                   },
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
                    "text": "2016"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_g5Fp9aKaaoF",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                 }
                }
               }
              },
              "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_TALL"
             }
            },
            {
             "musicResponsiveListItemRenderer": {
              "thumbnail": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w120-h120-l90-rj",
                   "width": 120,
                   "height": 120
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                },
                "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_UNSPECIFIED",
                "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FIT"
               }
              },
              "flexColumns": [
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Monokrom"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               },
               {
                "musicResponsiveListItemFlexColumnRenderer": {
                 "text": {
                  "runs": [
                   {
                    "text": "Album"
                   },
                   {
                    "text": " • "
                   },
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
                    "text": "2016"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_4TEBtGA3cZb",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                 }
                }
               }
              },
              "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_TALL"
             }
            }
           ],
           "continuations": [
            {
             "nextContinuationData": {
              "continuation": "ALBUM_PAGE_2_TOKEN"
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

internal const val ARTIST_PAGE_TULUS = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_ARTIST"
 },
 "header": {
  "musicImmersiveHeaderRenderer": {
   "title": {
    "runs": [
     {
      "text": "Tulus"
     }
    ]
   },
   "thumbnail": {
    "musicThumbnailRenderer": {
     "thumbnail": {
      "thumbnails": [
       {
        "url": "https://yt3.googleusercontent.com/h8P1jEIZLM8lkMxNA6Nbq98b43wcqllJSNmcZTCRPAB-F6rG_0Nqw5w7fwou0PN1QGwSW5viwWD5NV0=w540-h225-p-l90-rj",
        "width": 540,
        "height": 225
       },
       {
        "url": "https://yt3.googleusercontent.com/h8P1jEIZLM8lkMxNA6Nbq98b43wcqllJSNmcZTCRPAB-F6rG_0Nqw5w7fwou0PN1QGwSW5viwWD5NV0=w816-h340-p-l90-rj",
        "width": 816,
        "height": 340
       }
      ]
     }
    }
   },
   "monthlyListenerCount": {
    "runs": [
     {
      "text": "54,3 jt audiens bulanan"
     }
    ]
   }
  }
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
          "musicShelfRenderer": {
           "title": {
            "runs": [
             {
              "text": "Lagu teratas",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLOLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w",
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
                      "playlistId": "OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w"
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
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w120-h120-l90-rj",
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
                    "text": "Jatuh Suka",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "qrnJL6P-QqQ",
                      "playlistId": "OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w"
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
                    "text": "165 jt pemutaran"
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
                    "text": "Manusia",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_7FNmIeIV9kf",
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
               "videoId": "qrnJL6P-QqQ"
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
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w120-h120-l90-rj",
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
                    "text": "Monokrom",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "1RrF6Ee_io0",
                      "playlistId": "OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w"
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
                    "text": "222 jt pemutaran"
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
                    "text": "Monokrom",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_4TEBtGA3cZb",
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
               "videoId": "1RrF6Ee_io0"
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
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w120-h120-l90-rj",
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
                    "text": "Interaksi",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "-WF72CWKKEI",
                      "playlistId": "OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w"
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
                    "text": "152 jt pemutaran"
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
                    "text": "Manusia",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_7FNmIeIV9kf",
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
               "videoId": "-WF72CWKKEI"
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
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w60-h60-l90-rj",
                   "width": 60,
                   "height": 60
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w120-h120-l90-rj",
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
                    "text": "Hati-Hati di Jalan",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "E7kHvjvU6JY",
                      "playlistId": "OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w"
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
                    "text": "332 jt pemutaran"
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
                    "text": "Manusia",
                    "navigationEndpoint": {
                     "browseEndpoint": {
                      "browseId": "MPREb_7FNmIeIV9kf",
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
               "videoId": "E7kHvjvU6JY"
              }
             }
            }
           ],
           "bottomText": {
            "runs": [
             {
              "text": "Tampilkan semua"
             }
            ]
           },
           "bottomEndpoint": {
            "browseEndpoint": {
             "browseId": "VLOLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w",
             "browseEndpointContextSupportedConfigs": {
              "browseEndpointContextMusicConfig": {
               "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
              }
             }
            }
           }
          }
         },
         {
          "musicCarouselShelfRenderer": {
           "header": {
            "musicCarouselShelfBasicHeaderRenderer": {
             "title": {
              "runs": [
               {
                "text": "Album"
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
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Manusia",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_7FNmIeIV9kf",
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
                 "text": "2022"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_7FNmIeIV9kf",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/SJTWzxOnk0D_TIfYfkcIUzlYbSB5P8QtPYfdjRt3cBeyMk-0cuxdxTIXYp45ghLEhjEvnUpakX8scPgN=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Monokrom",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_4TEBtGA3cZb",
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
                 "text": "2016"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_4TEBtGA3cZb",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                   "url": "https://yt3.googleusercontent.com/rbdAVYsqSMwwOUh5nR9nL0LjoD9IFW9myJ2ChNNrO3iONjmldRA5dYLVVVQijDpiv95YmQ3omnjERp8q=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/rbdAVYsqSMwwOUh5nR9nL0LjoD9IFW9myJ2ChNNrO3iONjmldRA5dYLVVVQijDpiv95YmQ3omnjERp8q=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Gajah",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_qTrW9rQ11SS",
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
                 "text": "2014"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_qTrW9rQ11SS",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                   "url": "https://yt3.googleusercontent.com/lyU_Lq5Y358oPfvsbroDZp8wy-qDQkGvKvfw6hYf78JvWtX3x2_BTSpTtteWGGyMwFC5tixNaw622TQ7=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/lyU_Lq5Y358oPfvsbroDZp8wy-qDQkGvKvfw6hYf78JvWtX3x2_BTSpTtteWGGyMwFC5tixNaw622TQ7=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Tulus",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_kOJ8CITYAHR",
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
                 "text": "2011"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_kOJ8CITYAHR",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                   "url": "https://yt3.googleusercontent.com/IaaiZU1iEQ9sfLcutqYllc4deAG67KA_oqsQtHEt4UhRzpdDz1RZSo_SXv_Y_AZ5xssmTlZiflSSFL1uVw=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/IaaiZU1iEQ9sfLcutqYllc4deAG67KA_oqsQtHEt4UhRzpdDz1RZSo_SXv_Y_AZ5xssmTlZiflSSFL1uVw=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Langsung Dari Konser Monokrom Jakarta (Live)",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_JiwubZvgoGu",
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
                 "text": "2019"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_JiwubZvgoGu",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                "text": "Single & EP",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "MPADUC_DHlXllTSMB8pTC38_leFg",
                  "browseEndpointContextSupportedConfigs": {
                   "browseEndpointContextMusicConfig": {
                    "pageType": "MUSIC_PAGE_TYPE_ARTIST_DISCOGRAPHY"
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
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://yt3.googleusercontent.com/xrGDyYO3umAVFdsdyIM2G451xiAxCD6haJkCQel6TQlqE-XsEUCGsj_Q5Er4YjFpjWqv-_Ze-VaPPL0j=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/xrGDyYO3umAVFdsdyIM2G451xiAxCD6haJkCQel6TQlqE-XsEUCGsj_Q5Er4YjFpjWqv-_Ze-VaPPL0j=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Teh Hijau",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_RVAbNZdUasD",
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
                 "text": "2026"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_RVAbNZdUasD",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                   "url": "https://yt3.googleusercontent.com/vNgubMQUiW7RRLYNMVHuLpeC1Z2LahUFsdqpnJ6A-wFnIqBmN5WzKqxf_-RDhvgr_DOsho9iYXbbETCNxg=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/vNgubMQUiW7RRLYNMVHuLpeC1Z2LahUFsdqpnJ6A-wFnIqBmN5WzKqxf_-RDhvgr_DOsho9iYXbbETCNxg=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Adaptasi",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "MPREb_FKsUDydW3qF",
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
                 "text": "2020"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_FKsUDydW3qF",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
                "text": "Video",
                "navigationEndpoint": {
                 "browseEndpoint": {
                  "browseId": "VLOLAK5uy_m5BUAnhiPKqlHU_VJn4fOzzBIz8EUDi0M",
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
             "musicTwoRowItemRenderer": {
              "thumbnailRenderer": {
               "musicThumbnailRenderer": {
                "thumbnail": {
                 "thumbnails": [
                  {
                   "url": "https://i.ytimg.com/vi/zyLmavBAY7k/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3kL374WeAfgJPFlKXgjc04Lxz3q3w",
                   "width": 400,
                   "height": 225
                  },
                  {
                   "url": "https://i.ytimg.com/vi/zyLmavBAY7k/hq720.jpg?sqp=-oaymwEKCKAGEMIDIABIWg&rs=AMzJL3k6IUEyLuEfHl88gvkNpRYFYEK-WA",
                   "width": 800,
                   "height": 450
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Teh Hijau"
                }
               ]
              },
              "subtitle": {
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
                 "text": "19 jt x ditonton"
                }
               ]
              },
              "navigationEndpoint": {
               "watchEndpoint": {
                "videoId": "zyLmavBAY7k",
                "playlistId": "OLAK5uy_m5BUAnhiPKqlHU_VJn4fOzzBIz8EUDi0M",
                "index": 0
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
                "text": "Pertunjukan langsung"
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
                   "url": "https://i.ytimg.com/vi/3R8MaXnc7dY/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3lUbJqZ8gD_81I6ERcpGgIch9s5WA",
                   "width": 400,
                   "height": 225
                  },
                  {
                   "url": "https://i.ytimg.com/vi/3R8MaXnc7dY/hq720.jpg?sqp=-oaymwEKCKAGEMIDIABIWg&rs=AMzJL3ndkb8ZUCI5RcpN2L88FFYAyuqzeg",
                   "width": 800,
                   "height": 450
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "TULUS - Pamit (Live at Smecky Music Studio)"
                }
               ]
              },
              "subtitle": {
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
                 "text": "3,9 jt x ditonton"
                }
               ]
              },
              "navigationEndpoint": {
               "watchEndpoint": {
                "videoId": "3R8MaXnc7dY"
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
                "text": "Termasuk di"
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
                   "url": "https://yt3.googleusercontent.com/Nlt01qkHg1MyYC34uuFYGMxXUp8wCND-QRqwXPak5EZPAj5oAfdsvUPreA9hpRiGDG4Wp1DxHeJi9qI=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/Nlt01qkHg1MyYC34uuFYGMxXUp8wCND-QRqwXPak5EZPAj5oAfdsvUPreA9hpRiGDG4Wp1DxHeJi9qI=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Presenting Tulus",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLRDCLAK5uy_kR2pM9ewJOAAqSZLidzflfoknDik6dinM",
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
                "browseId": "VLRDCLAK5uy_kR2pM9ewJOAAqSZLidzflfoknDik6dinM",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
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
                "text": "Playlist dari Tulus"
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
                   "url": "https://yt3.ggpht.com/0puwEQfKIv0PnXxDpA2MKa_AagSQvMndCQCVTPPsdWO8IDIzaZN88rF5kYoeuHuHRfPN2Pj4zec=s192",
                   "width": 192,
                   "height": 192
                  },
                  {
                   "url": "https://yt3.ggpht.com/0puwEQfKIv0PnXxDpA2MKa_AagSQvMndCQCVTPPsdWO8IDIzaZN88rF5kYoeuHuHRfPN2Pj4zec=s576",
                   "width": 576,
                   "height": 576
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "TULUS - Manusia (Official Video Lyric)",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "VLPLzgVPDDV8B8coULgS5rJYh2HW9dhtjc8b",
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
                 "text": "Tulus",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCRggxhdYIz0zSvUgJmCWMGg",
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
                 "text": "3,3 jt x ditonton"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "VLPLzgVPDDV8B8coULgS5rJYh2HW9dhtjc8b",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
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
                "text": "Penggemar mungkin juga suka"
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
                   "url": "https://yt3.googleusercontent.com/uPbJD1rupFLyxWEOdwV7oO5joK_HbMKin5XsUR_hg1z0WZGp-u9_KtD6sF6X63qkdUZjkXXwabqmik_n=w226-h226-l90-rj",
                   "width": 226,
                   "height": 226
                  },
                  {
                   "url": "https://yt3.googleusercontent.com/uPbJD1rupFLyxWEOdwV7oO5joK_HbMKin5XsUR_hg1z0WZGp-u9_KtD6sF6X63qkdUZjkXXwabqmik_n=w544-h544-l90-rj",
                   "width": 544,
                   "height": 544
                  }
                 ]
                }
               }
              },
              "title": {
               "runs": [
                {
                 "text": "Rizky Febian",
                 "navigationEndpoint": {
                  "browseEndpoint": {
                   "browseId": "UCGhU4I_1obQHUdq_plb1g2w",
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
              "subtitle": {
               "runs": [
                {
                 "text": "11,5 jt audiens bulanan"
                }
               ]
              },
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "UCGhU4I_1obQHUdq_plb1g2w",
                "browseEndpointContextSupportedConfigs": {
                 "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
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

internal const val ALBUM_PAGE_MANUSIA = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_ALBUM"
 },
 "contents": {
  "twoColumnBrowseResultsRenderer": {
   "tabs": [
    {
     "tabRenderer": {
      "content": {
       "sectionListRenderer": {
        "contents": [
         {
          "musicResponsiveHeaderRenderer": {
           "thumbnail": {
            "musicThumbnailRenderer": {
             "thumbnail": {
              "thumbnails": [
               {
                "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w60-h60-l90-rj",
                "width": 60,
                "height": 60
               },
               {
                "url": "https://yt3.googleusercontent.com/0LkNos-al_3gRdfeVnsoULGEInj8_1uQkE4p9nT_6heSYKzO2emQrqm-XcexNEhl_sfTueS-gT45yoQ=w120-h120-l90-rj",
                "width": 120,
                "height": 120
               }
              ]
             },
             "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_UNSPECIFIED",
             "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_UNSPECIFIED"
            }
           },
           "title": {
            "runs": [
             {
              "text": "Manusia"
             }
            ]
           },
           "subtitle": {
            "runs": [
             {
              "text": "Album"
             },
             {
              "text": " • "
             },
             {
              "text": "2022"
             }
            ]
           },
           "straplineTextOne": {
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
             }
            ]
           },
           "secondSubtitle": {
            "runs": [
             {
              "text": "10 lagu"
             },
             {
              "text": " • "
             },
             {
              "text": "38 menit"
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
   ],
   "secondaryContents": {
    "sectionListRenderer": {
     "contents": [
      {
       "musicShelfRenderer": {
        "contents": [
         {
          "musicResponsiveListItemRenderer": {
           "flexColumns": [
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "Tujuh Belas",
                 "navigationEndpoint": {
                  "watchEndpoint": {
                   "videoId": "cA7Gzh2ISL0",
                   "playlistId": "OLAK5uy_kZv-qzpXB3MU-BG72A2o_5YV3M76vTtUQ",
                   "watchEndpointMusicSupportedConfigs": {
                    "watchEndpointMusicConfig": {
                     "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            },
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {},
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            },
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "38 jt pemutaran"
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            }
           ],
           "fixedColumns": [
            {
             "musicResponsiveListItemFixedColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "4.15"
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH",
              "size": "MUSIC_RESPONSIVE_LIST_ITEM_FIXED_COLUMN_SIZE_SMALL"
             }
            }
           ],
           "playlistItemData": {
            "videoId": "cA7Gzh2ISL0"
           },
           "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_MEDIUM",
           "index": {
            "runs": [
             {
              "text": "1"
             }
            ]
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
                 "text": "Kelana",
                 "navigationEndpoint": {
                  "watchEndpoint": {
                   "videoId": "Wc2ykyBEgIw",
                   "playlistId": "OLAK5uy_kZv-qzpXB3MU-BG72A2o_5YV3M76vTtUQ",
                   "watchEndpointMusicSupportedConfigs": {
                    "watchEndpointMusicConfig": {
                     "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            },
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {},
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            },
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "9,3 jt pemutaran"
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            }
           ],
           "fixedColumns": [
            {
             "musicResponsiveListItemFixedColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "3.12"
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH",
              "size": "MUSIC_RESPONSIVE_LIST_ITEM_FIXED_COLUMN_SIZE_SMALL"
             }
            }
           ],
           "playlistItemData": {
            "videoId": "Wc2ykyBEgIw"
           },
           "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_MEDIUM",
           "index": {
            "runs": [
             {
              "text": "2"
             }
            ]
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
                 "text": "Remedi",
                 "navigationEndpoint": {
                  "watchEndpoint": {
                   "videoId": "12LLF5HZvh8",
                   "playlistId": "OLAK5uy_kZv-qzpXB3MU-BG72A2o_5YV3M76vTtUQ",
                   "watchEndpointMusicSupportedConfigs": {
                    "watchEndpointMusicConfig": {
                     "musicVideoType": "MUSIC_VIDEO_TYPE_OMV"
                    }
                   }
                  }
                 }
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            },
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {},
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            },
            {
             "musicResponsiveListItemFlexColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "9 jt pemutaran"
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
             }
            }
           ],
           "fixedColumns": [
            {
             "musicResponsiveListItemFixedColumnRenderer": {
              "text": {
               "runs": [
                {
                 "text": "4.08"
                }
               ]
              },
              "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH",
              "size": "MUSIC_RESPONSIVE_LIST_ITEM_FIXED_COLUMN_SIZE_SMALL"
             }
            }
           ],
           "playlistItemData": {
            "videoId": "12LLF5HZvh8"
           },
           "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_MEDIUM",
           "index": {
            "runs": [
             {
              "text": "3"
             }
            ]
           }
          }
         }
        ],
        "shelfDivider": {
         "musicShelfDividerRenderer": {
          "hidden": true
         }
        },
        "contentsMultiSelectable": true
       }
      },
      {
       "musicCarouselShelfRenderer": {
        "header": {
         "musicCarouselShelfBasicHeaderRenderer": {
          "title": {
           "runs": [
            {
             "text": "Rilis untuk Anda"
            }
           ]
          },
          "headerStyle": "MUSIC_CAROUSEL_SHELF_BASIC_HEADER_STYLE_DEFAULT"
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
                "url": "https://yt3.googleusercontent.com/UDjpmND0DSM4VfIy4-Q0ZOmy79Y3MGB5C9jgdQTaS092O0rHDTPi-cMMRABKdXiEPZ3jCapc1ZdAMoWC=w226-h226-l90-rj",
                "width": 226,
                "height": 226
               },
               {
                "url": "https://yt3.googleusercontent.com/UDjpmND0DSM4VfIy4-Q0ZOmy79Y3MGB5C9jgdQTaS092O0rHDTPi-cMMRABKdXiEPZ3jCapc1ZdAMoWC=w544-h544-l90-rj",
                "width": 544,
                "height": 544
               }
              ]
             },
             "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_UNSPECIFIED",
             "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FILL"
            }
           },
           "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
           "title": {
            "runs": [
             {
              "text": "Merakit",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "MPREb_K1bBMNfnmnd",
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
              "text": "Album"
             },
             {
              "text": " • "
             },
             {
              "text": "Yura Yunita",
              "navigationEndpoint": {
               "browseEndpoint": {
                "browseId": "UCld1g5jDOANAYucxZxFqa2Q",
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
             "browseId": "MPREb_K1bBMNfnmnd",
             "browseEndpointContextSupportedConfigs": {
              "browseEndpointContextMusicConfig": {
               "pageType": "MUSIC_PAGE_TYPE_ALBUM"
              }
             }
            }
           }
          }
         }
        ],
        "itemSize": "COLLECTION_STYLE_ITEM_SIZE_SMALL"
       }
      }
     ]
    }
   }
  }
 }
}
"""

internal const val SEARCH_ARTISTS_PAGE_2 = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_ARTIST_SEARCH"
 },
 "continuationContents": {
  "musicShelfContinuation": {
   "contents": [
    {
     "musicResponsiveListItemRenderer": {
      "thumbnail": {
       "musicThumbnailRenderer": {
        "thumbnail": {
         "thumbnails": [
          {
           "url": "https://yt3.googleusercontent.com/exampleArtistTwo=w120-h120-p-l90-rj",
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
            "text": "Yura Yunita"
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
            "text": "Artis"
           },
           {
            "text": " • "
           },
           {
            "text": "12 jt audiens bulanan"
           }
          ]
         }
        }
       }
      ],
      "navigationEndpoint": {
       "browseEndpoint": {
        "browseId": "UCexampleYuraYunita",
        "browseEndpointContextSupportedConfigs": {
         "browseEndpointContextMusicConfig": {
          "pageType": "MUSIC_PAGE_TYPE_ARTIST"
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
"""

internal const val SEARCH_ALBUMS_PAGE_2 = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_ALBUM_SEARCH"
 },
 "continuationContents": {
  "musicShelfContinuation": {
   "contents": [
    {
     "musicResponsiveListItemRenderer": {
      "thumbnail": {
       "musicThumbnailRenderer": {
        "thumbnail": {
         "thumbnails": [
          {
           "url": "https://yt3.googleusercontent.com/exampleAlbumTwo=w120-h120-l90-rj",
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
            "text": "Gajah"
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
            "text": "Album"
           },
           {
            "text": " • "
           },
           {
            "text": "Tulus"
           },
           {
            "text": " • "
           },
           {
            "text": "2014"
           }
          ]
         }
        }
       }
      ],
      "navigationEndpoint": {
       "browseEndpoint": {
        "browseId": "MPREb_exampleGajah",
        "browseEndpointContextSupportedConfigs": {
         "browseEndpointContextMusicConfig": {
          "pageType": "MUSIC_PAGE_TYPE_ALBUM"
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
"""
