@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import coil3.PlatformContext
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import senada.resources.Res

internal enum class AppLanguage(val tag: String, val nativeName: String) {
    English("en", "English"),
    Indonesian("id", "Bahasa Indonesia"),
    ;

    companion object {
        fun fromTag(raw: String?): AppLanguage = when (raw?.trim()?.lowercase()) {
            "id" -> Indonesian
            "en" -> English
            else -> English
        }
    }
}

internal val LocalAppLanguage = compositionLocalOf { AppLanguage.English }

internal sealed interface UiText {
    data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : UiText {
        constructor(res: StringResource, vararg args: Any) : this(res, args.toList())
    }

    data class Raw(val text: String) : UiText

    fun resolve(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        is Resource -> {
            val resolvedArgs = args.map { if (it is UiText) it.resolve(language) else it }.toTypedArray()
            AppStrings.get(res, language, *resolvedArgs)
        }
        is Raw -> text
    }
}

internal expect fun registerPlatformContext(context: PlatformContext)

internal expect fun loadComposeCvrBytes(path: String): ByteArray?

internal enum class PluralCategory {
    ZERO, ONE, TWO, FEW, MANY, OTHER;

    companion object {
        fun fromCvr(name: String): PluralCategory = when (name.uppercase()) {
            "ZERO" -> ZERO
            "ONE" -> ONE
            "TWO" -> TWO
            "FEW" -> FEW
            "MANY" -> MANY
            else -> OTHER
        }

        fun forLanguage(language: AppLanguage, quantity: Int): PluralCategory = when (language) {
            AppLanguage.English -> if (quantity == 1) ONE else OTHER
            AppLanguage.Indonesian -> OTHER
        }
    }
}

internal data class CvrStringTable(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<PluralCategory, String>>,
)

internal object AppStrings {
    private val mutableLanguage = MutableStateFlow(AppLanguage.English)
    val languageFlow: StateFlow<AppLanguage> = mutableLanguage.asStateFlow()

    val currentLanguage: AppLanguage
        get() = mutableLanguage.value

    private val tables = mutableMapOf<AppLanguage, CvrStringTable>()

    fun setLanguage(language: AppLanguage) {
        mutableLanguage.value = language
    }

    suspend fun ensureLoadedAsync() {
        for (language in AppLanguage.entries) {
            if (tables[language] == null) {
                val path = cvrPath(language)
                val bytes = loadComposeCvrBytes(path) ?: runCatching { Res.readBytes(path) }.getOrNull()
                if (bytes != null) {
                    tables[language] = parseCvr(bytes.decodeToString())
                }
            }
        }
    }

    fun get(resource: StringResource, language: AppLanguage = currentLanguage, vararg args: Any?): String {
        val template = tableFor(language).strings[resource.key]
            ?: tableFor(AppLanguage.English).strings[resource.key]
            ?: resource.key
        return if (args.isEmpty()) template else formatResourceArgs(template, *args)
    }

    fun plural(
        resource: PluralStringResource,
        quantity: Int,
        language: AppLanguage = currentLanguage,
        vararg args: Any?,
    ): String {
        val category = PluralCategory.forLanguage(language, quantity)
        val table = tableFor(language)
        val forms = table.plurals[resource.key] ?: tableFor(AppLanguage.English).plurals[resource.key]
        val template = forms?.get(category) ?: forms?.get(PluralCategory.OTHER) ?: resource.key
        return if (args.isEmpty()) template else formatResourceArgs(template, *args)
    }

    internal fun loadedKeys(language: AppLanguage): Set<String> = tableFor(language).strings.keys

    internal fun loadedPluralKeys(language: AppLanguage): Set<String> = tableFor(language).plurals.keys

    internal fun rawTemplate(key: String, language: AppLanguage): String? = tableFor(language).strings[key]

    internal fun rawPluralForms(key: String, language: AppLanguage): Map<String, String> =
        tableFor(language).plurals[key]?.mapKeys { it.key.name } ?: emptyMap()

    private fun tableFor(language: AppLanguage): CvrStringTable {
        tables[language]?.let { return it }
        val bytes = loadComposeCvrBytes(cvrPath(language)) ?: return CvrStringTable(emptyMap(), emptyMap())
        val parsed = parseCvr(bytes.decodeToString())
        tables[language] = parsed
        return parsed
    }

    private fun cvrPath(language: AppLanguage): String = when (language) {
        AppLanguage.English -> "values/strings.commonMain.cvr"
        AppLanguage.Indonesian -> "values-id/strings.commonMain.cvr"
    }

    internal fun parseCvr(content: String): CvrStringTable {
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Map<PluralCategory, String>>()
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank() || line.startsWith("version:")) continue
            val firstSep = line.indexOf('|')
            if (firstSep <= 0) continue
            val secondSep = line.indexOf('|', firstSep + 1)
            if (secondSep <= firstSep + 1) continue
            val kind = line.substring(0, firstSep)
            val key = line.substring(firstSep + 1, secondSep)
            val payload = line.substring(secondSep + 1)
            when (kind) {
                "string" -> strings[key] = decodeBase64Utf8(payload)
                "plurals" -> {
                    val map = LinkedHashMap<PluralCategory, String>()
                    for (entry in payload.split(',')) {
                        val colon = entry.indexOf(':')
                        if (colon > 0) {
                            val qty = PluralCategory.fromCvr(entry.substring(0, colon))
                            map[qty] = decodeBase64Utf8(entry.substring(colon + 1))
                        }
                    }
                    plurals[key] = map
                }
            }
        }
        return CvrStringTable(strings, plurals)
    }

    private fun decodeBase64Utf8(encoded: String): String =
        if (encoded.isEmpty()) "" else Base64.decode(encoded).decodeToString()
}

internal fun formatResourceArgs(template: String, vararg args: Any?): String {
    if ('%' !in template) return template
    val out = StringBuilder(template.length + 16)
    var index = 0
    var nextSequential = 0
    while (index < template.length) {
        val ch = template[index]
        if (ch != '%' || index + 1 >= template.length) {
            out.append(ch)
            index++
            continue
        }
        if (template[index + 1] == '%') {
            out.append('%')
            index += 2
            continue
        }
        var cursor = index + 1
        var positionDigits = 0
        while (cursor < template.length && template[cursor].isDigit()) {
            positionDigits = positionDigits * 10 + (template[cursor] - '0')
            cursor++
        }
        val argIndex: Int?
        val specPos: Int
        if (positionDigits > 0 && cursor < template.length && template[cursor] == '$') {
            argIndex = positionDigits - 1
            specPos = cursor + 1
        } else if (cursor == index + 1) {
            argIndex = nextSequential++
            specPos = cursor
        } else {
            argIndex = null
            specPos = cursor
        }
        if (argIndex != null && specPos < template.length && template[specPos] in "sdf") {
            out.append(args.getOrNull(argIndex)?.toString().orEmpty())
            index = specPos + 1
        } else {
            out.append(ch)
            index++
        }
    }
    return out.toString()
}

@Composable
internal fun appString(resource: StringResource): String =
    AppStrings.get(resource, LocalAppLanguage.current)

@Composable
internal fun appString(resource: StringResource, vararg formatArgs: Any?): String =
    AppStrings.get(resource, LocalAppLanguage.current, *formatArgs)

@Composable
internal fun appPlural(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any?): String =
    AppStrings.plural(resource, quantity, LocalAppLanguage.current, *formatArgs)
