package senada.catalog

import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.UiText
import senada.resources.Res
import senada.resources.error_catalog_network
import senada.resources.error_catalog_rate_limited
import senada.resources.error_catalog_unavailable
import senada.resources.error_catalog_unreadable

/** Kegagalan pencarian atau resolusi audio yang perlu dijelaskan ke pengguna. */
class CatalogException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind {
        /** Lagu tidak dapat diputar dari sumber ini, misalnya dibatasi atau dihapus. */
        Unavailable,

        /** Sumber membatasi permintaan sementara; jangan langsung mencoba lagu lain. */
        RateLimited,

        /** Jaringan putus, waktu habis, atau server tidak menjawab dengan benar. */
        Network,

        /** Balasan berubah bentuk sehingga tidak dapat dibaca. */
        Unreadable,
    }

    internal val uiText: UiText
        get() = when (kind) {
            Kind.Unavailable -> UiText.Resource(Res.string.error_catalog_unavailable)
            Kind.RateLimited -> UiText.Resource(Res.string.error_catalog_rate_limited)
            Kind.Network -> UiText.Resource(Res.string.error_catalog_network)
            Kind.Unreadable -> UiText.Resource(Res.string.error_catalog_unreadable)
        }

    internal fun userMessage(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

    val userMessage: String
        get() = userMessage()
}
