package senada.catalog

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

    val userMessage: String
        get() = when (kind) {
            Kind.Unavailable -> "Lagu ini tidak tersedia dari YouTube Music."
            Kind.RateLimited -> "YouTube Music sedang membatasi permintaan. Coba lagi nanti."
            Kind.Network -> "Tidak dapat terhubung ke YouTube Music. Periksa koneksi lalu coba lagi."
            Kind.Unreadable -> "Balasan YouTube Music tidak dapat dibaca."
        }
}
