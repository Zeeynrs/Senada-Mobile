package senada.app

import androidx.compose.runtime.Composable
import coil3.PlatformContext

internal const val ProfilePhotoSize = 512

internal fun interface ProfilePhotoPicker {
    /** Mengembalikan pratinjau JPEG yang sudah dipotong, atau null bila picker dibatalkan. */
    suspend fun pick(): ByteArray?
}

@Composable
internal expect fun rememberProfilePhotoPicker(): ProfilePhotoPicker

/** Path relatif terhadap direktori foto profil dalam penyimpanan privat aplikasi. */
internal expect fun saveProfilePhoto(context: PlatformContext, jpeg: ByteArray): String

internal expect fun deleteProfilePhoto(context: PlatformContext, path: String)

internal expect fun profilePhotoModel(context: PlatformContext, path: String): String
