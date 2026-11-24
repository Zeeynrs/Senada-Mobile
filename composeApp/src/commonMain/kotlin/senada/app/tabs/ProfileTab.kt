package senada.app.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import senada.app.SenadaCard
import senada.app.UserProfileController
import senada.app.shell.ProfileRoute
import senada.app.shell.SenadaIcons
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaPalette
import senada.app.versionName
import senada.catalog.ArtistResolveResult
import senada.library.ListeningSummary

private data class ProfileSetting(val route: ProfileRoute, val subtitle: String, val icon: ImageVector)

private val groups = listOf(
    listOf(
        ProfileSetting(ProfileRoute.AudioLab, "Equalizer, efek suara, kecepatan & nada", SenadaIcons.AudioLab),
        ProfileSetting(ProfileRoute.Appearance, "Tema, aksen, sudut sampul, efek kaca", SenadaIcons.Appearance),
        ProfileSetting(ProfileRoute.Playback, "Kualitas audio, hemat data, timer tidur", SenadaIcons.PlayOutline),
    ),
    listOf(
        ProfileSetting(ProfileRoute.Backup, "Ekspor & impor, cocok dengan Senada desktop", SenadaIcons.Backup),
        ProfileSetting(ProfileRoute.Storage, "Cache, audio impor", SenadaIcons.Storage),
    ),
)

private val developerSetting = ProfileSetting(ProfileRoute.Developer, "Uji audio", SenadaIcons.Settings)
private val aboutSetting = ProfileSetting(ProfileRoute.About, "Versi & tentang", SenadaIcons.Person)

@Composable
internal fun ProfileTab(
    controller: UserProfileController,
    photo: Any?,
    summary: ListeningSummary?,
    oldestListening: Long?,
    artist: ArtistResolveResult.Found?,
    onOpen: (ProfileRoute) -> Unit,
    onOpenArtist: (ArtistResolveResult.Found) -> Unit,
    developerUnlocked: Boolean,
    onVersionTap: () -> Unit,
) {
    val profile by controller.state.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (profile.name.isEmpty()) "Halo!" else "Halo lagi, ${profile.name}!",
                modifier = Modifier.weight(1f).padding(end = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp, lineHeight = TextUnit.Unspecified, letterSpacing = (-0.03).em,
                ),
            )
            ProfileIconButton(SenadaIcons.ProfileSearch, "Cari pengaturan", colors.surfaceContainer, true) {
                if (searching) focus.clearFocus()
                searching = !searching
                query = ""
            }
        }
        if (searching) {
            OutlinedTextField(query, { query = it },
                Modifier.fillMaxWidth().semantics { contentDescription = "Kata kunci pengaturan" },
                label = { Text("Cari pengaturan") }, singleLine = true)
        } else {
            ListenerCard(profile, photo, oldestListening ?: profile.createdAt) { editing = true }
            ProfileListeningCard(summary) { onOpen(ProfileRoute.Stats) }
            ProfileRankings(summary, artist?.artworkUrl, onOpenArtist = {
                if (artist == null) onOpen(ProfileRoute.Stats) else onOpenArtist(artist)
            }, onOpenTrack = { onOpen(ProfileRoute.MostPlayed) })
        }
        Text("Pengaturan", modifier = Modifier.padding(start = 4.dp, top = 10.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 20.sp, lineHeight = TextUnit.Unspecified, letterSpacing = (-0.02).em,
            ),
        )
        val extra = buildList {
            if (searching) add(aboutSetting)
            if (developerUnlocked && searching) add(developerSetting)
        }
        val term = query.trim()
        val filtered = (groups + listOf(extra)).map { group ->
            group.filter { it.route.label.contains(term, ignoreCase = true) || it.subtitle.contains(term, ignoreCase = true) }
        }
        if (searching && filtered.all { it.isEmpty() }) {
            Text("Pengaturan tidak ditemukan.", color = colors.onSurfaceVariant)
        }
        for (rows in filtered) {
            if (rows.isNotEmpty()) {
                SenadaCard {
                    Column(Modifier.padding(1.dp)) {
                        rows.forEachIndexed { index, setting ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 70.dp), color = colors.outline)
                            SettingRow(setting) {
                                focus.clearFocus()
                                searching = false
                                query = ""
                                onOpen(setting.route)
                            }
                        }
                    }
                }
            }
        }
        if (!searching) {
            SenadaCard {
                Row(Modifier.fillMaxWidth().clickable(onClick = onVersionTap).padding(1.dp).padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MonoLabel("Versi & tentang")
                        Text(versionName(), style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 36.sp, lineHeight = TextUnit.Unspecified, letterSpacing = (-0.04).em))
                    }
                    TextButton(onClick = { onOpen(ProfileRoute.About) },
                        colors = ButtonDefaults.textButtonColors(containerColor = colors.surfaceContainerHighest,
                            contentColor = colors.onSurface),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text("Tentang Senada", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                    }
                }
            }
            if (developerUnlocked) {
                SenadaCard {
                    SettingRow(developerSetting) {
                        onOpen(ProfileRoute.Developer)
                    }
                }
            }
        }
    }
    if (editing) EditProfileSheet(controller, photo) { editing = false }
}

@Composable
private fun SettingRow(setting: ProfileSetting, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lab = setting.route == ProfileRoute.AudioLab
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).background(if (lab) Color(0xFF21413A) else colors.surfaceContainerHigh,
            RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(setting.icon, null, Modifier.size(20.dp), tint = if (lab) SenadaPalette.AccentDark else colors.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(setting.route.label, style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp, lineHeight = TextUnit.Unspecified, letterSpacing = 0.em))
            Text(setting.subtitle, style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = TextUnit.Unspecified, letterSpacing = 0.em, fontWeight = FontWeight.Medium),
                color = colors.onSurfaceVariant)
        }
        Icon(SenadaIcons.ProfileChevron, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
    }
}

internal fun LazyListScope.profileHeader(route: ProfileRoute, previousLabel: String, onBack: () -> Unit) {
    item(key = "profile-header") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            ProfileIconButton(SenadaIcons.ChevronLeft, "Kembali ke $previousLabel", MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = onBack)
            MonoLabel(previousLabel)
        }
    }
    item(key = "profile-title") {
        Text(route.label, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp).semantics { heading() },
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.05).em))
    }
}

@Composable
private fun ProfileIconButton(icon: ImageVector, description: String, background: Color,
                              outlined: Boolean = false, onClick: () -> Unit) {
    // Area sentuh Compose meluas sampai 48 dp di sekitar lingkaran 44 dp.
    Box(Modifier.size(44.dp).clip(CircleShape).background(background)
        .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier)
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(if (outlined) 20.dp else 18.dp))
    }
}
