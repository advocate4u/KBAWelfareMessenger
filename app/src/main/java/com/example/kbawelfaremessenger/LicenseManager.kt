package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseManager {
    private const val PREF_NAME = "kba_license"
    private const val KEY_LICENSE_ID = "license_id"
    private const val KEY_LICENSE_TOKEN = "license_token"
    private const val PREFIX = "ANI"

    // Must match the offline signing secret embedded in MyAdvAM.
    private const val SIGNING_SECRET_B64 = "Kuku3ICdCr/CTRnuJwEduKjujYF8oE0szV0n24o7j/M="

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    data class LicenseOptions(
        val validatePhone: Boolean = true,
        val sms: Boolean = true,
        val bulkSms: Boolean = true,
        val smsLogs: Boolean = true,
        val advocateDiary: Boolean = true,
        val advocateHelper: Boolean = true,
        val editMessageOnScreen: Boolean = true,
        val skipAlreadySent: Boolean = true,
        val confirmBeforeBulkSend: Boolean = true,
        val loggingEnabled: Boolean = true,
        val removeDuplicates: Boolean = true,
        val skipInvalidNumbers: Boolean = true
    )

    data class License(
        val licenseId: String,
        val phone: String,
        val expiryDate: LocalDate,
        val issueDate: LocalDate,
        val role: UserRole,
        val options: LicenseOptions
    )

    data class LicenseCheckResult(val allowed: Boolean, val message: String)

    fun installLicense(c: Context, idInput: String, token: String): LicenseCheckResult {
        return try {
            val id = idInput.trim().uppercase().replace(" ", "")
            if (!Regex("^$PREFIX[A-Z0-9]-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(id)) {
                return LicenseCheckResult(false, "Invalid license ID format.")
            }
            val license = verify(id, token) ?: return LicenseCheckResult(false, "License verification failed.")
            val today = LocalDate.now()
            if (today.isBefore(license.issueDate)) return LicenseCheckResult(false, "This license is not active yet.")
            if (today.isAfter(license.expiryDate)) return LicenseCheckResult(false, "This license has expired.")

            // A license is installed independently of the currently logged-in account.
            // First-time account activation enforces that the User ID matches the licensed phone.
            // For an existing account, SMS sending separately verifies the licensed SIM number.
            // A valid signed license is the authority for the local account role.
            // During first activation there is no account yet. For an existing account,
            // only allow a role change when the account User ID matches the licensed phone.
            val currentUserId = SecurityManager.currentUserId(c)
            if (!currentUserId.isNullOrBlank()) {
                val accountPhone = norm(currentUserId)
                if (accountPhone != license.phone) {
                    return LicenseCheckResult(false, "License phone does not match the current User ID.")
                }
                if (!SecurityManager.updateCurrentUserRole(c, license.role)) {
                    return LicenseCheckResult(false, "Unable to update the current account role from the license.")
                }
            }

            c.getSharedPreferences(PREF_NAME, 0).edit()
                .putString(KEY_LICENSE_ID, license.licenseId)
                .putString(KEY_LICENSE_TOKEN, token.trim())
                .apply()
            AppLogger.success(c, "LICENSE", "License activated: ${license.licenseId}")
            LicenseCheckResult(true, "License activated successfully.")
        } catch (e: Exception) {
            AppLogger.error(c, "LICENSE", "License installation failed: ${e.message}")
            LicenseCheckResult(false, "Unable to install license.")
        }
    }

    fun getInstalledLicense(c: Context): License? {
        return try {
            val prefs = c.getSharedPreferences(PREF_NAME, 0)
            val id = prefs.getString(KEY_LICENSE_ID, null) ?: return null
            val token = prefs.getString(KEY_LICENSE_TOKEN, null) ?: return null
            verify(id, token)
        } catch (_: Exception) {
            null
        }
    }

    fun getValidLicense(c: Context): License? {
        val license = getInstalledLicense(c) ?: return null
        val today = LocalDate.now()
        return license.takeIf { !today.isBefore(it.issueDate) && !today.isAfter(it.expiryDate) }
    }

    fun getLicensedPhone(c: Context): String? = getValidLicense(c)?.phone
    fun getLicenseId(c: Context): String? = getInstalledLicense(c)?.licenseId
    fun getExpiryDate(c: Context): LocalDate? = getInstalledLicense(c)?.expiryDate
    fun getLicenseRole(c: Context): UserRole? = getValidLicense(c)?.role
    fun isLicenseValid(c: Context): Boolean = getValidLicense(c) != null

    fun isFeatureEnabled(c: Context, feature: String): Boolean {
        val options = getValidLicense(c)?.options ?: return false
        return when (feature.lowercase()) {
            "sms" -> options.sms
            "bulk_sms" -> options.bulkSms
            "sms_logs" -> options.smsLogs
            "diary", "advocate_diary" -> options.advocateDiary
            "helper", "advocate_helper" -> options.advocateHelper
            "edit_message" -> options.editMessageOnScreen
            "skip_already_sent" -> options.skipAlreadySent
            "confirm_bulk" -> options.confirmBeforeBulkSend
            "logging" -> options.loggingEnabled
            "remove_duplicates" -> options.removeDuplicates
            "skip_invalid_numbers" -> options.skipInvalidNumbers
            else -> false
        }
    }

    fun checkLicenseAndSmsPhone(c: Context, smsPhone: String?): LicenseCheckResult {
        val license = getValidLicense(c)
            ?: return LicenseCheckResult(
                false,
                if (getInstalledLicense(c) != null) "License has expired or is not active." else "No valid MyAdv license is installed."
            )
        if (!license.options.sms) return LicenseCheckResult(false, "SMS sending is not enabled in this license.")
        if (!license.options.validatePhone) return LicenseCheckResult(true, "License verified. SMS number validation is disabled by the license.")
        val actual = smsPhone?.let(::norm).orEmpty()
        if (actual.isBlank()) return LicenseCheckResult(false, "Unable to verify the SMS SIM number. SMS sending is blocked.")
        if (actual != license.phone) return LicenseCheckResult(false, "Licensed phone number does not match the SMS SIM. SMS sending is blocked.")
        return LicenseCheckResult(true, "License and SMS SIM verified.")
    }

    fun clearLicense(c: Context) {
        c.getSharedPreferences(PREF_NAME, 0).edit().clear().apply()
        AppLogger.info(c, "LICENSE", "Installed license cleared.")
    }

    private fun verify(id: String, token: String): License? {
        val parts = token.trim().split(".", limit = 2)
        if (parts.size != 2) return null

        val payload = try {
            Base64.decode(parts[0], Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }

        val expected = try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(Base64.decode(SIGNING_SECRET_B64, Base64.DEFAULT), "HmacSHA256"))
            mac.doFinal(payload)
        } catch (_: Exception) {
            return null
        }

        val supplied = try {
            Base64.decode(parts[1], Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }
        if (!java.security.MessageDigest.isEqual(expected, supplied)) return null

        val fields = String(payload, StandardCharsets.UTF_8).lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()

        if (fields["license"] != id) return null
        if (fields["version"] != "4") return null

        val phone = norm(fields["phone"].orEmpty())
        val expiry = runCatching { LocalDate.parse(fields["expiry"].orEmpty(), fmt) }.getOrNull() ?: return null
        val issue = runCatching { LocalDate.parse(fields["issue"].orEmpty(), fmt) }.getOrNull() ?: return null
        val role = runCatching { UserRole.valueOf(fields["role"].orEmpty()) }.getOrNull() ?: return null
        if (phone.isBlank()) return null

        fun boolField(key: String, default: Boolean): Boolean = fields[key]?.toBooleanStrictOrNull() ?: default
        val options = LicenseOptions(
            validatePhone = boolField("validatePhone", true),
            sms = boolField("sms", true),
            bulkSms = boolField("bulkSms", true),
            smsLogs = boolField("smsLogs", true),
            advocateDiary = boolField("advocateDiary", true),
            advocateHelper = boolField("advocateHelper", true),
            editMessageOnScreen = boolField("editMessageOnScreen", true),
            skipAlreadySent = boolField("skipAlreadySent", true),
            confirmBeforeBulkSend = boolField("confirmBeforeBulkSend", true),
            loggingEnabled = boolField("loggingEnabled", true),
            removeDuplicates = boolField("removeDuplicates", true),
            skipInvalidNumbers = boolField("skipInvalidNumbers", true)
        )
        return License(id, phone, expiry, issue, role, options)
    }

    private fun norm(value: String): String {
        var normalized = value.trim().replace(Regex("[^0-9+]"), "")
        if (normalized.startsWith("+")) normalized = normalized.substring(1)
        if (normalized.startsWith("00")) normalized = normalized.substring(2)
        return when {
            normalized.length == 10 && normalized.all(Char::isDigit) -> "91$normalized"
            normalized.length == 12 && normalized.startsWith("91") && normalized.all(Char::isDigit) -> normalized
            else -> ""
        }
    }
}
