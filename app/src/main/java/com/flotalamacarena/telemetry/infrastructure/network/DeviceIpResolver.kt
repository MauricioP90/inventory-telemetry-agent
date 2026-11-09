package com.flotalamacarena.telemetry.infrastructure.network

import android.util.Log
import com.flotalamacarena.telemetry.domain.ports.IpResolver
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Resuelve la dirección IPv4 local del dispositivo consultando las interfaces de red activas
 * (Wi-Fi, Datos Móviles 4G/LTE, Ethernet USB).
 */
class DeviceIpResolver : IpResolver {

    override fun resolveIpAddress(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: ""
                        if (hostAddress.isNotBlank()) {
                            return hostAddress
                        }
                    }
                }
            }
            DEFAULT_IP
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo la dirección IP local: ${e.message}")
            DEFAULT_IP
        }
    }

    companion object {
        private const val TAG = "DeviceIpResolver"
        private const val DEFAULT_IP = "127.0.0.1"
    }
}
