# Desain Ulang UI/UX Material 3 — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun ulang tampilan Senada Mobile di atas Material 3 sesuai arah visual yang dipilih (Beranda gaya B, Sedang diputar gaya A), dalam lima PR yang masing-masing teruji.

**Architecture:** Token desain dan tema Material 3 di `senada/app/theme/`; kerangka aplikasi (navigasi mengambang, mini player, perilaku kembali) di `senada/app/shell/`; setiap tujuan navigasi punya file tab sendiri di `senada/app/tabs/`; layar Sedang diputar di `senada/app/nowplaying/`. Data hitungan putar ada di database library (SQLDelight, migrasi ke versi 4) dan dibaca lewat `LibraryRepository`. Navigasi memakai state sendiri ditambah penangan kembali per platform, tanpa dependency navigasi baru.

**Tech Stack:** Kotlin 2.2.21, Compose Multiplatform 1.9.3 (`compose.material3`), Compose resources (font), SQLDelight 2.2.1, Coil 3.3.0, kotlinx.coroutines 1.10.2. Verifikasi hanya lewat GitHub Actions: Mobile checks, `android-emulator.yml`, dan `ios-device.yml` untuk sesi uji iPhone.

**Spec:** `docs/superpowers/specs/2026-11-21-ui-ux-rework-design.md`

## Global Constraints

- Mulai dari `main` **setelah** PR aksesibilitas Neon (`fix/accessibility`) masuk. Label pembaca layar, sentuhan ≥ 48 dp, dan perilaku teks besar dari PR itu wajib tetap ada.
- Satu PR per fase (Fase 1–5), cabang dari `origin/main` terbaru. Merge hanya setelah pemilik menulis "merge".
- Dependency baru harus kompatibel dengan Kotlin 2.2.21. Fase ini tidak menambah dependency selain `compose.material3`; ikon memakai set vektor sendiri.
- Huruf: **Plus Jakarta Sans** (OFL), dibundel sebagai resource, ketebalan 500/600/700/800.
- Warna gelap: latar `#0B0C0E`, permukaan `#17181B` dan `#1D1F23`, garis `#2A2C31`, teks `#F2F2F3`, teks sekunder `#A3A6AD`, favorit `#FF5A6E`, aksen bawaan `#67CDB1`.
- Warna terang: latar `#F7F6F3`, permukaan `#FFFFFF` dan `#ECEAE5`, teks `#17181A`, teks sekunder `#5D6168`, aksen bawaan `#0F7B67`.
- Bentuk: kartu 20–24 dp, sampul daftar 8–12 dp, sampul besar 20 dp, tombol/navigasi pil penuh.
- Tanpa warna dinamis sistem. Sedang diputar selalu gelap-imersif.
- Teks UI bahasa Indonesia; komentar kode bahasa Indonesia; commit bahasa Inggris gaya Conventional Commits.
- Skrip `.github/scripts/android_background_test.py` wajib diperbarui pada PR yang memindahkan elemen yang diketuknya, tanpa mengurangi pemeriksaan yang ada (putar, jeda/lanjut di posisi sama, notifikasi, layar mati + lanjut otomatis, tombol media sistem, geser slider "Posisi pemutaran", Berikutnya/Sebelumnya di aplikasi, Putar HTTPS, tanpa crash).
- Tiap PR: Mobile checks + uji emulator lulus; checklist "iPhone fisik" dibiarkan terbuka untuk sesi uji gabungan.

## Review Focus

- **Tombol kembali Android saat Sedang diputar terbuka** harus menutup Sedang diputar, bukan keluar aplikasi; dari tab selain Beranda kembali ke Beranda dulu. Diuji di Task 3 (`AppNavigatorTest`).
- **Antrean berisi lagu tetapi belum ada yang dipilih** (aplikasi baru dibuka dengan antrean pulih, atau baru menambah lagu): mini player tetap muncul dan Putar memulai entri pertama. Diuji di Task 4 (`showsMiniPlayer`).
- **Playlist hero dihapus atau lagunya hilang dari library**: hero jatuh ke lagu teratas atau sambutan, tanpa crash. Diuji di Task 7.
- **Putar semua pada playlist kosong** tidak menambah hitungan playlist. Diuji di Task 7.
- **Tema terang**: Sedang diputar tetap memakai skema gelap dan teksnya tetap terbaca di atas warna sampul. Diuji di Task 6 (`nowPlayingColorScheme`).

