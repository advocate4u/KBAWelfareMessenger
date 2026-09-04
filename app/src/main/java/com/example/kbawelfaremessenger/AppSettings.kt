package com.example.kbawelfaremessenger

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AppSettings(
    var nameColumn: String = "Name",
    var phoneColumn: String = "Phone 1 - Value",
    var defaultCountryCode: String = "91",
    var removeDuplicates: Boolean = true,
    var skipInvalidNumbers: Boolean = true,
    var defaultMessage: String =
        "R/m {{name}} ji,\n\n" +
        "Kindly support & vote for Mohit Arora (Ch.547) " +
        "for Treasurer, DBA Karnal election. Your blessings mean a lot.\n\n" +
        "Thank you- Mohit Arora, 9518804747",
    var editMessageOnScreen: Boolean = true,
    var smsDelayMs: Long = 500L,
    var skipAlreadySent: Boolean = true,
    var confirmBeforeBulkSend: Boolean = true,
    var loggingEnabled: Boolean = true,
    var maxLogEntries: Int = 2000
)

object AppSettingsManager {
    private const val FILE_NAME = "app_settings.json"

    private fun getSettingsFile(context: Context): File =
        File(context.filesDir, FILE_NAME)

    fun load(context: Context): AppSettings {
        val file = getSettingsFile(context)

        val loaded = if (!file.exists()) {
            AppSettings().also { defaults ->
                runCatching { save(context, defaults) }
            }
        } else {
            try {
                fromJson(JSONObject(file.readText()))
            } catch (_: Exception) {
                AppSettings().also { defaults ->
                    runCatching { save(context, defaults) }
                }
            }
        }

        // License permissions are authoritative. The returned settings are
        // the effective settings used by the app, so stale local preferences
        // can never grant a feature denied by the currently valid license.
        val license = LicenseManager.getValidLicense(context)
        return loaded.copy(
            removeDuplicates = loaded.removeDuplicates && (license?.options?.removeDuplicates == true),
            skipInvalidNumbers = loaded.skipInvalidNumbers && (license?.options?.skipInvalidNumbers == true),
            editMessageOnScreen = loaded.editMessageOnScreen && (license?.options?.editMessageOnScreen == true),
            skipAlreadySent = loaded.skipAlreadySent && (license?.options?.skipAlreadySent == true),
            confirmBeforeBulkSend = loaded.confirmBeforeBulkSend && (license?.options?.confirmBeforeBulkSend == true),
            loggingEnabled = loaded.loggingEnabled && (license?.options?.loggingEnabled == true)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        getSettingsFile(context).writeText(toJson(settings).toString(2))
    }

    private fun toJson(settings: AppSettings): JSONObject {
        val csv = JSONObject()
            .put("nameColumn", settings.nameColumn)
            .put("phoneColumn", settings.phoneColumn)
            .put("defaultCountryCode", settings.defaultCountryCode)
            .put("removeDuplicates", settings.removeDuplicates)
            .put("skipInvalidNumbers", settings.skipInvalidNumbers)

        val message = JSONObject()
            .put("defaultMessage", settings.defaultMessage)
            .put("editMessageOnScreen", settings.editMessageOnScreen)

        val sms = JSONObject()
            .put("delayMs", settings.smsDelayMs)
            .put("skipAlreadySent", settings.skipAlreadySent)
            .put("confirmBeforeBulkSend", settings.confirmBeforeBulkSend)

        val logging = JSONObject()
            .put("enabled", settings.loggingEnabled)
            .put("maxEntries", settings.maxLogEntries)

        return JSONObject()
            .put("csv", csv)
            .put("message", message)
            .put("sms", sms)
            .put("logging", logging)
    }

    private fun fromJson(json: JSONObject): AppSettings {
        val defaults = AppSettings()
        val csv = json.optJSONObject("csv") ?: JSONObject()
        val message = json.optJSONObject("message") ?: JSONObject()
        val sms = json.optJSONObject("sms") ?: JSONObject()
        val logging = json.optJSONObject("logging") ?: JSONObject()

        return AppSettings(
            nameColumn = csv.optString("nameColumn", defaults.nameColumn),
            phoneColumn = csv.optString("phoneColumn", defaults.phoneColumn),
            defaultCountryCode = csv.optString("defaultCountryCode", defaults.defaultCountryCode),
            removeDuplicates = csv.optBoolean("removeDuplicates", defaults.removeDuplicates),
            skipInvalidNumbers = csv.optBoolean("skipInvalidNumbers", defaults.skipInvalidNumbers),
            defaultMessage = message.optString("defaultMessage", defaults.defaultMessage),
            editMessageOnScreen = message.optBoolean("editMessageOnScreen", defaults.editMessageOnScreen),
            smsDelayMs = sms.optLong("delayMs", defaults.smsDelayMs).coerceAtLeast(0L),
            skipAlreadySent = sms.optBoolean("skipAlreadySent", defaults.skipAlreadySent),
            confirmBeforeBulkSend = sms.optBoolean("confirmBeforeBulkSend", defaults.confirmBeforeBulkSend),
            loggingEnabled = logging.optBoolean("enabled", defaults.loggingEnabled),
            maxLogEntries = logging.optInt("maxEntries", defaults.maxLogEntries).coerceAtLeast(100)
        )
    }

    fun getSettingsFilePath(context: Context): String =
        getSettingsFile(context).absolutePath
}
