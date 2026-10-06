package digital.vmstudio.code.core.common.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether the device currently has validated internet access.
 *
 * The app must never claim a remote operation succeeded while offline (spec: offline
 * mode), so connectivity is observed rather than probed at call time.
 */
interface NetworkMonitor {
    /** Emits the current state immediately, then on every change. */
    val isOnline: Flow<Boolean>

    /**
     * A quick check before a request, or null when unknown. Collecting [isOnline]
     * for that registered and removed a system callback on every request.
     */
    fun isOnlineNow(): Boolean? = null
}

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        // Track networks by identity: a device can hold Wi-Fi and cellular at once,
        // and losing one does not mean losing connectivity.
        val validated = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (usable) validated += network else validated -= network
                trySend(validated.isNotEmpty())
            }

            override fun onLost(network: Network) {
                validated -= network
                trySend(validated.isNotEmpty())
            }

            override fun onUnavailable() {
                trySend(validated.isNotEmpty())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)

        trySend(manager.currentlyOnline())

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .conflate()

    /**
     * Any network that offers internet counts, validated or not: a gateway on the
     * local Wi-Fi works even when that Wi-Fi has no route to the internet, and the
     * request itself reports a real failure.
     */
    override fun isOnlineNow(): Boolean? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun ConnectivityManager.currentlyOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
