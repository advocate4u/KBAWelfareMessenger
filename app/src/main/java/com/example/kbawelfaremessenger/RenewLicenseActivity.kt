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

class RenewLicenseActivity : AppCompatActivity() {
    private val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renew_license)
        supportActionBar?.title = "Renew MyAdv License"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val current = LicenseManager.getInstalledLicense(this)
        findViewById<TextView>(R.id.txtCurrentLicense).text = if (current == null) {
            "Current license: Not available"
        } else {
            "Current primary phone: ${current.phone}\n" +
                "Current license: ${current.licenseId}\n" +
                "Valid until: ${current.expiryDate.format(formatter)}"
        }

        val id = findViewById<EditText>(R.id.edtRenewLicenseId)
        val token = findViewById<EditText>(R.id.edtRenewSignedToken)
        val status = findViewById<TextView>(R.id.txtRenewStatus)

        findViewById<Button>(R.id.btnRenewLicense).setOnClickListener {
            val result = LicenseManager.renewLicense(this, id.text.toString(), token.text.toString())
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (!result.allowed) {
                refreshStatus(status)
                return@setOnClickListener
            }

            if (SecurityManager.hasUser(this)) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            finish()
        }

        refreshStatus(status)
    }

    private fun refreshStatus(status: TextView) {
        val license = LicenseManager.getInstalledLicense(this)
        status.text = if (license == null) {
            "Status: NO LICENSE"
        } else {
            "Installed license: ${license.licenseId}\n" +
                "Primary phone: ${license.phone}\n" +
                "Valid until: ${license.expiryDate.format(formatter)}"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
