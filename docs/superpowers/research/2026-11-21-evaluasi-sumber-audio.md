# Evaluasi sumber katalog dan audio

Tanggal uji: 21 November 2026, dari koneksi rumah di Indonesia, tanpa akun. Langkah 1 dari [rencana evaluasi](../plans/2026-11-18-evaluasi-katalog-audio.md).

## Ringkasan

Hanya YouTube Music yang menyediakan katalog lagu populer dengan audio penuh tanpa server dan tanpa akun. Sumber resmi lain hanya memberi cuplikan 30 detik, atau katalognya tidak berisi lagu populer.

YouTube Music lolos uji teknis untuk 19 dari 20 lagu. Hasilnya berupa AAC dalam MP4 yang sudah didukung Media3 dan AVPlayer. Masalahnya ada di ketentuan layanan YouTube, yang melarang akses selain lewat layanan resminya. Senada desktop sudah memakai jalur ini, jadi keputusan pemilik proyek tentang risiko itu berlaku juga untuk versi mobile.

## Kandidat

| Sumber | Katalog lagu populer | Audio | Format | Akses | Ketentuan |
| --- | --- | --- | --- | --- | --- |
| **YouTube Music (Innertube)** | Ya | Lagu penuh | AAC/MP4 (itag 140, ±128 kbps); juga Opus/WebM | Tanpa kunci; perlu `visitorData` | Melanggar ToS YouTube (lihat di bawah) |
| iTunes Search API | Ya | Cuplikan 30 detik | AAC (`audio/x-m4p`) | Tanpa kunci | Cuplikan untuk promosi, bukan pemutar musik |
| Deezer API | Ya | Cuplikan 30 detik | MP3 | Tanpa kunci | Lagu penuh hanya lewat SDK dan langganan |
| Audius | Tidak; isinya unggahan pengguna, termasuk unggahan ulang lagu populer yang hak ciptanya tidak jelas | Lagu penuh | MP3 | Tanpa kunci; `app_name` | Terbuka, tapi isinya tidak sesuai kebutuhan |
| Jamendo | Tidak; hanya musik berlisensi Creative Commons | Lagu penuh | MP3 | `client_id` per aplikasi | Resmi; 0 hasil untuk lagu yang diuji |
| Spotify, Apple Music | Ya | Hanya lewat SDK resmi dan langganan pengguna | — | Kunci developer; Apple Developer Program berbayar | Tidak mungkin tanpa langganan pengguna |

Pencarian "tulus hati-hati di jalan" di Audius mengembalikan "TULUS - Hati-Hati di Jalan (Official Lyric Video)" yang diunggah akun lain. Pencarian "taylor swift anti-hero" hanya mengembalikan remix.

## YouTube Music: hasil uji teknis

Cara kerja yang diuji sama dengan Senada desktop (`senada/ytmusic.py` dan `senada/streams.py`), tapi tanpa yt-dlp:

1. **Pencarian:** `POST music.youtube.com/youtubei/v1/search` dengan client `WEB_REMIX` dan filter lagu. Respons ini juga memberi `visitorData`.
2. **Resolusi audio:** `POST www.youtube.com/youtubei/v1/player` dengan client `VISIONOS` (versi 1.02, sesuai yt-dlp) dan `visitorData` yang sama. Client ini memberi URL langsung tanpa tantangan JavaScript dan tanpa PO token. Tanpa `visitorData`, semua lagu ditolak dengan `LOGIN_REQUIRED`.

| Hasil | Angka |
| --- | --- |
| Lagu diuji | 20: pop, rock, dangdut, dan campursari Indonesia; K-pop; J-pop dengan judul Jepang; lagu Barat; lagu Spanyol |
| Pencarian berhasil, hasil teratas sesuai | 20 dari 20, ±0,4 detik |
| Audio dapat diresolusi | 19 dari 20, biasanya ±0,1 detik (satu kali 18 detik) |
| Gagal | "Bertaut" (Nadin Amizah) selalu `LOGIN_REQUIRED`; lagu lain dari artis yang sama berhasil |
| Format | Selalu ada itag 140 `audio/mp4; codecs="mp4a.40.2"`, juga 139, 249, 250, 251, dan HLS |
| Range request awal dan tengah file | 206 untuk semua lagu yang berhasil diresolusi |
| Unduhan penuh satu lagu | 3,9 MB dalam 0,2 detik; tidak ada pelambatan |
| Masa berlaku URL | 5–6 jam (`expire` di URL) |
| Header yang dibutuhkan | Tidak ada; URL bisa dibuka dengan User-Agent AppleCoreMedia, ExoPlayer, atau tanpa User-Agent |

