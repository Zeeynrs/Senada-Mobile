package senada.app.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.atan2
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import senada.app.playbackTime
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.catalog.youtube.resizeGoogleArtwork
import senada.playback.PlaybackController
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

@Composable
internal fun VinylTurntable(
    artwork: String?,
    audio: SampleAudioState,
    playback: PlaybackController,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val position = remember(playback) { playback.state.map { it.audio.positionMs }.distinctUntilChanged() }
        .collectAsState(playback.state.value.audio.positionMs)
    val selection = audio.selection
    val duration = audio.durationMs?.takeIf { it > 0L }
    val seekable = duration != null && audio.canSeek
    val animate = LocalSenadaLook.current.animationScale > 0f
    val rotating = visible && animate && audio.status == SampleAudioStatus.Playing
    val rotation = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rotating) {
        if (!rotating) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            // 33⅓ RPM, with a cap in case frames stop arriving temporarily.
            val elapsed = (frame - lastFrame).coerceAtMost(50_000_000L)
            rotation.floatValue = (rotation.floatValue + elapsed * 0.0000002f) % 360f
            lastFrame = frame
        }
    }
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val geometry = remember(maxWidth, maxHeight, density) {
            with(density) { VinylGeometry(maxWidth.toPx(), maxHeight.toPx()) }
        }
        val preview = remember(geometry, selection, seekable, duration) { mutableStateOf<Float?>(null) }
        fun progress(): Float = preview.value ?: if (duration != null) {
            (position.value.toDouble() / duration).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        fun seek(progress: Float): Boolean {
            val current = playback.state.value.audio
            if (duration == null || current.selection != selection || !current.canSeek) return false
            playback.seekTo(vinylSeekPosition(progress, duration))
            return true
        }

        Canvas(Modifier.fillMaxSize()) { drawRecord(geometry) }
        val label = with(density) { (geometry.labelRadius * 2f).toDp() }
        val labelX = with(density) { (geometry.center.x - geometry.labelRadius).toDp() }
        val labelY = with(density) { (geometry.center.y - geometry.labelRadius).toDp() }
        Box(Modifier.offset(labelX, labelY).size(label), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = rotation.floatValue
                    shape = CircleShape
                    clip = true
                }
                    .background(Color(0xFF343038)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SenadaIcons.Album, null, Modifier.size(label * .34f), tint = PlayerWhite.copy(alpha = .42f))
                val model = remember(artwork) { artwork?.takeIf(String::isNotBlank)?.let { resizeGoogleArtwork(it, 384) } }
                if (model != null) AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.size(10.dp).shadow(2.dp, CircleShape).background(Color(0xFFD8D9DD), CircleShape))
            Box(Modifier.size(4.dp).background(Color(0xFF25272C), CircleShape))
        }

        Canvas(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription = "Jarum Vinyl"
                    if (seekable) {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress(), 0f..1f)
                        setProgress(action = ::seek)
                    } else {
                        disabled()
                    }
                }
                .pointerInput(geometry, selection, seekable, duration) {
                    if (!seekable) return@pointerInput
                    val hitRadius = 26.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val grabOffset = geometry.needleAt(progress()) - down.position
                        if (grabOffset.getDistance() > hitRadius) return@awaitEachGesture
                        val firstDrag = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        try {
                            preview.value = geometry.progressAt(firstDrag.position + grabOffset)
                            val finished = drag(firstDrag.id) { change ->
                                preview.value = geometry.progressAt(change.position + grabOffset)
                                change.consume()
                            }
                            if (finished) preview.value?.let(::seek)
                        } finally {
                            preview.value = null
                        }
                    }
                },
        ) { drawTonearm(geometry, progress()) }

        val dragTarget = preview.value
        if (dragTarget != null && duration != null) {
            Text(
                playbackTime(vinylSeekPosition(dragTarget, duration)),
                color = PlayerWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .background(Color(0xFF202128), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun DrawScope.drawRecord(geometry: VinylGeometry) {
    val center = geometry.center
    val radius = geometry.radius
    if (radius <= 0f) return
    drawCircle(Color.Black.copy(alpha = .46f), radius + 4.dp.toPx(), center + Offset(0f, 5.dp.toPx()))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF29282C), Color(0xFF101014), Color(0xFF1D1C21)), center, radius),
        radius, center,
    )
    for (groove in 0..25) {
        val inset = radius * (.48f + groove * .018f)
        val reflectance = if (groove % 5 == 0) .14f else .06f
        drawCircle(Color.White.copy(alpha = reflectance), inset, center, style = Stroke(.65.dp.toPx()))
    }
    drawCircle(Color.White.copy(alpha = .21f), radius - 1.dp.toPx().coerceAtMost(radius * .1f), center, style = Stroke(1.dp.toPx()))
    drawCircle(Color(0xFF08090B), geometry.labelRadius + 3.dp.toPx(), center)
}

private fun DrawScope.drawTonearm(geometry: VinylGeometry, progress: Float) {
    val pivot = geometry.pivot
    val needle = geometry.needleAt(progress)
    val direction = needle - pivot
    val angle = atan2(direction.y, direction.x) * (180f / PI.toFloat())
    drawLine(Color.Black.copy(alpha = .52f), pivot + Offset(2.dp.toPx(), 3.dp.toPx()),
        needle + Offset(2.dp.toPx(), 3.dp.toPx()), 10.dp.toPx(), StrokeCap.Round)
    drawLine(Color(0xFF6A6873), pivot, needle, 7.dp.toPx(), StrokeCap.Round)
    drawLine(Color(0xFFD7D4DD), pivot, needle, 3.dp.toPx(), StrokeCap.Round)
    drawCircle(Color(0xFF212126), 14.dp.toPx(), pivot)
    drawCircle(Color(0xFFA4A3AD), 8.dp.toPx(), pivot)
    drawCircle(Color(0xFF5E5C65), 4.dp.toPx(), pivot)
    rotate(angle, needle) {
        val body = Offset(needle.x - 18.dp.toPx(), needle.y - 7.dp.toPx())
        drawRoundRect(Color(0xFF18181D), body, Size(23.dp.toPx(), 14.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color(0xFFD9D7DD), body + Offset(2.dp.toPx(), 2.dp.toPx()),
            Size(15.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        drawCircle(Color(0xFFE0A783), 2.2.dp.toPx(), needle)
    }
}
