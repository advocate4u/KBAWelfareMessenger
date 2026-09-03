package com.example.myadvlicensemanager

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
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

        role.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("USER", "ADMIN", "SUPER_ADMIN"))
        val today = LocalDate.now()
        issue.setText(today.format(fmt))
        expiry.setText(today.plusYears(1).format(fmt))
        issue.setOnClickListener { pickDate(issue) }
        expiry.setOnClickListener { pickDate(expiry) }
        findViewById<Button>(R.id.importKeyButton).setOnClickListener { importKey() }
        findViewById<Button>(R.id.generateButton).setOnClickListener { generate() }
        findViewById<Button>(R.id.shareButton).setOnClickListener { shareLicense() }
        status.text = if (LicenseAuthority.hasKey(this)) "Signing authority: READY" else "Signing authority: NOT INSTALLED"
    }

    private fun pickDate(target: EditText) {
        val d = runCatching { LocalDate.parse(target.text.toString(), fmt) }.getOrDefault(LocalDate.now())
        DatePickerDialog(this, { _, y, m, day -> target.setText(LocalDate.of(y, m + 1, day).format(fmt)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    private fun importKey() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/x-pkcs12"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 100)
    }

    @Deprecated("Android activity result API retained for broad project compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100 || resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        val password = EditText(this).apply { hint = "P12/PFX password"; inputType = 0x00000081 }
        AlertDialog.Builder(this).setTitle("Signing authority")
            .setMessage("Enter the signing-file password once. It is not stored by MyAdvAM.")
            .setView(password)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                val ok = contentResolver.openInputStream(uri)?.let { LicenseAuthority.installSigningKey(this, it, password.text.toString().toCharArray()) } == true
                status.text = if (ok) "Signing authority: READY" else "Signing authority: IMPORT FAILED"
                toast(if (ok) "Signing authority installed." else "Unable to import signing authority.")
            }.show()
    }

    private fun generate() {
        if (!LicenseAuthority.hasKey(this)) { toast("Import the signing authority first."); return }
        val target = phone.text.toString().trim()
        val issueDate = runCatching { LocalDate.parse(issue.text.toString(), fmt) }.getOrNull()
        val expiryDate = runCatching { LocalDate.parse(expiry.text.toString(), fmt) }.getOrNull()
        if (target.isBlank() || issueDate == null || expiryDate == null) { toast("Enter mobile number and valid dates."); return }
        val selectedRole = when (role.selectedItem.toString()) { "ADMIN" -> UserRole.ADMIN; "SUPER_ADMIN" -> UserRole.SUPER_ADMIN; else -> UserRole.USER }
        fun checked(id: Int) = findViewById<CheckBox>(id).isChecked
        val options = LicenseAuthority.LicenseOptions(
            validatePhone = checked(R.id.optValidatePhone), sms = checked(R.id.optSms), bulkSms = checked(R.id.optBulkSms), smsLogs = checked(R.id.optSmsLogs),
            advocateDiary = checked(R.id.optDiary), advocateHelper = checked(R.id.optHelper), editMessageOnScreen = checked(R.id.optEditMessage),
            skipAlreadySent = checked(R.id.optSkipSent), confirmBeforeBulkSend = checked(R.id.optConfirmBulk), loggingEnabled = checked(R.id.optLogging),
            removeDuplicates = checked(R.id.optDuplicates), skipInvalidNumbers = checked(R.id.optInvalidNumbers)
        )
        val license = LicenseAuthority.createLicense(this, target, selectedRole, issueDate, expiryDate, options)
        if (license == null) { toast("Could not generate license. Check dates and mobile number."); return }
        findViewById<TextView>(R.id.licenseId).text = license.id
        token.setText(license.token)
        status.text = "LICENSE GENERATED • ${license.role} • EXPIRES ${license.expiry.format(fmt)}"
        toast("License generated successfully.")
    }

    private fun shareLicense() {
        val id = findViewById<TextView>(R.id.licenseId).text.toString()
        val t = token.text.toString().trim()
        if (id.isBlank() || t.isBlank()) { toast("Generate a license first."); return }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "MyAdv License\nLicense ID: $id\nSigned license token:\n$t") }, "Share MyAdv License"))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
