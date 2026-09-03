package com.example.myadvlicensemanager

import android.content.Context
import android.util.Base64
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Offline license authority used by MyAdvAM.
 *
 * The signing material is embedded so the manager works without a separate key
 * file, password, P12/PFX file, or network connection. This is intentionally a
 * simple offline deployment model for the welfare project; it is not intended
 * to provide hardware-backed anti-extraction security.
 */
object LicenseAuthority {
    private const val PREFS = "myadv_manager"
    private const val ROLE = "role"
    private const val PHONE = "phone"

    // Shared offline signing secret. Keep identical to the verifier in MyAdv.
    private const val SIGNING_SECRET_B64 = "Kuku3ICdCr/CTRnuJwEduKjujYF8oE0szV0n24o7j/M="

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

    fun hasKey(c: Context): Boolean = true

    fun configureManager(c: Context, managerRole: ManagerRole, managerPhone: String?): Boolean {
        val phone = managerPhone?.let(::normalize) ?: ""
        c.getSharedPreferences(PREFS, 0).edit()
            .putString(ROLE, managerRole.name)
            .putString(PHONE, phone)
            .apply()
        return true
    }

    fun createLicense(
        c: Context,
        target: String,
        role: ManagerRole,
        issueDate: LocalDate,
        expiry: LocalDate,
        options: LicenseOptions
    ): License? {
        if (expiry.isBefore(issueDate) || expiry.isBefore(LocalDate.now())) return null
        val phone = normalize(target) ?: return null
        val id = generateLicenseId()
        val payload = buildString {
            appendLine("version=4")
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
        val token = sign(payload) ?: return null
        return License(id, phone, role, issueDate, expiry, options, token)
    }

    private fun generateLicenseId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun block() = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "ANI${chars[Random.nextInt(chars.length)]}-${block()}-${block()}-${block()}"
    }

    private fun sign(payload: String): String? = try {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base64.decode(SIGNING_SECRET_B64, Base64.DEFAULT), "HmacSHA256"))
        val signature = mac.doFinal(payloadBytes)
        Base64.encodeToString(payloadBytes, Base64.NO_WRAP) + "." +
            Base64.encodeToString(signature, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

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
}
