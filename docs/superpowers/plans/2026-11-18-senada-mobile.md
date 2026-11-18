# Senada Mobile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. User authorization in this conversation covers planning; product implementation is a subsequent task.

**Goal:** Membangun aplikasi musik Android dan iOS untuk komunitas kecil, dengan library lokal, lirik tersinkron, dan pemutaran yang andal serta terukur penggunaan sumber dayanya.

**Architecture:** Kotlin Multiplatform membagi model data, akses katalog, penyimpanan, dan logika fitur. Compose Multiplatform membagi tampilan; pemutar dan integrasi media menggunakan implementasi Android dan iOS masing-masing. Rust hanya dipertimbangkan setelah profiling menemukan kebutuhan konkret.

**Tech Stack:** Kotlin, Compose Multiplatform, Coroutines/Flow, Ktor Client, kotlinx.serialization, SQLDelight/SQLite, Coil Multiplatform, Android Media3, iOS AVFoundation dan MediaPlayer. GitHub Actions dengan runner Linux dan macOS menjadi usulan otomasi awal.

**Spec:** Keputusan percakapan dan bagian “Rancangan yang menjadi acuan” di dokumen ini. Pilihan tambahan yang belum dinyatakan pengguna ditandai sebagai usulan. Ini merupakan rencana bertahap; keluaran tahap 1 mengunci kelayakan sumber audio dan distribusi sebelum pekerjaan katalog dilanjutkan.

## Global Constraints

- Android dan iOS dikembangkan sejak awal; satu tahap fitur belum dianggap selesai jika baru lolos di Android.
- Pengguna memiliki iPhone dan belum memiliki Mac. OS komputer kerja, model iPhone, versi iOS, dan akses perangkat Android belum dikonfirmasi.
- Distribusi untuk komunitas kecil, tanpa publikasi Google Play atau App Store.
- KMP + Compose Multiplatform adalah pendekatan yang diterima pengguna. Rust ditunda.
- Usulan cakupan awal: tanpa akun Senada, tanpa backend wajib, library disimpan lokal. Perubahan menjadi layanan server memerlukan keputusan produk tersendiri.
- Target minimum usulan: Android 8/API 26 dan iOS 16, hanya arsitektur 64-bit. Konfirmasikan dengan perangkat komunitas dan kompatibilitas dependensi pada tahap 1.
- Pilih versi stabil yang saling kompatibel saat scaffold, kunci dalam version catalog dan Gradle wrapper; jangan memakai nomor “latest” dinamis.
- Jangan menganggap kode Python, proses mpv/yt-dlp, atau path file desktop dapat dijalankan langsung di iOS.
- Uji performa memakai build rilis, perangkat yang dicatat model/OS-nya, dan skenario yang konsisten.
- Signing key dan profil provisioning disimpan sebagai secret CI atau penyimpanan kredensial, bukan dalam repositori atau artefak log.

## Review Focus

1. Hasil resolve lama tiba setelah pengguna berpindah lagu: hanya permintaan aktif boleh memulai audio (tahap 3).
2. URL audio kedaluwarsa atau provider membatasi permintaan: resolusi ulang terbatas, pesan jelas, tanpa retry atau skip tanpa batas (tahap 2 dan 3).
3. Aplikasi tertutup, migrasi database, atau backup rusak: library tetap utuh dan restore gagal secara atomik (tahap 4 dan 6).
4. Telepon masuk, headset terlepas, atau jaringan hilang: perilaku playback konsisten dan UI mengikuti pemutar sebenarnya (tahap 3 dan 7).
5. Lirik datang terlambat setelah lagu berganti, seek, atau playlist berisi lagu yang sama berulang: sinkronisasi dan identitas entri antrean tetap benar (tahap 3 dan 5).

## Rancangan yang menjadi acuan

### Produk dan cakupan

