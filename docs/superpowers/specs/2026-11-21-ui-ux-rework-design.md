# Desain ulang UI/UX Senada Mobile

Tanggal: 21 November 2026. Status: menunggu persetujuan.

Mockup arah visual: [Arah Visual Senada Mobile](https://claude.ai/artifact/Mx4hj3wK68saC9MTJs7WAc) (privat, milik pemilik repo). Referensi: tangkapan layar Senada desktop (`Zeeynrs/Senada`, `docs/screenshots/`) dan Mochy Music (hanya sebagai inspirasi, bukan untuk ditiru).

## Maksud dan alasan

Fitur beta pertama sudah lengkap dan lolos uji di iPhone: pencarian YouTube Music, pemutaran latar belakang, antrean tersimpan, lirik, ulangi/acak, library, backup, impor audio, tema, hemat data. Tampilannya masih prototipe: satu daftar panjang berisi kolom cari, tombol teks, bagian "Uji audio", kontrol pemutar dan antrean, dengan tab yang digeser. Desain ulang ini membuat Senada Mobile terasa seperti aplikasi musik yang utuh dan bersaudara dengan Senada desktop, sebelum dibagikan ke komunitas kecil.

## Sasaran

- Arah visual gabungan yang dipilih pemilik: **Beranda bergaya B ("Pulau")** dan **Sedang diputar bergaya A ("Malam")**.
- Fondasi **Material 3** dengan token desain Senada (warna, huruf, bentuk, jarak) yang dipakai semua layar.
- Struktur navigasi aplikasi ponsel: navigasi bawah dan mini player yang mengambang, layar Sedang diputar penuh.
- Beranda dengan hero yang diisi dari **pemakaian sendiri** (lagu paling sering diputar atau playlist paling sering dipilih), tanpa rekomendasi dari server.
- Semua fitur yang ada tetap bisa dipakai dan tetap lolos uji otomatis di setiap langkah.

## Bukan sasaran

- Rekomendasi atau beranda dari YouTube Music, halaman artis/album, recap bulanan, impor dari layanan lain.
- Sampul tertanam dari file audio impor, karaoke per kata, visualizer.
- Tablet atau layar lipat secara khusus (cukup tidak rusak).
- Mengubah perilaku pemutaran, data library, atau format backup.

## Anti-sasaran (gagal meski secara teknis jalan)

- Terlihat seperti tiruan Mochy Music atau aplikasi lain.
- Perbaikan aksesibilitas yang sudah ada hilang: label pembaca layar, sentuhan minimal 48 dp, teks besar tidak terpotong.
- Layar terasa lambat atau boros baterai: animasi terus-menerus, polling posisi saat layar tertutup, memuat sampul besar di daftar.
- Uji emulator tidak lagi mencakup alur yang sekarang diuji (putar, jeda, lanjut otomatis, sebelumnya/berikutnya, geser posisi, Putar HTTPS).

## Batasan

- Kotlin Multiplatform, Compose Multiplatform 1.9.3, Kotlin 2.2.21; dependency baru harus kompatibel dengan Kotlin 2.2 (seperti Coil 3.3.0 dan SQLDelight 2.2.1 yang dipilih karena itu).
- Tidak ada JDK/Android SDK lokal: semua verifikasi lewat GitHub Actions (Mobile checks, uji emulator Android, build IPA). Uji iPhone fisik dikumpulkan beberapa PR sekali.
- Teks UI bahasa Indonesia.
- Skrip uji emulator (`.github/scripts/android_background_test.py`) mengetuk tombol berdasarkan teks ("Tambah", "Putar", "Sebelumnya", "Berikutnya", "Putar HTTPS") dan membaca slider lewat `contentDescription` "Posisi pemutaran". Setiap perubahan yang memindahkan atau mengganti elemen itu wajib memperbarui skrip pada PR yang sama, tanpa mengurangi cakupan pemeriksaannya.
- Pekerjaan aksesibilitas Neon (cabang `fix/accessibility`) disatukan ke `main` lebih dulu, lalu desain ulang dibangun di atasnya.
- Merge selalu menunggu persetujuan pemilik.

## Pendekatan

1. **Bertahap, satu PR per langkah di atas Material 3** — dipilih. Setiap langkah kecil teruji dan aplikasi selalu bisa dipakai; setelah kerangka navigasi masuk, layar bisa dikerjakan paralel. Kekurangan: sementara ada layar bergaya baru di samping layar bergaya lama.
2. Satu cabang besar sekaligus — tidak ada tampilan campuran, tetapi PR raksasa, sulit direview, bentrok dengan pekerjaan lain, dan masalah baru ketahuan di akhir.
3. Struktur layar dulu dengan gaya lama, gaya belakangan — navigasi cepat jadi, tetapi setiap layar ditata dua kali.

Pemilik memilih pendekatan 1.

## Arah visual

### Token

- **Huruf:** satu keluarga, **Plus Jakarta Sans** (lisensi OFL), dibundel sebagai resource Compose (bukan diunduh saat jalan). Judul display Beranda memakai ketebalan 800 dengan jarak huruf rapat; teks biasa 500–700.
- **Warna, tema gelap (utama):** latar `#0B0C0E`, permukaan `#17181B`/`#1D1F23`, garis `#2A2C31`, teks `#F2F2F3`, teks sekunder `#A3A6AD`, hati favorit `#FF5A6E`. Aksen bawaan teal desktop `#67CDB1`.
- **Warna, tema terang:** palet arah C — latar `#F7F6F3`, permukaan `#FFFFFF`/`#ECEAE5`, teks `#17181A`, teks sekunder `#5D6168`, aksen teal gelap `#0F7B67` (teal desktop terlalu terang untuk teks di latar terang).
- **Aksen dari sampul:** mekanisme yang ada (`ArtworkAccent.kt`, `accessibleAccent`) tetap; bila "Warna mengikuti sampul" menyala, aksen Material 3 (primary dan turunannya) mengikuti sampul lagu yang dipilih, dengan kontras minimal 4,5:1.
- **Bentuk:** sudut besar dan lembut — kartu 20–24 dp, sampul daftar 8–12 dp, sampul besar 20 dp, pil penuh untuk tombol dan navigasi.

### Kerangka aplikasi

- **Navigasi bawah mengambang** berbentuk pil (gaya B) dengan empat tujuan: **Beranda, Cari, Library, Pengaturan**. Tujuan aktif ditandai pil berisi aksen.
- **Mini player "pulau"** mengambang di atas navigasi di semua tab saat ada lagu terpilih: sampul bulat kecil, judul, artis, Putar/Jeda, Berikutnya. Mengetuk pulau membuka Sedang diputar.
- **Sedang diputar** dibuka sebagai layar penuh di atas tab; ditutup dengan tombol turun, tombol kembali Android, atau usap ke bawah.
- Konten tab bisa digulir di bawah elemen mengambang dengan jarak bawah yang cukup supaya baris terakhir tidak tertutup.
- **Menu pengembang:** bagian "Uji audio" (Putar lokal, Putar HTTPS) pindah ke bagian "Pengembang" di bawah Pengaturan.

### Sedang diputar (gaya A)

Latar dari warna sampul (gradien gelap, bukan gambar yang diburamkan berat); bar atas dengan tombol tutup, sumber pemutaran ("Dari Favorit", "Dari playlist …"), dan menu ⋯; sampul besar; judul dan artis dengan tombol favorit; bilah posisi tipis yang bisa digeser; kontrol Acak – Sebelumnya – **Putar/Jeda bulat putih** – Berikutnya – Ulangi; kartu bawah dengan dua bagian **Lirik** dan **Antrean**: lirik menampilkan beberapa baris dengan baris aktif tebal dan bisa dibuka penuh; antrean menampilkan entri dengan Putar/Hapus seperti sekarang.

### Beranda (gaya B)

- **Hero** besar di atas, latar dari warna sampul isi hero, teks kecil alasan ("Paling sering kamu putar bulan ini"), judul display besar, tombol **Putar** dan **Lihat**.
- **Baru diputar:** deret kartu sampul besar dari riwayat (dan kartu Favorit di depan).
- **Playlist kamu:** deret playlist.
- Tidak ada konten dari server di Beranda.

### Isi hero

- Hitungan diambil dari **30 hari terakhir**.
- Bila ada playlist yang dipilih (lewat **Putar semua** pada playlist) **minimal 3 kali** dalam 30 hari, hero menampilkan playlist itu (yang terbanyak). Bila tidak, hero menampilkan **lagu yang paling sering diputar**.
- **Putar** memutar playlist/lagu itu. **Lihat** membuka playlist; untuk lagu, membuka daftar **Paling sering diputar** (lagu teratas 30 hari) yang bisa diputar semua.
- **Belum ada hitungan:** hero sambutan dengan tombol **Cari lagu** yang membuka tab Cari.
- Hitungan lagu bertambah setiap kali lagu antrean mulai berbunyi, dengan aturan yang sama seperti riwayat (melanjutkan dari jeda tidak dihitung; tombol uji audio tidak dihitung). Hitungan playlist bertambah setiap kali **Putar semua** pada playlist ditekan.
- Data hitungan disimpan di database library (migrasi ke versi 4), dibersihkan otomatis untuk yang lebih tua dari 30 hari, **tidak ikut backup**, dan ikut terhapus bila lagunya tidak lagi dipakai di mana pun bila itu diperlukan untuk menjaga konsistensi.

### Cari, Library, Pengaturan

- **Cari:** kolom cari besar membulat, riwayat pencarian, tombol impor file audio, hasil dengan sampul, artis, durasi, Tambah dan menu ⋯.
- **Library:** satu tab dengan chip **Favorit, Playlist, Riwayat, File impor** (gaya C) menggantikan tab Favorit/Playlist/Riwayat terpisah; favorit dan playlist tetap dimuat per halaman 50.
- **Pengaturan:** bagian Tampilan, Data, Backup library, Penyimpanan, lalu Pengembang, dengan gaya kartu yang konsisten.

## Urutan PR

1. **Fondasi:** Material 3, token, font, tema terang/gelap, aksen sampul tersambung ke skema warna Material 3. Layar boleh masih tata letak lama.
2. **Kerangka:** navigasi bawah mengambang, mini player pulau, kerangka layar Sedang diputar (isi lama dipindah ke dalamnya), perilaku kembali, menu Pengembang, skrip emulator diperbarui.
3. **Sedang diputar** gaya A lengkap (lirik dan antrean di dalamnya).
4. **Hitungan putar + Beranda** gaya B (migrasi versi 4, aturan hero, daftar Paling sering diputar).
5. **Cari, Library, Pengaturan** gaya baru.

PR 3, 4, dan 5 boleh dikerjakan paralel setelah PR 2 masuk, asal tidak mengubah file yang sama.

## Pengujian

- Setiap PR: Mobile checks dan uji emulator Android lulus; tes commonTest untuk logika baru (aturan hero, hitungan 30 hari, pembersihan, migrasi 3 → 4, pemilihan sumber pemutaran).
- Skrip emulator tetap memeriksa semua alur yang ada sekarang; navigasi baru (buka/tutup Sedang diputar, pindah tab) ditambahkan.
- Uji iPhone fisik dikumpulkan beberapa PR sekali: tampilan gelap dan terang, aksen sampul, teks besar, VoiceOver dasar, kembali/usap tutup, mini player di semua tab.

## Diserahkan kepada pembangun

- Pustaka navigasi (pustaka navigasi Compose Multiplatform resmi atau state sendiri), selama tombol kembali Android dan tutup Sedang diputar bekerja.
- Ikon: set vektor sendiri yang konsisten atau dependency ikon, dengan memperhatikan ukuran aplikasi.
- Skema tabel hitungan dan cara pembersihannya, selama aturan di atas dipenuhi dan tes migrasi ada.
- Animasi buka/tutup Sedang diputar dan transisi antartab (singkat, hemat, menghormati pengurangan gerak bila tersedia).
- Detail jarak dan ukuran di luar token di atas.

## Keputusan yang diambil saya (Claude), bukan pemilik

- Satu keluarga huruf Plus Jakarta Sans untuk seluruh aplikasi (mockup B memakai huruf lain).
- Empat tujuan navigasi Beranda/Cari/Library/Pengaturan; tidak ada tab Lirik terpisah (lirik ada di Sedang diputar).
- Library digabung menjadi satu tab dengan chip; antrean pindah ke dalam Sedang diputar.
- Aksen bawaan teal desktop, tema gelap sebagai tampilan utama dan terang dari palet C.
- "Uji audio" pindah ke bagian Pengembang di Pengaturan.
- Ambang playlist "minimal 3 kali" dan jangka 30 hari (disetujui pemilik sebagai usulan).
- **Tanpa warna dinamis sistem** (Material You di Android 12+): warna aplikasi selalu dari token Senada atau aksen sampul, supaya Android dan iOS tampil sama.
- **Sedang diputar selalu gelap-imersif**, juga saat tema terang, seperti pemutar imersif desktop; tema terang berlaku untuk tab dan mini player.
- **Hero playlist** memakai sampul lagu pertama playlist untuk warna latar dan gambarnya (playlist tidak punya sampul sendiri). Bila dua lagu atau playlist punya hitungan sama, yang terakhir diputar menang.
