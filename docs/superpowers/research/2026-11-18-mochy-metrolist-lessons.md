# Pelajaran Mochy Music dan Metrolist untuk Senada Mobile

Tanggal peninjauan: 18 November 2026.

## Cakupan dan batas bukti

Peninjauan statis atas README, struktur repositori, konfigurasi build dan CI, serta bagian kode katalog, resolver, playback, cache, lirik, database dan iOS. Ini bukan audit keamanan lengkap. Aplikasi tidak dibangun, dijalankan pada perangkat, atau di-benchmark dalam penelitian ini. Kehadiran test di sumber bukan bukti test sedang lulus.

Snapshot kode:
- Mochy Music: `7e590802345329bb108d74f337af1ea9fe407d2a`.
- Metrolist: `0296061f0f32bbdf7f82f7aac8f5e4939fc051e6`.

Metadata rilis di bawah adalah pengamatan terpisah dari snapshot main, bukan klaim bahwa isi rilis identik dengan main yang ditinjau.

## Kesimpulan untuk Senada

Metrolist menjadi referensi teknis paling dekat untuk target Kotlin Multiplatform + Compose Multiplatform Android/iOS. Mochy berguna untuk alur Android, kebijakan cache, lirik, personalisasi dan cara mengukur performa. Ambil batas tanggung jawab dan pengujian kasus gagal; pilih sedikit fitur untuk beta Senada.

Tidak ditemukan alasan dari peninjauan ini untuk menambahkan Rust demi performa sebelum ada profiling Senada. Pemilihan framework saja belum menentukan baterai, RAM atau kelancaran.

## Perbandingan yang terverifikasi

| Aspek | Mochy Music | Metrolist | Implikasi |
|---|---|---|---|
| Platform pada kode yang ditinjau | Aplikasi Android, provider Android library | KMP dengan shared/commonMain, iosMain, Android dan desktop host, serta Swift/Xcode host | Metrolist relevan langsung untuk rancangan lintas platform |
| Playback | Media3 MediaLibraryService dan integrasi Android; juga kode C++ audio khusus | Android Media3 dan iOS AVPlayer dengan adapter platform | UI dan model bisa dibagi; lifecycle audio tetap spesifik |
| Akses musik | Katalog dan resolver terpisah; InnerTubeX dan jalur fallback lain | Kontrak StreamResolver dan implementasi bersama berbasis InnerTubeX | Evaluasi dependency yang ada sebelum menulis extractor baru |
| Library | Room Android dan migrasi terpusat | Room multiplatform, schema dan test migrasi platform | Prinsip migrasi lebih penting daripada otomatis mengganti SQLDelight Senada |
| Distribusi | APK menurut ABI/flavor, workflow rilis | APK dan IPA unsigned dari pipeline macOS; petunjuk signing perangkat | Prototipe tanpa Mac pribadi memiliki contoh implementasi nyata |
| Performa | Release shrinking, profileable build, Perfetto dan probe perangkat | Kebijakan polling adaptif, cache dan pengujian playback rinci | Ukur build rilis dan pekerjaan yang masih aktif saat background |

