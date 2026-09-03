package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.security.cert.CertificateFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Offline RSA license verification. The private signing key is never stored here. */
object LicenseManager {
    private const val PREF_NAME = "kba_license"
    private const val KEY_LICENSE_ID = "license_id"
    private const val KEY_LICENSE_TOKEN = "license_token"
    private const val LICENSE_PREFIX = "ANI"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val PUBLIC_KEY_PEM = """
-----BEGIN CERTIFICATE-----
MIIFSTCCAzGgAwIBAgIIGt1ndg0Nz0AwDQYJKoZIhvcNAQEMBQAwUjELMAkGA1UE
BhMCSU4xEzARBgNVBAoTCkFkdm9jYXRlNFUxLjAsBgNVBAMTJUtCQVdlbGZhcmVN
ZXNzZW5nZXIgTGljZW5zZSBBdXRob3JpdHkwIBcNMjYwOTAzMTczNjIyWhgPMjA1
NDAxMTkxNzM2MjJaMFIxCzAJBgNVBAYTAklOMRMwEQYDVQQKEwpBZHZvY2F0ZTRV
MS4wLAYDVQQDEyVLQkFXZWxmYXJlTWVzc2VuZ2VyIExpY2Vuc2UgQXV0aG9yaXR5
MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsaZIXqE0CgS6aFIceKCt
dtkw7gtga3Z/BN80DN3pWbTAxcf5DEu6umjm9tbTy2/nafe5MuSPntQnPfpWlqfG
+B4xFTnYbVheNpbZea4XIXIITlZ2Efom6HQBWoht6Q01gKPx367DErKV5whplm+H
XbY2FxQ5SdUdmHKVJ7HRwIIA+TqvG/KRLEBPspxDwsb3NL5CWdGmdrhUW4lQGxA/
6MwUzmZu2C4SJcFDLnQty51AB2mZNIALkUPidOsC+yrnTHhwEPuXGgwpFSsgNtTo
aQMDq3NhEhcS3uOrZ3TYkzdiZQOo5YTRRLO53oQnwCdYoYTIUAu5SQrFUwkpewVL
Wk6GqvtGr7oXcRu8sJGlSM/5pG4MUakjWRRKyEosYmJn9rXGpjbeyBGlfkjpCI2b
JeuNrJMTGr8O7DqbYwBW5ByMySaJ/ns3x9HifOd+WmmMscqG8sxXCuZzmorY6uQP
KaFQ7daQv02+iFa0535wyoG1GEQexmU7Eb1pOTfcEErFUZ7WBxGnQdtIyZ6IWVmE
srnrOie4NPG32XR9jA6P/3ImSUfRUnVbZir9Hkae8P2hSfq0F3AjnRjd6yQpEYXq
tNDRMfy47Fo/yNRS5K+zx5OVUNYbTfKYR5u6HSWy0e1xYCRl/qwek07UJMKqCI37
PeCrpcJUlfIZwMotZjf6BEsCAwEAAaMhMB8wHQYDVR0OBBYEFC/Q+IzKRxxfpWB+
7moncBd6rMTmMA0GCSqGSIb3DQEBDAUAA4ICAQAIbE2ErQqlxVSAd2gpek9EYso8
my7JIzmIk747jmRwiRr2wtFrIbu1ZTN6gL0XPklfCeSuLsvCv5rl29zMZm1Hn2X9
lufIjMk0WNsvtMosPNbM3zh/2lLy+FN2N5Y/yj/u+eBorFsY0J8iC6d4fJztNWh3
cHSQRWHKtp9nXUiD+8FcD7B5emt1Ewp5Tv6PspIl4jQM3vFLYrm2OElQ261C8OcE
+w9fpZE3fCC1RAHLqP2VtaBRzKX06NfvMXvEHso660JqRtu5wi8WMuYR8XAmfNUh
eKubDgvLPkpD+7Cc8mvpAlR1LDF9NktZsThP9C/jbn2c0Udvo0OXnopK95h3M6wI
V6UcM+zJQ7f7Yd5Ul0Ee4I0HIQn1naqCvvcnQRhrC4Q8vPhRuVc6Wo5ZBKHlYhjZ
C0hr6ULOovyR95o/NVHdBjALfm8+024n8+IouOtWeokuss/rTzcntik0sT7Hb5Y9
j4zFS5wsMu9HQ8cmEznD6CJ8KwJF+CMCx9Qn04ZPEV5QFv9ax+IGGQEEJBAQKw2w
5CYbI17zNCvV6wn21WsHTfdIEciKEFAvalQCHj9o+HZmCVCRqBny4K/eC90ld1I
S9aingWN6kAbWpCjYb3WT2hVmUMhr/Njs8P4Q6DPcBnVwgAbwQfgIC3FXipQbcQB
yn90df4QmpIDXBKWYA==
-----END CERTIFICATE-----
""".trimIndent()

    data class License(val licenseId: String, val phone: String, val expiryDate: LocalDate, val role: UserRole)
    data class LicenseCheckResult(val allowed: Boolean, val message: String)

