package senada.updater

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import senada.app.UiText
import senada.resources.Res
import senada.resources.updater_github_forbidden
import senada.resources.updater_github_invalid_release_list
import senada.resources.updater_github_invalid_source
import senada.resources.updater_github_offline
import senada.resources.updater_github_rate_limited
import senada.resources.updater_github_status

internal const val SENADA_MOBILE_REPO = "Zeeynrs/Senada-Mobile"
internal const val SENADA_RELEASES_WEB_URL = "https://github.com/$SENADA_MOBILE_REPO/releases"
internal const val SENADA_RELEASES_API_URL = "https://api.github.com/repos/$SENADA_MOBILE_REPO/releases?per_page=20"

internal val TRUSTED_UPDATE_HOSTS = setOf(
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "github-releases.githubusercontent.com",
    "play.google.com",
    "apps.apple.com",
    "testflight.apple.com",
)

internal fun isTrustedHttpsUrl(url: String): Boolean = try {
    val parsed = Url(url)
    parsed.protocol == URLProtocol.HTTPS && parsed.host.lowercase() in TRUSTED_UPDATE_HOSTS
} catch (_: Exception) {
    false
}

internal class UpdateNetworkException(val userMessage: UiText, val retryable: Boolean = true) : Exception()

internal interface ReleaseSource {
    suspend fun fetchReleases(userAgentVersion: String): List<GitHubRelease>
    suspend fun fetchDeltaManifest(manifestUrl: String, userAgentVersion: String): DeltaManifest?
}

/**
 * Fetches GitHub Releases metadata via REST API with conditional ETag caching so repeated checks
 * respect GitHub's 60 req/hr unauthenticated rate limit.
 */
internal class GitHubReleasesClient(
    private val http: HttpClient,
    private val releasesApiUrl: String = SENADA_RELEASES_API_URL,
) : ReleaseSource {
    private var cachedEtag: String? = null
    private var cachedReleases: List<GitHubRelease>? = null

    override suspend fun fetchReleases(userAgentVersion: String): List<GitHubRelease> {
        if (!isTrustedHttpsUrl(releasesApiUrl)) {
            throw UpdateNetworkException(UiText.Resource(Res.string.updater_github_invalid_source), retryable = false)
        }
        val response = try {
            http.get(releasesApiUrl) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.UserAgent, "Senada-Mobile/${userAgentVersion.ifBlank { "1.0.0" }}")
                cachedEtag?.let { header(HttpHeaders.IfNoneMatch, it) }
            }
        } catch (_: Exception) {
            throw UpdateNetworkException(UiText.Resource(Res.string.updater_github_offline))
        }

        if (response.status == HttpStatusCode.NotModified) {
            cachedReleases?.let { return it }
        }
        if (response.status == HttpStatusCode.NotFound) {
            return emptyList()
        }
        if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.TooManyRequests) {
            val remaining = response.headers["x-ratelimit-remaining"]?.toIntOrNull()
            if (remaining == 0 || response.status == HttpStatusCode.TooManyRequests) {
                throw UpdateNetworkException(UiText.Resource(Res.string.updater_github_rate_limited))
            }
            throw UpdateNetworkException(UiText.Resource(Res.string.updater_github_forbidden, response.status.value))
        }
        if (response.status.value !in 200..299) {
            throw UpdateNetworkException(UiText.Resource(Res.string.updater_github_status, response.status.value))
        }

        val body = response.bodyAsText()
        val parsed = try {
            parseGitHubReleasesJson(body)
        } catch (_: Exception) {
            throw UpdateNetworkException(UiText.Resource(Res.string.updater_github_invalid_release_list))
        }
        response.headers[HttpHeaders.ETag]?.takeIf { it.isNotBlank() }?.let { etag ->
            cachedEtag = etag
            cachedReleases = parsed
        }
        return parsed
    }

    override suspend fun fetchDeltaManifest(manifestUrl: String, userAgentVersion: String): DeltaManifest? {
        if (!isTrustedHttpsUrl(manifestUrl)) return null
        val response = try {
            http.get(manifestUrl) {
                header(HttpHeaders.Accept, "application/json, application/octet-stream")
                header(HttpHeaders.UserAgent, "Senada-Mobile/${userAgentVersion.ifBlank { "1.0.0" }}")
            }
        } catch (_: Exception) {
            return null
        }
        if (response.status.value !in 200..299) return null
        return try {
            parseDeltaManifestJson(response.bodyAsText())
        } catch (_: Exception) {
            null
        }
    }
}