Bukti platform: [build Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/build.gradle.kts#L12-L29), [shared KMP Metrolist](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/build.gradle.kts#L30-L37), [host Swift](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/iosApp/iosApp.swift), [controller iOS](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/iosMain/kotlin/com/metrolist/shared/playback/IosPlaybackController.kt).

## 1. Pisahkan katalog, resolusi audio, dan pemutar

Mochy mencari metadata melalui wrapper katalog, tetapi memakai resolver terpisah yang menghasilkan URL, format, masa berlaku, client dan headers. Metrolist memperjelasnya melalui kontrak resolve/refresh, laporan kegagalan playback, dan invalidasi saat lingkup jaringan berubah.

Untuk Senada, pertahankan tiga batas:
1. CatalogRepository menemukan lagu.
2. StreamResolver menghasilkan resource yang dapat diputar.
3. Pemutar platform mengonsumsi resource dan melaporkan keadaan nyata.

Hasil pencarian tidak boleh langsung dianggap sebagai bukti stream tersedia. URL sementara tidak disimpan sebagai identitas library. Evaluasi InnerTubeX dengan target iOS aktual; dependency Android seperti WebView, ConnectivityManager atau OkHttp tertentu tidak otomatis menjadi common code.

Bukti: [katalog Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/providers/innertube/src/main/kotlin/com/music/innertube/YouTube.kt#L239-L250), [hasil resolver Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/utils/YTPlayerUtils.kt#L188-L200), [kontrak Metrolist](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/commonMain/kotlin/com/metrolist/shared/youtube/playback/StreamResolver.kt), [integrasi Android Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/utils/InnerTubeXStreams.kt#L1-L63).

## 2. Codec dan container iOS harus dibuktikan lebih awal

Metrolist mempertahankan AVPlayer. Untuk Opus dalam WebM, kode bersama memisahkan packet dan menyusunnya ke indexed MP4 tanpa mengodekan ulang audio. Itu menambah penanganan timestamp, pre-skip, padding, seeking, pembatalan dan kepemilikan buffer/file.

Dokumen implementasinya menetapkan input Opus lengkap maksimal 32 MiB. Angka itu batas input, bukan jaminan pemakaian RAM total. Kegagalan direct capture/remux mendapatkan satu upaya ulang AAC dengan antrean, posisi dan intent tetap dipertahankan.

Usulan Senada: buktikan AAC/MP4 langsung terlebih dahulu pada iPhone, kemudian buat matriks format yang benar-benar didukung. Jika provider tidak menyediakan sumber kompatibel, tampilkan batas tersebut; dukungan remux menjadi keputusan lanjutan yang diukur. Jangan membangun decoder Rust atau membawa seluruh tumpukan format sebelum kebutuhan nyata muncul.

Bukti: [penjelasan Opus iOS](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/docs/ios-opus.md), [demuxer](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/commonMain/kotlin/com/metrolist/shared/playback/opus/WebmOpusDemuxer.kt), [remuxer](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/commonMain/kotlin/com/metrolist/shared/playback/opus/OpusMp4Remuxer.kt), [cache iOS](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/iosMain/kotlin/com/metrolist/shared/storage/IosPlaybackCache.kt).

## 3. Kebijakan gagal menentukan kualitas pengalaman mendengarkan

Metrolist membedakan kegagalan jaringan dari resource yang tidak tersedia. Auto-skip default dimatikan menurut dokumen. Bila diaktifkan, kebijakan memeriksa intent playback, jaringan dan repeat-one. Budget mencegah lebih dari tiga skip berturut-turut dan diaktifkan ulang setelah 30 detik progres sehat. Callback kegagalan berulang tidak boleh memajukan antrean berkali-kali.

Pelajaran Senada: saat offline atau timeout, pertahankan lagu dan tawarkan retry. Kegagalan preload berikutnya tidak memutus lagu yang sedang berjalan. Pisahkan ID lagu, ID entri antrean, dan ID permintaan agar hasil resolve lama tidak mengambil alih lagu baru.

Bukti: [dokumen playback](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/docs/playback.md), [kebijakan dan budget](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/commonMain/kotlin/com/metrolist/shared/playback/PlaybackFailureAutoSkip.kt), [test regresi](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/commonTest/kotlin/com/metrolist/shared/playback/PlaybackFailureAutoSkipTest.kt).

## 4. Cache sementara berbeda dari unduhan milik pengguna

Mochy menggunakan cache pemutaran dengan eviction LRU dan cache unduhan tanpa eviction otomatis. Default cache lagu pada sumber yang ditinjau adalah 1.024 MiB; itu pilihan produk Mochy, bukan anggaran yang otomatis cocok untuk Senada. Jalur download sengaja tidak menyambungkan byte cache playback karena sumber, kualitas dan format dapat berbeda.

Untuk Senada:
- Cache artwork, lirik, dan audio diberi anggaran serta kebijakan berbeda.
- Jika audio cache ditambahkan, identitasnya mencakup resource/format; jangan hanya video ID.
- Aksi bersihkan cache tidak menghapus playlist atau audio lokal milik pengguna.
- Download katalog tetap ditunda dari beta awal.

Bukti: [LRU dan download cache](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/di/AppModule.kt#L84-L113), [pemisahan jalur download](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/playback/DownloadUtil.kt#L121-L126).

## 5. Ringan berarti mengurangi pekerjaan yang tidak diperlukan

Mochy menghentikan polling progres ketika UI tidak terlihat. Kode blur memiliki pembatasan menurut kemampuan OS, cache efek dan opsi mematikan efek. Proyek juga memiliki release shrinking, varian profileable dan skrip untuk Perfetto, memori, CPU per thread, wake lock serta sensor.

Metrolist memiliki kebijakan interval polling: 100 ms ketika progres halus diperlukan dan sekitar 1 detik ketika tidak, dengan kondisi tambahan mendekati crossfade. Ini contoh pengaturan kerja berdasarkan keadaan, bukan bukti angka baterai yang telah diukur.

Usulan Senada: efek ringan secara default; ticker lirik hanya aktif ketika halaman terlihat; jangan memperbarui seluruh layar pada setiap frame. Tambahkan skenario pengukuran berulang untuk membuka player, mengganti lagu, menggulir library, membuka lirik dan mendengarkan layar mati.

Bukti: [polling Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/playback/PlayerConnection.kt#L271-L297), [blur](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/ui/component/BlurEffectCache.kt#L34-L70), [varian profileable](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/build.gradle.kts#L169-L220), [probe](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/tools/perf/probe.sh), [trace runner](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/tools/perf/run.sh), [polling Metrolist](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/androidApp/src/main/kotlin/com/metrolist/android/playback/PlaybackPollingPolicy.kt).

## 6. Migrasi dan backup adalah fitur inti aplikasi lokal

Mochy memusatkan daftar migrasi dan menyertakan komentar mengenai library yang dahulu terhapus ketika jalur migrasi tidak terdaftar konsisten. Pada sumber yang ditinjau masih ada fallbackToDestructiveMigration di salah satu builder; ini bukan pola yang disarankan untuk library Senada. Keberadaan konfigurasi itu sendiri tidak membuktikan pengguna saat ini sedang kehilangan data.

Metrolist memiliki schema historis dan pengujian migrasi. Untuk Senada, ekspor schema, uji update dengan database lama, lakukan restore transaksional, dan gagalkan migrasi dengan pesan yang dapat ditindaklanjuti alih-alih menghapus library diam-diam.

Bukti: [database Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/db/MusicDatabase.kt#L109-L197), [database bersama Metrolist](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/commonMain/kotlin/com/metrolist/shared/db/MusicDatabase.kt), [test migrasi Android](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/androidHostTest/kotlin/com/metrolist/shared/db/AndroidDatabaseMigrationTest.kt).

Test yang ada perlu benar-benar dijalankan oleh CI. Workflow PR Mochy yang ditinjau memanggil assemble dan lint tanpa task unit test eksplisit; ini tidak menghapus manfaat test di repositori, tetapi mengingatkan kita memeriksa perintah pipeline, bukan sekadar folder test.

Bukti: [workflow PR](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/.github/workflows/build_pr.yml#L47-L48).

Metrolist juga menyediakan pengujian migrasi iOS yang merekonstruksi schema historis dan memeriksa data yang dipertahankan. Ini penting karena test repository bersama saja belum memeriksa perilaku driver database native.

Bukti: [test migrasi iOS](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/shared/src/iosTest/kotlin/com/metrolist/shared/db/IosDatabaseMigrationTest.kt#L71-L100).

## 7. Lirik sebaiknya punya model bersama dan adapter provider kecil

Mochy membaca lirik embedded/lokal lebih dahulu dan menunda caption sampai provider normal gagal. Provider memiliki batas waktu; kegagalan koneksi tidak layak disimpan sebagai hasil permanen “tidak ada lirik”.

Untuk Senada, mulai dengan LRC lokal dan satu provider HTTPS, model synced/plain/unavailable yang konsisten, cache dengan asal provider, dan pembatalan respons saat track berganti. Banyak provider dapat ditambahkan setelah pengukuran akurasi dan waktu respons. Jangan menyalin jumlah request paralel atau seluruh fitur terjemahan/penyelarasan secara otomatis.

Bukti: [prioritas dan timeout lirik Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/lyrics/LyricsHelper.kt#L79-L185), [provider registry](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/app/src/main/kotlin/com/mochyy/mochymusic/lyrics/LyricsProviderRegistry.kt).

## 8. Pipeline IPA unsigned memberi pilihan distribusi awal yang lebih hemat

Metrolist membangun framework iOS dan IPA di runner macOS. build_ios.sh secara eksplisit mematikan signing saat archive, memvalidasi bundle/platform/arsitektur, kemudian mengemas unsigned IPA. Panduan instalasinya menjelaskan signing memakai alat sideload, mempertahankan akun/bundle ID saat update, refresh masa berlaku, dan sumber AltStore/SideStore.

Untuk Senada, ini memberi jalur yang perlu diuji sebelum menjadikan akun developer berbayar sebagai biaya wajib: build cloud -> unsigned IPA -> signing melalui alat yang tersedia pada komputer pengguna -> iPhone. Pengguna tetap membutuhkan signing yang valid, pengaturan alat dan pemeliharaan refresh. IPA unsigned bukan aplikasi yang dapat langsung dipasang. Dukungan alat dan entitlement harus cocok dengan perangkat/OS pengguna.

Bukti: [workflow iOS](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/.github/workflows/reusable-ios-build.yml), [skrip archive/package](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/scripts/build_ios.sh), [panduan instalasi iOS](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/docs/install/ios.md).

## Metadata ukuran unduhan rilis

| Proyek dan tag | Artefak | Ukuran sekitar |
|---|---|---|
| Mochy Music Mochy-8.4.2 | arm64 FOSS release APK | 31,1 MiB |
| Mochy Music Mochy-8.4.2 | universal FOSS release APK | 36,5 MiB |
| Metrolist v14.0.0 | Metrolist.apk | 13,9 MiB |
| Metrolist v14.0.0 | Metrolist.ipa | 50,8 MiB |

Sumber: metadata aset [rilis Mochy](https://github.com/realidkroo/Mochy-Music/releases/tag/Mochy-8.4.2) dan [rilis Metrolist](https://github.com/MetrolistGroup/Metrolist/releases/tag/v14.0.0), dibaca 18 November 2026. Varian, kompresi, arsitektur dan fitur berbeda. Ini bukan ukuran terpasang, pemakaian RAM, CPU, kelancaran, atau baterai. Data ini mendukung bahwa paket Kotlin bisa berukuran moderat; tidak membuktikan satu aplikasi lebih efisien saat dipakai.

## Provenance dan batas pemakaian ulang

LICENSE kedua repositori memuat GNU GPL versi 3. README Mochy menyebut basis ViviMusic dan framework Metrolist. Karena ada hubungan asal kode yang dinyatakan, kesamaan pola tidak boleh dihitung sebagai dua implementasi independen yang telah memvalidasi desain yang sama. Jika memakai ulang kode, catat asal commit, file dan notice terkait; lisensi aset/dependency perlu diperiksa sesuai bagian yang dipakai.

Bukti: [lisensi Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/LICENSE), [kredit Mochy](https://github.com/realidkroo/Mochy-Music/blob/7e590802345329bb108d74f337af1ea9fe407d2a/README.md#L209-L220), [lisensi Metrolist](https://github.com/MetrolistGroup/Metrolist/blob/0296061f0f32bbdf7f82f7aac8f5e4939fc051e6/LICENSE).

## Usulan penyesuaian rencana Senada

1. Tahap 1: evaluasi dependency InnerTubeX dan jalur iOS aktual sebelum membuat resolver sendiri.
2. Tahap 1: uji AAC/MP4 pada iPhone serta satu stream tidak kompatibel; putuskan kebutuhan format secara eksplisit.
3. Tahap 1: buktikan unsigned IPA cloud + alat sideload pengguna sebagai alternatif Ad Hoc berbayar.
4. Tahap 3: tambahkan failure category, bounded recovery, queue occurrence/request identity, serta perlindungan current playback saat preload gagal.
5. Tahap 4/6: jadikan migrasi tanpa kehilangan library dan update-in-place sebagai syarat rilis.
6. Tahap 5/7: polling sesuai visibilitas, blur sederhana/opsional, fixture performa dan profileable release.
7. Pertahankan lingkup beta kecil; belum ada temuan yang membenarkan Rust, banyak provider, remux Opus atau fitur audio khusus sebagai syarat awal.

Butir di atas merupakan rekomendasi hasil studi. Dokumen rencana awal tetap menjadi acuan percakapan sampai penyesuaian dipilih untuk implementasi.
