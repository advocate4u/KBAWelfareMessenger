package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var txtLoginTitle: TextView
    private lateinit var txtLoginMode: TextView
    private lateinit var txtLoginDescription: TextView
    private lateinit var edtUserId: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnLicense: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        initialiseViews()
        setupLoginScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::btnLogin.isInitialized) setupLoginScreen()
    }

    private fun initialiseViews() {
        txtLoginTitle = findViewById(R.id.txtLoginTitle)
        txtLoginMode = findViewById(R.id.txtLoginMode)
        txtLoginDescription = findViewById(R.id.txtLoginDescription)
        edtUserId = findViewById(R.id.edtUserId)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnLicense = findViewById(R.id.btnLicense)
    }

    private fun setupLoginScreen() {
        val hasUser = SecurityManager.hasUser(this)
        val license = LicenseManager.getValidLicense(this)
        txtLoginTitle.visibility = View.GONE

        if (license == null) {
            txtLoginMode.text = "INSTALL LICENSE FIRST"
            txtLoginDescription.visibility = View.GONE
            edtUserId.visibility = View.GONE
            edtPassword.visibility = View.GONE
            btnLogin.visibility = View.VISIBLE
            btnLicense.visibility = View.GONE
            btnLogin.text = "INSTALL LICENSE"
            btnLogin.setOnClickListener { openLicenseActivation() }
            return
        }

        txtLoginDescription.visibility = View.GONE
        edtUserId.visibility = View.VISIBLE
        edtPassword.visibility = View.VISIBLE
        btnLogin.visibility = View.VISIBLE
        btnLicense.visibility = View.VISIBLE
        edtPassword.isEnabled = true

        if (hasUser) {
            txtLoginMode.text = "LOGIN"
            edtUserId.isEnabled = true
            edtUserId.isFocusable = true
            edtUserId.isClickable = true
            edtPassword.hint = "Password"
            btnLogin.text = "LOGIN"
        } else {
            txtLoginMode.text = "CREATE LOGIN"
            val licensedPhone = license.phone.filter { it.isDigit() }.takeLast(10)
            edtUserId.setText(licensedPhone)
            edtUserId.isEnabled = false
            edtUserId.isFocusable = false
            edtUserId.isClickable = false
            edtPassword.hint = "Create Password (4 digits)"
            btnLogin.text = "CREATE LOGIN"
        }

        btnLicense.text = "INSTALL / CHANGE LICENSE"
        btnLogin.setOnClickListener { if (hasUser) loginUser() else activateFirstLogin() }
        btnLicense.setOnClickListener { openLicenseActivation() }
    }

    private fun activateFirstLogin() {
        val license = LicenseManager.getValidLicense(this) ?: run { openLicenseActivation(); return }
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        if (userId.isEmpty()) { edtUserId.error = "Licensed phone number is required"; edtUserId.requestFocus(); return }
        if (!password.matches(Regex("^\\d{4}$"))) { edtPassword.error = "Enter a 4-digit PIN"; edtPassword.requestFocus(); return }

        val licensedPhone = license.phone.filter { it.isDigit() }.takeLast(10)
        val enteredPhone = userId.filter { it.isDigit() }.takeLast(10)
        if (licensedPhone != enteredPhone) {
            UiFeedback.error(this, "The User ID must match the primary phone number on the installed license.", true)
            return
        }

        if (SecurityManager.createUser(this, userId, password)) {
            UiFeedback.success(this, "Login created successfully. Welcome to MyAdv.")
            openMainActivity()
        } else {
            UiFeedback.error(this, "Unable to create login. Verify the license and User ID.", true)
        }
    }

    private fun loginUser() {
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        if (userId.isEmpty()) { edtUserId.error = "Enter User ID"; edtUserId.requestFocus(); return }
        if (password.isEmpty()) { edtPassword.error = "Enter password"; edtPassword.requestFocus(); return }
        if (!LicenseManager.isLicenseValid(this)) { openLicenseActivation(); return }

        if (SecurityManager.authenticate(this, userId, password)) {
            AppLogger.info(this, "AUTH", "User login successful.")
            UiFeedback.success(this, "Welcome to MyAdv")
            openMainActivity()
        } else {
            AppLogger.warning(this, "AUTH", "Invalid login attempt.")
            UiFeedback.error(this, "Invalid User ID or password.", true)
        }
    }

    private fun openLicenseActivation() {
        startActivity(Intent(this, LicenseActivity::class.java))
        finish()
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }
}
