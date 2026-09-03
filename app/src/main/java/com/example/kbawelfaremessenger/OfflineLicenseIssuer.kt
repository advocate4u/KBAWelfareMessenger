package com.example.kbawelfaremessenger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Offline license issuer used only by an approved administrator.
 *
 * The RSA private key is NOT compiled into the APK. It is provisioned
 * once on the administrator's device and encrypted at rest with an
 * Android Keystore AES key. The private key is then used locally to
 * sign licenses without GitHub or internet access.
 */
object OfflineLicenseIssuer {
    private const val PREFS = "offline_license_issuer"
    private const val KEY_CIPHERTEXT = "encrypted_private_key"
    private const val KEY_IV = "private_key_iv"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val WRAP_KEY_ALIAS = "MyAdv.OfflineLicenseWrapKey"
    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    data class GeneratedLicense(val licenseId: String, val signedToken: String, val phone: String, val expiry: LocalDate)

    fun hasSigningKey(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_CIPHERTEXT)

    fun installSigningKey(context: Context, pem: String): Boolean {
        if (!SecurityManager.isAdmin(context)) return false
        return try {
            val privateKey = parsePrivateKey(pem)
            getOrCreateWrapKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getWrapKey())
            val encrypted = cipher.doFinal(privateKey.encoded)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
            true
        } catch (_: Exception) { false }
    }

    fun generateLicense(context: Context, phoneInput: String, expiry: LocalDate): GeneratedLicense? {
        if (!SecurityManager.isAdmin(context) || !hasSigningKey(context)) return null
        val phone = normalizePhone(phoneInput) ?: return null
        if (expiry.isBefore(LocalDate.now())) return null
        return try {
            val payload = "phone=$phone\nexpiry=${expiry.format(java.time.format.DateTimeFormatter.ofPattern(DATE_PATTERN))}"
            val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
            val privateKey = decryptPrivateKey(context)
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(payloadBytes)
            val signatureBytes = signature.sign()
            val token = Base64.encodeToString(payloadBytes, Base64.NO_WRAP) + "." +
                Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
            GeneratedLicense(randomLicenseId(), token, phone, expiry)
        } catch (_: Exception) { null }
    }

    private fun randomLicenseId(): String {
        val random = SecureRandom()
        fun group() = buildString { repeat(4) { append(ID_CHARS[random.nextInt(ID_CHARS.length)]) } }
        return "ANI${ID_CHARS[random.nextInt(ID_CHARS.length)]}-${group()}-${group()}-${group()}"
    }

    private fun normalizePhone(input: String): String? {
        var number = input.trim().replace(Regex("[^0-9+]"), "")
        if (number.startsWith("+")) number = number.substring(1)
        if (number.startsWith("00")) number = number.substring(2)
        return when {
            number.length == 10 && number.all { it.isDigit() } -> "91$number"
            number.length == 12 && number.startsWith("91") && number.all { it.isDigit() } -> number
            else -> null
        }
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        require(cleaned.isNotBlank())
        val bytes = Base64.decode(cleaned, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun getWrapKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (ks.getKey(WRAP_KEY_ALIAS, null) as? SecretKey) ?: throw IllegalStateException("Signing storage key missing")
    }

    private fun getOrCreateWrapKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = ks.getKey(WRAP_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(
            WRAP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun decryptPrivateKey(context: Context): PrivateKey {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ciphertext = Base64.decode(prefs.getString(KEY_CIPHERTEXT, null) ?: error("Signing key not configured"), Base64.DEFAULT)
        val iv = Base64.decode(prefs.getString(KEY_IV, null) ?: error("Signing key IV missing"), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getWrapKey(), GCMParameterSpec(128, iv))
        val pkcs8 = cipher.doFinal(ciphertext)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }
}
