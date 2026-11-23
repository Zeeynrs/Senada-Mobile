package senada.app.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import senada.app.ArtworkAccentCache
import senada.app.LocalArtworkAccent
import senada.app.loadArtworkAccent
import senada.app.minTouchTarget
import senada.app.shell.GlassSurface
import senada.app.shell.LocalGlassState
import senada.app.shell.ProfileAvatar
import senada.app.shell.SenadaIcons
import senada.app.theme.SenadaPalette
import senada.catalog.youtube.resizeGoogleArtwork
import senada.library.HomeHero

internal const val HERO_REF_WIDTH_DP = 390f
internal const val HERO_REF_HEIGHT_DP = 440f
internal const val HERO_REF_SCREEN_HEIGHT_DP = 844f
internal const val HERO_MAX_HEIGHT_DP = 560f
internal const val HERO_MIN_HEIGHT_DP = 300f
internal const val HERO_GRADIENT_HEIGHT_DP = 260
internal const val HERO_ARTWORK_SIZE_PX = 544
internal const val HERO_MINI_ARTWORK_SIZE_PX = 96
internal const val HOME_TOP_SCRIM_FADE_START_DP = 200f
internal const val HOME_TOP_SCRIM_FADE_END_DP = 280f
internal const val HOME_TOP_SCRIM_EXTRA_HEIGHT_DP = 24
internal const val HOME_TOP_SCRIM_MAX_ALPHA = 0.92f

private val HeroReasonDarkColor = Color(0xFFC9CBD0)
private val HeroFallbackSurfaceDark = Color(0xFF2B3440)
private val HeroMiniFallbackColor = Color(0xFF8E7356)

internal data class HeroCopy(
    val reason: String,
    val title: String,
    val primaryAction: String,
    val secondaryAction: String?,
    val artistName: String? = null,
)

internal fun heroCopy(hero: HomeHero): HeroCopy = when (hero) {
    is HomeHero.FromPlaylist -> HeroCopy(
        reason = "Playlist yang sering kamu putar",
        title = hero.summary.name,
        primaryAction = "Putar",
        secondaryAction = "Lihat",
    )
    is HomeHero.FromTrack -> HeroCopy(
        reason = "Karena kamu sering memutar",
        title = hero.track.title,
        primaryAction = "Putar",
        secondaryAction = "Lihat",
        artistName = hero.track.artists.filter { it.isNotBlank() }.joinToString().ifEmpty { null },
    )
    HomeHero.Welcome -> HeroCopy(
        reason = "Selamat datang di Senada",
        title = "Mulai dengarkan",
        primaryAction = "Cari lagu",
        secondaryAction = null,
    )
}

/**
 * Tinggi hero penuh lebar: 440 dp pada layar acuan 390×844 dp (~52% tinggi layar),
 * diskalakan mengikuti lebar dan tinggi layar lain, dibatasi maksimal 560 dp.
 */
internal fun heroHeightDp(screenWidthDp: Float, screenHeightDp: Float): Float {
    val widthScaled = if (screenWidthDp > 0f) {
        HERO_REF_HEIGHT_DP * (screenWidthDp / HERO_REF_WIDTH_DP)
    } else {
        HERO_REF_HEIGHT_DP
    }
    val heightScaled = if (screenHeightDp > 0f) {
        screenHeightDp * (HERO_REF_HEIGHT_DP / HERO_REF_SCREEN_HEIGHT_DP)
    } else {
        widthScaled
    }
    return minOf(widthScaled, heightScaled).coerceIn(HERO_MIN_HEIGHT_DP, HERO_MAX_HEIGHT_DP)
}

/**
 * Progres kemunculan scrim atas Beranda (0f..1f): tetap 0 saat di posisi paling atas hingga
 * offset gulir 200 dp agar tampilan hero persis mockup, lalu memudar naik sampai 1f di 280 dp
 * (atau saat item hero sudah lewat) agar jam dan ikon status bar tetap terbaca.
 */
internal fun homeTopScrimProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffsetDp: Float,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (firstVisibleItemScrollOffsetDp <= HOME_TOP_SCRIM_FADE_START_DP) return 0f
    val span = (HOME_TOP_SCRIM_FADE_END_DP - HOME_TOP_SCRIM_FADE_START_DP).coerceAtLeast(1f)
    return ((firstVisibleItemScrollOffsetDp - HOME_TOP_SCRIM_FADE_START_DP) / span).coerceIn(0f, 1f)
}

/**
 * Scrim tetap di tepi atas Beranda setinggi inset status bar + 24 dp: gradasi vertikal dari
 * warna latar tema (alpha 0.92) ke transparan, hanya muncul setelah hero mulai tergulir.
 */
