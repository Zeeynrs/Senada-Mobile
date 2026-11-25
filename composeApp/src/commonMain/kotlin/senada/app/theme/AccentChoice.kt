package senada.app.theme

import androidx.compose.ui.graphics.Color
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.resources.Res
import senada.resources.accent_follow_cover

internal enum class AccentChoice(
    val key: String,
    private val fixedLabel: String?,
    private val color: Color? = null,
) {
    Senada("senada", "Senada"),
    FollowCover("followCover", null),
    Mint("mint", "Mint", Color(0xFF1ED760)),
    Indigo("indigo", "Indigo", Color(0xFF5865F2)),
    Sky("sky", "Sky", Color(0xFF2AABEE)),
    Aurora("aurora", "Aurora", Color(0xFF8B5CF6)),
    Sunset("sunset", "Sunset", Color(0xFFFF6B5A)),
    Ocean("ocean", "Ocean", Color(0xFF14B8A6)),
    Forest("forest", "Forest", Color(0xFF65A30D)),
    Berry("berry", "Berry", Color(0xFFD946EF)),
    Amber("amber", "Amber", Color(0xFFF59E0B)),
    Rose("rose", "Rose", Color(0xFFF43F5E)),
    Carbon("carbon", "Carbon", Color(0xFF8A8F98)),
    Chrome("chrome", "Chrome", Color(0xFFA78BFA));

    fun label(language: AppLanguage = AppStrings.currentLanguage): String =
        fixedLabel ?: AppStrings.get(Res.string.accent_follow_cover, language)

    val label: String get() = label()

    /** Nilai null memakai aksen Senada sesuai tema terang atau gelap. */
    fun source(cover: Color?): Color? = if (this == FollowCover) cover else color
}
