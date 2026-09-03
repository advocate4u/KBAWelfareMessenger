package com.example.myadvlicensemanager

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LicenseManagerActivity : Activity() {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private lateinit var issue: EditText
    private lateinit var expiry: EditText
    private lateinit var phone: EditText
    private lateinit var role: Spinner
    private lateinit var token: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_manager)
        title = "MyAdvAM — License Manager"

        phone = findViewById(R.id.licensePhone)
        issue = findViewById(R.id.issueDate)
        expiry = findViewById(R.id.expiryDate)
        role = findViewById(R.id.licenseRole)
        token = findViewById(R.id.signedToken)
        status = findViewById(R.id.statusText)

        role.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("USER", "ADMIN", "SUPER_ADMIN")
        )

        val today = LocalDate.now()
        issue.setText(today.format(fmt))
        expiry.setText(today.plusYears(1).format(fmt))
        issue.setOnClickListener { pickDate(issue) }
        expiry.setOnClickListener { pickDate(expiry) }

        findViewById<Button>(R.id.generateButton).setOnClickListener { generate() }
        findViewById<Button>(R.id.shareButton).setOnClickListener { shareLicense() }

        status.text = if (LicenseAuthority.hasKey(this)) {
            "Signing authority: READY (built in)"
        } else {
            "Signing authority: NOT AVAILABLE IN THIS BUILD"
        }
    }

    private fun pickDate(target: EditText) {
        val date = runCatching { LocalDate.parse(target.text.toString(), fmt) }
            .getOrDefault(LocalDate.now())
        DatePickerDialog(
            this,
            { _, year, month, day ->
                target.setText(LocalDate.of(year, month + 1, day).format(fmt))
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }

    private fun generate() {
        if (!LicenseAuthority.hasKey(this)) {
            toast("Signing authority is unavailable in this build.")
            return
        }

        val target = phone.text.toString().trim()
        val issueDate = runCatching { LocalDate.parse(issue.text.toString(), fmt) }.getOrNull()
        val expiryDate = runCatching { LocalDate.parse(expiry.text.toString(), fmt) }.getOrNull()

        if (target.isBlank() || issueDate == null || expiryDate == null) {
            toast("Enter mobile number and valid dates.")
            return
        }
        if (expiryDate.isBefore(issueDate)) {
            toast("Expiry date cannot be before issue date.")
            return
        }

        val selectedRole = when (role.selectedItem?.toString()) {
            "ADMIN" -> LicenseAuthority.ManagerRole.ADMIN
            "SUPER_ADMIN" -> LicenseAuthority.ManagerRole.SUPER_ADMIN
            else -> LicenseAuthority.ManagerRole.SUPER_ADMIN
        }

        fun checked(id: Int): Boolean = findViewById<CheckBox>(id).isChecked

        val options = LicenseAuthority.LicenseOptions(
            validatePhone = checked(R.id.optValidatePhone),
            sms = checked(R.id.optSms),
            bulkSms = checked(R.id.optBulkSms),
            smsLogs = checked(R.id.optSmsLogs),
            advocateDiary = checked(R.id.optDiary),
            advocateHelper = checked(R.id.optHelper),
            editMessageOnScreen = checked(R.id.optEditMessage),
            skipAlreadySent = checked(R.id.optSkipSent),
            confirmBeforeBulkSend = checked(R.id.optConfirmBulk),
            loggingEnabled = checked(R.id.optLogging),
            removeDuplicates = checked(R.id.optDuplicates),
            skipInvalidNumbers = checked(R.id.optInvalidNumbers)
        )

        val license = LicenseAuthority.createLicense(
            this,
            target,
            selectedRole,
            issueDate,
            expiryDate,
            options
        )

        if (license == null) {
            toast("Could not generate license. Check signing authority, dates and mobile number.")
            return
        }

        findViewById<TextView>(R.id.licenseId).text = license.id
        token.setText(license.token)
        status.text = "LICENSE GENERATED • ${license.role} • EXPIRES ${license.expiry.format(fmt)}"
        toast("License generated successfully.")
    }

    private fun shareLicense() {
        val id = findViewById<TextView>(R.id.licenseId).text.toString().trim()
        val signedToken = token.text.toString().trim()
        if (id.isBlank() || signedToken.isBlank()) {
            toast("Generate a license first.")
            return
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "MyAdv License\nLicense ID: $id\nSigned license token:\n$signedToken"
            )
        }
        startActivity(Intent.createChooser(sendIntent, "Share MyAdv License"))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

}