@Composable
internal fun HomeTopScrim(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val totalHeight = safeTop + HOME_TOP_SCRIM_EXTRA_HEIGHT_DP.dp
    val holdFraction = if (totalHeight > 0.dp) (safeTop / totalHeight).coerceIn(0f, 0.85f) else 0f
    val background = MaterialTheme.colorScheme.background
    val brush = remember(background, holdFraction) {
        Brush.verticalGradient(
            0f to background.copy(alpha = HOME_TOP_SCRIM_MAX_ALPHA),
            holdFraction to background.copy(alpha = HOME_TOP_SCRIM_MAX_ALPHA),
            1f to background.copy(alpha = 0f),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .graphicsLayer { alpha = progress() }
            .background(brush),
    )
}

private val heroArtworkColors = ArtworkAccentCache()

/** Ikon segitiga Putar 14×14 persis SVG mockup Main.dc.html. */
private val HeroPlayTriangle: ImageVector =
    ImageVector.Builder("HeroPlayTriangle", 14.dp, 14.dp, 14f, 14f).apply {
        addPath(
            pathData = addPathNodes("M3 1.5v11l9-5.5z"),
            fill = SolidColor(Color.Black),
        )
    }.build()

/**
 * Hero Beranda v2 penuh lebar sampai tepi atas layar: latar sampul (atau blok warna ArtworkAccent)
 * dengan gradasi 260 dp ke latar halaman, bar atas (logo Senada + avatar Profil), teks alasan,
 * judul display 32 sp, dan tombol kaca Putar / Lihat (atau Cari lagu pada sambutan).
 */
@Composable
internal fun HomeHeroCard(
    hero: HomeHero,
    screenVisible: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    profileName: String = "",
    profilePhoto: String? = null,
    onOpenProfile: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val copy = heroCopy(hero)
    val artworkUrl = when (hero) {
        is HomeHero.FromPlaylist -> hero.artworkUrl
        is HomeHero.FromTrack -> hero.track.artworkUrl
        HomeHero.Welcome -> null
    }
    val context = LocalPlatformContext.current
    var artworkAccent by remember(artworkUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artworkUrl, screenVisible, context) {
        if (screenVisible && !artworkUrl.isNullOrBlank()) {
            artworkAccent = heroArtworkColors.get(artworkUrl) {
                loadArtworkAccent(context, resizeGoogleArtwork(artworkUrl, HERO_MINI_ARTWORK_SIZE_PX))
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.background.luminance() < 0.5f
    val fallbackAccent = artworkAccent ?: LocalArtworkAccent.current ?: colors.primary
    val baseColor = if (darkTheme) {
        fallbackAccent.copy(alpha = 0.28f).compositeOver(HeroFallbackSurfaceDark)
    } else {
        fallbackAccent.copy(alpha = 0.24f).compositeOver(colors.surfaceContainerHigh)
    }
    val fadeTo = colors.background
    val bottomGradient = remember(fadeTo) {
        Brush.verticalGradient(
            0.0f to fadeTo.copy(alpha = 0f),
            0.92f to fadeTo,
            1.0f to fadeTo,
        )
    }
    val density = LocalDensity.current
    val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val heroHeight = remember(screenWidthDp, screenHeightDp) {
        heroHeightDp(screenWidthDp, screenHeightDp).dp
    }
    val largeFont = density.fontScale >= 1.5f
    val heroGlassState = remember { HazeState() }

    val topBarTextColor = if (darkTheme) SenadaPalette.DarkText else colors.onSurface
    val topBarWaveColor = if (darkTheme) SenadaPalette.AccentDark else colors.primary
    val reasonColor = if (darkTheme) HeroReasonDarkColor else colors.onSurfaceVariant
    val titleColor = if (darkTheme) SenadaPalette.DarkText else colors.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = heroHeight),
    ) {
        // Latar penuh lebar + gradasi bawah sebagai sumber blur untuk tombol kaca di atasnya.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(baseColor)
                .hazeSource(heroGlassState),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = remember(artworkUrl) { resizeGoogleArtwork(artworkUrl, HERO_ARTWORK_SIZE_PX) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(HERO_GRADIENT_HEIGHT_DP.dp)
                    .background(bottomGradient),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = heroHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeTopBar(
                safeTop = safeTop,
                profileName = profileName,
                profilePhoto = profilePhoto,
                darkTheme = darkTheme,
                textColor = topBarTextColor,
                waveColor = topBarWaveColor,
                onOpenProfile = onOpenProfile,
            )

            Spacer(Modifier.height(24.dp))

            // Teks hero di bagian bawah: pada 390×844 judul 1 baris berada tepat di top 318 dp.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            ) {
                HeroReasonRow(
                    copy = copy,
                    artworkUrl = artworkUrl,
                    artworkAccent = artworkAccent,
                    reasonColor = reasonColor,
                    artistColor = titleColor,
                    largeFont = largeFont,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = copy.title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 32.sp,
                        lineHeight = 33.6.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.04).em,
                    ),
                    color = titleColor,
                    maxLines = if (largeFont) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                // Jarak visual 14 dp ke pil kaca (8 dp + 6 dp margin sentuh vertikal target 48 dp).
                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalGlassState provides heroGlassState) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        HeroGlassButton(
                            label = copy.primaryAction,
                            accessibilityLabel = if (hero == HomeHero.Welcome) copy.primaryAction else "Putar ${copy.title}",
                            horizontalPadding = 16.dp,
                            leadingIcon = if (hero == HomeHero.Welcome) SenadaIcons.Search else HeroPlayTriangle,
                            onClick = onPrimary,
                        )
                        copy.secondaryAction?.let { label ->
                            HeroGlassButton(
                                label = label,
                                accessibilityLabel = when (hero) {
                                    is HomeHero.FromPlaylist -> "Lihat playlist ${hero.summary.name}"
                                    is HomeHero.FromTrack -> "Lihat lagu yang paling sering diputar"
                                    HomeHero.Welcome -> label
                                },
                                horizontalPadding = 14.dp,
                                leadingIcon = null,
                                onClick = onSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    safeTop: Dp,
    profileName: String,
    profilePhoto: String?,
    darkTheme: Boolean,
    textColor: Color,
    waveColor: Color,
    onOpenProfile: () -> Unit,
) {
    // Posisi mockup top 52 dp dari tepi layar; melonggar mengikuti status bar jika inset lebih tinggi.
    val topBarVisualTop = maxOf(52.dp, safeTop)
    // Avatar visual 36 dp berada di tengah target sentuh 48 dp -> margin sentuh 6 dp di atas & kanan.
    val topPadding = (topBarVisualTop - 6.dp).coerceAtLeast(0.dp)
    val avatarBorder = if (darkTheme) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = topPadding, start = 20.dp, end = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SenadaLogoMark(
                frameColor = textColor,
                waveColor = waveColor,
            )
            Text(
                text = "Senada",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.02).em,
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .minTouchTarget()
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenProfile)
                .semantics { contentDescription = "Buka profil" },
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                name = profileName,
                size = 36.dp,
                borderColor = avatarBorder,
                photoModel = profilePhoto,
            )
        }
    }
}