Nama proyek: **Senada Mobile**. Pengguna telah memilih repositori [Zeeynrs/Senada-Mobile](https://github.com/Zeeynrs/Senada-Mobile) sebagai tempat proyek. Siklus rilis dan dependency mobile dikelola terpisah dari desktop. Repositori awal berisi README dan lisensi MIT; catatan lisensi proyek pembanding tersedia dalam studi terpisah.

Fitur beta pertama:

- Pencarian lagu dan hasil berhalaman, sesuai provider yang lulus tahap kelayakan.
- Play, pause, seek, previous/next, antrean, repeat dan shuffle.
- Kontrol sistem dan pemutaran saat layar terkunci.
- Favorit, playlist lokal, riwayat maksimal 200 lagu unik.
- Lirik sinkron per baris, dengan tampilan plain lyrics atau status tidak tersedia.
- Tema terang/gelap dan aksen yang mengikuti artwork, dengan efek sederhana.
- Ekspor-impor library yang kompatibel dengan backup desktop untuk data yang didukung.
- Impor satu file audio melalui pemilih dokumen untuk pengujian dan mendengarkan lokal; pemindaian seluruh folder bukan syarat beta.

Ditunda: download katalog untuk offline, impor Spotify dan pencocokan massal, karaoke per kata, terjemahan, crossfade, equalizer, visualizer, overlay mini player di luar aplikasi, Discord presence, sinkronisasi cloud dan migrasi desktop ke stack baru.

Audio contoh atau file lokal hanya alat pembuktian pemutar. Jika sumber katalog yang diinginkan belum layak pada salah satu platform, jangan menyebut beta streaming Senada selesai. Pilih bersama apakah memperpanjang eksplorasi provider, mengubah sumber musik, atau merilis produk dengan cakupan lokal yang berbeda.

### Temuan dari Senada desktop

Ditinjau melalui GitHub pada 18 November 2026; referensi ini bukan audit lengkap seluruh aplikasi.

| Sumber | Perilaku sekarang | Tindakan mobile |
|---|---|---|
| `senada/engine.py` | mpv melalui proses terpisah dan JSON IPC | Implementasikan ulang dengan Media3 dan AVPlayer/AVQueuePlayer |
| `senada/streams.py` | yt-dlp melalui subprocess; URL audio dicache dengan masa berlaku | Pisahkan `StreamResolver`; buktikan pengganti yang berjalan pada kedua platform |
| `senada/ytmusic.py` | Katalog melalui endpoint Innertube, normalisasi track | Gunakan sebagai referensi kontrak dan fixture, bukan jaminan API mobile stabil |
| `senada/store.py` | Favorit, playlist, saved items dan riwayat lokal dalam JSON | Database SQLite lokal; identitas track tetap stabil |
| `senada/backup.py` | JSON `app=Senada`, `kind=backup`, `format=1`; merge atau replace | Beta: impor merge transaksional, ekspor format kompatibel; jangan impor settings desktop |
| `senada/lyrics.py` | LRC/TTML/KRC serta beberapa provider | Port parser LRC dulu; satu provider HTTPS sebagai kandidat awal |
| `senada/matching.py` | Pencocokan judul, artis, durasi dan versi rekaman | Referensi tahap impor Spotify setelah beta |

Track YouTube desktop memakai `uri=yt:track:<videoId>`. Pertahankan URI itu dalam ekspor untuk lagu yang berasal dari provider tersebut; gunakan ID yang diberi namespace untuk sumber lain. URL streaming sementara tidak menjadi identitas lagu dan tidak disimpan sebagai URL permanen di library.

### Batas arsitektur dan kontrak

Mulai dengan satu modul aplikasi bersama, bukan banyak modul Gradle. Gunakan package terpisah untuk fitur yang memiliki tanggung jawab berbeda.

```text
Senada-Mobile/
  composeApp/src/commonMain/kotlin/senada/
    app/                 # App.kt, AppDependencies.kt
    model/               # Track.kt, Playlist.kt
    catalog/             # CatalogRepository.kt, StreamResolver.kt
    playback/            # PlaybackController.kt, PlaybackCoordinator.kt
    library/             # LibraryRepository.kt, LibraryScreen.kt
    lyrics/              # LrcParser.kt, LyricsRepository.kt, LyricsScreen.kt
    search/              # SearchViewModel.kt, SearchScreen.kt
    backup/              # BackupCodec.kt, BackupRepository.kt
    settings/            # SettingsScreen.kt
  composeApp/src/commonMain/sqldelight/senada/db/
  composeApp/src/commonTest/kotlin/senada/
  composeApp/src/androidMain/kotlin/senada/platform/
  composeApp/src/iosMain/kotlin/senada/platform/
  iosApp/                # Xcode host, signing, capabilities
  .github/workflows/     # check.yml dan release.yml
  docs/                  # setup, keputusan provider, QA, laporan performa
```

Kontrak inti usulan (tipe pendukung didefinisikan bersama kontraknya):

- `Track`: `id: String`, `title: String`, `artists: List<String>`, `durationMs: Long?`, `artworkUrl: String?`, `source: String`. `id` diberi namespace; `yt:track:...` dipertahankan.
- `TrackPage`: `items: List<Track>`, `nextPageToken: String?`.
- `CatalogRepository.search(query: String, pageToken: String?): TrackPage` adalah suspend function.
- `StreamResolver.resolve(track: Track): ResolvedAudio` adalah suspend function. `ResolvedAudio` berisi `location: String`, `headers: Map<String,String>`, `expiresAtEpochMs: Long?`, `mimeType: String?`.
- `PlaybackController.state: StateFlow<PlaybackState>`; `setQueue(items: List<QueueItem>, startIndex: Int)`, `play()`, `pause()`, `seekTo(positionMs: Long)`, `skipNext()`, `skipPrevious()`, `setRepeatMode(mode: RepeatMode)`, `setShuffleEnabled(enabled: Boolean)` adalah suspend function. `QueueItem` memiliki ID entri tersendiri agar lagu duplikat valid.
- `PlaybackState`: antrean/indeks aktif, status idle/loading/playing/paused/error, posisi, durasi, repeat, shuffle, dan error yang dapat ditampilkan. Pemutar native adalah sumber kebenaran status audio.
- `LibraryRepository`: flow favorit dan playlist; operasi suspend untuk membuat/mengubah playlist, toggle favorit, dan merekam riwayat. Semua operasi tulis transaksional.
- `LrcParser.parse(text: String): List<LyricLine>`; `LyricLine(startMs: Long, text: String)`; `LyricsRepository.get(track: Track): LyricsDocument` adalah suspend function, dengan hasil synced/plain/unavailable.
- `BackupCodec.decode(text: String): BackupDocument`; `BackupCodec.encode(document: BackupDocument): String`; `BackupRepository.importMerge(text: String): ImportSummary` adalah suspend function, dengan validasi sebelum transaksi.

Library/playlist hidup di database. Antrean saat ini disnapshot terpisah dari sesi audio; setelah aplikasi dimulai ulang, tawarkan melanjutkan antrean dalam keadaan pause, jangan mulai audio otomatis. Cache artwork/lirik dapat dibersihkan tanpa menghapus library atau file audio milik pengguna.

### Definisi ringan

Angka berikut merupakan target perencanaan yang perlu diperiksa terhadap baseline, bukan hasil benchmark atau janji.

| Ukuran | Usulan target dan metode |
|---|---|
| Cold start | Konten library lokal interaktif median <=2 detik, p95 <=3 detik dalam 20 peluncuran proses dingin di perangkat referensi; jaringan tidak termasuk |
| Ukuran paket | Target awal APK arm64 <=60 MB dan IPA rilis <=100 MB, tanpa media dan debug symbols; catat ukuran terpasang secara terpisah |
| Artwork | Satu image loader; cache disk awal 100 MiB dan cache memori dibatasi 10% memori yang dilaporkan loader, disesuaikan setelah pengukuran |
| Pemuatan data | Halaman 50 track, lazy list dengan key stabil; fixture uji library 10.000 track |
| Memori | Catat baseline library, playback, dan lirik; lakukan 3 siklus masing-masing 50 pergantian lagu. Kenaikan yang menetap lintas siklus harus ditelusuri sebelum beta; tidak menetapkan batas RAM universal tanpa perangkat referensi |
| Baterai | Sesi 60 menit layar mati dengan brightness/network/codec/volume/route audio yang dicatat; bandingkan rilis pada perangkat sama. Hindari polling UI, animasi atau permintaan artwork saat layar tidak menampilkan konten |
| Sinkronisasi lirik | Update tampilan paling sering sekitar 250 ms saat halaman aktif, tanpa mengambil jaringan berulang; setelah seek gunakan posisi aktual pemutar |

Jika suatu target terlewati, kumpulkan profil dan jelaskan penyebabnya sebelum mengubah target atau menambahkan Rust. Pemrosesan berat, decode gambar dan akses disk/jaringan tidak dijalankan pada thread UI.

## Tahap 1 — Kelayakan perangkat, build, distribusi, dan sumber audio

**Estimasi:** 1–2 minggu kerja fokus; bergantung pada signing dan provider.

**Files:** `docs/setup.md`, `docs/decisions/001-platforms-and-distribution.md`, `docs/decisions/002-audio-source.md`, `.github/workflows/check.yml`, `.github/workflows/release.yml`, `gradle/libs.versions.toml`, konfigurasi template KMP dan host `iosApp/`.

- [ ] Catat OS komputer, model/OS iPhone, calon perangkat Android referensi, pengalaman Kotlin, dan batas biaya Apple serta CI. Gunakan emulator untuk iterasi Android, dengan minimal satu penguji Android fisik sebelum beta.
- [ ] Buat proyek KMP + Compose minimal dalam checkout mobile tersendiri. Pilih satu matriks Kotlin/Compose/Gradle/AGP/Xcode stabil yang kompatibel; simpan versi tepatnya.
- [ ] Jalankan test bersama di host yang mendukung, build Android di runner Linux dan build aplikasi iOS di runner macOS. CI untuk perubahan biasa tidak memiliki akses ke kredensial rilis.
- [ ] Buktikan satu build rilis terpasang dan satu pembaruan aplikasi mempertahankan data di iPhone serta Android penguji. Ad Hoc menjadi usulan utama; biaya Apple Developer belum dianggap disetujui pengguna.
- [ ] Jika memakai jalur gratis iOS, buktikan alat pemasangan yang benar-benar tersedia pada komputer pengguna serta refresh tanda tangan; jangan menganggap IPA dari CI dapat langsung dipasang. Konfigurasi simulator-only tidak memenuhi langkah ini.
- [ ] Buktikan playback file lokal dan URL HTTPS audio yang diizinkan pada kedua platform. Catat codec/container yang lolos di perangkat nyata.
- [ ] Evaluasi provider katalog dan resolver sumber audio: pencarian, resolve pertama, kedaluwarsa, headers, timeout, ketersediaan codec, dan pemulihan setelah jaringan berubah. Jalankan minimal 20 track dengan metadata beragam di kedua platform.
- [ ] Catat kelayakan akses provider dan pembatasan layanannya. Hindari menjadikan proses yt-dlp desktop atau server proxy sebagai asumsi tersembunyi. Jika memerlukan backend, tulis dampak biaya, privasi dan pemeliharaan, lalu ajukan sebagai perubahan arsitektur.

**Lulus ketika:** aplikasi dan pembaruannya terpasang di iPhone; pemutar audio contoh berjalan di kedua platform; keputusan provider tertulis dengan bukti uji. Jika provider belum lolos, pekerjaan UI/library boleh menggunakan fixture, tetapi lingkup beta streaming belum dikunci.

## Tahap 2 — Kontrak data, jaringan, dan provider

**Estimasi:** 1 minggu.

**Files:** `model/Track.kt`, `catalog/CatalogRepository.kt`, `catalog/StreamResolver.kt`, implementasi provider yang dipilih, `commonTest/.../catalog/`, `docs/fixtures/` (lokasi relatif terhadap struktur di atas).

**Consumes:** hasil keputusan sumber audio tahap 1. **Produces:** `Track`, `TrackPage`, `ResolvedAudio`, kontrak pencarian dan resolve yang tercantum di rancangan.

- [ ] Tulis fixture serta test `search_parses_missing_artwork`, `search_paginates_without_duplicate_track_ids`, `search_cancelled_query_cannot_replace_new_results` dan `resolve_expired_source_refreshes_once`. Assertions: metadata kosong tidak crash, halaman berlanjut, hasil terbaru menang, dan satu operasi tidak membuat loop retry.
- [ ] Jalankan test dan konfirmasi gagal karena kontrak/implementasi belum tersedia; implementasikan Ktor client dan normalisasi model untuk membuat test lulus.
- [ ] Tetapkan pencarian debounce 350 ms, request timeout awal 15 detik, pembatalan query sebelumnya, serta status loading/empty/offline/error. Hormati Retry-After bila provider mendukungnya; jangan retry otomatis tanpa batas.
- [ ] Cache hasil metadata secara terbatas. Simpan masa berlaku resolver; jangan memasukkan URL sementara ke backup atau log lengkap yang dapat dibagikan.
- [ ] Jalankan test kontrak di CI, lalu uji endpoint aktual secara terpisah dari test deterministik. Commit hasil tahap dengan catatan provider yang dipakai.

**Lulus ketika:** query, pagination, pembatalan dan kegagalan provider bekerja pada kedua platform; status “hasil ditemukan” dibedakan dari “audio dapat diputar”.

## Tahap 3 — Playback dan integrasi sistem

**Estimasi:** 2–3 minggu.

**Files:** `playback/PlaybackController.kt`, `playback/PlaybackCoordinator.kt`, `androidMain/.../AndroidPlaybackController.kt`, `androidMain/.../PlaybackService.kt`, `iosMain/.../IosPlaybackController.kt`, `iosMain/.../IosMediaSession.kt`, Android manifest, iOS capabilities/Info.plist, test coordinator dan antrean.

**Consumes:** kontrak resolver tahap 2. **Produces:** `PlaybackController`, `PlaybackState`, `QueueItem`, `RepeatMode` dan satu coordinator yang menghubungkan perintah dengan pemutar native.

- [ ] Tulis test `stale_resolution_never_starts_old_track`, `duplicate_tracks_have_distinct_queue_entries`, `failed_track_does_not_loop_forever`, `shuffle_preserves_active_entry`, `repeat_modes_select_expected_next_entry`. Gunakan resolver/pemutar palsu dan clock yang dapat dikontrol.
- [ ] Jalankan test gagal, implementasikan coordinator, lalu buat test lulus. Resolusi hanya boleh mengubah playback jika ID permintaan dan ID entri antrean masih aktif.
- [ ] Implementasikan Android Media3 dengan Player/MediaSession di service, lifecycle dan audio focus yang sesuai dokumentasi. UI tidak memiliki instans pemutar sendiri.
- [ ] Implementasikan AVPlayer/AVQueuePlayer di iOS, audio session playback, background mode audio, metadata Now Playing, dan remote commands. Observer/callback dilepas sesuai lifecycle.
- [ ] Terapkan play/pause/seek/previous/next/repeat/shuffle, penyimpanan snapshot antrean, dan posisi berdasarkan event pemutar. Prefetch maksimum satu item berikutnya hanya setelah sumber lolos tahap 1.
- [ ] Uji layar terkunci 30 menit, berpindah aplikasi, panggilan masuk, pause manual lalu interupsi, headset lepas, koneksi Bluetooth berubah, offline dan URL gagal. Headset terlepas harus pause; pemulihan interupsi mengikuti izin sistem dan intent pengguna, bukan selalu auto-play.
- [ ] Lakukan satu pengujian sesi 60 menit di kedua platform sebelum commit tahap. Catat kegagalan dan batas OS; jangan menjanjikan audio bertahan setelah pengguna force-quit.

**Lulus ketika:** satu jam mendengarkan dan kendali sistem konsisten di Android dan iPhone, tanpa dua audio bermain bersamaan atau antrean macet.

## Tahap 4 — Library lokal dan alur mendengarkan

**Estimasi:** 1–2 minggu.

**Files:** `library/LibraryRepository.kt`, SQLDelight schema dan migrations, `library/LibraryScreen.kt`, `search/SearchViewModel.kt`, `search/SearchScreen.kt`, `playback/NowPlayingScreen.kt`, `app/App.kt`, test database dan pencarian.

**Consumes:** model dan controller dari tahap 2–3. **Produces:** favorit/playlist/riwayat persisten serta layar yang mengendalikan playback yang sama.

- [ ] Buat schema track, favorite, playlist, playlist item, history dan saved item. Saved item menyimpan kind, ID yang diberi namespace, serta metadata JSON untuk kompatibilitas backup sebelum seluruh jenis koleksi punya layar. Identitas playlist item terpisah dari track ID agar lagu sama dapat berada beberapa kali bila pengguna menambahkannya demikian.
- [ ] Tulis dan jalankan test gagal `favorite_survives_reopen`, `playlist_reorder_persists`, `history_keeps_latest_200_unique_tracks`, `transaction_failure_leaves_previous_library_intact`, serta test query berhalaman pada 10.000 track; implementasikan repository hingga lulus.
- [ ] Bangun alur Cari -> putar -> player -> favorit -> playlist. Gunakan fixture lebih dahulu bila katalog masih dalam eksplorasi, dengan status demo jelas pada build tersebut.
- [ ] Gunakan lazy list, halaman 50 lagu, thumbnail sesuai ukuran tampilan, image loader bersama, dan status jaringan yang tidak menghalangi library lokal.
- [ ] Impor satu file audio melalui document picker: salin ke penyimpanan aplikasi setelah pengguna memilih, tampilkan ukuran, dan tangani ruang kosong yang tidak cukup. Jangan menyimpan path desktop atau menganggap permission URI bertahan di semua platform.
- [ ] Jalankan test database menggunakan driver Android dan iOS yang sesuai. Uji membuka ulang aplikasi dan restore antrean dalam keadaan pause. Commit hasil tahap.

**Lulus ketika:** library tetap tersedia tanpa internet dan setelah restart; library besar dapat ditelusuri tanpa memuat seluruh artwork/data sekaligus.

## Tahap 5 — Lirik dan identitas visual Senada

**Estimasi:** 1 minggu.

**Files:** `lyrics/LrcParser.kt`, `lyrics/LyricsRepository.kt`, `lyrics/LyricsScreen.kt`, `app/SenadaTheme.kt`, test parser, fixtures lirik yang boleh dipakai.

**Consumes:** `Track` dan posisi/ID lagu aktif dari `PlaybackState`. **Produces:** `LyricLine`, `LyricsDocument`, lirik sinkron per baris dan tema dasar.

- [ ] Tulis test parser LRC untuk beberapa timestamp per baris, timestamp pecahan, metadata, baris kosong, Unicode, urutan timestamp, dan input rusak. Contoh assertion: `[00:01.50]Halo` menghasilkan `LyricLine(1500, "Halo")`; `[00:01.00][00:02.00]Hai` menghasilkan dua baris pada 1000 dan 2000 ms.
- [ ] Konfirmasi test gagal, port perilaku parser yang dipilih, lalu buat test lulus. Jangan memasukkan seluruh provider dan format sekaligus.
- [ ] Integrasikan satu provider HTTPS kandidat, misalnya LRCLIB setelah memeriksa API dan ketentuannya. Cache lirik, sediakan plain/unavailable fallback, dan batalkan/abaikan hasil lagu sebelumnya.
- [ ] Uji `old_lyrics_response_cannot_replace_current_track` dan `seek_recomputes_active_line_from_player_position`. Gunakan fake clock/position untuk assertion deterministik.
- [ ] Tambahkan tema terang/gelap, kontras dan ukuran teks yang dapat diakses, aksen artwork yang dihitung sekali per sampul, dan pengurangan animasi. Hentikan ticker dan animasi halaman ketika tidak terlihat.
- [ ] Periksa teks panjang, VoiceOver/TalkBack, font sistem besar dan sinkronisasi setelah seek pada perangkat nyata. Commit hasil tahap.

**Lulus ketika:** pergantian lagu dan seek tidak menampilkan lirik lama; playback tidak terganggu ketika lirik tidak tersedia.

## Tahap 6 — Backup, interoperabilitas desktop, dan pengaturan

**Estimasi:** 1 minggu.

**Files:** `backup/BackupCodec.kt`, `backup/BackupRepository.kt`, `settings/SettingsScreen.kt`, `commonTest/.../backup/`, fixtures backup desktop dan mobile.

**Consumes:** model dan repository library. **Produces:** `BackupDocument`, `ImportSummary`, `importMerge`, ekspor JSON format 1 untuk data yang didukung.

- [ ] Ambil fixture backup desktop yang tidak mengandung data pribadi dan dokumentasikan field `liked`, `playlists`, `saved`, `history`, serta `uri`, `id`, `name`, `artists`, `durationMs`. Simpan data saved yang belum punya tampilan agar tidak hilang saat round-trip.
- [ ] Tulis test `desktop_backup_imports_supported_fields`, `mobile_export_is_readable_by_desktop`, `repeat_import_is_idempotent`, `malformed_backup_writes_nothing`, `unsupported_future_format_is_rejected`, `desktop_settings_are_not_applied`.
- [ ] Implementasikan validasi dan merge transaksional setelah test gagal. Batas usulan input 10 MiB, tampilkan ringkasan sebelum menerapkan, dan buat cadangan lokal sebelum perubahan. Favorit dan saved item dideduplikasi berdasarkan URI/ID; playlist dengan ID sama mempertahankan judul, urutan dan seluruh entri lama, lalu menambahkan track incoming yang belum ada berdasarkan track ID. Duplikat yang sudah ada tidak dihapus; impor ulang tidak menambah entri lagi. Riwayat menggabungkan track ID unik berdasarkan waktu terbaru dan dibatasi 200. Tambahkan fixture untuk semua aturan ini.
- [ ] Tangani entri local-file desktop sebagai tidak tersedia sampai file diimpor ulang pada ponsel. Backup tidak membawa audio, path akses, signing credential atau URL streaming sementara.
- [ ] Implementasikan pilihan tema, penghapusan cache yang tidak menghapus library, dan pengaturan data seluler yang benar-benar mengendalikan request terkait. Commit tahap setelah uji round-trip nyata dengan desktop.

**Lulus ketika:** ekspor-impor mempertahankan identitas lagu dan playlist, gagal dengan aman pada input rusak, serta tidak menjanjikan pemindahan file audio.

## Tahap 7 — Performa dan kestabilan pada perangkat nyata

**Estimasi:** 1–2 minggu.

**Files:** `docs/performance/baseline.md`, `docs/qa/device-matrix.md`, `docs/qa/playback-checklist.md`, benchmark Android bila diperlukan dan pengujian migrasi database.

- [ ] Catat perangkat Android kelas menengah referensi, iPhone pengguna, versi OS, versi build, kondisi baterai dan jaringan. Gunakan build rilis untuk evaluasi pengguna dan performa.
- [ ] Ukur target pada bagian “Definisi ringan”; simpan hasil, metode dan artefak yang dapat dibandingkan. Waktu resolve jaringan dilaporkan terpisah dari waktu membuka library dan waktu memulai pemutar setelah URL tersedia.
- [ ] Periksa lonjakan alokasi/cache setelah pergantian lagu, aktivitas UI saat background, dan request jaringan yang berulang. Perbaiki penyebab yang terukur sebelum menambah fitur.
- [ ] Android: gunakan profiler/Macrobenchmark sesuai akses perangkat. iOS: pakai logging terukur dan laporan perangkat; Instruments membutuhkan akses Mac yang dapat terhubung ke perangkat, dan tidak otomatis tersedia hanya karena CI memakai macOS. Jadwalkan akses tersebut bila diperlukan untuk menemukan masalah native yang tidak terlihat di log.
- [ ] Uji update dari build beta sebelumnya dengan fixture library berisi 10.000 track, storage hampir penuh, artwork hilang, provider down, mode pesawat, dan 3 siklus masing-masing 50 perubahan lagu.
- [ ] Catat hasil per platform. Jika ada hambatan komputasi terbukti, tulis eksperimen Kotlin vs Rust yang mengukur manfaat dan biaya integrasi; ini bukan syarat beta dan tidak dilakukan otomatis.

**Lulus ketika:** tidak ada kehilangan data, crash berulang atau kebocoran resource yang diketahui; target yang meleset memiliki penyebab dan keputusan tertulis. Jangan mengklaim memenuhi target yang belum diukur.

## Tahap 8 — Beta komunitas dan rilis berulang

**Estimasi:** 1–2 minggu kalender untuk observasi awal.

**Files:** `docs/community-install.md`, `docs/community-update.md`, `docs/known-issues.md`, release notes dan workflow rilis yang dibatasi akses secret-nya.

- [ ] Undang 5–10 penguji sukarela dengan gabungan Android/iOS. Pengguna menguji iPhone sendiri; minimal satu anggota menguji Android fisik.
- [ ] Rilis APK bertanda tangan dan build iOS sesuai jalur yang sudah dibuktikan. Naikkan versionCode/build number dan gunakan identitas signing yang konsisten; verifikasi pembaruan mempertahankan data sebelum dibagikan.
- [ ] Untuk APK langsung, sediakan instruksi instalasi, checksum dan sumber unduhan yang jelas. Periksa aturan developer verification yang berlaku saat rilis, terutama bagi komunitas Indonesia dan perubahan menuju 2027; jangan menganggap publikasi Play Store wajib. FAQ resmi membedakan direct sideload dari participating stores pada fase awal 2026.
- [ ] Untuk Ad Hoc, daftarkan perangkat penguji, dokumentasikan pemasangan, update, dan masa berlaku profil. Untuk jalur akun gratis, uji dan jelaskan refresh tanda tangan. Jangan menjanjikan distribusi IPA bebas ke semua iPhone.
- [ ] Sertakan laporan bug yang meminta versi aplikasi, model/OS, langkah reproduksi dan log yang disunting pengguna. Jangan menambahkan telemetry otomatis atau backend pelaporan tanpa kebutuhan dan keputusan tersendiri.
- [ ] Prioritaskan masalah instalasi/update, audio berhenti, data hilang dan baterai; kumpulkan masukan fitur setelah fondasi stabil. Rilis perbaikan dan amati setidaknya satu minggu penggunaan.

**Lulus ketika:** penguji dapat memasang, memperbarui dan mendengarkan tanpa bantuan pengembang setiap kali; masalah utama tercatat dan diperbaiki di kedua platform.

## Urutan dependensi dan estimasi keseluruhan

Tahap 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8. UI berbasis fixture bisa disiapkan ketika provider sedang dievaluasi, tetapi tidak menjadi bukti kelayakan streaming. Tidak ada pekerjaan implementasi paralel yang diasumsikan dalam estimasi.

Perkiraan kerja fokus untuk satu pengembang yang sudah mengenal Kotlin: **9–14 minggu pengembangan**, lalu **1–2 minggu observasi beta**. Ini perkiraan perencanaan, bukan tanggal janji. Belajar KMP/iOS, kendala provider, signing, akses hardware dan kerja paruh waktu dapat memperpanjangnya; hitung ulang setelah tahap 1 dan setelah prototipe playback.

## Keputusan yang perlu ditutup sebelum pekerjaan terkait dimulai

| Keputusan | Usulan awal | Kapan diperlukan |
|---|---|---|
| OS komputer dan perangkat referensi | Catat komputer aktual, iPhone dan satu Android milik penguji | Tahap 1 |
| Biaya Apple Developer dan cloud CI | Ad Hoc + runner macOS bila anggaran diterima; jalur gratis harus dibuktikan | Sebelum janji distribusi iOS |
| Provider dan resolver lintas platform | Tanpa backend wajib; hasil spike menentukan kelayakan | Sebelum tahap 2 produksi |
| Bundle ID aplikasi | Repo publik `Zeeynrs/Senada-Mobile` sudah dipilih; bundle ID ditetapkan sebelum signing | Saat scaffold dan signing |
| Minimum OS | Usulan Android 8/API 26, iOS 16, sesuaikan perangkat dan matriks dependency | Sebelum scaffold dikunci |
| Anggaran ringan | Target awal di dokumen; kalibrasi dengan baseline nyata | Setelah prototype, sebelum beta |

## Referensi

- [Senada desktop](https://github.com/Zeeynrs/Senada) dan kode `senada/engine.py`, `streams.py`, `ytmusic.py`, `store.py`, `backup.py`, `lyrics.py`, `matching.py`.
- [Compose dan Jetpack Compose](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html).
- [Kompatibilitas Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html).
- [Ktor Client multiplatform](https://ktor.io/docs/client-create-multiplatform-application.html).
- [SQLDelight multiplatform](https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/).
- [Coil](https://coil-kt.github.io/coil/).
- [Android Media3 background playback](https://developer.android.com/media/media3/session/background-playback).
- [Apple AVPlayer](https://developer.apple.com/documentation/avfoundation/avplayer).
- [GitHub hosted runners](https://docs.github.com/en/actions/concepts/runners/github-hosted-runners).
- [Apple Ad Hoc profiles](https://developer.apple.com/help/account/provisioning-profiles/create-an-ad-hoc-provisioning-profile) dan [batas perangkat](https://developer.apple.com/help/account/devices/devices-overview/).
- [Apple Developer Program dan biaya](https://developer.apple.com/programs/enroll/).
- [Personal Team dan masa berlaku profil](https://developer.apple.com/help/account/basics/about-your-developer-account).
- [Android developer verification FAQ](https://developer.android.com/developer-verification/guides/faq).
- [Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).

## Pemeriksaan rencana

- [x] Cakupan platform, komunitas, perangkat, dan prioritas ringan tercatat.
- [x] Temuan kode desktop dipisahkan dari rancangan mobile yang masih berupa usulan.
- [x] Sumber audio dan pemasangan iOS menjadi hasil tahap awal, bukan asumsi keberhasilan.
- [x] Setiap tahap memiliki keluaran dan kriteria lulus; lima kondisi kegagalan utama memiliki pengujian yang ditentukan.
- [x] Kontrak inti, lokasi file, target performa, estimasi, dan keterbatasan profiling tertulis.
- [x] Tidak menganggap pengguna menyetujui pengeluaran Apple/CI atau perubahan ke backend.
- [x] Saran Rust tetap bersyarat pada hasil profiling, sesuai keputusan pengguna.
