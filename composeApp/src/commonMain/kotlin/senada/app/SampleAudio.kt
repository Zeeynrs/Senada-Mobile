package senada.app

import org.jetbrains.compose.resources.ExperimentalResourceApi
import senada.catalog.DemoCatalog
import senada.model.Track
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.resources.Res

/*
 * Audio contoh untuk build debug: tes emulator dan tombol uji di bagian Pengembang memakainya.
 * Build rilis tidak menampilkan lagu contoh maupun tombolnya, lihat [DemoCatalog.tracks].
 */

private const val HTTPS_SAMPLE = "https://raw.githubusercontent.com/Zeeynrs/Senada-Mobile/" +
    "9968da3556233a78d5812564d977954d010c1988/" +
    "composeApp/src/commonMain/composeResources/files/selepas-hujan.m4a"

internal fun sampleAudioSelection(source: SampleAudioSource): SampleAudioSelection {
    val track = DemoCatalog.debugTracks.first()
    return when (source) {
        SampleAudioSource.Local, SampleAudioSource.Offline -> demoAudioSelection(track)
        // Tombol uji hanya memakai file lokal dan satu URL HTTPS tetap.
        SampleAudioSource.Https, SampleAudioSource.YouTubeMusic ->
            SampleAudioSelection(track.title, SampleAudioSource.Https, HTTPS_SAMPLE)
    }
}

/**
 * File contoh untuk entri antrean lagu contoh. Entri lama dari versi sebelumnya bisa masih ada di
 * antrean, favorit atau riwayat pengguna, jadi file ini tetap bisa diputar di build rilis.
 */
@OptIn(ExperimentalResourceApi::class)
internal fun demoAudioSelection(track: Track, queueEntryId: Long? = null): SampleAudioSelection {
    val path = checkNotNull(DemoCatalog.localAudioPath(track.id))
    return SampleAudioSelection(track.title, SampleAudioSource.Local, Res.getUri(path), queueEntryId)
}
