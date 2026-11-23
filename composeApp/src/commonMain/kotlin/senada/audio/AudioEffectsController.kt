package senada.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The application thread owns settings edits and persistence. */
internal class AudioEffectsController(private val store: AudioEffectsStore) {
    private val mutableEffects = MutableStateFlow(store.load())
    val effects: StateFlow<AudioEffects> = mutableEffects.asStateFlow()

    fun update(transform: (AudioEffects) -> AudioEffects) {
        val current = mutableEffects.value
        val next = transform(current).bounded()
        if (next == current) return
        store.save(next)
        mutableEffects.value = next
    }
}
