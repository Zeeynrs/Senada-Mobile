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

private const val MIN_ACCENT_SATURATION = 0.20f
private const val MIN_ACCENT_CHROMA = 0.10f
private const val MIN_ACCENT_VALUE = 0.20f
private const val MIN_ACCENT_LIGHTNESS = 0.12f
private const val MAX_ACCENT_LIGHTNESS = 0.88f

/**
 * Delapan tingkat per kanal menyatukan variasi kecil akibat kompresi sampul.
 * Kelompok warna yang cukup jenuh dengan kecerahan wajar diutamakan dan dipilih berdasarkan
 * jumlah piksel terbanyak; warna netral (hampir abu-abu, putih, atau hitam) hanya dipakai
 * sebagai cadangan bila sampul tidak memiliki warna jenuh.
 */
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

    var colorfulBin = -1
    var colorfulCount = 0
    var colorfulSaturation = 0f
    var neutralBin = -1
    var neutralCount = 0

    for (bin in counts.indices) {
        val count = counts[bin]
        if (count == 0) continue
        if (count > neutralCount) {
            neutralBin = bin
            neutralCount = count
        }
        val saturation = accentSaturationIfColorful(
            red = reds[bin] / (count * 255f),
            green = greens[bin] / (count * 255f),
            blue = blues[bin] / (count * 255f),
        ) ?: continue
        if (count > colorfulCount || (count == colorfulCount && saturation > colorfulSaturation)) {
            colorfulBin = bin
            colorfulCount = count
            colorfulSaturation = saturation
        }
    }

    val chosenBin = if (colorfulBin >= 0) colorfulBin else neutralBin
    if (chosenBin < 0) return null
    val chosenCount = counts[chosenBin]
    return Color(reds[chosenBin] / chosenCount, greens[chosenBin] / chosenCount, blues[chosenBin] / chosenCount)
}

/** Mengembalikan saturasi HSV bila warna cukup jenuh dan tidak hampir hitam/putih/abu-abu, atau null bila netral. */
private fun accentSaturationIfColorful(red: Float, green: Float, blue: Float): Float? {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val chroma = max - min
    val saturation = if (max > 0f) chroma / max else 0f
    val lightness = (max + min) / 2f
    val colorful = saturation >= MIN_ACCENT_SATURATION &&
        chroma >= MIN_ACCENT_CHROMA &&
        max >= MIN_ACCENT_VALUE &&
        lightness in MIN_ACCENT_LIGHTNESS..MAX_ACCENT_LIGHTNESS
    return if (colorful) saturation else null
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
