# Pembaruan Dalam Aplikasi (Android & iOS)

**Tujuan.** Pengguna dapat memeriksa pembaruan dari **Profil › Tentang Senada**, memilih kanal **Stabil** atau **Beta**, dan memulai aksi pembaruan sesuai kebijakan kanal instalasi di Android maupun iOS—termasuk transfer parsial (delta APK) saat prasyarat terpenuhi.

## Audit Awal

- **Senada Desktop (`Zeeynrs/Senada`)**: memakai `GET /repos/Zeeynrs/Senada/releases/latest` (hanya stabil, tidak mengenal prarilis), membandingkan versi lewat tuple angka, memverifikasi `digest` (`sha256:...`), lalu menjalankan installer Inno Setup penuh tanpa delta.
- **Senada Mobile (`Zeeynrs/Senada-Mobile`)**:
  - Rilis publik saat ini adalah `v1.0.0` (`prerelease: true`, `Beta test`) dengan dua aset: `Senada-Android-1.0.0-beta.apk` (18.566.932 byte) dan `Senada-iPhone-1.0.0-beta-unsigned.ipa` (14.834.267 byte).
  - **Android**: `applicationId = io.github.zeeynrs.senada`, `versionCode = 2`, `versionName = 1.0.0`. APK v1.0.0 beta ditandatangani kunci debug runner (`CN=Android Debug`, SHA-256 `4c8f5609…`). Belum ada `signingConfigs.release` permanen di `composeApp/build.gradle.kts`.
  - **iOS**: `PRODUCT_BUNDLE_IDENTIFIER = io.github.zeeynrs.senada`, `MARKETING_VERSION = 1.0.0`. Workflow `ios-device.yml` mengemas `Senada-unsigned.ipa` (`CODE_SIGNING_ALLOWED=NO`) untuk sideload via AltStore/AltServer.
  - Belum ada listing Google Play, App Store, atau tautan undangan TestFlight publik.

## Kebijakan Kanal & Desain

- **Android — GitHub Releases (Sideload)**: memeriksa `GET /repos/Zeeynrs/Senada-Mobile/releases?per_page=20` dengan ETag cache (`If-None-Match`), memisahkan rilis Stabil/Beta secara ketat, mendukung patch delta `SNDL` v1 (`ApkDelta`) dengan fallback otomatis ke unduhan APK penuh, memverifikasi SHA-256 + identitas paket + `versionCode` + kecocokan sertifikat penandatanganan (`AndroidApkVerifier`) sebelum meminta izin `REQUEST_INSTALL_PACKAGES` dan menyerahkan APK melalui `FileProvider` ke pemasang sistem Android.
- **Android — Google Play (`com.android.vending`)**: tidak melakukan sideload APK; mengarahkan pengguna ke Google Play Store (yang menangani pembaruan delta secara native). Jika kelak dibuat build khusus Google Play, izin `REQUEST_INSTALL_PACKAGES` wajib dihapus pada flavor Play.
- **iOS — Sideload IPA / TestFlight / App Store**: mematuhi Apple App Store Review Guideline 2.5.2 (tidak mengunduh atau memasang `.ipa` dari dalam aplikasi). Mendeteksi kanal (`embedded.mobileprovision`/Simulator vs `sandboxReceipt` vs App Store), menampilkan status akurat, dan membuka halaman rilis GitHub di Safari untuk sideload IPA (atau tautan TestFlight/App Store hanya jika dikonfigurasi secara eksplisit di `Info.plist`).

## Bukti Transfer Parsial (Delta APK `SNDL` v1)

Pengujian nyata memakai `tools/apk_delta.py` dari `Senada-Android-1.0.0-beta.apk` (base) ke `composeApp-debug.apk` (target hasil perubahan fitur ini):

- Ukuran APK dasar: `18.566.932` byte (`18,57 MB`)
- Ukuran APK target penuh: `18.682.284` byte (`18,68 MB`)
- Ukuran patch delta `.apkdelta`: `1.155.156` byte (`1,16 MB`, **hemat 93,82%**)
- Byte disalin langsung dari APK terpasang: `17.293.879` byte
- SHA-256 hasil rekonstruksi: identik bit-per-bit dengan APK target (`1a787668…`) dan lolos `apksigner verify`.

## Prasyarat yang Belum Terpenuhi untuk Pemasangan In-Place Produksi

1. **Kunci penandatanganan rilis Android permanen**: APK publik v1.0.0 beta memakai kunci debug sementara runner (`4c8f5609…`). Diperlukan keystore rilis tetap (`signingConfigs.release` via GitHub Secrets) agar pembaruan langsung tidak ditolak oleh pemeriksa tanda tangan (`SigningCertificateMismatch`) dan mempertahankan data pengguna.
2. **Publikasi aset delta di pipeline rilis**: rilis berikutnya perlu menyertakan file `.apkdelta` dan manifes `senada-android-delta.json` (dibuat dengan `tools/apk_delta.py create`) bersama APK rilis agar klien memakai jalur delta; tanpa manifes, klien otomatis beralih ke unduhan APK penuh.
3. **Distribusi resmi iOS / Google Play**: bila kelak beralih ke TestFlight/App Store atau Google Play, diperlukan akun Apple Developer / signing provisioning (serta opsional `SenadaTestFlightURL`/`SenadaAppStoreURL` di `Info.plist`) atau flavor Android Play tanpa `REQUEST_INSTALL_PACKAGES` menggunakan Play In-App Updates API.
