package com.pannu.firestickremote.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("firetv_rescue", Context.MODE_PRIVATE)
    private val alias = "firetv_rescue_token_key"

    fun saveConnection(ip: String, token: String, modelPosition: Int) {
        prefs.edit()
            .putString("ip", ip)
            .putString("token", encrypt(token))
            .putInt("model", modelPosition)
            .apply()
    }

    fun saveWifiHints(oldSsid: String, newSsid: String) {
        prefs.edit().putString("old_ssid", oldSsid).putString("new_ssid", newSsid).apply()
    }

    fun getIp(): String = prefs.getString("ip", "") ?: ""
    fun getToken(): String = decrypt(prefs.getString("token", "") ?: "")
    fun getModelPosition(): Int = prefs.getInt("model", 0)
    fun getOldSsid(): String = prefs.getString("old_ssid", "") ?: ""
    fun getNewSsid(): String = prefs.getString("new_ssid", "") ?: ""

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val all = Base64.decode(value, Base64.NO_WRAP)
            if (all.size <= 12) return ""
            val iv = all.copyOfRange(0, 12)
            val encrypted = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
