package senada.app.nowplaying

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Ikon khusus pemutar dari geometri mockup Senada. */
internal object NowPlayingIcons {
    val Previous = filled("Sebelumnya", 44, 30, "M22 2L4 15l18 13zM42 2L24 15l18 13z")
    val Next = filled("Berikutnya", 44, 30, "M2 2l18 13L2 28zM22 2l18 13-18 13z")
    val Play = filled("Putar", 40, 44, "M7 2L37 22 7 42z")
    val Pause = filled(
        "Jeda", 40, 44,
        "M8 2h4a3 3 0 0 1 3 3v34a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3z" +
            "M28 2h4a3 3 0 0 1 3 3v34a3 3 0 0 1-3 3h-4a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3z",
    )

    private const val HeartPath = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    val Heart = icon("Favorit") { stroke(HeartPath) }
    val HeartFilled = icon("Favorit aktif") { fill(HeartPath) }
    val More = icon("Menu lainnya") {
        listOf(6, 12, 18).forEach { x ->
            fill("M$x 10.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z")
        }
    }
    val Lyrics = icon("Lirik") {
        stroke("M5 5h14v10H10l-5 4zM9 9h6M9 12h4")
    }
    val Moon = icon("Timer tidur") {
        stroke("M19 14.5A7.5 7.5 0 1 1 9.5 5a6 6 0 0 0 9.5 9.5z")
    }
    val AudioLab = icon("Audio Lab") {
        stroke("M6 4v16M12 4v16M18 4v16")
        fill("M6 11.6a2.4 2.4 0 1 1 0 4.8a2.4 2.4 0 1 1 0-4.8z" +
            "M12 5.6a2.4 2.4 0 1 1 0 4.8a2.4 2.4 0 1 1 0-4.8z" +
            "M18 13.6a2.4 2.4 0 1 1 0 4.8a2.4 2.4 0 1 1 0-4.8z")
    }
    val Queue = icon("Antrean") {
        stroke("M4 6h12M4 12h12M4 18h8")
        fill("M17 15v6l4-3z")
    }
    val DragHandle = icon("Pegangan urutan") {
        for (y in listOf(6.5f, 12f, 17.5f)) {
            fill("M9 ${y - 1.3f}a1.3 1.3 0 1 1 0 2.6a1.3 1.3 0 1 1 0-2.6z")
            fill("M15 ${y - 1.3f}a1.3 1.3 0 1 1 0 2.6a1.3 1.3 0 1 1 0-2.6z")
        }
    }
    val Chevron = ImageVector.Builder("Buka lirik", 14.dp, 14.dp, 16f, 16f).apply {
        stroke("M6 3l5 5-5 5")
    }.build()

    private fun icon(name: String, paths: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 20.dp, 20.dp, 24f, 24f).apply(paths).build()

    private fun filled(name: String, width: Int, height: Int, path: String): ImageVector =
        ImageVector.Builder(name, width.dp, height.dp, width.toFloat(), height.toFloat())
            .apply { fill(path) }.build()

    private fun ImageVector.Builder.fill(data: String) {
        addPath(pathData = addPathNodes(data), fill = SolidColor(PlayerWhite))
    }

    private fun ImageVector.Builder.stroke(data: String) {
        addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}