---

## Fase 1 — Fondasi Material 3 (PR 1, cabang `feat/m3-foundation`)

### Task 1: Token, font, dan tema Material 3

**Files:**
- Modify: `composeApp/build.gradle.kts` (commonMain: tambah `implementation(compose.material3)`)
- Create: `composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_medium.ttf`, `…_semibold.ttf`, `…_bold.ttf`, `…_extrabold.ttf` (instance statis dari rilis resmi Plus Jakarta Sans, `github.com/tokotype/PlusJakartaSans`)
- Create: `docs/licenses/PlusJakartaSans-OFL.txt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/theme/SenadaTokens.kt`
- Modify: `composeApp/src/commonMain/kotlin/senada/app/Theme.kt`
- Test: `composeApp/src/commonTest/kotlin/senada/app/theme/SenadaTokensTest.kt`

**Interfaces:**
- Produces: `internal object SenadaPalette` (nilai hex Global Constraints sebagai `Color`, nama `DarkBackground`, `DarkSurface`, `DarkSurfaceHigh`, `DarkOutline`, `DarkText`, `DarkTextMuted`, `LightBackground`, `LightSurface`, `LightSurfaceHigh`, `LightText`, `LightTextMuted`, `Favorite`, `AccentDark` = `#67CDB1`, `AccentLight` = `#0F7B67`)
- Produces: `internal fun senadaColorScheme(dark: Boolean, accent: Color?): ColorScheme` — `accent == null` → aksen bawaan; bila ada → `accessibleAccent(accent, background)`; `onPrimary = textOnAccent(primary)`
- Produces: `@Composable internal fun senadaTypography(): Typography` dan `internal val SenadaShapes: Shapes` (extraSmall 8, small 12, medium 20, large 24, extraLarge 28 dp)
- Produces: `SenadaTheme(artworkUrl, visible, content: @Composable (AppearancePreferences) -> Unit)` tetap dengan tanda tangan sama, kini membungkus `androidx.compose.material3.MaterialTheme(colorScheme, typography, shapes)`

- [ ] **Step 1: Tulis tes yang gagal** — `SenadaTokensTest`:
  - `darkSchemeUsesSenadaColors`: `senadaColorScheme(true, null).background == Color(0xFF0B0C0E)`, `.primary == Color(0xFF67CDB1)`
  - `lightSchemeUsesDarkerTeal`: `senadaColorScheme(false, null).primary == Color(0xFF0F7B67)`, `.background == Color(0xFFF7F6F3)`
  - `coverAccentIsMadeReadable`: untuk `accent = Color(0xFFFFF3B0)` di skema terang, `contrastRatio(primary, background) >= 4.5f`
  - `textOnPrimaryIsReadable`: untuk kedua skema dan aksen `Color(0xFF67CDB1)`, `Color(0xFF1F5FB0)`, `contrastRatio(onPrimary, primary) >= 4.5f`
  - `mutedTextIsReadable`: `contrastRatio(onSurfaceVariant, background) >= 4.5f` di kedua skema
- [ ] **Step 2: Jalankan** — push, buka draft PR; Mobile checks gagal di tes baru.
- [ ] **Step 3: Implementasikan** token, `senadaColorScheme`, `senadaTypography` (memakai `Font(Res.font.plus_jakarta_sans_*, FontWeight.W500..W800)`), `SenadaShapes`, dan `SenadaTheme` di Material 3. Map: `background/surface/surfaceVariant/outline/onBackground/onSurface/onSurfaceVariant` dari palet; `error` tetap bawaan M3.
- [ ] **Step 4: Jalankan** — Mobile checks lulus.
- [ ] **Step 5: Commit** — `feat(theme): add Senada Material 3 tokens and Plus Jakarta Sans`

