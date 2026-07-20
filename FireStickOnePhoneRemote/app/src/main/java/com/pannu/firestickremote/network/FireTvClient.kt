package com.pannu.firestickremote.network

import com.pannu.firestickremote.core.FireTvEndpoints
import com.pannu.firestickremote.core.FireTvProfile
import com.pannu.firestickremote.core.IpTools
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FireTvClient(private val profile: FireTvProfile) {
    data class Result(
        val success: Boolean,
        val code: Int = -1,
        val body: String = "",
        val error: String = "",
        val transport: String = ""
    )

    fun wake(ip: String): Result = request(
        ip = ip,
        port = FireTvEndpoints.DIAL_PORT,
        path = FireTvEndpoints.WAKE,
        method = "POST",
        body = "",
        token = null,
        preferHttps = false,
        contentType = "text/plain"
    )

    fun requestPin(ip: String, friendlyName: String): Result {
        wake(ip)
        return controlRequest(
            ip,
            FireTvEndpoints.PIN_DISPLAY,
            "POST",
            JSONObject().put("friendlyName", friendlyName).toString(),
            null
        )
    }

    fun verifyPin(ip: String, pin: String): Pair<Result, String> {
        val result = controlRequest(
            ip,
            FireTvEndpoints.PIN_VERIFY,
            "POST",
            JSONObject().put("pin", pin).toString(),
            null
        )
        val token = try {
            JSONObject(result.body).optString("description", "")
        } catch (_: Exception) {
            ""
        }
        return result to token
    }

    fun getStatus(ip: String, token: String?): Result =
        controlRequest(ip, FireTvEndpoints.STATUS, "GET", null, token)

    fun getProperties(ip: String, token: String?): Result =
        controlRequest(ip, FireTvEndpoints.PROPERTIES, "GET", null, token)

    fun sendNavigation(ip: String, token: String, action: String): Result {
        require(FireTvEndpoints.navigationActions.contains(action)) { "Unknown navigation action" }
        val simple = action in setOf("home", "back", "menu")
        if (simple) return controlRequest(ip, FireTvEndpoints.navigation(action), "POST", "", token)

        val down = controlRequest(
            ip,
            FireTvEndpoints.navigation(action),
            "POST",
            JSONObject().put("keyActionType", "keyDown").toString(),
            token
        )
        if (!down.success || !profile.sendKeyUp) return down
        Thread.sleep(55)
        val up = controlRequest(
            ip,
            FireTvEndpoints.navigation(action),
            "POST",
            JSONObject().put("keyActionType", "keyUp").toString(),
            token
        )
        return if (up.success) down else up
    }

    fun sendText(ip: String, token: String, text: String): Result {
        var last = Result(true, 200, "")
        for (char in text) {
            last = controlRequest(
                ip,
                FireTvEndpoints.TEXT,
                "POST",
                JSONObject().put("text", char.toString()).toString(),
                token
            )
            if (!last.success) break
            Thread.sleep(25)
        }
        return last
    }

    fun playPause(ip: String, token: String): Result =
        controlRequest(ip, FireTvEndpoints.media("play"), "POST", "", token)

    fun seek(ip: String, token: String, forward: Boolean): Result {
        val body = JSONObject()
            .put("direction", if (forward) "forward" else "backward")
            .put("durationInSeconds", "10")
            .put("speed", "1")
            .toString()
        return controlRequest(ip, FireTvEndpoints.media("scan"), "POST", body, token)
    }

    fun launchApp(ip: String, token: String, packageName: String): Result =
        controlRequest(ip, FireTvEndpoints.launchApp(packageName), "POST", "", token)

    fun probe(ip: String, token: String?): List<String> {
        val report = mutableListOf<String>()
        val wake = wake(ip)
        report += "DIAL 8009: ${format(wake)}"
        val status = getStatus(ip, token)
        report += "Control 8080 status: ${format(status)}"
        if (status.success) report += "Status body: ${status.body.take(300)}"
        val props = getProperties(ip, token)
        report += "Properties: ${format(props)}"
        if (props.success) report += "Properties body: ${props.body.take(400)}"
        return report
    }

    private fun format(result: Result): String = when {
        result.success -> "PASS (${result.transport} HTTP ${result.code})"
        else -> "FAIL (${result.transport}) ${result.error.ifBlank { "HTTP ${result.code}" }}"
    }

    private fun controlRequest(
        ip: String,
        path: String,
        method: String,
        body: String?,
        token: String?
    ): Result {
        val https = request(ip, FireTvEndpoints.CONTROL_PORT, path, method, body, token, true)
        if (https.success || !profile.allowHttpFallback) return https
        return request(ip, FireTvEndpoints.CONTROL_PORT, path, method, body, token, false)
    }

    private fun request(
        ip: String,
        port: Int,
        path: String,
        method: String,
        body: String?,
        token: String?,
        preferHttps: Boolean,
        contentType: String = "application/json; charset=utf-8"
    ): Result {
        if (!IpTools.isPrivateIpv4(ip)) return Result(false, error = "Only private local IP addresses are allowed")
        val scheme = if (preferHttps) "https" else "http"
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$scheme://$ip:$port$path")
            connection = url.openConnection() as HttpURLConnection
            if (connection is HttpsURLConnection) configureLocalTls(connection as HttpsURLConnection)
            connection.requestMethod = method
            connection.connectTimeout = profile.connectTimeoutMs
            connection.readTimeout = profile.readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", contentType)
            if (port == FireTvEndpoints.CONTROL_PORT) {
                connection.setRequestProperty("x-api-key", FireTvEndpoints.API_KEY)
                connection.setRequestProperty("user-agent", "FireStickRescueRemote/1.0 okhttp-compatible")
                if (!token.isNullOrBlank()) connection.setRequestProperty("x-client-token", token)
            }
            if (method != "GET") {
                connection.doOutput = true
                val bytes = (body ?: "").toByteArray(StandardCharsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val responseBody = stream?.use {
                BufferedReader(InputStreamReader(it)).readText()
            }.orEmpty()
            Result(code in 200..299, code, responseBody, transport = scheme.uppercase())
        } catch (e: Exception) {
            Result(false, error = "${e.javaClass.simpleName}: ${e.message ?: "connection failed"}", transport = scheme.uppercase())
        } finally {
            connection?.disconnect()
        }
    }

    private fun configureLocalTls(connection: HttpsURLConnection) {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        })
        val context = SSLContext.getInstance("TLS")
        context.init(null, trustAll, SecureRandom())
        connection.sslSocketFactory = context.socketFactory
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
    }
}
