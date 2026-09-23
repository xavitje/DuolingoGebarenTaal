package io.github.xavitje.signcoach.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class NetworkMonitor actual constructor() {
    private val _isOnline = MutableStateFlow(true) // optimistisch totdat start() de echte status weet
    actual val isOnline: StateFlow<Boolean> = _isOnline

    private var callback: ConnectivityManager.NetworkCallback? = null

    actual fun start() {
        val context = appContext?.get() ?: run {
            Log.w(TAG, "NetworkMonitor.start() vóór initialize() aangeroepen — kan status niet volgen")
            return
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        _isOnline.value = isCurrentlyConnected(cm)

        if (callback != null) return // al gestart
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }
            override fun onLost(network: Network) {
                _isOnline.value = isCurrentlyConnected(cm)
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        runCatching { cm.registerNetworkCallback(request, cb) }
            .onSuccess { callback = cb }
            .onFailure { Log.w(TAG, "Kon netwerk-callback niet registreren", it) }
    }

    actual fun stop() {
        val context = appContext?.get() ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    private fun isCurrentlyConnected(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "SignCoach"
        private var appContext: WeakReference<Context>? = null

        /** Moet vóór gebruik één keer aangeroepen worden, bv. in MainActivity.onCreate. */
        fun initialize(context: Context) {
            appContext = WeakReference(context.applicationContext)
        }
    }
}