### Task 2: Pindahkan semua layar ke komponen Material 3

**Files:**
- Modify: semua file di `composeApp/src/commonMain/kotlin/senada/app/*.kt` dan `senada/lyrics/LyricsPanel.kt` yang mengimpor `androidx.compose.material.*`
- Modify: `composeApp/build.gradle.kts` (hapus `implementation(compose.material)`)

**Interfaces:**
- Consumes: Task 1 (`SenadaTheme`, tipografi)
- Produces: tidak ada API baru; tampilan masih tata letak lama dengan komponen M3

- [ ] **Step 1: Ganti impor dan komponen** dengan pemetaan tetap: `typography.h4→headlineMedium`, `h6→titleLarge`, `subtitle1→titleMedium`, `body1→bodyLarge`, `body2→bodyMedium`; `colors.X→colorScheme.X`; `DropdownMenuItem(onClick){Text}` → `DropdownMenuItem(text = { Text(...) }, onClick = ...)`; `ScrollableTabRow` → M3 `ScrollableTabRow`; `AlertDialog`, `Switch`, `RadioButton`, `Slider`, `OutlinedTextField`, `Button`, `TextButton`, `OutlinedButton`, `IconButton`, `Surface` → padanan `androidx.compose.material3`. Teks tombol dan `contentDescription` yang ada tidak berubah.
- [ ] **Step 2: Hapus dependency `compose.material`**; pastikan tidak ada impor `androidx.compose.material.` tersisa (`grep -rn "androidx.compose.material\." composeApp/src` hanya menemukan `material3` dan `material.icons` bila ada).
- [ ] **Step 3: Verifikasi** — Mobile checks lulus; `gh workflow run android-emulator.yml --ref feat/m3-foundation` lulus tanpa mengubah skrip.
- [ ] **Step 4: Commit dan PR** — `refactor(ui): move every screen to Material 3 components`; isi PR mencantumkan kedua run.

---

## Fase 2 — Kerangka navigasi (PR 2, cabang `feat/app-shell`)

### Task 3: Navigator dan tombol kembali

**Files:**
- Create: `composeApp/src/commonMain/kotlin/senada/app/shell/AppNavigator.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/shell/PlatformBackHandler.kt` (expect)
- Create: `composeApp/src/androidMain/kotlin/senada/app/shell/PlatformBackHandler.android.kt` (actual memakai `androidx.activity.compose.BackHandler`)
- Create: `composeApp/src/iosMain/kotlin/senada/app/shell/PlatformBackHandler.ios.kt` (actual tanpa aksi; iOS menutup lewat tombol/usap)
- Test: `composeApp/src/commonTest/kotlin/senada/app/shell/AppNavigatorTest.kt`

**Interfaces:**
- Produces: `internal enum class AppDestination(val label: String) { Home("Beranda"), Search("Cari"), Library("Library"), Settings("Pengaturan") }`
- Produces: `internal class AppNavigator` dengan state Compose: `destination: AppDestination`, `nowPlayingOpen: Boolean`, `openedPlaylistId: String?`, `mostPlayedOpen: Boolean`; fungsi `select(destination)`, `openNowPlaying()`, `closeNowPlaying()`, `openPlaylist(id: String)`, `openMostPlayed()`, `back(): Boolean` (true bila menangani kembali). Urutan `back()`: tutup Sedang diputar → tutup Paling sering diputar → tutup detail playlist → pindah ke Beranda bila tab lain → false.
- Produces: `@Composable internal fun rememberAppNavigator(): AppNavigator` (tahan rotasi lewat `rememberSaveable` dengan Saver sendiri)
- Produces: `@Composable internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)`

- [ ] **Step 1: Tulis tes yang gagal** — `AppNavigatorTest`:
  - `backClosesNowPlayingFirst`: select(Library), openPlaylist("p"), openNowPlaying() → `back()` true dan `nowPlayingOpen == false`, `openedPlaylistId == "p"`
  - `backThenClosesPlaylistThenGoesHome`: lanjutan → `back()` menutup playlist; `back()` berikutnya → `destination == Home`; `back()` di Beranda → false
  - `selectingATabClosesDetailScreens`: openPlaylist("p"), select(Search) → `openedPlaylistId == null`
