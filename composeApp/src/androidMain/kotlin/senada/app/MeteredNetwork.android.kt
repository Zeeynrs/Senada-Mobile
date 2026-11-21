package senada.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import coil3.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private var networkState: StateFlow<Boolean>? = null

@Synchronized
internal actual fun meteredNetwork(context: PlatformContext): StateFlow<Boolean> =
    networkState ?: observeNetwork(context.applicationContext).also { networkState = it }

private fun NetworkCapabilities.isMetered() =
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

private fun observeNetwork(context: PlatformContext): StateFlow<Boolean> {
    val state = MutableStateFlow(false)
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return state
    try {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                state.value = capabilities.isMetered()
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        })
    } catch (_: SecurityException) {
        state.value = false
    }
    return state
}