internal fun parseGitHubReleasesJson(rawJson: String): List<GitHubRelease> {
    val root = Json.parseToJsonElement(rawJson)
    val array = root as? JsonArray ?: throw IllegalArgumentException("Expected JSON array")
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val tagName = obj.stringOrNull("tag_name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val htmlUrl = obj.stringOrNull("html_url")?.takeIf { isTrustedHttpsUrl(it) } ?: SENADA_RELEASES_WEB_URL
        val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { assetEl ->
            val assetObj = assetEl as? JsonObject ?: return@mapNotNull null
            val name = assetObj.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val downloadUrl = assetObj.stringOrNull("browser_download_url")?.takeIf { isTrustedHttpsUrl(it) }
                ?: return@mapNotNull null
            val size = assetObj.longOrNull("size") ?: 0L
            val digest = normalizeSha256Digest(assetObj.stringOrNull("digest"))
            val contentType = assetObj.stringOrNull("content_type")
            ReleaseAsset(
                name = name,
                downloadUrl = downloadUrl,
                sizeBytes = size.coerceAtLeast(0L),
                sha256Digest = digest,
                contentType = contentType,
            )
        }
        GitHubRelease(
            tagName = tagName,
            name = obj.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: tagName,
            body = obj.stringOrNull("body").orEmpty().trim(),
            htmlUrl = htmlUrl,
            isDraft = obj.booleanOrDefault("draft", false),
            isPrerelease = obj.booleanOrDefault("prerelease", false),
            publishedAt = obj.stringOrNull("published_at"),
            assets = assets,
        )
    }
}

internal fun parseDeltaManifestJson(rawJson: String): DeltaManifest? {
    val obj = Json.parseToJsonElement(rawJson).jsonObject
    val formatVersion = obj.intOrNull("formatVersion") ?: return null
    if (formatVersion != 1) return null
    val targetAssetName = obj.stringOrNull("targetAssetName")?.takeIf { it.isNotBlank() } ?: return null
    val targetSha256 = normalizeSha256Digest(obj.stringOrNull("targetSha256")) ?: return null
    val patches = obj["patches"]?.jsonArray.orEmpty().mapNotNull { el ->
        val p = el as? JsonObject ?: return@mapNotNull null
        val baseSha256 = normalizeSha256Digest(p.stringOrNull("baseSha256")) ?: return@mapNotNull null
        val patchAssetName = p.stringOrNull("patchAssetName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val patchSizeBytes = p.longOrNull("patchSizeBytes")?.takeIf { it > 0L } ?: return@mapNotNull null
        val entryTargetSha = normalizeSha256Digest(p.stringOrNull("targetSha256")) ?: targetSha256
        DeltaPatchEntry(
            baseSha256 = baseSha256,
            targetSha256 = entryTargetSha,
            patchAssetName = patchAssetName,
            patchSizeBytes = patchSizeBytes,
            patchSha256 = normalizeSha256Digest(p.stringOrNull("patchSha256")),
            targetVersion = p.stringOrNull("targetVersion"),
            targetVersionCode = p.longOrNull("targetVersionCode"),
        )
    }
    return DeltaManifest(
        formatVersion = formatVersion,
        targetAssetName = targetAssetName,
        targetSha256 = targetSha256,
        patches = patches,
    )
}

internal fun normalizeSha256Digest(raw: String?): String? {
    val cleaned = raw?.trim()?.removePrefix("sha256:")?.removePrefix("SHA256:")?.lowercase() ?: return null
    if (cleaned.length != 64 || !cleaned.all { it in '0'..'9' || it in 'a'..'f' }) return null
    return cleaned
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: default

private fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

private fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
