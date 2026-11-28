# Senada Mobile

Senada Mobile adalah aplikasi musik untuk **Android dan iOS** yang dibangun dengan **Kotlin Multiplatform dan Compose Multiplatform**. Aplikasi ini sedang disiapkan untuk beta komunitas.

Kode sumber tersedia di [Zeeynrs/Senada-Mobile](https://github.com/Zeeynrs/Senada-Mobile).

## Status

Senada sudah bisa mencari dan memutar lagu dari YouTube Music serta menampilkan sampul dan lirik sinkron dari LRCLIB. Audio tetap berjalan di latar belakang, dengan kontrol di layar kunci pada kedua platform dan di notifikasi media pada Android. Antrean tersimpan saat aplikasi ditutup. Mode ulangi dan acak, favorit, playlist lokal, serta riwayat juga sudah tersedia.

- **Emulator Android di CI.** Uji dengan lagu contoh mencakup pemutaran saat layar mati, perpindahan otomatis, kontrol media sistem, dan penggeseran posisi saat dijeda. [Hasil uji emulator Android](https://github.com/Zeeynrs/Senada-Mobile/pull/14)
- **iPhone fisik.** Pencarian dan pemutaran YouTube Music, kontrol layar kunci, sampul, pemulihan antrean, lirik sinkron, ulangi dan acak, serta favorit, playlist, dan riwayat sudah diuji. [YouTube Music](https://github.com/Zeeynrs/Senada-Mobile/pull/9) · [Sampul](https://github.com/Zeeynrs/Senada-Mobile/pull/10) · [Antrean](https://github.com/Zeeynrs/Senada-Mobile/pull/11) · [Lirik](https://github.com/Zeeynrs/Senada-Mobile/pull/12) · [Ulangi dan acak](https://github.com/Zeeynrs/Senada-Mobile/pull/15) · [Library](https://github.com/Zeeynrs/Senada-Mobile/pull/16)
- **Cadangan library.** Ekspor dan impor library dalam format Senada Desktop versi 1 tersedia di tab **Pengaturan**, dengan ringkasan sebelum impor diterapkan. Format dan aturan penggabungan tercakup dalam tes otomatis; pemilih file dan pertukaran dengan Senada Desktop belum diuji di iPhone fisik. [Lapisan data](https://github.com/Zeeynrs/Senada-Mobile/pull/18) · [Layar ekspor dan impor](https://github.com/Zeeynrs/Senada-Mobile/pull/20)

## Sumber audio dan distribusi

Senada mengakses YouTube Music langsung dari ponsel tanpa server Senada. Cara akses ini tidak diizinkan oleh Ketentuan Layanan YouTube. Karena itu, distribusi Senada Mobile hanya melalui sideload (APK langsung di Android dan IPA lewat AltStore di iOS), di luar Google Play dan App Store. [Evaluasi sumber audio](docs/superpowers/research/2026-11-21-evaluasi-sumber-audio.md) · [Ketentuan Layanan YouTube](https://www.youtube.com/static?template=terms)

## Memasang untuk pengujian

- **Android.** Pasang APK debug dari artefak **Mobile checks** atau buat dan pasang APK dari kode sumber. [Panduan Android](docs/setup.md#android) · [Pemeriksaan di GitHub](docs/setup.md#pemeriksaan-di-github)
- **iOS.** Pasang IPA uji di iPhone fisik lewat AltStore atau jalankan aplikasi di simulator melalui Xcode di Mac. [Uji di iPhone dari Windows](docs/setup.md#uji-di-iphone-dari-windows) · [Panduan iOS](docs/setup.md#ios)

## Rencana pengembangan

- [Rencana implementasi Senada Mobile](docs/superpowers/plans/2026-11-18-senada-mobile.md) — arsitektur, delapan tahap pengerjaan, pengujian, dan kriteria selesai.
- [Studi Mochy Music dan Metrolist](docs/superpowers/research/2026-11-18-mochy-metrolist-lessons.md) — temuan dari kode sumber dan usulan yang relevan untuk Senada.

## Teknologi

| Bagian | Teknologi |
|---|---|
| Tampilan bersama | Compose Multiplatform |
| Logika fitur | Kotlin Multiplatform, Coroutines dan Flow |
| Jaringan | Ktor Client 3.3.3 |
| Library lokal | SQLite melalui SQLDelight 2.2.1 |
| Sampul | Coil Multiplatform 3.3.0 |
| Lirik | LRCLIB |
| Audio Android | Media3/ExoPlayer |
| Audio iOS | AVPlayer |
| Build iOS | Mesin macOS, termasuk layanan CI |

Versi dependensi lainnya tercatat di [version catalog](gradle/libs.versions.toml).

## Target beta pertama

- [x] Pencarian lagu dan hasil berhalaman dari YouTube Music.
- [x] Pemutar, antrean tersimpan, ulangi, acak, serta kontrol media sistem saat layar terkunci.
- [x] Favorit, playlist lokal, dan riwayat.
- [x] Lirik sinkron per baris.
- [x] Tema terang dan gelap yang mengikuti pengaturan sistem.
- [ ] Warna tema yang mengikuti sampul lagu.
- [x] Ekspor dan impor cadangan library yang kompatibel dengan Senada Desktop (uji di perangkat masih berjalan).
- [ ] Impor satu file audio lewat pemilih dokumen.

## Langkah berikutnya

1. Uji layar cadangan di iPhone dan Android, termasuk pertukaran data dengan Senada Desktop.
2. Tambahkan warna tema yang mengikuti sampul, pilihan tema, dan impor satu file audio lewat pemilih dokumen.
3. Uji sesi pemutaran panjang, kestabilan, dan performa dengan build rilis di perangkat Android dan iPhone fisik.
4. Bagikan build beta lewat sideload ke komunitas kecil setelah memastikan pembaruan mempertahankan data pengguna.

## Proyek terkait

- [Senada Desktop](https://github.com/Zeeynrs/Senada)
- [Mochy Music](https://github.com/realidkroo/Mochy-Music)
- [Metrolist](https://github.com/MetrolistGroup/Metrolist)

## Lisensi

Senada Mobile berlisensi [GNU General Public License v3.0 or later](LICENSE), hak cipta © 2026 Zeeynrs.

Rilis hingga dan termasuk versi 1.0.2 diterbitkan di bawah lisensi MIT. Kode dari [Mochy Music](https://github.com/realidkroo/Mochy-Music) (GPL-3.0) dapat diadaptasi, dan asal-usulnya dicatat pada header file terkait serta di [NOTICE.md](NOTICE.md).