- [ ] **Step 2: Jalankan** — Mobile checks gagal.
- [ ] **Step 3: Implementasikan** `AppNavigator`, Saver, dan `PlatformBackHandler` (Android: `BackHandler(enabled, onBack)`; iOS: kosong).
- [ ] **Step 4: Jalankan** — lulus.
- [ ] **Step 5: Commit** — `feat(shell): add app navigator and back handling`

### Task 4: Navigasi mengambang, mini player, dan pemindahan isi ke tab

**Files:**
- Create: `composeApp/src/commonMain/kotlin/senada/app/shell/SenadaIcons.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/shell/FloatingNavBar.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/shell/MiniPlayer.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/shell/AppShell.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/tabs/HomeTab.kt`, `SearchTab.kt`, `LibraryTab.kt`, `SettingsTab.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/nowplaying/NowPlayingScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/DeveloperSection.kt`
- Modify: `composeApp/src/commonMain/kotlin/senada/app/SenadaApp.kt` (tinggal tema, image loader, `LifecycleStartEffect`, dan `AppShell`)
- Modify: `composeApp/src/commonMain/kotlin/senada/app/SampleAudio.kt` (`SampleAudioControls` tanpa tombol Putar lokal/HTTPS)
- Modify: `docs/setup.md` (nama tab dan lokasi tombol)
- Test: `composeApp/src/commonTest/kotlin/senada/app/shell/MiniPlayerTest.kt`

**Interfaces:**
- Consumes: Task 3 (`AppNavigator`, `AppDestination`, `PlatformBackHandler`)
- Produces: `internal object SenadaIcons` (ImageVector garis 24 dp: `Home`, `Search`, `Library`, `Settings`, `Play`, `Pause`, `Next`, `Previous`, `Shuffle`, `Repeat`, `RepeatOne`, `Heart`, `HeartFilled`, `ChevronDown`, `More`, `Lyrics`, `Queue`, `Close`)
- Produces: `internal fun showsMiniPlayer(state: PlaybackState): Boolean` = antrean tidak kosong atau ada pilihan audio
- Produces: `@Composable internal fun MiniPlayer(state: PlaybackState, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit)` — pil radius 28 dp; seluruh pil bisa diketuk dengan `contentDescription = "Buka Sedang diputar"`; tombol `contentDescription` "Putar"/"Jeda" dan "Berikutnya"; Putar memanggil `playback.resume()`
- Produces: `@Composable internal fun FloatingNavBar(current: AppDestination, onSelect: (AppDestination) -> Unit)` — pil tinggi 64 dp, radius 32 dp, tiap tujuan 48 dp dengan `contentDescription = destination.label` dan `selected` di semantics
- Produces: `@Composable internal fun AppShell(playback: PlaybackController, library: LibraryRepository)` — isi tab digulir dengan jarak bawah 168 dp saat mini player tampil, 96 dp bila tidak; `PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }`
- Produces: `@Composable internal fun NowPlayingScreen(state: PlaybackState, playback: PlaybackController, library: LibraryRepository, onClose: () -> Unit)` — untuk fase ini berisi kontrol lama (`SampleAudioControls` tanpa tombol sampel), panel lirik, dan daftar antrean; tombol tutup `contentDescription = "Tutup"`
- Produces: `@Composable internal fun DeveloperSection(playback: PlaybackController)` — judul "Pengembang", tombol teks "Putar lokal" dan "Putar HTTPS", status pemutaran sampel
- Isi tab sementara: **Cari** = kolom cari, riwayat pencarian, impor file audio, hasil (dengan tombol "Tambah"); **Beranda** = daftar Favorit dan Riwayat lama; **Library** = sub-tab Favorit/Playlist/Riwayat lama; **Pengaturan** = bagian yang ada + `DeveloperSection` di paling bawah.

