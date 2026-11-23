# Senada Mobile v2: tata letak Mochy × pemutar BitChord, Profil, dan Audio Lab

Tanggal: 23 November 2026. Status: menunggu persetujuan.

Mockup: [Senada Mobile — Arah Visual v2](https://claude.ai/artifact/9gQ2977S4TQUMnwEvjpTiE) (privat, milik pemilik repo), dengan tujuh layar: Beranda, Sedang diputar, Profil, Audio Lab, Equalizer, Efek suara, Tampilan.

Referensi: [Mochy Music](https://github.com/realidkroo/Mochy-Music) dan [BitChord](https://github.com/kushagrasinghx/BitChord). Keduanya berlisensi **GPL-3.0**, sedangkan Senada berlisensi **MIT**. Karena itu keduanya hanya dipakai sebagai inspirasi visual. **Kode, aset, logo, ikon, dan teks dari kedua repo tidak boleh disalin.**

## Maksud

Pemilik ingin UI dirombak menjadi gabungan dua referensi tersebut. Tombol paling kanan di navigasi bawah diganti menjadi **Profil**. Di dalam Profil ada statistik mendengar dan semua pengaturan: tema dan tampilan, equalizer, efek suara, dan lainnya.

## Keputusan pemilik (23 Nov 2026)

| Pertanyaan | Pilihan |
|---|---|
| Pembagian gaya | **Tata letak Mochy, pemutar BitChord.** Beranda, Library, Profil, dan Audio Lab bergaya Mochy: hitam pekat, judul display besar, kartu tebal, label mono kecil. Sedang diputar bergaya BitChord: latar warna sampul yang diblur, tombol kaca bulat, baris lirik di bawah judul. |
| EQ dan efek di iOS | **Harus jalan di Android dan iPhone.** |
| Identitas profil | **Lokal dan bisa diedit:** nama dan foto dipilih sendiri, tanpa akun. |
| Cara kerja | **Mockup dan spec dulu**, lalu dikerjakan bertahap per PR. |

## Sasaran

- Navigasi bawah: **Beranda, Cari, Library, Profil**. Tombol Profil berupa avatar bulat berisi foto atau inisial pengguna.
- Halaman **Profil** berisi, berurutan:
  1. Sapaan.
  2. Kartu pendengar: foto, nama, dan "Mendengar sejak".
  3. Total waktu dengar 30 hari dengan grafik 7 hari dan tautan ke statistik lengkap.
  4. Artis dan lagu teratas.
  5. Grup pengaturan: Audio Lab, Tampilan, Pemutaran, Backup library, Penyimpanan.
  6. Kartu versi dan "Tentang Senada".
- **Audio Lab** berisi Equalizer, Efek suara, Kecepatan & nada, Kualitas audio, dan Timer tidur. Semuanya berlaku di Android dan iPhone, dan pengaturannya tetap tersimpan setelah aplikasi ditutup.
- **Tampilan** berisi:
  - tema (Gelap, Terang, Ikuti sistem);
  - aksen dan "Warna mengikuti sampul";
  - sudut sampul;
  - latar pemutar dari sampul;
  - efek kaca (bisa dimatikan);
  - kecepatan animasi.
- Semua fitur yang ada tetap bisa dipakai dan lolos uji otomatis di setiap PR.

## Bukan sasaran

- Akun, login, atau sinkronisasi profil.
- Gaya pemutar yang bisa dipilih-pilih seperti Mochy (iPod, bedside, poster), recap bulanan, kanvas video, atau Discord.
- Pergeseran nada yang terpisah dari kecepatan. AVPlayer di iOS tidak mendukungnya, jadi pilihannya diganti "Kunci nada" (lihat Audio Lab).
- Crossfade dan automix.
- Tablet secara khusus.

## Anti-sasaran

- Terlihat seperti salinan Mochy atau BitChord, misalnya memakai logo, maskot, teks, atau nama fitur mereka ("Mochy Page", "Larp mode").
- Kode GPL masuk ke repo MIT.
- Efek suara membuat pemutaran tersendat, putus di latar belakang, atau baterai boros saat semua efek mati. Kalau semua efek mati, audio harus lewat jalur yang sama seperti sekarang.
- Aksesibilitas yang sudah ada hilang: label pembaca layar, sentuhan minimal 48 dp, dan teks besar tidak terpotong.
- Cakupan uji emulator berkurang.

## Arah visual

### Token, ditambah dari spec 21 Nov

- **Huruf:** Plus Jakarta Sans tetap dipakai. Ditambah **JetBrains Mono 500** (OFL) khusus untuk label kecil berhuruf kapital dengan jarak huruf 0,08 em, misalnya "TOTAL WAKTU DENGAR · 30 HARI" atau "AKTIF · BASS+". Huruf ini dibundel sebagai resource Compose, dan lisensinya ditaruh di `docs/licenses/`.
- **Warna:** palet gelap dan terang yang ada tetap dipakai. Ditambah garis dekorasi `#3A3D44` untuk gambar latar di kartu Audio Lab.
- **Kartu:** permukaan `#17181B`, garis 1 dp `#2A2C31`, radius 24 dp. Kartu Audio Lab memakai judul 26 sp tebal 800 dengan gambar garis besar di sudut kanan.
- **Judul halaman:** display 40–46 sp tebal 800, jarak huruf −0,05 em. Judul boleh dipecah dua baris ("Audio / Lab", "Efek / suara").
- **Tombol kembali:** lingkaran 44 dp `#1D1F23` di kiri atas, dengan label kecil tujuan di kanan.

### Kerangka

- Navigasi dan mini player tetap berupa pil kaca mengambang (Haze). Tombol aktif memakai latar putih tipis 12% (bukan aksen penuh), mengikuti kesan monokrom Mochy.
- Tombol Profil menampilkan foto bulat 28 dp. Kalau belum ada foto, isinya inisial nama di atas warna dari nama.

### Beranda

- **Hero penuh lebar** sampai tepi atas layar, tingginya sekitar 52% layar. Gambar sampul di-crop memenuhi area dengan gradasi ke latar. Logo Senada ada di kiri atas, avatar Profil di kanan atas.
- Teks hero: "Karena kamu sering memutar [foto artis] Nama Artis", lalu judul display, lalu tombol kaca **Putar** dan **Lihat**. Aturan isi hero dari spec 21 Nov tetap berlaku.
- Bagian "Baru diputar ›" dengan kartu sampul 132 dp (Favorit paling depan), lalu rak YouTube Music yang sudah ada.

### Sedang diputar (gaya BitChord, menggantikan gaya A)

- **Latar:** sampul yang diblur berat (Android 12+ dan iOS memakai blur Compose). Di Android 8–11, latarnya diganti gradien dua warna dari `ArtworkAccent`. Bisa dimatikan lewat Tampilan › Latar pemutar dari sampul.
- Isi dari atas ke bawah:
  1. pegangan dan "Diputar dari …";
  2. sampul besar 20 dp;
  3. judul 24 sp dan artis 19 sp, dengan tombol kaca bulat favorit dan ⋯;
  4. **baris lirik aktif** yang bisa diketuk untuk membuka lirik penuh;
  5. bilah posisi dengan waktu berjalan dan sisa waktu;
  6. transport putih besar tanpa lingkaran;
  7. baris tombol kaca: Lirik, Timer tidur, Audio Lab, Antrean.
- Acak dan Ulangi pindah ke menu ⋯ atau ke kepala Antrean. Pemeriksaan emulator tetap mencari teks dan label yang sama (lihat Pengujian).

### Profil

Isinya seperti di Sasaran.

- **Kartu pendengar** memakai latar `#1D1F23` dengan tulisan vertikal "KARTU · SENADA" di tepi kanan. Mengetuk pensil membuka lembar "Ubah profil": kolom nama dan tombol Pilih foto / Hapus foto.
- **"Mendengar sejak"** diambil dari waktu dengar tertua di database. Kalau belum ada, dipakai tanggal profil dibuat.
- Grafik 7 hari dan angka diambil dari `listeningStats` yang sudah ada.
- Layar **Statistik dengar** dan **Paling sering diputar** yang sudah ada dibuka dari Profil dan diberi gaya kartu baru.
- Menu **Pengembang** (Uji audio) disembunyikan. Cara membukanya: ketuk kartu versi 7 kali. Teks tombol "Putar HTTPS" dan lainnya tidak berubah agar skrip emulator tetap bisa menemukannya.

### Tampilan

Pilihan yang sudah ada (tema, aksen, ikuti sampul) ditambah:

- **Sudut sampul:** 0–28 dp, bawaan 12 dp di daftar dan 20 dp di sampul besar. Nilainya diskalakan bersama.
- **Latar pemutar dari sampul:** nyala/mati.
- **Efek kaca:** kalau mati, pil navigasi dan mini player memakai permukaan padat `#1D1F23`.
- **Kecepatan animasi:** Pelan ×1,5, Normal, Cepat ×0,6, Mati. "Mati" juga otomatis aktif bila sistem meminta pengurangan gerak.

## Audio Lab: fitur dan teknik

### Pengaturan

| Fitur | Isi | Bawaan |
|---|---|---|
| Equalizer | Nyala/mati, preset (Datar, Bass+, Vokal, Akustik, Elektronik, Malam, plus preset buatan sendiri), 10 band 31 Hz–16 kHz ±12 dB, preamp −12…+6 dB | Mati, Datar |
| Reverb | Nyala/mati, ruang (Kamar, Studio, Aula, Katedral), campuran 0–100%, ukuran ruang 0–100% | Mati |
| Bass boost | Nyala/mati, kekuatan 0–100% | Mati |
| Kecepatan & nada | 0,5×–2,0× (pilihan cepat 0,75 / 1,00 / 1,25 / 1,50), **Kunci nada** (nyala: nada tetap; mati: nada ikut berubah seperti piringan hitam) | 1,00×, kunci nyala |
| Samakan volume | Nyala/mati. Memakai nilai loudness dari respons YouTube Music bila ada. Kalau tidak ada, tidak berbuat apa-apa. | Mati |
| Kualitas audio | Pindahan dari "Hemat data" yang sudah ada | Seperti sekarang |
| Timer tidur | Pindahan dari tombol di pemutar, sekarang bisa dibuka dari keduanya | Seperti sekarang |

Semua pengaturan audio disimpan dalam satu model `AudioEffects` (JSON lewat penyimpanan pengaturan yang sudah ada). Pengaturan ini tidak ikut backup library, karena format backup harus tetap sama dengan Senada desktop.

### Arsitektur

1. **`commonMain/senada/audio/`**: model `AudioEffects`, tabel preset, dan **DSP bersama dalam Kotlin murni**:
   - biquad peaking ×10 dan preamp;
   - low-shelf untuk bass boost;
   - reverb gaya Schroeder/Freeverb (ditulis sendiri);
   - soft limiter supaya tidak pecah saat band dinaikkan.

   Ada `fun process(buffer: FloatArray, frames: Int, channels: Int)` yang tidak membuat objek baru di jalur audio. Kalau semua efek mati, data audio langsung diteruskan tanpa diproses. DSP diuji di `commonTest`: respons frekuensi tiap band, Datar menghasilkan output identik bit, limiter tidak melewati 0 dBFS, dan tidak ada NaN.
2. **Android:** `AudioProcessor` Media3 yang memanggil DSP bersama, dipasang lewat `DefaultRenderersFactory` dan `DefaultAudioSink`. Ini jalan di semua versi Android sejak minSdk 26, tanpa bergantung pada `audiofx` yang band-nya berbeda per pabrikan. Kecepatan dan kunci nada memakai `setPlaybackParameters(speed, pitch)`.
3. **iOS:** `MTAudioProcessingTap` dipasang di `AVPlayerItem.audioMix`, dan callback `process` memanggil DSP bersama. Kecepatan memakai `AVPlayer.rate`. Kunci nada memakai `audioTimePitchAlgorithm`: `.timeDomain`/`.spectral` untuk nada tetap, `.varispeed` untuk nada ikut berubah.

### Risiko terbesar: iOS

Tap hanya berlaku untuk aset progresif, tidak untuk HLS. Senada memutar stream m4a progresif, jadi seharusnya bisa. Yang belum terbukti:

- callback C dari Kotlin/Native (`staticCFunction`) berjalan di thread audio real-time;
- GC Kotlin/Native tidak menyebabkan suara putus;
- tap tetap hidup saat item berganti dan saat aplikasi di latar belakang atau layar terkunci.

Karena itu, **PR iOS pertama adalah spike**: tap yang hanya menerapkan preamp dan satu band, diuji di iPhone fisik dengan stream YouTube Music dan file impor, di latar depan dan latar belakang, selama 10 menit. Ini satu-satunya pengecualian dari aturan "kumpulkan beberapa fitur sebelum build IPA", karena seluruh rencana iOS bergantung pada hasilnya. **Kalau spike gagal,** cadangannya adalah AudioUnit bawaan Apple (`AUNBandEQ` dan `AUReverb2`) yang dipanggil dari tap dalam Swift di `iosApp`. Suaranya akan sedikit berbeda dari Android. Bila jalur cadangan ini dipakai, pemilik diberi tahu.

## Data dan navigasi

- `AppDestination.Settings` diganti `AppDestination.Profile` (label "Profil"). Saver navigator harus tetap bisa memulihkan nilai lama `"Settings"` sebagai Profile.
- Ditambah tumpukan rute Profil: `ProfileRoute` = Stats, MostPlayed, AudioLab, Equalizer, SoundFx, Appearance, Playback, Backup, Storage, About, Developer. Flag `listeningStatsOpen` dan `mostPlayedOpen` digabung ke dalamnya. Tombol kembali menutup rute teratas dulu.
- **Profil lokal:** `UserProfile(name, photoPath?, createdAt)`, disimpan seperti pengaturan.
  - Foto dipilih lewat Photo Picker di Android dan `PHPickerViewController` di iOS, dipotong persegi, diperkecil ke 512 px, lalu disimpan di penyimpanan aplikasi.
  - Nama boleh kosong. Kalau kosong, sapaan menjadi "Halo!" dan avatar berupa ikon orang.
  - **Ikut backup:** catatan profil menyebut "ikut tersimpan di backup". Sebelum menambah kunci `profile.name` ke file backup, pastikan dulu Senada desktop (`Zeeynrs/Senada`) mengabaikan kunci yang tidak dikenalnya. Kalau tidak, nama ditunda dari backup dan pemilik diberi tahu. Foto tidak ikut backup.

## Urutan PR

Setiap PR kecil dan lolos Mobile checks serta uji emulator Android. Pembagian pekerja mengikuti kesepakatan 21 Nov: Claude memegang pemutaran dan iOS, dan PlaybackController tidak boleh disentuh dua agen sekaligus.

| # | PR | Isi | Pekerja yang diusulkan |
|---|---|---|---|
| 1 | Kerangka v2 | Token mono dan label, navbar dengan tombol Profil, `ProfileRoute`, bagian Pengaturan lama dipindah ke halaman Profil apa adanya, skrip emulator diperbarui | Astra |
| 2 | Profil | Kartu pendengar, ubah nama/foto (dua platform), ringkasan statistik, artis/lagu teratas, kartu versi, gaya baru Statistik | Astra |
| 3 | DSP bersama | `senada.audio`: model, preset, penyimpanan, DSP, dan uji `commonTest` | Claude |
| 4 | Efek Android | AudioProcessor Media3, kecepatan dan kunci nada, uji emulator: memutar dengan EQ nyala tetap berjalan dan posisi tetap maju | Claude |
| 5 | Spike iOS | Tap dengan preamp dan satu band, **IPA dan uji iPhone** | Claude |
| 6 | Efek iOS | Tap penuh atau jalur cadangan, kecepatan, dan kunci nada | Claude |
| 7 | Audio Lab UI | Halaman Audio Lab, Equalizer, Efek suara, dan pintasan dari pemutar | Sol (UI saja, setelah #3) |
| 8 | Tampilan v2 | Sudut sampul, efek kaca, kecepatan animasi, latar pemutar | Sol |
| 9 | Beranda v2 | Hero penuh lebar dan kartu baru | Astra |
| 10 | Sedang diputar v2 | Gaya BitChord dan baris lirik aktif | Claude (menyentuh pemutar) |
| 11 | Cari, Library, artis, album | Gaya kartu dan judul baru | Sol |
| 12 | Samakan volume | Loudness dari respons YouTube Music | Claude |

PR 1–2 dan PR 3–4 bisa berjalan paralel. Build IPA untuk uji di iPhone dilakukan setelah PR 5, lalu sekali lagi setelah PR 6–10 terkumpul.

## Pengujian

- `commonTest`: DSP (lihat Arsitektur), penyimpanan `AudioEffects`/`UserProfile` (termasuk JSON rusak yang harus kembali ke nilai bawaan), saver navigator (termasuk pemulihan nilai lama "Settings"), rute Profil, dan kecepatan animasi.
- **Emulator Android:** alur yang sudah ada (putar, jeda, lanjut otomatis, sebelumnya/berikutnya, geser posisi, Putar HTTPS dari menu Pengembang yang dibuka dengan 7 ketukan) ditambah: buka Profil › Audio Lab › Equalizer, nyalakan, pilih Bass+, putar, pastikan posisi tetap maju dan tidak ada error di logcat.
- **iPhone fisik**, dikumpulkan: spike tap (PR 5), efek penuh di latar belakang dan layar terkunci, pemilih foto, blur latar pemutar, dan teks besar di Profil.

## Diserahkan kepada pembangun

Detail jarak, ikon (gambar sendiri, gaya garis), animasi kecil, susunan grafik statistik lengkap, dan teks bantuan di setiap pengaturan.

## Keputusan yang diambil Claude, bukan pemilik

- DSP ditulis sendiri dan dipakai bersama oleh kedua platform (bukan `audiofx` Android), supaya preset terdengar sama di Android dan iPhone dan bisa diuji tanpa perangkat.
- Pergeseran nada terpisah dihapus dan diganti "Kunci nada", karena AVPlayer tidak mendukungnya.
- "Efek suara" diartikan sebagai efek audio pada musik (reverb, bass boost, samakan volume), bukan bunyi klik di antarmuka. Kalau yang dimaksud bunyi antarmuka, itu ditambahkan sebagai PR terpisah.
- JetBrains Mono ditambahkan untuk label kecil.
- Acak dan Ulangi pindah dari baris transport ke menu ⋯ dan kepala Antrean.
- Menu Pengembang disembunyikan di balik 7 ketukan pada kartu versi.
