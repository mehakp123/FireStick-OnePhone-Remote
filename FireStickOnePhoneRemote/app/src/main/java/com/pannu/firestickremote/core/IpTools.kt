package com.pannu.firestickremote.core

object IpTools {
    private val ipv4Regex = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")

    fun isIpv4(value: String): Boolean = ipv4Regex.matches(value.trim())

    fun isPrivateIpv4(value: String): Boolean {
        if (!isIpv4(value)) return false
        val octets = value.trim().split('.').map(String::toInt)
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            else -> false
        }
    }

    fun subnet24(value: String): String? {
        if (!isIpv4(value)) return null
        val parts = value.trim().split('.')
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    fun hostRange(subnet: String): Sequence<String> = sequence {
        if (!Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(subnet)) return@sequence
        for (host in 2..254) yield("$subnet.$host")
    }
}
