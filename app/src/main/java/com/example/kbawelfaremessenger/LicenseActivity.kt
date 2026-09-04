package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.format.DateTimeFormatter

class LicenseActivity : AppCompatActivity() {
    private val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        supportActionBar?.title = "MyAdv License Activation"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.txtLicenseTitle).text = "MyAdv License Activation"
        findViewById<TextView>(R.id.txtLicenseDescription).text = "Install the license provided by your administrator. Verification works offline."
        findViewById<TextView>(R.id.txtInstallSection).text = "INSTALL LICENSE PROVIDED BY ADMINISTRATOR"

        listOf(R.id.txtAdminKeyStatus, R.id.btnSaveAdminKey, R.id.txtAdminKeyInfo,
            R.id.txtGenerateSection, R.id.edtGeneratePhone, R.id.spnGenerateRole,
            R.id.edtGenerateExpiry, R.id.btnGenerateLicense, R.id.txtGeneratedLicense,
            R.id.btnShareGenerated).forEach { findViewById<View>(it).visibility = View.GONE }

        val id = findViewById<EditText>(R.id.edtLicenseId)
        val token = findViewById<EditText>(R.id.edtSignedToken)
        val status = findViewById<TextView>(R.id.txtLicenseDetails)

        findViewById<Button>(R.id.btnActivateLicense).setOnClickListener {
            val result = LicenseManager.installLicense(this, id.text.toString(), token.text.toString())
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (result.allowed) {
                // Do not leave the user on the license page. Go directly to the
                // first-time account creation screen or normal login screen.
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    putExtra("license_just_activated", true)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
                return@setOnClickListener
            }
            refresh(status)
        }

        findViewById<Button>(R.id.btnClearLicense).setOnClickListener {
            LicenseManager.clearLicense(this)
            refresh(status)
        }
        refresh(status)
    }

    private fun refresh(status: TextView) {
        val license = LicenseManager.getInstalledLicense(this)
        status.text = if (license == null) {
            "Status: NOT ACTIVATED"
        } else {
            "Status: ${if (LicenseManager.isLicenseValid(this)) "ACTIVE" else "EXPIRED"}\n" +
                "License No: ${license.licenseId}\n" +
                "Role: ${license.role.name}\n" +
                "Licensed phone: ${license.phone}\n" +
                "Valid Until: ${license.expiryDate.format(formatter)}"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
