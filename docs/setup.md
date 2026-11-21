# Menjalankan Senada Mobile

Kedua platform membuka `SenadaApp()`. Saat kolom cari kosong, layar menampilkan dua lagu katalog contoh yang ikut di dalam aplikasi. Ketik judul atau artis untuk mencari di YouTube Music (perlu internet). Pilih **Tambah** dua kali pada lagu yang sama, lalu **Hapus** salah satu entri. Satu entri tetap ada. Antrean disimpan di memori selama sesi aplikasi.

## Struktur kode

| Lokasi | Tanggung jawab |
|---|---|
| `composeApp/src/commonMain/kotlin/senada/model` | Metadata dan ID lagu. Dua entri antrean boleh merujuk ke ID lagu yang sama. |
| `composeApp/src/commonMain/kotlin/senada/catalog` | Katalog contoh, kontrak pencarian berhalaman dan resolusi alamat audio, serta `catalog/youtube` untuk pencarian dan alamat audio YouTube Music lewat Innertube. |
| `composeApp/src/commonMain/kotlin/senada/playback` | Model antrean dan ID per entri, serta `PlaybackController` yang memiliki antrean dan pemutaran selama proses aplikasi berjalan. |
| `composeApp/src/commonMain/kotlin/senada/app` | Layar Compose, data contoh dan `SenadaSources` (klien HTTP, katalog dan resolver bersama). Layar membaca status dari `PlaybackController`, bukan memiliki pemutar sendiri. |
| `composeApp/src/androidMain`, `composeApp/src/iosMain` | Titik masuk platform, adapter ExoPlayer dan AVPlayer, `PlaybackService` (MediaSession Android), serta Now Playing dan kontrol layar kunci iOS. |
| `iosApp` | Host SwiftUI dan spesifikasi proyek Xcode. |
| `composeApp/src/commonTest` | Tes Kotlin yang sama dijalankan melalui Android unit test atau simulator iOS. |

Konfigurasi awal memakai Kotlin 2.2.21, Compose Multiplatform 1.9.3, Android Gradle Plugin 8.9.2, Gradle 8.11.1 dan JDK 17. Target awalnya Android API 26+ dan iOS 16+. ID aplikasi pengembangan di kedua platform adalah `io.github.zeeynrs.senada`. Konfirmasikan ID dan tim sebelum menandatangani build komunitas. [Matriks kompatibilitas Kotlin](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)

## Android

Siapkan JDK 17, Android SDK Platform 35, Android SDK Build-Tools 35.0.0 dan Platform-Tools. Arahkan `JAVA_HOME` ke JDK 17 dan `ANDROID_HOME` ke SDK, atau atur SDK melalui Android Studio. Jalankan dari root repo.

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug
```

APK debug berada di `composeApp/build/outputs/apk/debug/composeApp-debug.apk`. Dengan satu emulator atau perangkat Android tersambung melalui ADB, jalankan:

```sh
./gradlew :composeApp:installDebug
adb shell am start -n io.github.zeeynrs.senada/senada.platform.MainActivity
```

## iOS

Gunakan Mac Apple Silicon dengan JDK 17, Xcode 26.0.1, runtime simulator iOS yang terpasang dan XcodeGen. Pilih Xcode tersebut lewat `xcode-select` atau `DEVELOPER_DIR`. Dari root repo:

```sh
xcodegen generate --spec iosApp/project.yml
./gradlew :composeApp:iosSimulatorArm64Test
xcodebuild -project iosApp/Senada.xcodeproj -scheme Senada \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

Untuk membuka aplikasinya, jalankan `open iosApp/Senada.xcodeproj`, pilih simulator iPhone lalu tekan **Run**. Xcode memanggil Gradle untuk membangun framework Kotlin dari source. Edit `iosApp/project.yml` dan jalankan XcodeGen kembali saat konfigurasi proyek berubah. Untuk iPhone fisik, pilih tim signing dan provisioning di Mac atau siapkan proses signing untuk build perangkat. [Integrasi Gradle dengan Xcode](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)

### Uji di iPhone dari Windows

