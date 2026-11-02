package vn.thanhtaxixanh.bg98g00107.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * 70mai Wi-Fi typically has no Internet, so Android may prefer mobile data.
 * Explicitly select the Wi-Fi network for local RTSP sockets without changing
 * the process default (maps and other network requests must keep working).
 */
internal object CameraWifiNetwork {
    fun find(context: Context): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return null
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }
}