- [ ] **Step 1: Tulis tes yang gagal** — `MiniPlayerTest`:
  - `hiddenWithEmptyQueueAndNothingSelected`: `PlaybackState()` → false
  - `shownWhenQueueHasSongsButNothingIsSelected`: antrean satu entri, `audio.selection == null` → true
  - `shownForTheSampleButtons`: antrean kosong, `audio.selection` sampel → true
- [ ] **Step 2: Jalankan** — gagal.
- [ ] **Step 3: Implementasikan** ikon, `FloatingNavBar`, `MiniPlayer`, `AppShell`, keempat tab (memindahkan kode yang ada tanpa mengubah perilakunya), `NowPlayingScreen` sementara, `DeveloperSection`, dan `SenadaApp` yang menipis.
- [ ] **Step 4: Jalankan** — Mobile checks lulus.
- [ ] **Step 5: Commit** — `feat(shell): floating navigation, mini player and tab screens`

### Task 5: Uji emulator mengikuti kerangka baru

**Files:**
- Modify: `.github/scripts/android_background_test.py`

**Interfaces:**
- Consumes: label dari Task 4 — tab "Beranda"/"Cari"/"Library"/"Pengaturan" (content-desc), mini player "Buka Sedang diputar", "Putar"/"Jeda"/"Berikutnya" (content-desc), "Tutup", "Tambah" dan "Putar HTTPS" (teks), slider "Posisi pemutaran"

- [ ] **Step 1: Ubah alur** — buka aplikasi → ketuk tab `Cari` (content-desc) → "Tambah" ×2 → ketuk `Putar` di mini player (content-desc) → sesi PLAYING lagu pertama → buka Sedang diputar lewat "Buka Sedang diputar" sebelum pemeriksaan Jeda/Putar, slider, Berikutnya/Sebelumnya (tombol di Sedang diputar boleh dicari lewat teks atau content-desc) → "Tutup" → HOME + layar mati dan seterusnya seperti sekarang → buka lagi aplikasi, ketuk tab `Pengaturan`, gulir ke "Putar HTTPS". Tambahkan pemeriksaan: "Tombol kembali menutup Sedang diputar" (`input keyevent KEYCODE_BACK` saat terbuka → aplikasi tetap di depan dan mini player terlihat).
- [ ] **Step 2: Verifikasi** — `gh workflow run android-emulator.yml --ref feat/app-shell` lulus dengan semua pemeriksaan lama ditambah pemeriksaan kembali.
- [ ] **Step 3: Commit dan PR** — `test(emulator): follow the new app shell`; PR "Kerangka navigasi" mencantumkan Mobile checks dan run emulator.

---

## Fase 3 — Sedang diputar gaya A (PR 3, cabang `feat/now-playing`)

### Task 6: Layar Sedang diputar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/senada/app/nowplaying/NowPlayingScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/nowplaying/NowPlayingTheme.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/nowplaying/PlaySource.kt`
- Modify: `composeApp/src/commonMain/kotlin/senada/app/LibraryViews.kt` (`LibraryActions.playAllFavorites/playAllOf` mengisi sumber)
- Modify: `composeApp/src/commonMain/kotlin/senada/lyrics/LyricsPanel.kt` (gaya kartu dan baris aktif tebal; perilaku tetap)
- Modify: `.github/scripts/android_background_test.py` bila label berubah
- Test: `composeApp/src/commonTest/kotlin/senada/app/nowplaying/NowPlayingTest.kt`

**Interfaces:**
- Produces: `internal sealed interface PlaySource { data object Favorites; data class Playlist(val name: String); data object MostPlayed }` dan `internal fun PlaySource?.label(): String?` → "Dari Favorit", "Dari playlist <nama>", "Dari Paling sering diputar", null
- Produces: `internal object PlaySourceState { val current: MutableStateFlow<PlaySource?> }` — diisi oleh `LibraryActions.playAll*`; dikosongkan saat antrean menjadi kosong
- Produces: `internal fun nowPlayingColorScheme(accent: Color?): ColorScheme` = `senadaColorScheme(dark = true, accent)` apa pun tema aplikasi
- Tata letak (dari mockup A): bar atas (Tutup, label sumber, menu ⋯), sampul besar radius 20 dp, judul + artis + tombol favorit, slider "Posisi pemutaran" tipis, baris kontrol Acak (`contentDescription` "Acak: mati/nyala") – Sebelumnya – Putar/Jeda bulat putih 76 dp – Berikutnya – Ulangi ("Ulangi: mati/semua/satu"), kartu bawah dengan pilihan **Lirik** dan **Antrean**; latar gradien dari aksen sampul ke `#0B0C0E`.

