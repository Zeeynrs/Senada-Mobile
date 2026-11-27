package senada.app

import senada.platform.BuildConfig

internal actual fun isDebugBuild(): Boolean = BuildConfig.DEBUG
