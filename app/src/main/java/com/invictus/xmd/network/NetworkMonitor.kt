package com.invictus.xmd.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.invictus.xmd.service.DownloadService

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

    /** True if the currently active network is metered (cellular, or a
     *  Wi-Fi hotspot the user/carrier marked as metered) -- used by the
     *  daily mobile-data limit to tell "mobile" usage apart from Wi-Fi,
     *  independent of [isOnWifi]'s Wi-Fi-vs-cellular transport check. */
    fun isMetered(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun unregister(context: Context, callback: ConnectivityManager.NetworkCallback) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    /** True if there's a working internet connection right now, on ANY
     *  transport (Wi-Fi, cellular, ethernet, ...) -- unlike [isOnWifi] this
     *  doesn't care which transport, just whether the device is online at
     *  all. Used to tell "this request failed because the link died" apart
     *  from "this request failed because there's no internet whatsoever". */
    fun hasInternet(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Registers [onAvailable] / [onLost] for any-transport internet
     * connectivity -- fires regardless of the Wi-Fi-only downloads setting,
     * so a total outage (airplane mode, no signal, router down) can pause
     * live downloads as "Waiting for network" and auto-resume them the
     * moment connectivity of any kind returns, the same way Chrome does.
     * Caller owns the lifecycle and must pass the same callback to
     * [unregister] (typically in Service.onDestroy()).
     */
    fun registerInternet(
        context: Context,
        onAvailable: () -> Unit,
        onLost: () -> Unit
    ): ConnectivityManager.NetworkCallback {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    onAvailable()
                }
            }

            override fun onLost(network: Network) {
                // Only fire if there's truly nothing left -- onLost can fire
                // while switching between two networks that both work fine.
                if (!hasInternet(context)) onLost()
            }
        }
        cm.registerNetworkCallback(request, callback)
        return callback
    }
}