- [ ] **Step 1: Tulis tes yang gagal** — `NowPlayingTest`:
  - `sourceLabels`: `PlaySource.Favorites.label() == "Dari Favorit"`, `PlaySource.Playlist("Senja").label() == "Dari playlist Senja"`, `null.label() == null`
  - `alwaysDarkEvenInLightTheme`: `nowPlayingColorScheme(null).background == Color(0xFF0B0C0E)`
  - `coverAccentStaysReadableOnDark`: aksen `Color(0xFF1A1A40)` → `contrastRatio(primary, background) >= 4.5f`
- [ ] **Step 2: Jalankan** — gagal.
- [ ] **Step 3: Implementasikan** layar, `PlaySource`, `nowPlayingColorScheme`, gaya lirik; Sedang diputar membungkus isinya dengan `MaterialTheme(colorScheme = nowPlayingColorScheme(accent))`.
- [ ] **Step 4: Verifikasi** — Mobile checks dan run emulator lulus.
- [ ] **Step 5: Commit dan PR** — `feat(now-playing): immersive now playing screen`

---

## Fase 4 — Hitungan putar dan Beranda gaya B (PR 4, cabang `feat/home`)

### Task 7: Hitungan putar 30 hari

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/senada/db/Library.sq` (tabel, indeks, query, dan `deleteUnusedTracks` juga mempertahankan lagu yang dirujuk `play_event`)
- Create: `composeApp/src/commonMain/sqldelight/senada/db/3.sqm` (versi 3 → 4: tabel dan indeks yang sama)
- Create: `composeApp/src/commonMain/kotlin/senada/library/PlayStats.kt`
- Modify: `composeApp/src/commonMain/kotlin/senada/library/HistoryRecorder.kt` (juga memanggil `recordTrackPlay`)
- Modify: `composeApp/src/commonMain/kotlin/senada/app/LibraryViews.kt` (`playAllOf` memanggil `recordPlaylistPlay` bila playlist tidak kosong)
- Modify: `composeApp/src/commonTest/kotlin/senada/library/SearchHistoryTest.kt` dan `LibraryPagingTest.kt` bila asersi versi skema berubah
- Test: `composeApp/src/commonTest/kotlin/senada/library/PlayStatsTest.kt`

**Interfaces:**
- Produces (SQL): `play_event(track_id TEXT NOT NULL, played_at INTEGER NOT NULL)` dan `playlist_play(playlist_id TEXT NOT NULL, played_at INTEGER NOT NULL)`, masing-masing berindeks pada `played_at`
- Produces: `internal const val PLAY_STATS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000`, `internal const val HERO_PLAYLIST_MIN_PLAYS = 3`
- Produces: `internal sealed interface HomeHero { data class FromPlaylist(val summary: PlaylistSummary, val artworkUrl: String?, val plays: Int); data class FromTrack(val track: Track, val plays: Int); data object Welcome }`
- Produces di `LibraryRepository` (ekstensi di `PlayStats.kt`): `suspend fun recordTrackPlay(track: Track)`, `suspend fun recordPlaylistPlay(playlistId: String)`, `fun homeHero(): Flow<HomeHero>`, `fun mostPlayed(limit: Int = 20): Flow<List<Track>>`. Jendela waktu dihitung dari `now()` milik repository; setiap pencatatan juga menghapus kejadian lebih tua dari 30 hari. Seri diputus oleh waktu putar terakhir yang lebih baru. Hitungan tidak ikut backup.

- [ ] **Step 1: Tulis tes yang gagal** — `PlayStatsTest` (jam tes terkendali):
  - `heroWelcomesBeforeAnyPlays`: `homeHero().first() == HomeHero.Welcome`
  - `heroShowsMostPlayedTrack`: lagu A diputar 3×, B 1× → `FromTrack(A, 3)`
  - `tiesGoToTheMoreRecentPlay`: A dan B masing-masing 2×, B terakhir → `FromTrack(B, 2)`
  - `aPlaylistPlayedThreeTimesWins`: playlist P (lagu pertama punya sampul) dipilih 3×, lagu A 10× → `FromPlaylist(P, artworkUrl = sampul lagu pertama, plays = 3)`
  - `twoPlaylistPlaysAreNotEnough`: P 2× → hero lagu teratas
  - `oldPlaysFallOutOfTheWindow`: A 5× pada hari 0, B 1× pada hari 31 → `FromTrack(B, 1)`; kejadian A terhapus
  - `deletedPlaylistFallsBack`: P 3× lalu `deletePlaylist(P)` → hero lagu teratas atau Welcome
  - `emptyPlaylistIsNotCounted`: `playAllOf` untuk playlist kosong tidak menambah `playlist_play` (uji lewat `recordPlaylistPlay` yang menolak playlist tanpa entri)
  - `mostPlayedListsTopTracks`: urutan menurun berdasarkan hitungan, maksimal `limit`
  - `countedSongsSurviveCleanup`: lagu dengan `play_event` dalam jendela tidak terhapus oleh `deleteUnusedTracks`
  - `aVersionThreeDatabaseGainsPlayStats`: buang kedua tabel, `Schema.migrate(driver, 3, 4)`, catat satu putaran → hero lagu itu
- [ ] **Step 2: Jalankan** — gagal.
- [ ] **Step 3: Implementasikan** tabel, migrasi, query, fungsi repository, dan pemanggilan dari `HistoryRecorder` serta `playAllOf`.
- [ ] **Step 4: Jalankan** — Mobile checks lulus.
- [ ] **Step 5: Commit** — `feat(library): count plays over the last 30 days`

### Task 8: Beranda gaya B

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/senada/app/tabs/HomeTab.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/tabs/HomeHeroCard.kt`
- Create: `composeApp/src/commonMain/kotlin/senada/app/tabs/MostPlayedScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/senada/app/shell/AppShell.kt` (rute `mostPlayedOpen`)
- Modify: `docs/setup.md`
- Test: `composeApp/src/commonTest/kotlin/senada/app/tabs/HomeHeroTest.kt`

