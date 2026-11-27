package senada.lyrics

import java.util.regex.Pattern

// Android's ICU regex engine and the JVM used by Android unit tests both support extended graphemes.
private val grapheme = Pattern.compile("\\X")

internal actual fun graphemeOffsets(text: String): IntArray {
    val matcher = grapheme.matcher(text)
    return buildList {
        add(0)
        while (matcher.find()) add(matcher.end())
    }.toIntArray()
}
