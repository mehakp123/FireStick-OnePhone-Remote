package com.pannu.firestickremote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pannu.firestickremote.core.FireTvModel
import com.pannu.firestickremote.core.IpTools
import com.pannu.firestickremote.network.DiscoveryManager
import com.pannu.firestickremote.network.FireTvClient
import com.pannu.firestickremote.network.NetworkInspector
import com.pannu.firestickremote.storage.SecurePrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SecurePrefs
    private val executor = Executors.newSingleThreadExecutor()
    private val logBuffer = StringBuilder()

    private lateinit var modelSpinner: Spinner
    private lateinit var ipAddress: EditText
    private lateinit var pinInput: EditText
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var oldSsid: EditText
    private lateinit var oldPassword: EditText
    private lateinit var newSsid: EditText
    private lateinit var newPassword: EditText
    private lateinit var textInput: EditText

    private var token: String = ""

    private val nearbyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        appendLog("Nearby Wi-Fi permission: ${if (granted) "granted" else "not granted; IP scan will still be attempted"}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SecurePrefs(this)
        bindViews()
        setupModelSpinner()
        restoreState()
        setupActions()
        requestNearbyPermissionIfNeeded()
        appendLog("App started. Local interface candidates: ${NetworkInspector.hotspotCandidates()}")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onPause() {
        prefs.saveWifiHints(oldSsid.text.toString().trim(), newSsid.text.toString().trim())
        super.onPause()
    }

    private fun bindViews() {
        modelSpinner = findViewById(R.id.modelSpinner)
        ipAddress = findViewById(R.id.ipAddress)
        pinInput = findViewById(R.id.pinInput)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        oldSsid = findViewById(R.id.oldSsid)
        oldPassword = findViewById(R.id.oldPassword)
        newSsid = findViewById(R.id.newSsid)
        newPassword = findViewById(R.id.newPassword)
        textInput = findViewById(R.id.textInput)
    }

    private fun setupModelSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            FireTvModel.displayNames()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter
    }

    private fun restoreState() {
        ipAddress.setText(prefs.getIp())
        token = prefs.getToken()
        modelSpinner.setSelection(prefs.getModelPosition().coerceIn(0, FireTvModel.entries.lastIndex))
        oldSsid.setText(prefs.getOldSsid())
        newSsid.setText(prefs.getNewSsid())
        if (token.isNotBlank() && ipAddress.text.toString().isNotBlank()) {
            updateStatus("Saved pairing available", true)
        }
    }

    private fun setupActions() {
        findViewById<Button>(R.id.openHotspot).setOnClickListener { openHotspotSettings() }
        findViewById<Button>(R.id.scanButton).setOnClickListener { scanForFireTv() }
        findViewById<Button>(R.id.diagnoseButton).setOnClickListener { diagnose() }
        findViewById<Button>(R.id.requestPin).setOnClickListener { requestPin() }
        findViewById<Button>(R.id.verifyPin).setOnClickListener { verifyPin() }
        findViewById<Button>(R.id.sendNewPassword).setOnClickListener { sendNewWifiPassword() }
        findViewById<Button>(R.id.copyNewPassword).setOnClickListener { copyNewPassword() }
        findViewById<Button>(R.id.shareLog).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.sendTextButton).setOnClickListener { sendText() }

        bindCommand(R.id.upButton, "Up") { it.sendNavigation(currentIp(), token, "dpad_up") }
        bindCommand(R.id.downButton, "Down") { it.sendNavigation(currentIp(), token, "dpad_down") }
        bindCommand(R.id.leftButton, "Left") { it.sendNavigation(currentIp(), token, "dpad_left") }
        bindCommand(R.id.rightButton, "Right") { it.sendNavigation(currentIp(), token, "dpad_right") }
        bindCommand(R.id.selectButton, "Select") { it.sendNavigation(currentIp(), token, "select") }
        bindCommand(R.id.backButton, "Back") { it.sendNavigation(currentIp(), token, "back") }
        bindCommand(R.id.homeButton, "Home") { it.sendNavigation(currentIp(), token, "home") }
        bindCommand(R.id.menuButton, "Menu") { it.sendNavigation(currentIp(), token, "menu") }
        bindCommand(R.id.playButton, "Play/Pause") { it.playPause(currentIp(), token) }
        bindCommand(R.id.rewindButton, "Rewind") { it.seek(currentIp(), token, false) }
        bindCommand(R.id.forwardButton, "Fast-forward") { it.seek(currentIp(), token, true) }
        bindCommand(R.id.netflixButton, "Launch Netflix") { it.launchApp(currentIp(), token, "com.netflix.ninja") }
        bindCommand(R.id.youtubeButton, "Launch YouTube") { it.launchApp(currentIp(), token, "com.amazon.firetv.youtube") }
        bindCommand(R.id.primeButton, "Launch Prime Video") { it.launchApp(currentIp(), token, "com.amazon.cloud9") }
    }

    private fun selectedModel(): FireTvModel = FireTvModel.fromPosition(modelSpinner.selectedItemPosition)
    private fun client(): FireTvClient = FireTvClient(selectedModel().profile)

    private fun currentIp(): String = ipAddress.text.toString().trim()

    private fun validateIp(): Boolean {
        val ip = currentIp()
        if (!IpTools.isPrivateIpv4(ip)) {
            updateStatus("Enter a valid private Fire Stick IP", false)
            appendLog("Rejected IP '$ip': not a private IPv4 address")
            return false
        }
        return true
    }

    private fun requirePairing(): Boolean {
        if (!validateIp()) return false
        if (token.isBlank()) {
            updateStatus("Pair with the PIN first", false)
            return false
        }
        return true
    }

    private fun scanForFireTv() {
        setBusy(true, "Scanning hotspot and Wi-Fi networks…")
        executor.execute {
            val model = selectedModel()
            appendLog("Scan started with profile: ${model.profile.displayName}")
            val found = DiscoveryManager(model.profile).discover { appendLog(it) }
            runOnUiThread {
                setBusy(false)
                if (found.isEmpty()) {
                    updateStatus("No Fire Stick found. Confirm it joined the old-name hotspot.", false)
                    appendLog("Scan finished: no device exposing port 8009 or 8080 was found")
                } else {
                    val saved = prefs.getIp()
                    val chosen = found.firstOrNull { it == saved } ?: found.first()
                    ipAddress.setText(chosen)
                    updateStatus("Found ${found.size} candidate(s): $chosen", true)
                    appendLog("Scan finished: ${found.joinToString()}")
                }
            }
        }
    }

    private fun requestPin() {
        if (!validateIp()) return
        setBusy(true, "Requesting PIN…")
        executor.execute {
            val result = client().requestPin(currentIp(), "One Phone Rescue Remote")
            runOnUiThread {
                setBusy(false)
                if (result.success) {
                    updateStatus("PIN requested. Enter the 4 digits shown on the TV.", true)
                } else {
                    updateStatus("Could not display PIN: ${result.error.ifBlank { "HTTP ${result.code}" }}", false)
                }
                appendResult("PIN display", result)
            }
        }
    }

    private fun verifyPin() {
        if (!validateIp()) return
        val pin = pinInput.text.toString().trim()
        if (!Regex("^\\d{4}$").matches(pin)) {
            updateStatus("Enter the 4-digit PIN shown on TV", false)
            return
        }
        setBusy(true, "Pairing…")
        executor.execute {
            val (result, receivedToken) = client().verifyPin(currentIp(), pin)
            runOnUiThread {
                setBusy(false)
                if (result.success && receivedToken.isNotBlank()) {
                    token = receivedToken
                    prefs.saveConnection(currentIp(), token, modelSpinner.selectedItemPosition)
                    updateStatus("Paired successfully. Remote is ready.", true)
                    pinInput.setText("")
                } else {
                    updateStatus("Pairing failed. Request a new PIN and try again.", false)
                }
                appendResult("PIN verify", result)
            }
        }
    }

    private fun diagnose() {
        if (!validateIp()) return
        setBusy(true, "Running diagnostics…")
        executor.execute {
            val report = client().probe(currentIp(), token.ifBlank { null })
            report.forEach { appendLog(it) }
            runOnUiThread {
                setBusy(false)
                val success = report.any { it.contains("PASS") }
                updateStatus(if (success) "Diagnostics completed" else "No Fire TV service responded", success)
            }
        }
    }

    private fun bindCommand(buttonId: Int, label: String, command: (FireTvClient) -> FireTvClient.Result) {
        findViewById<View>(buttonId).setOnClickListener {
            if (!requirePairing()) return@setOnClickListener
            executor.execute {
                val result = command(client())
                runOnUiThread {
                    if (!result.success) {
                        updateStatus("$label failed: ${result.error.ifBlank { "HTTP ${result.code}" }}", false)
                    } else {
                        updateStatus("$label sent", true)
                    }
                    appendResult(label, result)
                }
            }
        }
    }

    private fun sendText() {
        if (!requirePairing()) return
        val text = textInput.text.toString()
        if (text.isEmpty()) return
        setBusy(true, "Sending text…")
        executor.execute {
            val result = client().sendText(currentIp(), token, text)
            runOnUiThread {
                setBusy(false)
                if (result.success) {
                    updateStatus("Text sent", true)
                    textInput.setText("")
                } else {
                    updateStatus("Text failed: ${result.error.ifBlank { "HTTP ${result.code}" }}", false)
                }
                appendResult("Text (${text.length} characters)", result)
            }
        }
    }

    private fun openHotspotSettings() {
        val intent = Intent(Settings.ACTION_TETHER_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        appendLog("Opened hotspot settings. Use old SSID '${oldSsid.text.toString().trim()}'.")
    }

    private fun sendNewWifiPassword() {
        if (!requirePairing()) return
        val password = newPassword.text.toString()
        if (password.isBlank()) {
            updateStatus("Enter the new Wi-Fi password first", false)
            return
        }
        setBusy(true, "Sending new Wi-Fi password…")
        executor.execute {
            val result = client().sendText(currentIp(), token, password)
            runOnUiThread {
                setBusy(false)
                if (result.success) {
                    updateStatus("Password text sent. Select Connect on the TV.", true)
                } else {
                    updateStatus("Password send failed: ${result.error.ifBlank { "HTTP ${result.code}" }}", false)
                }
                appendResult("New Wi-Fi password (${password.length} characters)", result)
            }
        }
    }

    private fun copyNewPassword() {
        val password = newPassword.text.toString()
        if (password.isBlank()) {
            Toast.makeText(this, "Enter the new Wi-Fi password first", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("New Wi-Fi password", password))
        Toast.makeText(this, "New Wi-Fi password copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareLog() {
        val details = buildString {
            appendLine("Fire Stick Rescue Remote diagnostic report")
            appendLine("Model profile: ${selectedModel().profile.displayName}")
            appendLine("Fire Stick IP: ${currentIp()}")
            appendLine("Paired token present: ${token.isNotBlank()}")
            appendLine("Interfaces: ${NetworkInspector.hotspotCandidates()}")
            appendLine()
            append(logBuffer.toString())
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Fire Stick Rescue Remote diagnostics")
            putExtra(Intent.EXTRA_TEXT, details)
        }
        startActivity(Intent.createChooser(intent, "Share diagnostic report"))
    }

    private fun requestNearbyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            nearbyPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    private fun setBusy(busy: Boolean, message: String = "") {
        findViewById<View>(R.id.scanButton).isEnabled = !busy
        findViewById<View>(R.id.requestPin).isEnabled = !busy
        findViewById<View>(R.id.verifyPin).isEnabled = !busy
        findViewById<View>(R.id.diagnoseButton).isEnabled = !busy
        if (busy && message.isNotBlank()) updateStatus(message, true)
    }

    private fun updateStatus(message: String, success: Boolean) {
        statusText.text = "Status: $message"
        statusText.setTextColor(if (success) Color.rgb(88, 214, 141) else Color.rgb(255, 107, 107))
    }

    private fun appendResult(label: String, result: FireTvClient.Result) {
        val message = if (result.success) {
            "$label: PASS (${result.transport} HTTP ${result.code}) ${result.body.take(120)}"
        } else {
            "$label: FAIL (${result.transport}) ${result.error.ifBlank { "HTTP ${result.code} ${result.body.take(120)}" }}"
        }
        appendLog(message)
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(logBuffer) {
            logBuffer.append('[').append(time).append("] ").append(message).append('\n')
            if (logBuffer.length > 30000) logBuffer.delete(0, 5000)
        }
        runOnUiThread { logText.text = logBuffer.toString() }
    }
}
