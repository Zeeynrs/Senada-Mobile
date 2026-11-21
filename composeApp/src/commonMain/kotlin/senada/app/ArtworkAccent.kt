package senada.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val SAMPLE_SIZE = 64

internal expect fun artworkRequest(context: PlatformContext): ImageRequest.Builder
internal expect fun BitmapImage.readPixels(): IntArray

internal suspend fun loadArtworkAccent(context: PlatformContext, url: String): Color? = withContext(Dispatchers.Default) {
    try {
        val request = artworkRequest(context).data(url).size(SAMPLE_SIZE)
            .precision(Precision.EXACT).scale(Scale.FIT).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        val image = result?.image as? BitmapImage ?: return@withContext null
        if (image.width !in 1..SAMPLE_SIZE || image.height !in 1..SAMPLE_SIZE) return@withContext null
        dominantAccent(image.readPixels())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

internal class ArtworkAccentCache {
    private val colors = mutableMapOf<String, Color?>()
    private val mutex = Mutex()

    suspend fun get(url: String, load: suspend () -> Color?): Color? = mutex.withLock {
        if (url in colors) colors[url] else load().also { colors[url] = it }
    }
}

/** Delapan tingkat per kanal menyatukan variasi kecil akibat kompresi sampul. */
internal fun dominantAccent(pixels: IntArray): Color? {
    val counts = IntArray(512)
    val reds = IntArray(512)
    val greens = IntArray(512)
    val blues = IntArray(512)
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        val red = (pixel ushr 16) and 255
        val green = (pixel ushr 8) and 255
        val blue = pixel and 255
        val bin = (red / 32) * 64 + (green / 32) * 8 + blue / 32
        counts[bin]++
        reds[bin] += red
        greens[bin] += green
        blues[bin] += blue
    }
    val bin = counts.indices.maxBy { counts[it] }
    val count = counts[bin]
    if (count == 0) return null
    return Color(reds[bin] / count, greens[bin] / count, blues[bin] / count)
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

internal fun textOnAccent(accent: Color): Color =
    if (contrastRatio(accent, Color.Black) >= contrastRatio(accent, Color.White)) Color.Black else Color.White

internal fun accessibleAccent(source: Color, background: Color): Color {
    if (contrastRatio(source, background) >= 4.5f) return source
    val target = textOnAccent(background)
    // Cari campuran terkecil ke hitam atau putih yang masih terbaca.
    var low = 0f
    var high = 1f
    fun mix(amount: Float) = Color(
        red = source.red + (target.red - source.red) * amount,
        green = source.green + (target.green - source.green) * amount,
        blue = source.blue + (target.blue - source.blue) * amount,
    )
    repeat(9) {
        val mid = (low + high) / 2
        if (contrastRatio(mix(mid), background) >= 4.5f) high = mid else low = mid
    }
    return mix(high)
}
