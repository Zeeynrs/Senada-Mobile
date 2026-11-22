package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import senada.app.ArtworkAccentCache
import senada.app.accessibleAccent
import senada.app.loadArtworkAccent
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.catalog.youtube.resizeGoogleArtwork
import senada.library.HomeHero

internal data class HeroCopy(
    val reason: String,
    val title: String,
    val primaryAction: String,
    val secondaryAction: String?,
)

internal fun heroCopy(hero: HomeHero): HeroCopy = when (hero) {
    is HomeHero.FromPlaylist -> HeroCopy(
        "Playlist yang paling sering kamu pilih bulan ini",
        hero.summary.name,
        "Putar",
        "Lihat",
    )
    is HomeHero.FromTrack -> HeroCopy("Paling sering kamu putar bulan ini", hero.track.title, "Putar", "Lihat")
    HomeHero.Welcome -> HeroCopy("Selamat datang di Senada", "Mulai dengarkan", "Cari lagu", null)
}

private val heroArtworkColors = ArtworkAccentCache()

/**
 * Kartu hero ringkas di bagian atas Beranda: gradien aksen lembut, sampul asli lagu/playlist atau
 * tanda nada Senada untuk sambutan, judul skala headline, dan tombol aksi utama/sekunder.
 */
@Composable
internal fun HomeHeroCard(
    hero: HomeHero,
    screenVisible: Boolean,
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
                loadArtworkAccent(context, resizeGoogleArtwork(artworkUrl, 96))
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val background = colors.surfaceContainerHigh
    val textColor = colors.onSurface
    val mutedTextColor = colors.onSurfaceVariant
    val tint = (artworkAccent ?: colors.primary).copy(alpha = 0.28f).compositeOver(background)
    // Pastikan teks alasan (onSurfaceVariant) dan judul (onSurface) sama-sama memenuhi kontras AA.
    val gradient = Brush.verticalGradient(listOf(accessibleAccent(tint, mutedTextColor), background))
    val stacked = LocalDensity.current.fontScale >= 1.5f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        contentColor = textColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(gradient).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stacked) {
                HeroVisual(hero = hero, background = background)
                HeroTextBlock(copy = copy, textColor = textColor, mutedTextColor = mutedTextColor, stacked = true)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroVisual(hero = hero, background = background)
                    HeroTextBlock(
                        copy = copy,
                        textColor = textColor,
                        mutedTextColor = mutedTextColor,
                        stacked = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.minTouchTarget().semantics {
                        contentDescription = if (hero == HomeHero.Welcome) copy.primaryAction else "Putar ${copy.title}"
                    },
                ) {
                    Icon(
                        imageVector = if (hero == HomeHero.Welcome) SenadaIcons.Search else SenadaIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(copy.primaryAction, modifier = Modifier.clearAndSetSemantics {})
                }
                copy.secondaryAction?.let { label ->
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, textColor.copy(alpha = 0.45f)),
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = when (hero) {
                                is HomeHero.FromPlaylist -> "Lihat playlist ${hero.summary.name}"
                                is HomeHero.FromTrack -> "Lihat lagu yang paling sering diputar"
                                HomeHero.Welcome -> label
                            }
                        },
                    ) {
                        Text(label, color = textColor, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroVisual(hero: HomeHero, background: Color) {
    when (hero) {
        HomeHero.Welcome -> {
            val badgeBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f).compositeOver(background)
            val iconTint = accessibleAccent(MaterialTheme.colorScheme.primary, badgeBackground)
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(badgeBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SenadaIcons.MusicNote,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        is HomeHero.FromTrack -> {
            HomeCover(
                modifier = Modifier.size(76.dp).clip(MaterialTheme.shapes.small),
                icon = SenadaIcons.MusicNote,
                artworkUrl = hero.track.artworkUrl,
                imageSize = 256,
                iconSize = 28.dp,
            )
        }
        is HomeHero.FromPlaylist -> {
            HomeCover(
                modifier = Modifier.size(76.dp).clip(MaterialTheme.shapes.small),
                icon = SenadaIcons.Library,
                artworkUrl = hero.artworkUrl,
                imageSize = 256,
                iconSize = 28.dp,
            )
        }
    }
}

@Composable
private fun HeroTextBlock(
    copy: HeroCopy,
    textColor: Color,
    mutedTextColor: Color,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    val maxLines = if (stacked) Int.MAX_VALUE else 2
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = copy.reason,
            style = MaterialTheme.typography.labelMedium,
            color = mutedTextColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = copy.title,
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}
