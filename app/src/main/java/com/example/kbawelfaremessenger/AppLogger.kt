package com.example.kbawelfaremessenger

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val FILE_NAME = "app_log.json"

    // --------------------------------------------------
    // PUBLIC LOG METHODS
    // --------------------------------------------------

    fun info(
        context: Context,
        operation: String,
        message: String
    ) {
        write(
            context = context,
            level = "INFO",
            operation = operation,
            message = message
        )
    }

    fun success(
        context: Context,
        operation: String,
        message: String
    ) {
        write(
            context = context,
            level = "SUCCESS",
            operation = operation,
            message = message
        )
    }

    fun warning(
        context: Context,
        operation: String,
        message: String
    ) {
        write(
            context = context,
            level = "WARNING",
            operation = operation,
            message = message
        )
    }

    fun error(
        context: Context,
        operation: String,
        message: String
    ) {
        write(
            context = context,
            level = "ERROR",
            operation = operation,
            message = message
        )
    }


    // --------------------------------------------------
    // WRITE LOG
    // --------------------------------------------------

    private fun write(
        context: Context,
        level: String,
        operation: String,
        message: String
    ) {

        try {

            val settings =
                AppSettingsManager.load(context)

            if (!settings.loggingEnabled) {
                return
            }

            val file =
                File(
                    context.filesDir,
                    FILE_NAME
                )

            val logs =
                if (file.exists()) {

                    try {
                        JSONArray(
                            file.readText()
                        )
                    } catch (_: Exception) {
                        JSONArray()
                    }

                } else {
                    JSONArray()
                }


            val logEntry =
                JSONObject()
                    .put(
                        "timestamp",
                        getTimestamp()
                    )
                    .put(
                        "level",
                        level
                    )
                    .put(
                        "operation",
                        operation
                    )
                    .put(
                        "message",
                        sanitise(message)
                    )


            logs.put(logEntry)


            // --------------------------------------------------
            // KEEP ONLY THE LATEST N LOG ENTRIES
            // --------------------------------------------------

            while (
                logs.length() >
                settings.maxLogEntries
            ) {

                logs.remove(0)
            }


            file.writeText(
                logs.toString(2)
            )

        } catch (_: Exception) {

            /*
             * Logging must NEVER crash the application.
             *
             * If logging itself fails, silently ignore it.
             */
        }
    }


    // --------------------------------------------------
    // TIMESTAMP
    // --------------------------------------------------

    private fun getTimestamp(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(
            Date()
        )
    }


    // --------------------------------------------------
    // BASIC SENSITIVE-DATA PROTECTION
    // --------------------------------------------------

    private fun sanitise(
        message: String
    ): String {

        var result = message

        /*
         * Never allow passwords to accidentally appear
         * in the application log.
         */

        result =
            result.replace(
                Regex(
                    "(?i)password\\s*[:=]\\s*[^\\s,;]+"
                ),
                "password=[REDACTED]"
            )

        result =
            result.replace(
                Regex(
                    "(?i)passcode\\s*[:=]\\s*[^\\s,;]+"
                ),
                "passcode=[REDACTED]"
            )

        return result
    }


    // --------------------------------------------------
    // CLEAR LOGS
    // --------------------------------------------------

    fun clear(
        context: Context
    ) {

        try {

            val file =
                File(
                    context.filesDir,
                    FILE_NAME
                )

            if (file.exists()) {
                file.delete()
            }

        } catch (_: Exception) {

            // Ignore logging cleanup failure.
        }
    }


    // --------------------------------------------------
    // READ LOGS
    // --------------------------------------------------

    fun read(
        context: Context
    ): String {

        return try {

            val file =
                File(
                    context.filesDir,
                    FILE_NAME
                )

            if (file.exists()) {

                file.readText()

            } else {

                "No logs available."
            }

        } catch (_: Exception) {

            "Unable to read logs."
        }
    }


    // --------------------------------------------------
    // LOG FILE PATH
    // --------------------------------------------------

    fun getLogFilePath(
        context: Context
    ): String {

        return File(
            context.filesDir,
            FILE_NAME
        ).absolutePath
    }
}
