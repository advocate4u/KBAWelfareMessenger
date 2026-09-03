package com.example.myadvlicensemanager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

object LicenseAuthority {
    private const val PREFS = "myadv_manager"
    private const val ROLE = "role"
    private const val PHONE = "phone"
    private const val KEY_CIPHERTEXT = "private_key"
    private const val KEY_IV = "private_key_iv"
    private const val KS = "AndroidKeyStore"
    private const val WRAP = "MyAdv.Manager.WrapKey"
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    enum class ManagerRole { SUPER_ADMIN, ADMIN }
    data class LicenseOptions(
        val validatePhone: Boolean = true, val sms: Boolean = true, val bulkSms: Boolean = true,
        val smsLogs: Boolean = true, val advocateDiary: Boolean = true, val advocateHelper: Boolean = true,
        val editMessageOnScreen: Boolean = true, val skipAlreadySent: Boolean = true,
        val confirmBeforeBulkSend: Boolean = true, val loggingEnabled: Boolean = true,
        val removeDuplicates: Boolean = true, val skipInvalidNumbers: Boolean = true
    )
    data class License(
        val id: String, val phone: String, val role: ManagerRole,
        val issueDate: LocalDate, val expiry: LocalDate, val options: LicenseOptions, val token: String
    )

    fun role(c: Context): ManagerRole? = c.getSharedPreferences(PREFS, 0)
        .getString(ROLE, null)?.let { runCatching { ManagerRole.valueOf(it) }.getOrNull() }
    fun phone(c: Context): String? = c.getSharedPreferences(PREFS, 0).getString(PHONE, null)

    fun hasKey(c: Context): Boolean {
        val prefs = c.getSharedPreferences(PREFS, 0)
        if (prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)) return true
        return provisionBundledKey(c)
    }

    fun configureManager(c: Context, managerRole: ManagerRole, managerPhone: String?): Boolean {
        val phone = managerPhone?.let(::normalize) ?: ""
        c.getSharedPreferences(PREFS, 0).edit()
            .putString(ROLE, managerRole.name).putString(PHONE, phone).apply()
        return true
    }

    fun createLicense(c: Context, target: String, role: ManagerRole, issueDate: LocalDate,
                      expiry: LocalDate, options: LicenseOptions): License? {
        if (!hasKey(c) || expiry.isBefore(issueDate) || expiry.isBefore(LocalDate.now())) return null
        val phone = normalize(target) ?: return null
        val id = generateLicenseId()
        val payload = buildString {
            appendLine("version=3")
            appendLine("license=$id")
            appendLine("phone=$phone")
            appendLine("role=$role")
            appendLine("issue=${issueDate.format(fmt)}")
            appendLine("expiry=${expiry.format(fmt)}")
            appendLine("validatePhone=${options.validatePhone}")
            appendLine("sms=${options.sms}")
            appendLine("bulkSms=${options.bulkSms}")
            appendLine("smsLogs=${options.smsLogs}")
            appendLine("advocateDiary=${options.advocateDiary}")
            appendLine("advocateHelper=${options.advocateHelper}")
            appendLine("editMessageOnScreen=${options.editMessageOnScreen}")
            appendLine("skipAlreadySent=${options.skipAlreadySent}")
            appendLine("confirmBeforeBulkSend=${options.confirmBeforeBulkSend}")
            appendLine("loggingEnabled=${options.loggingEnabled}")
            appendLine("removeDuplicates=${options.removeDuplicates}")
            append("skipInvalidNumbers=${options.skipInvalidNumbers}")
        }
        val token = sign(c, payload) ?: return null
        return License(id, phone, role, issueDate, expiry, options, token)
    }

    private fun generateLicenseId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun block() = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "ANI${chars[Random.nextInt(chars.length)]}-${block()}-${block()}-${block()}"
    }

    private fun sign(c: Context, payload: String): String? = try {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(decryptKey(c))
        val bytes = payload.toByteArray(Charsets.UTF_8)
        signature.update(bytes)
        Base64.encodeToString(bytes, Base64.NO_WRAP) + "." +
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    private fun normalize(value: String): String? {
        var n = value.trim().replace(Regex("[^0-9+]"), "")
        if (n.startsWith("+")) n = n.substring(1)
        if (n.startsWith("00")) n = n.substring(2)
        return when {
            n.length == 10 && n.all(Char::isDigit) -> "91$n"
            n.length == 12 && n.startsWith("91") && n.all(Char::isDigit) -> n
            else -> null
        }
    }

    private fun provisionBundledKey(c: Context): Boolean = try {
        val encoded = BuildConfig.MYADV_SIGNING_PRIVATE_KEY_B64.trim()
        if (encoded.isBlank()) return false
        val keyBytes = Base64.decode(encoded, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        saveKey(c, key)
    } catch (_: Exception) { false }

    private fun saveKey(c: Context, key: PrivateKey): Boolean = try {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, wrapKey())
        val encrypted = cipher.doFinal(key.encoded)
        c.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
        true
    } catch (_: Exception) { false }

    private fun wrapKey(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        (ks.getKey(WRAP, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS)
        generator.init(KeyGenParameterSpec.Builder(
            WRAP, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }

    private fun decryptKey(c: Context): PrivateKey {
        val prefs = c.getSharedPreferences(PREFS, 0)
        val encrypted = Base64.decode(prefs.getString(KEY_CIPHERTEXT, null) ?: error("key"), Base64.DEFAULT)
        val iv = Base64.decode(prefs.getString(KEY_IV, null) ?: error("iv"), Base64.DEFAULT)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, wrapKey(),
            javax.crypto.spec.GCMParameterSpec(128, iv))
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(cipher.doFinal(encrypted)))
    }
}
