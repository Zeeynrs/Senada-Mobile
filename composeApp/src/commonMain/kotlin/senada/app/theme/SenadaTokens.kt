package senada.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.jetbrains.compose.resources.Font
import senada.app.accessibleAccent
import senada.app.textOnAccent
import senada.resources.Res
import senada.resources.plus_jakarta_sans_bold
import senada.resources.plus_jakarta_sans_extrabold
import senada.resources.plus_jakarta_sans_medium
import senada.resources.plus_jakarta_sans_semibold

/** Warna Senada dari dokumen desain; gelap adalah tampilan utama, terang memakai palet "Siang". */
internal object SenadaPalette {
    val DarkBackground = Color(0xFF0B0C0E)
    val DarkSurface = Color(0xFF17181B)
    val DarkSurfaceHigh = Color(0xFF1D1F23)
    val DarkOutline = Color(0xFF2A2C31)
    val DarkText = Color(0xFFF2F2F3)
    val DarkTextMuted = Color(0xFFA3A6AD)

    val LightBackground = Color(0xFFF7F6F3)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceHigh = Color(0xFFECEAE5)
    val LightOutline = Color(0xFFC9C6BF)
    val LightText = Color(0xFF17181A)
    val LightTextMuted = Color(0xFF5D6168)

    val Favorite = Color(0xFFFF5A6E)

    /** Teal desktop; terlalu terang untuk teks di latar terang, jadi tema terang memakai versi gelapnya. */
    val AccentDark = Color(0xFF67CDB1)
    val AccentLight = Color(0xFF0F7B67)
}

/**
 * Skema Material 3 Senada. [accent] disesuaikan agar kontras terhadap latar.
 * Jika tidak ada warna aksen, teal Senada dipakai.
 */
internal fun senadaColorScheme(dark: Boolean, accent: Color?): ColorScheme {
    val p = SenadaPalette
    val background = if (dark) p.DarkBackground else p.LightBackground
    val primary = accent?.let { accessibleAccent(it, background) } ?: if (dark) p.AccentDark else p.AccentLight
    val onPrimary = textOnAccent(primary)
    return if (dark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.24f).compositeOver(p.DarkSurface), onPrimaryContainer = p.DarkText,
            secondary = primary, onSecondary = onPrimary,
            secondaryContainer = p.DarkOutline, onSecondaryContainer = p.DarkText,
            background = p.DarkBackground, onBackground = p.DarkText,
            surface = p.DarkBackground, onSurface = p.DarkText,
            surfaceVariant = p.DarkSurfaceHigh, onSurfaceVariant = p.DarkTextMuted,
            surfaceContainerLowest = p.DarkBackground, surfaceContainerLow = p.DarkSurface,
            surfaceContainer = p.DarkSurface, surfaceContainerHigh = p.DarkSurfaceHigh,
            surfaceContainerHighest = p.DarkOutline,
            outline = p.DarkOutline, outlineVariant = p.DarkOutline,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.18f).compositeOver(p.LightSurface), onPrimaryContainer = p.LightText,
            secondary = primary, onSecondary = onPrimary,
            secondaryContainer = p.LightSurfaceHigh, onSecondaryContainer = p.LightText,
            background = p.LightBackground, onBackground = p.LightText,
            surface = p.LightBackground, onSurface = p.LightText,
            surfaceVariant = p.LightSurfaceHigh, onSurfaceVariant = p.LightTextMuted,
            surfaceContainerLowest = p.LightSurface, surfaceContainerLow = p.LightSurface,
            surfaceContainer = p.LightSurface, surfaceContainerHigh = p.LightSurfaceHigh,
            surfaceContainerHighest = p.LightSurfaceHigh,
            outline = p.LightOutline, outlineVariant = p.LightSurfaceHigh,
        )
    }
}

/** Satu keluarga huruf untuk seluruh aplikasi; judul display memakai 800 dengan jarak huruf rapat. */
@Composable
internal fun senadaTypography(): Typography {
    val jakarta = FontFamily(
        Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
        Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
    )
    val base = Typography()
    fun TextStyle.with(weight: FontWeight, tracking: Double = 0.0) =
        copy(fontFamily = jakarta, fontWeight = weight, letterSpacing = tracking.em)
    return Typography(
        displayLarge = base.displayLarge.with(FontWeight.ExtraBold, -0.04),
        displayMedium = base.displayMedium.with(FontWeight.ExtraBold, -0.04),
        displaySmall = base.displaySmall.with(FontWeight.ExtraBold, -0.03),
        headlineLarge = base.headlineLarge.with(FontWeight.ExtraBold, -0.02),
        headlineMedium = base.headlineMedium.with(FontWeight.ExtraBold, -0.02),
        headlineSmall = base.headlineSmall.with(FontWeight.ExtraBold, -0.01),
        titleLarge = base.titleLarge.with(FontWeight.ExtraBold),
        titleMedium = base.titleMedium.with(FontWeight.Bold),
        titleSmall = base.titleSmall.with(FontWeight.Bold),
        bodyLarge = base.bodyLarge.with(FontWeight.Medium),
        bodyMedium = base.bodyMedium.with(FontWeight.Medium),
        bodySmall = base.bodySmall.with(FontWeight.Medium),
        labelLarge = base.labelLarge.with(FontWeight.Bold),
        labelMedium = base.labelMedium.with(FontWeight.SemiBold),
        labelSmall = base.labelSmall.with(FontWeight.SemiBold),
    )
}

/** Sudut besar dan lembut: sampul daftar 8–12 dp, kartu 20–24 dp. */
internal val SenadaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
