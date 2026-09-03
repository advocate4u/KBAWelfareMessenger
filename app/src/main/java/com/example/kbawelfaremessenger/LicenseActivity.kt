package com.example.kbawelfaremessenger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class LicenseActivity : AppCompatActivity() {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val signingKeyPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        promptForKeyPassword(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val role = SecurityManager.currentRole(this)
        val canManage = SecurityManager.canManageLicenses(this)
        val isSuperAdmin = SecurityManager.isSuperAdmin(this)
        supportActionBar?.title = if (canManage) "License Center" else "MyAdv License Activation"

        val title = findViewById<TextView>(R.id.txtLicenseTitle)
        val description = findViewById<TextView>(R.id.txtLicenseDescription)
        val adminKeyStatus = findViewById<TextView>(R.id.txtAdminKeyStatus)
        val saveKey = findViewById<Button>(R.id.btnSaveAdminKey)
        val adminKeyInfo = findViewById<TextView>(R.id.txtAdminKeyInfo)
        val generateSection = findViewById<TextView>(R.id.txtGenerateSection)
        val generatePhone = findViewById<EditText>(R.id.edtGeneratePhone)
        val generateRole = findViewById<Spinner>(R.id.spnGenerateRole)
        val generateExpiry = findViewById<EditText>(R.id.edtGenerateExpiry)
        val generateButton = findViewById<Button>(R.id.btnGenerateLicense)
        val generated = findViewById<TextView>(R.id.txtGeneratedLicense)
        val shareGenerated = findViewById<Button>(R.id.btnShareGenerated)
        val installSection = findViewById<TextView>(R.id.txtInstallSection)
        val installId = findViewById<EditText>(R.id.edtLicenseId)
        val installToken = findViewById<EditText>(R.id.edtSignedToken)
        val activateButton = findViewById<Button>(R.id.btnActivateLicense)
        val clearButton = findViewById<Button>(R.id.btnClearLicense)
        val status = findViewById<TextView>(R.id.txtLicenseDetails)

        title.text = if (canManage) "MyAdv License Center" else "MyAdv License Activation"
        description.text = when (role) {
            UserRole.SUPER_ADMIN -> "SUPER ADMIN: generate ADMIN or USER licenses completely offline."
            UserRole.ADMIN -> "ADMIN: generate USER licenses completely offline."
            else -> "Install the license provided by your administrator. License verification works offline."
        }

        if (!canManage) {
            listOf(
                adminKeyStatus, saveKey, adminKeyInfo, generateSection, generatePhone,
                generateRole, generateExpiry, generateButton, generated, shareGenerated, clearButton
            ).forEach { it.visibility = View.GONE }
            installSection.text = "INSTALL LICENSE PROVIDED BY ADMINISTRATOR"
        } else {
            val availableRoles = if (isSuperAdmin) listOf(UserRole.ADMIN, UserRole.USER) else listOf(UserRole.USER)
            generateRole.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                availableRoles.map { it.name }
            )

            saveKey.visibility = View.VISIBLE
            adminKeyStatus.visibility = View.VISIBLE
            adminKeyInfo.visibility = View.VISIBLE
            generateSection.visibility = View.VISIBLE
            generatePhone.visibility = View.VISIBLE
            generateRole.visibility = View.VISIBLE
            generateExpiry.visibility = View.VISIBLE
            generateButton.visibility = View.VISIBLE
            generated.visibility = View.VISIBLE
            shareGenerated.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE

            fun refreshKeyStatus() {
                adminKeyStatus.text = if (OfflineLicenseIssuer.hasSigningKey(this))
                    "Signing key: CONFIGURED (encrypted on this device)"
                else
                    "Signing key: NOT CONFIGURED"
            }
            refreshKeyStatus()

            saveKey.setOnClickListener {
                signingKeyPicker.launch(
                    arrayOf("application/x-pkcs12", "application/pkcs12", "application/octet-stream")
                )
            }

            generateExpiry.setOnClickListener {
                showExpiryDatePicker(generateExpiry)
            }

            generateButton.setOnClickListener {
                val phone = generatePhone.text.toString().trim()
                val expiry = runCatching {
                    LocalDate.parse(generateExpiry.text.toString().trim(), dateFormatter)
                }.getOrNull()
                val selectedRole = UserRole.valueOf(generateRole.selectedItem.toString())

                if (phone.isBlank()) {
                    generatePhone.error = "Enter phone number"
                    return@setOnClickListener
                }
                if (expiry == null) {
                    generateExpiry.error = "Select an expiry date"
                    showExpiryDatePicker(generateExpiry)
                    return@setOnClickListener
                }

                val license = OfflineLicenseIssuer.generateLicense(this, phone, expiry, selectedRole)
                if (license == null) {
                    Toast.makeText(
                        this,
                        "Cannot generate ${selectedRole.name} license. Configure the signing key first and check your access.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                generated.text = "License ID:\n${license.licenseId}\n\nRole: ${license.role.name}\nPhone: ${license.phone}\nExpiry: ${license.expiry.format(dateFormatter)}\n\nSigned Token:\n${license.signedToken}"
            }

            shareGenerated.setOnClickListener {
                val text = generated.text.toString()
                if (text.isBlank() || text.startsWith("No license generated")) {
                    Toast.makeText(this, "Generate a license first.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, "MyAdv Advocate License")
                }, "Share Advocate License"))
            }

            clearButton.setOnClickListener {
                LicenseManager.clearLicense(this)
                refreshLicenseStatus(status)
            }
        }

        activateButton.setOnClickListener {
            val id = installId.text.toString().trim()
            val token = installToken.text.toString().trim()
            if (id.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Enter license ID and signed license token.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = LicenseManager.installLicense(this, id, token)
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (result.allowed) {
                installId.text.clear()
                installToken.text.clear()
            }
            refreshLicenseStatus(status)
        }

        refreshLicenseStatus(status)
    }

    private fun showExpiryDatePicker(target: EditText) {
        val today = Calendar.getInstance()
        val initial = runCatching {
            LocalDate.parse(target.text.toString().trim(), dateFormatter)
        }.getOrNull()

        val year = initial?.year ?: today.get(Calendar.YEAR)
        val month = (initial?.monthValue ?: (today.get(Calendar.MONTH) + 1)) - 1
        val day = initial?.dayOfMonth ?: today.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
            target.setText(selectedDate.format(dateFormatter))
            target.error = null
        }, year, month, day).apply {
            datePicker.minDate = today.timeInMillis
            setTitle("Select license expiry date")
        }.show()
    }

    private fun refreshLicenseStatus(status: TextView) {
        val license = LicenseManager.getInstalledLicense(this)
        status.text = if (license == null) {
            "Status: NOT ACTIVATED"
        } else {
            val valid = LicenseManager.isLicenseValid(this)
            "Status: ${if (valid) "ACTIVE" else "EXPIRED"}\nLicense ID: ${license.licenseId}\nRole: ${license.role.name}\nLicensed phone: ${license.phone}\nExpiry: ${license.expiryDate.format(dateFormatter)}"
        }
    }

    private fun promptForKeyPassword(uri: android.net.Uri) {
        val passwordInput = EditText(this).apply {
            hint = "PKCS#12 password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }

        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 0, 48, 0)
            addView(passwordInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Signing Key Password")
            .setMessage("Enter the password for the protected .p12/.pfx package. It will be used only for this import and will not be stored.")
            .setView(container)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("IMPORT") { _, _ ->
                val password = passwordInput.text.toString().toCharArray()
                if (password.isEmpty()) {
                    Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                Thread {
                    val ok = try {
                        contentResolver.openInputStream(uri)?.let { input ->
                            OfflineLicenseIssuer.installSigningKeyFromPkcs12(this, input, password)
                        } ?: false
                    } catch (_: Exception) {
                        false
                    }

                    runOnUiThread {
                        if (ok) {
                            Toast.makeText(this, "Signing key imported and encrypted on this device.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Unable to import signing key. Check the .p12/.pfx file and password.", Toast.LENGTH_LONG).show()
                        }
                        recreate()
                    }
                }.start()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
