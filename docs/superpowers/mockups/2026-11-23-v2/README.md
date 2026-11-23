# Mockup Senada v2 (23 Nov 2026)

Sumber kebenaran visual untuk spec `docs/superpowers/specs/2026-11-23-ui-v2-profil-audio-lab-design.md`.
Kanvas aslinya: https://claude.ai/artifact/9gQ2977S4TQUMnwEvjpTiE (privat).

Setiap `*.dc.html` adalah satu layar ponsel 390 px (CSS px = dp di Compose). Ukuran, jarak, radius,
warna, ketebalan huruf dan teks di dalamnya adalah nilai target. Konversi: `px` → `dp`, `font-size` px → `sp`,
`letter-spacing: -.04em` → `(-0.04).em`, `rgba(r,g,b,a)` → `Color(r,g,b).copy(alpha = a)`.
Kotak berwarna polos (`[sampul ...]`) adalah tempat sampul asli; teks "Nama Artis/Judul Lagu" diganti data nyata.
Bagian `<script>` hanya untuk pratinjau (preset Equalizer, nada warna pemutar), bukan kode yang perlu ditiru.

| File | Layar |
|---|---|
| Main.dc.html | Beranda |
| NowPlaying.dc.html | Sedang diputar |
| Profile.dc.html | Profil (tujuan navbar paling kanan) |
| AudioLab.dc.html | Audio Lab |
| Equalizer.dc.html | Equalizer (gain per preset ada di tabel `table` skripnya) |
| SoundFx.dc.html | Efek suara |
| Appearance.dc.html | Tampilan |
