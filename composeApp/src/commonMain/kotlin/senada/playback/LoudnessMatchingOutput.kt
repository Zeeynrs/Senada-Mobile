package senada.playback

import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import senada.audio.AudioEffects

/** Menurunkan volume pemutar setelah efek audio, dengan volume timer tidur tetap independen. */
internal class LoudnessMatchingOutput(
    private val output: SampleAudioOutput,
    private val effects: StateFlow<AudioEffects>,
    scope: CoroutineScope,
) : SampleAudioOutput by output {
    private var loudnessDb: Float? = null
    private var gain = 1f

    override var volume = output.volume
        set(value) {
            field = value
            applyVolume()
        }

    init {
        scope.launch {
            effects.map { it.loudnessMatch }.distinctUntilChanged().collect { updateGain() }
        }
    }

    override fun play(selection: SampleAudioSelection, startAtMs: Long) {
        loudnessDb = selection.loudnessDb?.takeIf { selection.source == SampleAudioSource.YouTubeMusic }
        updateGain()
        output.play(selection, startAtMs)
    }

    override fun clearSelection() {
        loudnessDb = null
        updateGain()
    }

    private fun updateGain() {
        val db = loudnessDb
        if (effects.value.loudnessMatch && db != null && db.isFinite() && db > 0f) {
            // Offset positif berarti lebih keras dari acuan. Gain <= 1 menjaga headroom.
            gain = 10f.pow(-db / 20f)
        } else {
            gain = 1f
        }
        applyVolume()
    }

    private fun applyVolume() {
        val effective = volume * gain
        if (output.volume != effective) output.volume = effective
    }
}
