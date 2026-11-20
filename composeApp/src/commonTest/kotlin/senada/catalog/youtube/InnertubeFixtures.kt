package senada.catalog.youtube

// Respons Innertube asli yang dipangkas; URL stream diganti alamat contoh.

internal const val SEARCH_PAGE_1 = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_SEARCH"
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
              "text": "Lagu"
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
                    "text": "Teh Hijau",
                    "navigationEndpoint": {
                     "watchEndpoint": {
                      "videoId": "5F28ye50-Kc",
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
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
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "3.33"
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
                    "text": "242 jt pemutaran"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "playlistItemData": {
               "videoId": "5F28ye50-Kc"
              },
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
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
                "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FIT"
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
                      "watchEndpointMusicSupportedConfigs": {
                       "watchEndpointMusicConfig": {
                        "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
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
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "3.55"
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
                    "text": "165 jt pemutaran"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "playlistItemData": {
               "videoId": "qrnJL6P-QqQ"
              },
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
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
                "thumbnailScale": "MUSIC_THUMBNAIL_SCALE_ASPECT_FIT"
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
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
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
                   },
                   {
                    "text": " • "
                   },
                   {
                    "text": "4.02"
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
                    "text": "332 jt pemutaran"
                   }
                  ]
                 },
                 "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
                }
               }
              ],
              "playlistItemData": {
               "videoId": "E7kHvjvU6JY"
              },
              "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
              "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_TALL"
             }
            }
           ],
           "continuations": [
            {
             "nextContinuationData": {
              "continuation": "PAGE_2_TOKEN"
             }
            }
           ]
          }
         }
        ]
       }
      },
      "tabIdentifier": "music_search_catalog"
     }
    }
   ]
  }
 }
}
"""

internal const val SEARCH_PAGE_2 = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_SEARCH"
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
           "url": "https://yt3.googleusercontent.com/yl8mtEERLb7bOvQPiBM0lcA6SO0rsUiJIFJUig3lPNZUv_QBF8EzAlFqkoK9RGyTzQ3fxcdZmoWEzek0=w60-h60-l90-rj",
           "width": 60,
           "height": 60
          },
          {
           "url": "https://yt3.googleusercontent.com/yl8mtEERLb7bOvQPiBM0lcA6SO0rsUiJIFJUig3lPNZUv_QBF8EzAlFqkoK9RGyTzQ3fxcdZmoWEzek0=w120-h120-l90-rj",
           "width": 120,
           "height": 120
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
            "text": "TULUS KAU HIANATI (feat. Fresly Nikijuluw)",
            "navigationEndpoint": {
             "watchEndpoint": {
              "videoId": "-NeQ-Rn9HIc",
              "watchEndpointMusicSupportedConfigs": {
               "watchEndpointMusicConfig": {
                "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
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
         "text": {
          "runs": [
           {
            "text": "Akim Oei",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "UCRuI7tc20t-5uvH8UMDgR1w",
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
            "text": "TULUS KAU HIANATI",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "MPREb_4XoqcFeRevy",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ALBUM"
               }
              }
             }
            }
           },
           {
            "text": " • "
           },
           {
            "text": "5.47"
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
            "text": "105 rb pemutaran"
           }
          ]
         },
         "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
        }
       }
      ],
      "playlistItemData": {
       "videoId": "-NeQ-Rn9HIc"
      },
      "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
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
           "url": "https://yt3.googleusercontent.com/NVmXXWXl-wxGriItSZ6MOYxE2C_k4B3CM4Gi8pOB8I8lcLsOwaVE95J7IfGGhNQhnz1Lbx91b1EadFu0=w60-h60-l90-rj",
           "width": 60,
           "height": 60
          },
          {
           "url": "https://yt3.googleusercontent.com/NVmXXWXl-wxGriItSZ6MOYxE2C_k4B3CM4Gi8pOB8I8lcLsOwaVE95J7IfGGhNQhnz1Lbx91b1EadFu0=w120-h120-l90-rj",
           "width": 120,
           "height": 120
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
            "text": "Tulus Hati Luhur Budi",
            "navigationEndpoint": {
             "watchEndpoint": {
              "videoId": "Anzdcw6vEGY",
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
         },
         "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
        }
       },
       {
        "musicResponsiveListItemFlexColumnRenderer": {
         "text": {
          "runs": [
           {
            "text": "Revina Alvira",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "UCGx0RgwyfHLNEFUuXGqL2FQ",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ARTIST"
               }
              }
             }
            }
           },
           {
            "text": " & "
           },
           {
            "text": "Rian",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "UCm9rH8suELdHoi4r8uhYEnQ",
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
            "text": "Tulus Hati Luhur Budi",
            "navigationEndpoint": {
             "browseEndpoint": {
              "browseId": "MPREb_MXRUM8rHSvc",
              "browseEndpointContextSupportedConfigs": {
               "browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_ALBUM"
               }
              }
             }
            }
           },
           {
            "text": " • "
           },
           {
            "text": "4.58"
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
            "text": "113 rb pemutaran"
           }
          ]
         },
         "displayPriority": "MUSIC_RESPONSIVE_LIST_ITEM_COLUMN_DISPLAY_PRIORITY_HIGH"
        }
       }
      ],
      "playlistItemData": {
       "videoId": "Anzdcw6vEGY"
      },
      "flexColumnDisplayStyle": "MUSIC_RESPONSIVE_LIST_ITEM_FLEX_COLUMN_DISPLAY_STYLE_TWO_LINE_STACK",
      "itemHeight": "MUSIC_RESPONSIVE_LIST_ITEM_HEIGHT_TALL"
     }
    }
   ],
   "autoReloadWhenEmpty": false
  }
 }
}
"""

