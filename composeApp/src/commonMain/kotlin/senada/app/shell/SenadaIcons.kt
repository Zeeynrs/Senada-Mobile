package senada.app.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ikon garis 24 dp milik Senada, tanpa dependency ikon tambahan. Warnanya mengikuti `LocalContentColor`
 * lewat `Icon`, jadi hitam di sini hanya bentuk.
 */
internal object SenadaIcons {
    val Home = stroke("Home", "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1Z")
    val Search = stroke("Search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14Z", "M20 20l-3.5-3.5")
    val Library = stroke("Library", "M5 4v16", "M10 4v16", "M15 5l4 15")
    val Settings = stroke(
        "Settings",
        "M4 7h10", "M18 7h2", "M4 17h4", "M12 17h8",
        "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4Z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4Z",
    )
    val Play = fill("Play", "M8 5v14l11-7Z")
    val Pause = fill("Pause", "M6 5h4v14H6Z", "M14 5h4v14h-4Z")
    val Next = fill("Next", "M5 4l10 8-10 8Z", "M17 5h2v14h-2Z")
    val Previous = fill("Previous", "M19 20L9 12l10-8Z", "M5 5h2v14H5Z")
    val Shuffle = stroke("Shuffle", "M16 3h5v5", "M4 20L21 3", "M21 16v5h-5", "M15 15l6 6", "M4 4l5 5")
    val Repeat = stroke(
        "Repeat",
        "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3",
    )
    val RepeatOne = stroke(
        "RepeatOne",
        "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3", "M11 10h1v4",
    )
    private const val HEART =
        "M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"
    val Heart = stroke("Heart", HEART)
    val HeartFilled = fill("HeartFilled", HEART)
    val ChevronDown = stroke("ChevronDown", "M6 9l6 6 6-6")
    val ChevronRight = stroke("ChevronRight", "M9 6l6 6-6 6")
    val Clock = stroke("Clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z", "M12 7v5l3.5 2")
    val Import = stroke(
        "Import",
        "M12 3v12",
        "M8 11l4 4 4-4",
        "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2",
    )
    val MusicNote = stroke(
        "MusicNote",
        "M9 18V6l11-2v12",
        "M6.5 16a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5Z",
        "M17.5 14a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5Z",
    )
    val More = fill(
        "More",
        "M5 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4Z", "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4Z", "M19 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4Z",
    )
    val Lyrics = stroke("Lyrics", "M4 6h16", "M4 12h10", "M4 18h13")
    val Queue = stroke("Queue", "M3 6h13", "M3 12h13", "M3 18h8", "M16 15l5 3-5 3Z")
    val Close = stroke("Close", "M6 6l12 12", "M18 6L6 18")

    private fun stroke(name: String, vararg paths: String) = icon(name, filled = false, paths)
    private fun fill(name: String, vararg paths: String) = icon(name, filled = true, paths)

    private fun icon(name: String, filled: Boolean, paths: Array<out String>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = if (filled) null else SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