    fun installLicense(context: Context, licenseId: String, signedToken: String): LicenseCheckResult {
        return try {
            val cleanId = normaliseLicenseId(licenseId)
            if (!isValidLicenseIdFormat(cleanId)) return LicenseCheckResult(false, "Invalid license key format.")
            val license = verifySignedToken(cleanId, signedToken) ?: return LicenseCheckResult(false, "License verification failed.")
            if (LocalDate.now().isAfter(license.expiryDate)) return LicenseCheckResult(false, "This license has expired.")
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LICENSE_ID, license.licenseId)
                .putString(KEY_LICENSE_TOKEN, signedToken.trim())
                .apply()
            AppLogger.success(context, "LICENSE", "License installed successfully. ID: ${license.licenseId}")
            LicenseCheckResult(true, "License activated successfully for ${license.role.name}.")
        } catch (e: Exception) {
            AppLogger.error(context, "LICENSE", "License installation failed: ${e.message}")
            LicenseCheckResult(false, "Unable to install license.")
        }
    }

    fun getInstalledLicense(context: Context): License? = try {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_LICENSE_ID, null) ?: return null
        val token = prefs.getString(KEY_LICENSE_TOKEN, null) ?: return null
        verifySignedToken(normaliseLicenseId(id), token)
    } catch (_: Exception) { null }

    fun getValidLicense(context: Context): License? = getInstalledLicense(context)?.takeIf { !LocalDate.now().isAfter(it.expiryDate) }
    fun getLicensedPhone(context: Context): String? = getValidLicense(context)?.phone
    fun getLicenseId(context: Context): String? = getInstalledLicense(context)?.licenseId
    fun getExpiryDate(context: Context): LocalDate? = getInstalledLicense(context)?.expiryDate
    fun getLicenseRole(context: Context): UserRole? = getValidLicense(context)?.role
    fun isLicenseValid(context: Context): Boolean = getValidLicense(context) != null

    fun checkLicenseAndSmsPhone(context: Context, smsPhone: String?): LicenseCheckResult {
        val license = getValidLicense(context) ?: return if (getInstalledLicense(context) != null)
            LicenseCheckResult(false, "License has expired.") else LicenseCheckResult(false, "No valid KBA license is installed.")
        val actual = smsPhone?.let { normalisePhone(it) }
        if (actual.isNullOrBlank()) return LicenseCheckResult(false, "Unable to verify the phone number of the SMS SIM.\n\nSMS sending is blocked because the licensed phone number cannot be verified.")
        if (actual != license.phone) return LicenseCheckResult(false, "Licensed phone number does not match the SMS SIM.\n\nLicensed number: ${displayPhone(license.phone)}\nSMS SIM number: ${displayPhone(actual)}\n\nSMS sending is blocked.")
        return LicenseCheckResult(true, "License and SMS SIM verified.")
    }

    fun clearLicense(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        AppLogger.info(context, "LICENSE", "Installed license cleared.")
    }

    private fun verifySignedToken(licenseId: String, signedToken: String): License? {
        val parts = signedToken.trim().split(".", limit = 2)
        if (parts.size != 2) return null
        val payloadBytes = Base64.decode(parts[0], Base64.DEFAULT)
        val signatureBytes = Base64.decode(parts[1], Base64.DEFAULT)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(loadPublicKey())
        signature.update(payloadBytes)
        if (!signature.verify(signatureBytes)) return null
        val payload = String(payloadBytes, StandardCharsets.UTF_8)
        val parsed = parsePayload(payload) ?: return null
        val expected = createCanonicalPayload(parsed.first, parsed.second, parsed.third)
        if (payload != expected) return null
        return License(licenseId, parsed.first, parsed.second, parsed.third)
    }

    private fun parsePayload(payload: String): Triple<String, LocalDate, UserRole>? {
        val lines = payload.split("\n")
        if (lines.size != 2 && lines.size != 3) return null
        if (!lines[0].startsWith("phone=") || !lines[1].startsWith("expiry=")) return null
        val phone = normalisePhone(lines[0].removePrefix("phone="))
        if (phone.isBlank()) return null
        val expiry = runCatching { LocalDate.parse(lines[1].removePrefix("expiry="), dateFormatter) }.getOrNull() ?: return null
        val role = if (lines.size == 3 && lines[2].startsWith("role=")) {
            runCatching { UserRole.valueOf(lines[2].removePrefix("role=")) }.getOrNull() ?: return null
        } else UserRole.USER
        if (lines.size == 3 && !lines[2].startsWith("role=")) return null
        return Triple(phone, expiry, role)
    }

    private fun createCanonicalPayload(phone: String, expiryDate: LocalDate, role: UserRole): String =
        "phone=$phone\nexpiry=${expiryDate.format(dateFormatter)}\nrole=${role.name}"

    private fun loadPublicKey() = run {
        val cleaned = PUBLIC_KEY_PEM.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").replace(Regex("\\s"), "")
        val certificateBytes = Base64.decode(cleaned, Base64.DEFAULT)
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificateBytes)).publicKey
    }

    private fun normaliseLicenseId(value: String): String = value.trim().uppercase().replace(" ", "")
    private fun isValidLicenseIdFormat(value: String): Boolean = Regex("^$LICENSE_PREFIX[A-Z0-9]-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(value)

    private fun normalisePhone(value: String): String {
        var n = value.trim().replace(Regex("[^0-9+]"), "")
        if (n.startsWith("+")) n = n.substring(1)
        if (n.startsWith("00")) n = n.substring(2)
        return when {
            n.length == 10 && n.all { it.isDigit() } -> "91$n"
            n.length == 12 && n.startsWith("91") && n.all { it.isDigit() } -> n
            else -> ""
        }
    }

    private fun displayPhone(phone: String): String = if (phone.length == 12 && phone.startsWith("91")) "+91 ${phone.substring(2)}" else phone
}
