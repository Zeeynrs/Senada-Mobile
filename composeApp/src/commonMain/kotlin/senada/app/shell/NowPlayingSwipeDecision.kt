package senada.app.shell

internal fun shouldOpenNowPlaying(offset: Float, height: Float, velocity: Float, flingThreshold: Float): Boolean =
    height > 0f && (offset >= height * 0.30f || velocity <= -flingThreshold)

internal fun shouldCloseNowPlaying(offset: Float, height: Float, velocity: Float, flingThreshold: Float): Boolean =
    height > 0f && (offset >= height * 0.25f || velocity >= flingThreshold)