**Interfaces:**
- Consumes: Task 7 (`HomeHero`, `homeHero()`, `mostPlayed()`), Task 3 (`openPlaylist`, `openMostPlayed`), Task 6 (`PlaySource.MostPlayed`)
- Produces: `internal data class HeroCopy(val reason: String, val title: String, val primaryAction: String, val secondaryAction: String?)` dan `internal fun heroCopy(hero: HomeHero): HeroCopy`:
  - `FromPlaylist` → reason "Playlist yang paling sering kamu pilih bulan ini", title = nama playlist, aksi "Putar" / "Lihat"
  - `FromTrack` → reason "Paling sering kamu putar bulan ini", title = judul lagu, aksi "Putar" / "Lihat"
  - `Welcome` → reason "Selamat datang di Senada", title "Mulai dengarkan", aksi "Cari lagu" / null
- Tata letak (dari mockup B): hero tinggi ±420 dp dengan latar dari aksen sampul isi hero dan judul display 800; deret "Baru diputar" (kartu 150 dp radius 24, kartu Favorit di depan) dari `history()`; deret "Playlist kamu" dari `playlists`. "Lihat" pada lagu membuka `MostPlayedScreen` (daftar + "Putar semua" dengan `PlaySource.MostPlayed`).

- [ ] **Step 1: Tulis tes yang gagal** — `HomeHeroTest`: tiga kasus `heroCopy` dengan teks persis di atas.
- [ ] **Step 2: Jalankan** — gagal.
- [ ] **Step 3: Implementasikan** `heroCopy`, `HomeHeroCard`, `HomeTab`, `MostPlayedScreen`, dan rutenya.
- [ ] **Step 4: Verifikasi** — Mobile checks dan run emulator lulus (Beranda tidak diketuk oleh skrip; pastikan tab Cari tetap dibuka lewat content-desc).
- [ ] **Step 5: Commit dan PR** — `feat(home): hero from your own listening`