internal const val PLAYER_OK = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_PLAYER"
 },
 "playabilityStatus": {
  "status": "OK",
  "playableInEmbed": true
 },
 "streamingData": {
  "expiresInSeconds": "21540",
  "adaptiveFormats": [
   {
    "itag": 139,
    "url": "https://rr1.example.invalid/videoplayback?expire=1763730000&itag=139&mime=x",
    "mimeType": "audio/mp4; codecs=\"mp4a.40.5\"",
    "bitrate": 50392,
    "contentLength": "1299819",
    "trackAbsoluteLoudnessLkfs": -8.48,
    "qualityOrdinal": "QUALITY_ORDINAL_UNKNOWN"
   },
   {
    "itag": 140,
    "url": "https://rr1.example.invalid/videoplayback?expire=1763730000&itag=140&mime=x",
    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
    "bitrate": 131378,
    "contentLength": "3447410",
    "trackAbsoluteLoudnessLkfs": -8.48,
    "qualityOrdinal": "QUALITY_ORDINAL_UNKNOWN"
   },
   {
    "itag": 249,
    "url": "https://rr1.example.invalid/videoplayback?expire=1763730000&itag=249&mime=x",
    "mimeType": "audio/webm; codecs=\"opus\"",
    "bitrate": 58092,
    "contentLength": "1383486",
    "trackAbsoluteLoudnessLkfs": -8.48,
    "qualityOrdinal": "QUALITY_ORDINAL_UNKNOWN"
   },
   {
    "itag": 250,
    "url": "https://rr1.example.invalid/videoplayback?expire=1763730000&itag=250&mime=x",
    "mimeType": "audio/webm; codecs=\"opus\"",
    "bitrate": 76610,
    "contentLength": "1833686",
    "trackAbsoluteLoudnessLkfs": -8.48,
    "qualityOrdinal": "QUALITY_ORDINAL_UNKNOWN"
   },
   {
    "itag": 251,
    "url": "https://rr1.example.invalid/videoplayback?expire=1763730000&itag=251&mime=x",
    "mimeType": "audio/webm; codecs=\"opus\"",
    "bitrate": 150456,
    "contentLength": "3614812",
    "trackAbsoluteLoudnessLkfs": -8.48,
    "qualityOrdinal": "QUALITY_ORDINAL_UNKNOWN"
   }
  ],
  "hlsManifestUrl": "https://example.invalid/hls",
  "aspectRatio": 1
 },
 "adBreakHeartbeatParams": "Q0FBJTNE"
}
"""

internal const val PLAYER_LOGIN_REQUIRED = """
{
 "responseContext": {
  "visitorData": "VISITOR_FROM_PLAYER"
 },
 "playabilityStatus": {
  "status": "LOGIN_REQUIRED",
  "reason": "Login"
 }
}
"""
