package com.hoco.routermanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address

object Discovery {
    class Wifi(val network: Network, val gateway: String?)

    /** Finds the connected Wi-Fi network even if Android is using mobile data as its default. */
    @Suppress("DEPRECATION")
    fun findWifi(ctx: Context): Wifi? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val gw = cm.getLinkProperties(n)?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress
            return Wifi(n, gw)
        }
        return null
    }

    /** Current SSID; null if Android hides it (location permission missing). */
    @Suppress("DEPRECATION")
    fun ssid(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val s = wm.connectionInfo?.ssid ?: return null
        return s.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }
}
