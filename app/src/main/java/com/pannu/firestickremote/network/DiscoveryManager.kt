package com.pannu.firestickremote.network

import com.pannu.firestickremote.core.FireTvEndpoints
import com.pannu.firestickremote.core.FireTvProfile
import com.pannu.firestickremote.core.IpTools
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiscoveryManager(private val profile: FireTvProfile) {
    fun discover(onProgress: (String) -> Unit = {}): List<String> {
        val found = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        onProgress("Searching with DIAL/SSDP…")
        discoverSsdp().forEach { found += it }
        if (found.isNotEmpty()) onProgress("DIAL found: ${found.joinToString()}")

        val subnets = NetworkInspector.candidateSubnets()
        onProgress("Scanning hotspot subnets: ${subnets.joinToString()}")
        val pool = Executors.newFixedThreadPool(48)
        for (subnet in subnets) {
            for (ip in IpTools.hostRange(subnet)) {
                pool.submit {
                    if (found.contains(ip)) return@submit
                    val controlOpen = portOpen(ip, FireTvEndpoints.CONTROL_PORT, profile.connectTimeoutMs)
                    val dialOpen = if (!controlOpen) portOpen(ip, FireTvEndpoints.DIAL_PORT, profile.connectTimeoutMs) else true
                    if (controlOpen || dialOpen) found += ip
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(50, TimeUnit.SECONDS)
        return found.sorted()
    }

    private fun portOpen(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout.coerceAtMost(900))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun discoverSsdp(): Set<String> {
        val found = mutableSetOf<String>()
        val request = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n"
            ).toByteArray()

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 650
                val target = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(request, request.size, target, 1900)) }
                val deadline = System.currentTimeMillis() + 2200
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val buffer = ByteArray(4096)
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        val location = text.lineSequence()
                            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()
                        val host = location?.let { URI(it).host } ?: packet.address?.hostAddress
                        if (host != null && IpTools.isPrivateIpv4(host)) found += host
                    } catch (_: java.net.SocketTimeoutException) {
                        // Continue until the overall deadline.
                    }
                }
            }
        } catch (_: Exception) {
        }
        return found
    }
}
