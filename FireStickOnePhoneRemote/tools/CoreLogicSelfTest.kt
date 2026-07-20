import com.pannu.firestickremote.core.FireTvEndpoints
import com.pannu.firestickremote.core.FireTvModel
import com.pannu.firestickremote.core.IpTools
import com.pannu.firestickremote.core.ProtocolMode

fun checkCondition(value: Boolean, message: String) {
    if (!value) throw IllegalStateException(message)
}

fun main() {
    checkCondition(IpTools.isPrivateIpv4("192.168.43.1"), "192.168 hotspot must be private")
    checkCondition(IpTools.isPrivateIpv4("172.20.10.1"), "172.20 hotspot must be private")
    checkCondition(!IpTools.isPrivateIpv4("8.8.8.8"), "public IP must be rejected")
    checkCondition(IpTools.subnet24("192.168.162.1") == "192.168.162", "subnet extraction")
    checkCondition(FireTvEndpoints.navigation("select") == "/v1/FireTV?action=select", "nav endpoint")
    checkCondition(FireTvEndpoints.launchApp("com.netflix.ninja").endsWith("com.netflix.ninja"), "app endpoint")
    checkCondition(FireTvModel.entries.size >= 10, "model/profile list is incomplete")
    checkCondition(FireTvModel.entries.any { it.profile.mode == ProtocolMode.VEGA_EXPERIMENTAL }, "Vega profile missing")
    checkCondition(FireTvModel.entries.all { it.profile.connectTimeoutMs in 500..5000 }, "invalid timeout")
    println("PASS: ${FireTvModel.entries.size} model profiles, endpoints, and IP logic validated")
}
