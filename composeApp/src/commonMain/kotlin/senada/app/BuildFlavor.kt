package senada.app

/** Benar hanya pada build debug/pengujian, salah pada build rilis. */
internal expect fun isDebugBuild(): Boolean
