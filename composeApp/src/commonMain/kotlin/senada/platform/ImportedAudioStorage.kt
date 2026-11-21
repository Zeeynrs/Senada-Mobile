package senada.platform

import coil3.PlatformContext
import senada.local.ImportedAudioStorage

internal expect fun importedAudioStorage(context: PlatformContext): ImportedAudioStorage
