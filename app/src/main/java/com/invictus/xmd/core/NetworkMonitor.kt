package com.invictus.xmd.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Thin wrapper around ConnectivityManager used only for the Wi-Fi-only
 * downloads setting -- answers "is the active network Wi-Fi right now" and
 * lets [DownloadService] listen for Wi-Fi being lost/regained so it can
 * pause/resume live downloads without polling.
 */
object NetworkMonitor {

    /** True if the currently active network is Wi-Fi (or Ethernet, treated
     *  the same as "not metered cellular" -- e.g. an emulator/TV box). */
    fun isOnWifi(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Registers [onWifiAvailable] / [onWifiLost] for as long as the returned
     * callback stays registered -- caller owns the lifecycle and must pass
     * the same callback to [unregister] (typically in Service.onDestroy()).
     */
    fun register(
        context: Context,
        onWifiAvailable: () -> Unit,
        onWifiLost: () -> Unit
    ): ConnectivityManager.NetworkCallback {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                ) {
                    onWifiAvailable()
                } else {
                    onWifiLost()
                }
            }

            override fun onLost(network: Network) {
                // Covers Wi-Fi dropping to no connectivity at all, not just
                // switching to cellular (onCapabilitiesChanged handles that).
                if (!isOnWifi(context)) onWifiLost()
            }
        }
        cm.registerNetworkCallback(request, callback)
        return callback
    }

    fun unregister(context: Context, callback: ConnectivityManager.NetworkCallback) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