/** Logo Senada 28 dp persis SVG mockup: kotak bulat bergaris (rx=9) dan gelombang teal. */
@Composable
private fun SenadaLogoMark(
    frameColor: Color,
    waveColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val s = size.width / 28f
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(1f * s, 1f * s),
            size = Size(26f * s, 26f * s),
            cornerRadius = CornerRadius(9f * s, 9f * s),
            style = Stroke(width = 2f * s),
        )
        // SVG: M8 17c2-6 4 2 6-3s4 1 6-2
        val wave = Path().apply {
            moveTo(8f * s, 17f * s)
            cubicTo(10f * s, 11f * s, 12f * s, 19f * s, 14f * s, 14f * s)
            cubicTo(16f * s, 9f * s, 18f * s, 15f * s, 20f * s, 12f * s)
        }
        drawPath(
            path = wave,
            color = waveColor,
            style = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun HeroReasonRow(
    copy: HeroCopy,
    artworkUrl: String?,
    artworkAccent: Color?,
    reasonColor: Color,
    artistColor: Color,
    largeFont: Boolean,
) {
    val reasonStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val artist = copy.artistName
    if (artist == null) {
        Text(
            text = copy.reason,
            style = reasonStyle,
            color = reasonColor,
            maxLines = if (largeFont) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = copy.reason,
            style = reasonStyle,
            color = reasonColor,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(artworkAccent ?: HeroMiniFallbackColor),
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = remember(artworkUrl) { resizeGoogleArtwork(artworkUrl, HERO_MINI_ARTWORK_SIZE_PX) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Text(
                text = artist,
                style = reasonStyle,
                color = artistColor,
                maxLines = if (largeFont) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroGlassButton(
    label: String,
    accessibilityLabel: String,
    horizontalPadding: Dp,
    leadingIcon: ImageVector?,
    onClick: () -> Unit,
) {
    val pillShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .minTouchTarget()
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(shape = pillShape) {
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}