---

## Fase 5 — Cari, Library, Pengaturan (PR 5, cabang `feat/tabs-restyle`)

Ketiga task ini menyentuh file berbeda dan boleh dikerjakan paralel setelah Fase 2 masuk.

### Task 9: Cari

**Files:** Modify `composeApp/src/commonMain/kotlin/senada/app/tabs/SearchTab.kt`, `SearchHistoryViews.kt`, `ImportAudioViews.kt`

- [ ] **Step 1: Terapkan gaya**: kolom cari pil tinggi 52 dp, riwayat sebagai baris dengan ikon jam, tombol impor sebagai baris aksi, hasil memakai baris lagu baru (sampul 48 dp radius 8, judul/artis/durasi, "Tambah", menu ⋯). Teks "Tambah" tetap.
- [ ] **Step 2: Verifikasi** — Mobile checks dan run emulator lulus.
- [ ] **Step 3: Commit** — `feat(search): restyle the search tab`

### Task 10: Library dengan chip

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/senada/app/tabs/LibraryTab.kt`, `LibraryViews.kt`
- Modify: `composeApp/src/commonMain/sqldelight/senada/db/Library.sq` (query `localTracks`: lagu `source = 'local'` di tabel `track`)
- Test: `composeApp/src/commonTest/kotlin/senada/library/LibraryRepositoryTest.kt` (tambahan)

**Interfaces:**
- Produces: `internal enum class LibraryFilter(val label: String) { Favorites("Favorit"), Playlists("Playlist"), History("Riwayat"), Imported("File impor") }`; `fun LibraryRepository.localTracks(): Flow<List<Track>>`
- Chip filter (gaya C) menggantikan sub-tab; favorit dan playlist tetap per halaman 50; "File impor" kosong menampilkan "Belum ada file impor. Impor dari tab Cari."

- [ ] **Step 1: Tulis tes yang gagal** — `localTracksListsOnlyImportedSongs`: favorit satu lagu `ytmusic` dan satu lagu `local` → `localTracks()` hanya lagu lokal.
- [ ] **Step 2: Jalankan** — gagal; **Step 3:** implementasikan query, filter, dan tampilan; **Step 4:** Mobile checks dan run emulator lulus.
- [ ] **Step 5: Commit** — `feat(library): one library tab with filter chips`

### Task 11: Pengaturan

**Files:** Modify `composeApp/src/commonMain/kotlin/senada/app/tabs/SettingsTab.kt`, `AppearanceViews.kt`, `DataSaver.kt`, `SettingsViews.kt`, `StorageViews.kt`, `DeveloperSection.kt`

- [ ] **Step 1: Terapkan gaya**: setiap bagian (Tampilan, Data, Backup library, Penyimpanan, Pengembang) dalam kartu permukaan radius 24 dp dengan judul bagian; perilaku dan teks tombol tetap ("Putar HTTPS" tetap teks).
- [ ] **Step 2: Verifikasi** — Mobile checks dan run emulator lulus.
- [ ] **Step 3: Commit dan PR** — `feat(settings): restyle settings`; PR Fase 5 mencantumkan semua run.

---

## Setelah Fase 5

- [ ] Build IPA dari `main` (`gh workflow run ios-device.yml --ref main`), taruh di `Downloads/Senada-iPhone-<sha>/`, dan minta sesi uji iPhone: tema gelap/terang, aksen sampul, mini player di semua tab, buka/tutup Sedang diputar (tombol dan usap), lirik dan antrean, hero (sambutan, lagu, playlist), Library chip, teks besar, VoiceOver dasar.
