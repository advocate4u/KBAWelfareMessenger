package com.example.kbawelfaremessenger

import android.content.Context

/** Centralized enforcement of license-controlled application settings. */
object LicenseFeatureAccess {
    fun canEditMessage(context: Context): Boolean = LicenseManager.isFeatureEnabled(context, "edit_message")
    fun canSkipAlreadySent(context: Context): Boolean = LicenseManager.isFeatureEnabled(context, "skip_already_sent")
    fun canConfirmBulk(context: Context): Boolean = LicenseManager.isFeatureEnabled(context, "confirm_bulk")
    fun canLogging(context: Context): Boolean = LicenseManager.isFeatureEnabled(context, "logging")
}