Catatan teknis:

- **Terikat IP:** URL diterbitkan untuk IP yang meminta (`ip=` di URL). Resolusi harus dilakukan di ponsel itu sendiri, bukan lewat server bersama.
- **Rapuh:** client `VISIONOS` dapat diblokir kapan saja. yt-dlp mencatat `ANDROID_VR` dan `IOS` sekarang memerlukan PO token, sementara `WEB_MUSIC` perlu token GVS ([PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide), [issue #17348](https://github.com/yt-dlp/yt-dlp/issues/17348)). Konfigurasi client harus bisa diperbarui tanpa merombak kode, dan aplikasi harus menampilkan pesan jelas saat resolusi gagal.
- **Uji CI:** IP data center lebih sering dibatasi YouTube, jadi uji endpoint nyata dijalankan terpisah dari tes otomatis, sesuai rencana.

## Ketentuan YouTube

- **Ketentuan Layanan YouTube, Izin dan Batasan:** melarang mengakses atau mengunduh konten kecuali diizinkan, mengakali bagian mana pun dari layanan, dan mengakses layanan dengan cara otomatis ([ToS](https://www.youtube.com/static?template=terms)).
- **API resmi (YouTube Data API):** hanya untuk pencarian dan metadata. Kebijakan developer melarang memisahkan audio dari video (III.I.7), memutar dari pemutar latar belakang (III.I.9), dan menyimpan atau mengunduh konten (III.E.1) ([Developer Policies](https://developers.google.com/youtube/terms/developer-policies)). Jadi API resmi tidak bisa dipakai untuk pemutar musik seperti Senada.

Akibatnya:

- **Distribusi:** Senada Mobile dengan sumber ini tidak bisa masuk App Store atau Google Play. Distribusi hanya lewat sideload (AltStore, APK), sama seperti aplikasi sejenis (Metrolist, Mochy).
- **Risiko:** YouTube dapat memblokir client atau membatasi IP pengguna. Senada desktop sudah menangani kasus "not a bot"/HTTP 429 dengan berhenti mencoba. Mobile perlu perilaku yang sama.

## Rekomendasi

Pilih YouTube Music lewat Innertube untuk prototipe, dengan syarat pemilik proyek menerima tiga hal:

1. Senada Mobile hanya didistribusikan lewat sideload.
2. Sumber ini melanggar ToS YouTube dan bisa berhenti bekerja sewaktu-waktu.
3. Aplikasi harus memberi tahu pengguna dengan jelas saat terjadi kegagalan.

Alasannya, ini satu-satunya kandidat yang memenuhi tujuan Senada: lagu populer, audio penuh, tanpa server. Sumber ini juga menjaga kompatibilitas library dengan desktop (`yt:track:<videoId>`).

Jika syarat itu tidak bisa diterima, tidak ada alternatif resmi yang setara. Pilihannya mengubah lingkup produk, misalnya hanya file lokal, atau hanya musik Creative Commons dari Jamendo.

## Langkah berikutnya bila disetujui

Sesuai langkah 2 rencana evaluasi:

1. Buat `YouTubeMusicCatalog : CatalogRepository` (WEB_REMIX search) dan `YouTubeMusicResolver : StreamResolver` (VISIONOS player, pilih itag 140) di `commonMain`, memakai Ktor. Pertahankan `Track.id = yt:track:<videoId>`, dan `ResolvedAudio.expiresAtEpochMs` dari parameter `expire`.
2. Simpan `visitorData` per sesi dan pakai ulang untuk pencarian, continuation, dan resolusi.
3. Untuk tes otomatis, gunakan fixture JSON yang direkam dari respons asli dengan URL dihapus. Uji endpoint nyata secara manual.
4. Tangani `LOGIN_REQUIRED` dan `UNPLAYABLE` sebagai "lagu tidak tersedia" dengan opsi mencoba lagi. Tangani HTTP 429 atau pesan "not a bot" sebagai pembatasan sementara, tanpa langsung mencoba lagu berikutnya.
