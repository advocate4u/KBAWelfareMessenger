package com.example.kbawelfaremessenger

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AppSettings(

    // -------------------------
    // CSV SETTINGS
    // -------------------------

    var nameColumn: String = "Name",

    var phoneColumn: String = "Phone 1 - Value",

    var defaultCountryCode: String = "91",

    var removeDuplicates: Boolean = true,

    var skipInvalidNumbers: Boolean = true,


    // -------------------------
    // MESSAGE SETTINGS
    // -------------------------

    var defaultMessage:
        String =
            "R/m {{name}} ji,\n\n" +
            "Kindly support & vote for Mohit Arora (Ch.547) " +
            "for Treasurer, DBA Karnal election. Your blessings mean a lot.\n\n" +
            "Thank you- Mohit Arora, 9518804747",

    /*
     * Controls whether the user can edit the message
     * directly on MainActivity.
     *
     * true  = Message editor visible/editable
     * false = Message editor hidden; configured message is used
     */
    var editMessageOnScreen: Boolean = true,


    // -------------------------
    // SMS SETTINGS
    // -------------------------

    var smsDelayMs: Long = 500L,

    /*
     * If true, contacts which have already been
     * successfully sent an SMS will be skipped.
     */
    var skipAlreadySent: Boolean = true,

    /*
     * If true, show confirmation before bulk SMS.
     */
    var confirmBeforeBulkSend: Boolean = true,


    // -------------------------
    // LOGGING SETTINGS
    // -------------------------

    var loggingEnabled: Boolean = true,

    var maxLogEntries: Int = 2000
)


object AppSettingsManager {

    private const val FILE_NAME =
        "app_settings.json"


    /**
     * Returns the private settings file.
     *
     * The file is stored inside the application's
     * private internal storage.
     *
     * It is NOT stored in:
     *
     * assets/
     * Downloads/
     * Documents/
     * public storage
     */
    private fun getSettingsFile(
        context: Context
    ): File {

        return File(
            context.filesDir,
            FILE_NAME
        )
    }


    /**
     * Loads application settings.
     *
     * If the file doesn't exist, default settings
     * are created automatically.
     *
     * If the JSON is damaged/corrupt, defaults are
     * restored instead of crashing the application.
     */
    fun load(
        context: Context
    ): AppSettings {

        val file =
            getSettingsFile(context)

        // First launch / settings file doesn't exist.
        if (!file.exists()) {

            val defaults =
                AppSettings()

            save(
                context,
                defaults
            )

            return defaults
        }

        return try {

            val json =
                JSONObject(
                    file.readText()
                )

            fromJson(json)

        } catch (_: Exception) {

            // Recover safely from corrupt JSON.
            val defaults =
                AppSettings()

            try {
                save(
                    context,
                    defaults
                )
            } catch (_: Exception) {
                // Ignore write failure.
            }

            defaults
        }
    }


    /**
     * Saves the complete application settings
     * into private app storage.
     */
    fun save(
        context: Context,
        settings: AppSettings
    ) {

        val file =
            getSettingsFile(context)

        val json =
            toJson(settings)

        file.writeText(
            json.toString(2)
        )
    }


    /**
     * Converts AppSettings into JSON.
     */
    private fun toJson(
        settings: AppSettings
    ): JSONObject {

        val csv =
            JSONObject()
                .put(
                    "nameColumn",
                    settings.nameColumn
                )
                .put(
                    "phoneColumn",
                    settings.phoneColumn
                )
                .put(
                    "defaultCountryCode",
                    settings.defaultCountryCode
                )
                .put(
                    "removeDuplicates",
                    settings.removeDuplicates
                )
                .put(
                    "skipInvalidNumbers",
                    settings.skipInvalidNumbers
                )


        val message =
            JSONObject()
                .put(
                    "defaultMessage",
                    settings.defaultMessage
                )
                .put(
                    "editMessageOnScreen",
                    settings.editMessageOnScreen
                )


        val sms =
            JSONObject()
                .put(
                    "delayMs",
                    settings.smsDelayMs
                )
                .put(
                    "skipAlreadySent",
                    settings.skipAlreadySent
                )
                .put(
                    "confirmBeforeBulkSend",
                    settings.confirmBeforeBulkSend
                )


        val logging =
            JSONObject()
                .put(
                    "enabled",
                    settings.loggingEnabled
                )
                .put(
                    "maxEntries",
                    settings.maxLogEntries
                )


        return JSONObject()
            .put(
                "csv",
                csv
            )
            .put(
                "message",
                message
            )
            .put(
                "sms",
                sms
            )
            .put(
                "logging",
                logging
            )
    }


    /**
     * Converts JSON into AppSettings.
     *
     * optXXX() is intentionally used so that
     * missing settings fall back to safe defaults.
     */
    private fun fromJson(
        json: JSONObject
    ): AppSettings {

        val defaultSettings =
            AppSettings()

        val csv =
            json.optJSONObject("csv")
                ?: JSONObject()

        val message =
            json.optJSONObject("message")
                ?: JSONObject()

        val sms =
            json.optJSONObject("sms")
                ?: JSONObject()

        val logging =
            json.optJSONObject("logging")
                ?: JSONObject()


        return AppSettings(

            nameColumn =
                csv.optString(
                    "nameColumn",
                    defaultSettings.nameColumn
                ),

            phoneColumn =
                csv.optString(
                    "phoneColumn",
                    defaultSettings.phoneColumn
                ),

            defaultCountryCode =
                csv.optString(
                    "defaultCountryCode",
                    defaultSettings.defaultCountryCode
                ),

            removeDuplicates =
                csv.optBoolean(
                    "removeDuplicates",
                    defaultSettings.removeDuplicates
                ),

            skipInvalidNumbers =
                csv.optBoolean(
                    "skipInvalidNumbers",
                    defaultSettings.skipInvalidNumbers
                ),


            defaultMessage =
                message.optString(
                    "defaultMessage",
                    defaultSettings.defaultMessage
                ),

            editMessageOnScreen =
                message.optBoolean(
                    "editMessageOnScreen",
                    defaultSettings.editMessageOnScreen
                ),


            smsDelayMs =
                sms.optLong(
                    "delayMs",
                    defaultSettings.smsDelayMs
                ).coerceAtLeast(0L),

            skipAlreadySent =
                sms.optBoolean(
                    "skipAlreadySent",
                    defaultSettings.skipAlreadySent
                ),

            confirmBeforeBulkSend =
                sms.optBoolean(
                    "confirmBeforeBulkSend",
                    defaultSettings.confirmBeforeBulkSend
                ),


            loggingEnabled =
                logging.optBoolean(
                    "enabled",
                    defaultSettings.loggingEnabled
                ),

            maxLogEntries =
                logging.optInt(
                    "maxEntries",
                    defaultSettings.maxLogEntries
                ).coerceAtLeast(100)
        )
    }


    /**
     * Returns the actual private JSON file path.
     *
     * Useful for diagnostics/logging.
     */
    fun getSettingsFilePath(
        context: Context
    ): String {

        return getSettingsFile(context).absolutePath
    }
}
