@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package senada.app

import coil3.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

private object NetworkState {
    val metered = MutableStateFlow(false)
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            metered.value = path != null && nw_path_get_status(path) == nw_path_status_satisfied &&
                (nw_path_is_expensive(path) || nw_path_is_constrained(path))
        }
        nw_path_monitor_start(monitor)
    }
}

internal actual fun meteredNetwork(context: PlatformContext): StateFlow<Boolean> = NetworkState.metered
