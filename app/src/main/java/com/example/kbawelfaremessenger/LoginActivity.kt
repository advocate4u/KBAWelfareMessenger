package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var txtLoginTitle: TextView
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
        edtUserId = findViewById(R.id.edtUserId)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnLicense = findViewById(R.id.btnLicense)
    }

    private fun setupLoginScreen() {
        val hasUser = SecurityManager.hasUser(this)
        val hasLicense = LicenseManager.isLicenseValid(this)
        txtLoginTitle.text = "MyAdv"
        btnLogin.text = if (hasUser) "LOGIN" else if (hasLicense) "CREATE PASSWORD & ACTIVATE" else "ACTIVATE LICENSE FIRST"
        btnLicense.text = if (hasLicense) "VIEW / CHANGE LICENSE" else "INSTALL LICENSE"

        btnLogin.setOnClickListener {
            if (hasUser) loginUser() else activateFirstLogin()
        }
        btnLicense.setOnClickListener { startActivity(Intent(this, LicenseActivity::class.java)) }
    }

    private fun activateFirstLogin() {
        val license = LicenseManager.getValidLicense(this)
        if (license == null) {
            UiFeedback.error(this, "Install your MyAdv license first. The license determines whether this account is ADMIN or USER.", true)
            startActivity(Intent(this, LicenseActivity::class.java))
            return
        }

        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        if (userId.isEmpty()) { edtUserId.error = "Enter User ID / phone number"; edtUserId.requestFocus(); return }
        if (password.length < 6) { edtPassword.error = "Create a password of at least 6 characters"; edtPassword.requestFocus(); return }

        val licensedPhone = license.phone.filter { it.isDigit() }.takeLast(10)
        val enteredPhone = userId.filter { it.isDigit() }.takeLast(10)
        if (licensedPhone != enteredPhone) {
            UiFeedback.error(this, "User ID must match the phone number on the installed license.", true)
            return
        }

        val created = SecurityManager.createUser(this, userId, password)
        if (created) {
            UiFeedback.success(this, if (license.role == UserRole.ADMIN) "Admin account activated successfully." else "User account activated successfully.")
            openMainActivity()
        } else {
            UiFeedback.error(this, "Unable to activate account. Make sure the license matches this phone and is valid.", true)
        }
    }

    private fun loginUser() {
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        if (userId.isEmpty()) { edtUserId.error = "Enter User ID / phone number"; edtUserId.requestFocus(); return }
        if (password.isEmpty()) { edtPassword.error = "Enter password"; edtPassword.requestFocus(); return }
        if (SecurityManager.authenticate(this, userId, password)) {
            AppLogger.info(this, "AUTH", "User login successful.")
            UiFeedback.success(this, "Welcome to MyAdv")
            openMainActivity()
        } else {
            AppLogger.warning(this, "AUTH", "Invalid login attempt.")
            UiFeedback.error(this, "Invalid User ID or password.", true)
        }
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }
}
