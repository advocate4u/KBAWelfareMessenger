package com.example.myadvlicensemanager

import androidx.appcompat.app.AppCompatActivity
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LicenseManagerActivity : AppCompatActivity() {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private lateinit var issue: EditText
    private lateinit var expiry: EditText
    private lateinit var phone: EditText
    private lateinit var phone2: EditText
    private lateinit var role: Spinner
    private lateinit var token: EditText
    private lateinit var status: TextView
    private lateinit var licenseId: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen already has its own MyAdvAnIT heading. Hide the action bar
        // so the application label is not rendered a second time above it.
        supportActionBar?.hide()
        setContentView(R.layout.activity_license_manager)
        phone = findViewById(R.id.licensePhone)
        phone2 = findViewById(R.id.licensePhone2)
        issue = findViewById(R.id.issueDate)
        expiry = findViewById(R.id.expiryDate)
        role = findViewById(R.id.licenseRole)
        token = findViewById(R.id.signedToken)
        status = findViewById(R.id.statusText)
        licenseId = findViewById(R.id.licenseId)
        role.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("USER", "ADMIN", "SUPER_ADMIN"))

        val today = LocalDate.now()
        issue.setText(today.format(fmt))
        expiry.setText(today.plusMonths(1).format(fmt))
        issue.setOnClickListener { pickDate(issue) }
        expiry.setOnClickListener { pickDate(expiry) }

        val permissionsHeader = findViewById<TextView>(R.id.permissionsHeader)
        val permissionsContainer = findViewById<View>(R.id.permissionsContainer)
        permissionsContainer.visibility = View.GONE
        permissionsHeader.setOnClickListener {
            val expanded = permissionsContainer.visibility == View.VISIBLE
            permissionsContainer.visibility = if (expanded) View.GONE else View.VISIBLE
            permissionsHeader.text = if (expanded) "PERMISSIONS & FEATURES  ▼" else "PERMISSIONS & FEATURES  ▲"
        }
        findViewById<Button>(R.id.generateButton).setOnClickListener { generate() }
        findViewById<Button>(R.id.copyLicenseIdButton).setOnClickListener { copyToClipboard("MyAdv License Key", licenseId.text.toString().trim()) }
        findViewById<Button>(R.id.copyTokenButton).setOnClickListener { copyToClipboard("MyAdv License Token", token.text.toString().trim()) }
        findViewById<Button>(R.id.shareButton).setOnClickListener { shareLicense() }
        status.text = if (LicenseAuthority.hasKey(this)) "Signing authority: READY" else "Signing authority: NOT AVAILABLE IN THIS BUILD"
    }

    private fun pickDate(target: EditText) {
        val date = runCatching { LocalDate.parse(target.text.toString(), fmt) }.getOrDefault(LocalDate.now())
        DatePickerDialog(this, { _, year, month, day -> target.setText(LocalDate.of(year, month + 1, day).format(fmt)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    private fun generate() {
        val target = phone.text.toString().trim()
        val target2 = phone2.text.toString().trim()
        val issueDate = runCatching { LocalDate.parse(issue.text.toString(), fmt) }.getOrNull()
        val expiryDate = runCatching { LocalDate.parse(expiry.text.toString(), fmt) }.getOrNull()
        if (target.isBlank() || issueDate == null || expiryDate == null) { toast("Enter primary mobile number and valid dates."); return }
        if (expiryDate.isBefore(issueDate)) { toast("Expiry date cannot be before issue date."); return }
        val selectedRole = when (role.selectedItem?.toString()) {
            "USER" -> LicenseAuthority.ManagerRole.USER
            "SUPER_ADMIN" -> LicenseAuthority.ManagerRole.SUPER_ADMIN
            else -> LicenseAuthority.ManagerRole.ADMIN
        }
        fun checked(id: Int) = findViewById<CheckBox>(id).isChecked
        val options = LicenseAuthority.LicenseOptions(
            validatePhone = checked(R.id.optValidatePhone), sms = checked(R.id.optSms), bulkSms = checked(R.id.optBulkSms),
            smsLogs = checked(R.id.optSmsLogs), advocateDiary = checked(R.id.optDiary), advocateHelper = checked(R.id.optHelper),
            editMessageOnScreen = checked(R.id.optEditMessage), skipAlreadySent = checked(R.id.optSkipSent),
            confirmBeforeBulkSend = checked(R.id.optConfirmBulk), loggingEnabled = checked(R.id.optLogging),
            removeDuplicates = checked(R.id.optDuplicates), skipInvalidNumbers = checked(R.id.optInvalidNumbers),
            preview = checked(R.id.optPreview), testSms = checked(R.id.optTestSms), whatsapp = checked(R.id.optWhatsApp),
            rangeSelection = checked(R.id.optRange)
        )
        val license = LicenseAuthority.createLicense(this, target, target2, selectedRole, issueDate, expiryDate, options)
        if (license == null) { toast("Could not generate license. Check both mobile numbers and dates."); return }
        licenseId.text = license.id
        token.setText(license.token)
        status.text = "LICENSE GENERATED • ${license.role} • EXPIRES ${license.expiry.format(fmt)}"
        toast("License generated successfully.")
    }

    private fun copyToClipboard(label: String, value: String) {
        if (value.isBlank() || value == "Generated automatically") { toast("Generate a license first."); return }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        toast("$label copied.")
    }

    private fun shareLicense() {
        val id = licenseId.text.toString().trim()
        val signedToken = token.text.toString().trim()
        if (id.isBlank() || signedToken.isBlank()) { toast("Generate a license first."); return }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "MyAdv License\nLicense Key: $id\nSigned license token:\n$signedToken")
        }
        startActivity(Intent.createChooser(sendIntent, "Share MyAdv License"))
    }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
