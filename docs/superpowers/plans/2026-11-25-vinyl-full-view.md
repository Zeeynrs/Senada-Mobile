# Tema Vinyl untuk Full View

**Tujuan.** Pengguna bisa memilih tema Vinyl dari Sedang diputar atau Profil → Tampilan. Simpan pilihan bersama pengaturan tampilan. Gunakan Standar sebagai pilihan awal.

**Rancangan.** Pada ponsel tegak, piringan terpotong di atas dan kiri. Jarum berporos di kanan dan menyentuh alur piringan. Lirik tersinkron berada di bawah piringan, diikuti metadata dan kontrol pemutaran. Pada layar mendatar yang lebar, letakkan piringan di kiri dan lirik serta kontrol di kanan. Gunakan `NowPlayingScreen`, `PlaybackController`, dan panel lirik yang sama.

- [x] Tambahkan tes baca-tulis pilihan tema, kompatibilitas pengaturan lama, dan pemetaan geometri jarum dari awal sampai akhir lagu.
- [x] Simpan tema di `AppearanceStore.kt` dan tampilkan pemilih dengan pratinjau di `AppearanceViews.kt` dan Sedang diputar.
- [x] Hubungkan `VinylNowPlaying` ke tema terpilih. Putar label sampul selama audio berjalan dengan menghormati pengurangan gerak. Saat jarum diseret, tampilkan waktu tujuan dan kirim satu seek saat dilepas. Batalkan gestur jika lagu berubah, seek menjadi tidak tersedia, atau gestur dibatalkan.
- [x] Jalankan tes Android dan build debug.
- [ ] Pada perangkat, cek potongan piringan, orientasi, aksesibilitas, pergantian tema selama lagu berjalan, tarik jarum saat Putar dan Jeda, serta lagu tanpa lirik atau durasi.
