# Menjalankan Senada Mobile

Kedua platform membuka `SenadaApp()`. Pilih **Tambah** dua kali pada lagu contoh yang sama, lalu **Hapus** salah satu entri. Satu entri tetap ada. Antrean prototipe hidup di memori selama sesi aplikasi. Kedua lagu adalah contoh metadata untuk menguji antrean.

## Struktur kode

| Lokasi | Tanggung jawab |
|---|---|
| `composeApp/src/commonMain/kotlin/senada/model` | Metadata dan ID lagu. Dua entri antrean boleh merujuk ke ID lagu yang sama. |
| `composeApp/src/commonMain/kotlin/senada/catalog` | Kontrak pencarian berhalaman dan resolusi alamat audio sementara. Implementasi provider menyusul setelah uji kelayakan. |
| `composeApp/src/commonMain/kotlin/senada/playback` | Model antrean dan ID per entri. |
| `composeApp/src/commonMain/kotlin/senada/app` | Layar Compose, data contoh dan ViewModel pemilik antrean. |
| `composeApp/src/androidMain`, `composeApp/src/iosMain` | Titik masuk platform dan adapter Media3 serta AVPlayer untuk uji audio. |
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
2. Ikuti [panduan AltStore Classic untuk Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) untuk menyiapkan AltServer, AltStore, dan Developer Mode di iPhone. Di AltStore, buka **My Apps**, ketuk **+**, lalu pilih file `.ipa` yang sudah disalin ke iPhone. AltStore menandatangani paket saat memasangnya. Dengan akun Apple gratis, refresh diperlukan setiap 7 hari. [Panduan refresh](https://faq.altstore.io/altstore-classic/your-altstore)
3. Buka Senada dan uji tambah serta hapus entri antrean. Buat run baru atau jalankan ulang run yang sama untuk mendapatkan nomor build berikutnya. Pasang IPA baru dengan akun dan bundle ID yang sama tanpa menghapus Senada terlebih dahulu, lalu cek bahwa Senada dapat dibuka kembali. Antrean demo direset saat sesi aplikasi berakhir.

## Uji audio contoh

1. Buka **Uji audio** dan tekan **Putar lokal**. Sampel *Selepas Hujan* selama 12 detik ikut di dalam aplikasi.
2. Saat audio terdengar, tekan **Jeda**, lalu **Putar lokal** lagi. Audio melanjutkan dari posisi terakhir.
3. Dengan koneksi internet aktif, tekan **Putar HTTPS**. Aplikasi memulai sampel yang sama dari awal. Setelah selesai, tekan **Putar HTTPS** lagi untuk mengulang dari awal.

Sampel AAC-LC dalam `.m4a` ini dibuat untuk pengujian. Versi HTTPS merujuk ke salinan file yang dipatok ke commit di repo. Pemutar dijeda saat aplikasi masuk latar belakang. Uji audio menggunakan satu sampel; antrean demo bisa diuji secara terpisah.

## Pemeriksaan di GitHub

Workflow [Mobile checks](../.github/workflows/check.yml) menjalankan build dan tes Android di Linux serta tes dan build simulator iOS di macOS ketika pull request dibuka atau diperbarui. Build simulator dapat diperiksa dari Linux lewat log GitHub. Pemasangan pada iPhone dan uji audio memerlukan pengujian perangkat tersendiri.
