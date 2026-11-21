package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

@Composable
internal fun HomeHeroCard(
    hero: HomeHero,
    screenVisible: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val copy = heroCopy(hero)
    val (artworkUrl, icon) = when (hero) {
        is HomeHero.FromPlaylist -> hero.artworkUrl to SenadaIcons.Library
        is HomeHero.FromTrack -> hero.track.artworkUrl to SenadaIcons.Queue
        HomeHero.Welcome -> null to SenadaIcons.Home
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
    val tint = (artworkAccent ?: colors.primary).copy(alpha = 0.42f).compositeOver(background)
    val gradient = Brush.verticalGradient(listOf(accessibleAccent(tint, textColor), background))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        contentColor = textColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp).background(gradient).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        ) {
            HomeCover(
                modifier = Modifier.align(Alignment.End).size(148.dp).clip(MaterialTheme.shapes.medium),
                icon = icon,
                artworkUrl = artworkUrl,
                imageSize = 448,
            )
            Text(copy.reason, style = MaterialTheme.typography.labelLarge)
            Text(
                copy.title,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.minTouchTarget().semantics {
                        contentDescription = if (hero == HomeHero.Welcome) copy.primaryAction else "Putar ${copy.title}"
                    },
                ) {
                    Text(copy.primaryAction, modifier = Modifier.clearAndSetSemantics {})
                }
                copy.secondaryAction?.let { label ->
                    OutlinedButton(
                        onClick = onSecondary,
                        border = BorderStroke(1.dp, textColor.copy(alpha = 0.6f)),
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