1. Dari tab **Actions**, jalankan workflow [iPhone test build](../.github/workflows/ios-device.yml) lewat **Run workflow**. Buka hasil run, unduh artefak `Senada-iPhone-unsigned-<nomor>` dari **Artifacts**, dan ekstrak `Senada-unsigned.ipa`. Artefak disimpan selama 14 hari.
2. Ikuti [panduan AltStore Classic untuk Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) untuk menyiapkan AltServer, AltStore, dan Developer Mode di iPhone. Di AltStore, buka **My Apps**, ketuk **+**, lalu pilih file `.ipa` yang sudah disalin ke iPhone. Cara lain dari PC: sambungkan iPhone, tahan **Shift** sambil mengklik ikon AltServer di tray, pilih **Sideload .ipa…**, lalu pilih file `.ipa`. AltStore menandatangani paket saat memasangnya. Dengan akun Apple gratis, refresh diperlukan setiap 7 hari. [Panduan refresh](https://faq.altstore.io/altstore-classic/your-altstore)
3. Buka Senada dan uji tambah serta hapus entri antrean. Buat run baru atau jalankan ulang run yang sama untuk mendapatkan nomor build berikutnya. Pasang IPA baru dengan akun dan bundle ID yang sama tanpa menghapus Senada terlebih dahulu, lalu cek bahwa Senada dapat dibuka kembali. Antrean demo direset saat sesi aplikasi berakhir.

## Uji audio contoh

1. Buka **Uji audio** dan tekan **Putar lokal**. Sampel *Selepas Hujan* selama 12 detik ikut di dalam aplikasi.
2. Saat audio terdengar, tekan **Jeda**, lalu **Putar lokal** lagi. Audio melanjutkan dari posisi terakhir.
3. Dengan koneksi internet aktif, tekan **Putar HTTPS**. Aplikasi memulai sampel yang sama dari awal. Setelah selesai, tekan **Putar HTTPS** lagi untuk mengulang dari awal.

Kedua lagu contoh berformat AAC-LC dalam `.m4a`. Versi HTTPS merujuk ke salinan *Selepas Hujan* yang dipatok ke commit di repo. Audio tetap berjalan saat aplikasi masuk latar belakang atau layar dikunci.

Untuk memeriksa pemutaran antrean, tambahkan *Selepas Hujan* dan *Sebelum Pagi*. Tekan **Putar** pada entri pertama, lalu gunakan **Berikutnya** dan **Sebelumnya** untuk berpindah lagu. Setelah lebih dari 3 detik diputar, **Sebelumnya** kembali ke awal lagu yang sama; tekan sekali lagi dalam 3 detik pertama untuk pindah ke entri sebelumnya. Geser bilah progres saat memutar dan saat dijeda. Saat dijeda, audio harus tetap diam sampai ditekan **Putar**. Biarkan entri pertama selesai untuk memeriksa bahwa putar otomatis berlanjut ke entri kedua. Tekan **Hapus** pada entri yang sedang diputar untuk mengakhiri pemutarannya.

## Uji YouTube Music

Pencarian dan pemutaran YouTube Music berjalan langsung dari ponsel tanpa server Senada. Alamat audionya (AAC dalam MP4) terikat ke IP ponsel dan berlaku sekitar 6 jam. Cara ini tidak diizinkan Ketentuan Layanan YouTube, jadi Senada hanya dibagikan lewat sideload, bukan App Store atau Play Store. Latar belakang keputusannya ada di [evaluasi sumber audio](superpowers/research/2026-11-21-evaluasi-sumber-audio.md). Workflow GitHub tidak menguji YouTube Music karena IP pusat data bisa diblokir, jadi uji langkah berikut di perangkat.

1. Ketik nama lagu atau artis, misalnya `tulus`. Hasil muncul di bawah judul **YouTube Music** dengan sampul, artis dan durasi. Tekan **Muat lebih banyak** untuk halaman berikutnya.
2. Tambahkan dua atau tiga lagu, lalu putar entri pertama. Status **Memuat audio…** tampil sebentar sebelum audio terdengar. Kunci layar: audio lanjut otomatis ke entri berikutnya, dan layar kunci menampilkan judul, artis dan sampul lagu yang sedang diputar.
3. Cari `nadin amizah bertaut` dan putar hasilnya. Lagu ini memang tidak tersedia, jadi muncul **Lagu ini tidak tersedia dari YouTube Music.** dan antrean tidak melompat ke lagu lain.
4. Nyalakan mode pesawat, lalu cari atau putar lagu baru. Muncul pesan koneksi; setelah internet kembali, **Coba lagi** atau **Putar** berhasil.
5. Jeda lagu di tengah jalan, misalnya di menit kedua. Bila jeda lebih dari sekitar 6 jam, atau jaringan berganti (Wi-Fi ke seluler), tekan **Putar**: Senada meminta alamat baru dan melanjutkan dari posisi terakhir, bukan dari awal. Kegagalan stream saat diputar juga dicoba ulang sekali secara otomatis dari posisi yang sama.

## Uji pemutaran latar belakang

Lakukan di iPhone fisik dan Android (emulator boleh untuk iterasi, perangkat fisik sebelum beta).

1. Tambahkan kedua lagu contoh ke antrean dan putar entri pertama.
2. Kunci layar. Audio tetap terdengar dan berlanjut otomatis ke entri kedua. Layar kunci menampilkan judul, artis, durasi dan tombol putar/jeda, sebelumnya dan berikutnya. Di Android, kontrol yang sama muncul sebagai notifikasi media.
3. Dari layar kunci, tekan jeda, putar, berikutnya dan sebelumnya, lalu geser posisi bila tersedia. Buka kembali aplikasi; status di layar Senada sama dengan layar kunci.
4. Saat audio diputar, pindah ke aplikasi lain lalu kembali. Audio tidak berhenti.
5. Saat audio diputar dengan headset tersambung, lepaskan headset. Audio harus berhenti dan tidak mulai sendiri.
6. iPhone: saat audio diputar, terima atau mulai panggilan (atau putar video di aplikasi lain). Setelah selesai, audio hanya melanjutkan bila sebelumnya sedang diputar dan sistem mengizinkannya. Jika dijeda manual sebelum interupsi, audio tetap diam.
7. Catat hasil, versi build, model perangkat dan versi OS. Uji layar terkunci 30 menit dan sesi 60 menit menjadi syarat penutup tahap pemutaran sistem di roadmap.

## Pemeriksaan di GitHub

Workflow [Mobile checks](../.github/workflows/check.yml) menjalankan build dan tes Android di Linux serta tes dan build simulator iOS di macOS ketika pull request dibuka atau diperbarui. Build simulator dapat diperiksa dari Linux lewat log GitHub. Pemasangan pada iPhone dan uji audio memerlukan pengujian perangkat tersendiri.
