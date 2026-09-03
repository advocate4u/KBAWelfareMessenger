package com.example.kbawelfaremessenger

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.format.DateTimeFormatter

class LicenseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityManager.isAdmin(this)) {
            Toast.makeText(this, "Administrator access required.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_license)
        supportActionBar?.title = "App License"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val licenseId = findViewById<EditText>(R.id.edtLicenseId)
        val signedToken = findViewById<EditText>(R.id.edtSignedToken)
        val status = findViewById<TextView>(R.id.txtLicenseDetails)

        fun refresh() {
            val license = LicenseManager.getInstalledLicense(this)
            status.text = if (license == null) "Status: NOT ACTIVATED" else {
                val valid = LicenseManager.isLicenseValid(this)
                "Status: ${if (valid) "ACTIVE" else "EXPIRED"}\n" +
                    "License ID: ${license.licenseId}\n" +
                    "Licensed phone: ${license.phone}\n" +
                    "Expiry: ${license.expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
            }
        }

        findViewById<Button>(R.id.btnActivateLicense).setOnClickListener {
            val id = licenseId.text.toString().trim()
            val token = signedToken.text.toString().trim()
            if (id.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Enter license ID and signed license token.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = LicenseManager.installLicense(this, id, token)
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (result.allowed) { licenseId.text.clear(); signedToken.text.clear() }
            refresh()
        }
        findViewById<Button>(R.id.btnClearLicense).setOnClickListener {
            LicenseManager.clearLicense(this)
            refresh()
        }
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
