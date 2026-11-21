package senada.app.tabs

import androidx.compose.foundation.lazy.LazyListScope
import senada.app.AppearancePreferences
import senada.app.AppearanceSection
import senada.app.BackupSection
import senada.app.DataSection
import senada.app.DeveloperSection
import senada.app.StorageSection
import senada.library.LibraryRepository
import senada.playback.PlaybackController

/** Tab Pengaturan: Tampilan, Data, Backup library, Penyimpanan, lalu Pengembang dalam kartu permukaan. */
internal fun LazyListScope.settingsTab(
    appearance: AppearancePreferences,
    library: LibraryRepository,
    playback: PlaybackController,
) {
    item(key = "appearance") { AppearanceSection(appearance) }
    item(key = "data") { DataSection(appearance) }
    item(key = "backup") { BackupSection(library) }
    item(key = "storage") { StorageSection(playback, library) }
    item(key = "developer") { DeveloperSection(playback) }
}
