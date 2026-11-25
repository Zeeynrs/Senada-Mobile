package senada.lyrics

private const val HANGUL_BASE = 0xAC00
private const val SYLLABLES_PER_INITIAL = 588
private const val FINALS_PER_VOWEL = 28

private val initials = listOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
private val vowels = listOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
private val finals = listOf("", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t")
private val liaison = mapOf(
    1 to "g", 2 to "kk", 4 to "n", 7 to "d", 8 to "r", 16 to "m", 17 to "b", 19 to "s",
    20 to "ss", 22 to "j", 23 to "ch", 24 to "k", 25 to "t", 26 to "p", 27 to "",
)

internal fun hasLatinLetters(text: String): Boolean =
    text.any { it in 'a'..'z' || it in 'A'..'Z' || (it in '\u00C0'..'\u024F' && it.isLetter()) }

internal fun needsRomanizationFromSource(text: String): Boolean = text.any {
    it in '\u3041'..'\u30FF' || it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF'
}

internal fun lyricRomanization(text: String, fromSource: String? = null): String? =
    fromSource?.trim()?.takeIf { it.isNotEmpty() && it != text.trim() }
        ?: romanizeHangul(text)

// Mirrors Senada Desktop's Hangul fallback, including common liaison and nasal assimilation.
private fun romanizeHangul(text: String): String? {
    if (text.none { it in '가'..'힣' }) return null
    var carriedInitial: String? = null
    return buildString {
        for ((index, char) in text.withIndex()) {
            if (char !in '가'..'힣') {
                append(char)
                carriedInitial = null
                continue
            }
            val syllable = char.code - HANGUL_BASE
            append(carriedInitial ?: initials[syllable / SYLLABLES_PER_INITIAL])
            append(vowels[syllable % SYLLABLES_PER_INITIAL / FINALS_PER_VOWEL])
            carriedInitial = null

            val final = syllable % FINALS_PER_VOWEL
            val next = text.getOrNull(index + 1)?.takeIf { it in '가'..'힣' }
            val nextInitial = next?.let { (it.code - HANGUL_BASE) / SYLLABLES_PER_INITIAL }
            val ending = when {
                nextInitial == 11 && final in liaison -> {
                    carriedInitial = liaison.getValue(final)
                    ""
                }
                (final == 4 && nextInitial == 5) || (final == 8 && nextInitial in listOf(2, 5)) -> {
                    carriedInitial = "l"
                    "l"
                }
                nextInitial == 2 || nextInitial == 6 -> when (finals[final]) {
                    "k" -> "ng"
                    "t" -> "n"
                    "p" -> "m"
                    else -> finals[final]
                }
                else -> finals[final]
            }
            append(ending)
        }
    }
}
