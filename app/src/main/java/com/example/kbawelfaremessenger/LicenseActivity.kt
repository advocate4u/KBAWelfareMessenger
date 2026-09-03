package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LicenseActivity : AppCompatActivity() {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val isAdmin = SecurityManager.isAdmin(this)
        supportActionBar?.title = if (isAdmin) "Admin License Center" else "MyAdv License Activation"

        val title = findViewById<TextView>(R.id.txtLicenseTitle)
        val description = findViewById<TextView>(R.id.txtLicenseDescription)
        val adminKeyStatus = findViewById<TextView>(R.id.txtAdminKeyStatus)
        val keyInput = findViewById<EditText>(R.id.edtAdminPrivateKey)
        val saveKey = findViewById<Button>(R.id.btnSaveAdminKey)
        val adminKeyInfo = findViewById<TextView>(R.id.txtAdminKeyInfo)
        val generateSection = findViewById<TextView>(R.id.txtGenerateSection)
        val generatePhone = findViewById<EditText>(R.id.edtGeneratePhone)
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

        title.text = if (isAdmin) "MyAdv Admin License Center" else "MyAdv License Activation"
        description.text = if (isAdmin) {
            "Generate and manage offline advocate licenses. Only approved administrator phone numbers can generate licenses."
        } else {
            "Install the license provided by your administrator. License verification works offline."
        }

        if (!isAdmin) {
            listOf(adminKeyStatus, keyInput, saveKey, adminKeyInfo, generateSection, generatePhone,
                generateExpiry, generateButton, generated, shareGenerated, clearButton)
                .forEach { it.visibility = View.GONE }
            installSection.text = "INSTALL LICENSE PROVIDED BY ADMINISTRATOR"
        } else {
            adminKeyStatus.visibility = View.VISIBLE
            keyInput.visibility = View.VISIBLE
            saveKey.visibility = View.VISIBLE
            adminKeyInfo.visibility = View.VISIBLE
            generateSection.visibility = View.VISIBLE
            generatePhone.visibility = View.VISIBLE
            generateExpiry.visibility = View.VISIBLE
            generateButton.visibility = View.VISIBLE
            generated.visibility = View.VISIBLE
            shareGenerated.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE
        }

        fun refresh() {
            val license = LicenseManager.getInstalledLicense(this)
            status.text = if (license == null) "Status: NOT ACTIVATED" else {
                val valid = LicenseManager.isLicenseValid(this)
                "Status: ${if (valid) "ACTIVE" else "EXPIRED"}\nLicense ID: ${license.licenseId}\nLicensed phone: ${license.phone}\nExpiry: ${license.expiryDate.format(dateFormatter)}"
            }
            if (isAdmin) {
                adminKeyStatus.text = if (OfflineLicenseIssuer.hasSigningKey(this))
                    "Admin signing key: CONFIGURED (encrypted on this device)"
                else "Admin signing key: NOT CONFIGURED"
            }
        }

        if (isAdmin) {
            saveKey.setOnClickListener {
                val pem = keyInput.text.toString().trim()
                if (!pem.contains("BEGIN PRIVATE KEY") || !pem.contains("END PRIVATE KEY")) {
                    keyInput.error = "Paste the PKCS#8 private key"
                    return@setOnClickListener
                }
                val ok = OfflineLicenseIssuer.installSigningKey(this, pem)
                if (ok) {
                    keyInput.text.clear()
                    Toast.makeText(this, "Signing key encrypted and saved on this admin device.", Toast.LENGTH_LONG).show()
                    refresh()
                } else Toast.makeText(this, "Unable to configure signing key.", Toast.LENGTH_LONG).show()
            }

            generateButton.setOnClickListener {
                val phone = generatePhone.text.toString().trim()
                val expiry = runCatching { LocalDate.parse(generateExpiry.text.toString().trim(), dateFormatter) }.getOrNull()
                if (phone.isBlank()) { generatePhone.error = "Enter advocate phone number"; return@setOnClickListener }
                if (expiry == null) { generateExpiry.error = "Use YYYY-MM-DD"; return@setOnClickListener }
                val license = OfflineLicenseIssuer.generateLicense(this, phone, expiry)
                if (license == null) {
                    Toast.makeText(this, "Cannot generate license. Configure the admin signing key first.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                generated.text = "License ID:\n${license.licenseId}\n\nSigned Token:\n${license.signedToken}\n\nPhone: ${license.phone}\nExpiry: ${license.expiry.format(dateFormatter)}"
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
            if (result.allowed) { installId.text.clear(); installToken.text.clear() }
            refresh()
        }

        if (isAdmin) {
            clearButton.setOnClickListener {
                LicenseManager.clearLicense(this)
                refresh()
            }
        }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
