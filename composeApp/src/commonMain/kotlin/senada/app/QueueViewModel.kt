package senada.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import senada.model.Track
import senada.playback.Queue

class QueueViewModel : ViewModel() {
    var queue by mutableStateOf(Queue())
        private set

    fun add(track: Track) {
        queue = queue.append(track)
    }

    fun remove(entryId: Long) {
        queue = queue.remove(entryId)
    }
}
