package com.pannu.firestickremote.network

import com.pannu.firestickremote.core.IpTools
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkInspector {
    data class InterfaceInfo(val name: String, val address: String, val subnet: String)

    fun hotspotCandidates(): List<InterfaceInfo> {
        val result = mutableListOf<InterfaceInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val name = networkInterface.name.lowercase()
                val likelyLocal = listOf("wlan", "ap", "softap", "swlan", "eth", "rndis").any { name.contains(it) }
                if (!likelyLocal) continue
                for (address in Collections.list(networkInterface.inetAddresses)) {
                    if (address !is Inet4Address || address.isLoopbackAddress) continue
                    val ip = address.hostAddress ?: continue
                    if (!IpTools.isPrivateIpv4(ip)) continue
                    val subnet = IpTools.subnet24(ip) ?: continue
                    result += InterfaceInfo(networkInterface.name, ip, subnet)
                }
            }
        } catch (_: Exception) {
        }
        return result.distinctBy { it.subnet }
    }

    fun candidateSubnets(): List<String> {
        val detected = hotspotCandidates().map { it.subnet }
        val fallbacks = listOf(
            "192.168.43", "192.168.137", "192.168.232", "192.168.233",
            "192.168.162", "192.168.49", "172.20.10"
        )
        return (detected + fallbacks).distinct().take(8)
    }
}
