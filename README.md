# Senada Mobile

**Senada Mobile masih dalam pengembangan awal.**

Senada Mobile adalah proyek aplikasi musik untuk **Android dan iOS**, dikembangkan dengan **Kotlin Multiplatform dan Compose Multiplatform**.

Repositori proyek: [Zeeynrs/Senada-Mobile](https://github.com/Zeeynrs/Senada-Mobile).

## Status

Prototipe Android sudah diuji di emulator. Kamu bisa menambahkan dua lagu contoh ke antrean, memasukkan lagu yang sama lebih dari sekali, dan menghapus satu entri. Pemutaran audio dan pencarian katalog menjadi tahap berikutnya. Host iOS sudah tersedia dan masih menunggu verifikasi build.

[Panduan build, struktur kode, dan pengujian](docs/setup.md)

Pengembangan awal berfokus pada keandalan audio, library lokal, lirik tersinkron, dan penggunaan sumber daya yang diukur pada perangkat nyata. Distribusi awal ditujukan untuk komunitas kecil di luar Google Play dan App Store.

## Rencana pengembangan

- [Rencana implementasi Senada Mobile](docs/superpowers/plans/2026-11-18-senada-mobile.md) — arsitektur, delapan tahap pengerjaan, pengujian, dan kriteria selesai.
- [Studi Mochy Music dan Metrolist](docs/superpowers/research/2026-11-18-mochy-metrolist-lessons.md) — temuan dari kode sumber dan usulan yang relevan untuk Senada.

## Arah teknologi

| Bagian | Pilihan awal |
|---|---|
| Tampilan bersama | Compose Multiplatform |
| Logika fitur | Kotlin Multiplatform, Coroutines dan Flow |
| Jaringan | Ktor Client |
| Library lokal | SQLite melalui SQLDelight |
| Artwork | Coil Multiplatform |
| Audio Android | Media3/ExoPlayer |
| Audio iOS | AVPlayer/AVQueuePlayer |
| Build iOS | Mesin macOS, termasuk layanan CI |

Versi dasar Kotlin, Compose dan Android Gradle Plugin tercatat di [version catalog](gradle/libs.versions.toml). Integrasi katalog serta resolver audio masih memerlukan pengujian pada kedua platform. Penambahan Rust bergantung pada kebutuhan yang ditemukan melalui profiling.

## Target beta pertama

- Pencarian lagu sesuai provider yang lolos pengujian.
- Pemutar, antrean, repeat, shuffle, dan kontrol media sistem.
- Pemutaran saat layar terkunci.
- Favorit, playlist lokal, dan riwayat.
- Lirik tersinkron per baris.
- Tema terang/gelap dan warna mengikuti sampul.
- Ekspor-impor library yang kompatibel dengan data desktop yang didukung.

## Langkah pertama

1. Buktikan build, pemasangan, dan pembaruan aplikasi di Android serta iPhone.
2. Uji audio lokal dan AAC/MP4 melalui pemutar native.
3. Evaluasi sumber katalog dan resolver, termasuk kompatibilitas dependency lintas platform.
4. Tentukan jalur signing dan distribusi iOS yang sesuai dengan perangkat serta anggaran komunitas.

## Proyek terkait

- [Senada Desktop](https://github.com/Zeeynrs/Senada)
- [Mochy Music](https://github.com/realidkroo/Mochy-Music)
- [Metrolist](https://github.com/MetrolistGroup/Metrolist)

## Lisensi

Repositori ini menggunakan [MIT License](LICENSE). Informasi asal dan lisensi proyek pembanding tercatat dalam dokumen studi.
