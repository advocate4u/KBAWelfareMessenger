package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
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
        txtLoginTitle.text = ""
        txtLoginTitle.visibility = android.view.View.GONE

        if (license == null) {
            txtLoginMode.text = "INSTALL LICENSE FIRST"
            txtLoginDescription.text = "Install a valid MyAdv license to continue."
            edtUserId.setText("")
            edtPassword.setText("")
            edtUserId.isEnabled = false
            edtPassword.isEnabled = false
            btnLogin.text = "INSTALL LICENSE"
            btnLicense.text = "INSTALL LICENSE"
            btnLogin.setOnClickListener { openLicenseActivation() }
            btnLicense.setOnClickListener { openLicenseActivation() }
            return
        }
        edtPassword.isEnabled = true
        edtUserId.isEnabled = hasUser
        edtUserId.isFocusable = hasUser
        edtUserId.isClickable = hasUser

        if (hasUser) {
            txtLoginMode.text = "LOGIN"
            txtLoginDescription.text = "Enter your User ID and MyAdv password."
            edtPassword.hint = "Password"
            btnLogin.text = "LOGIN"
        } else {
            txtLoginMode.text = "CREATE YOUR MYADV LOGIN"
            txtLoginDescription.text = "License verified. Create your 4-digit PIN for this licensed device."
            edtPassword.hint = "Create 4-digit PIN"
            btnLogin.text = "CREATE LOGIN & CONTINUE"
            if (license != null) {
                edtUserId.setText(license.phone.filter { it.isDigit() }.takeLast(10))
                edtUserId.setSelection(edtUserId.text.length)
            }
        }

        btnLicense.text = "VIEW / CHANGE LICENSE"
        btnLogin.setOnClickListener { if (hasUser) loginUser() else activateFirstLogin() }
        btnLicense.setOnClickListener { openLicenseActivation() }
    }

    private fun activateFirstLogin() {
        val license = LicenseManager.getValidLicense(this) ?: run { openLicenseActivation(); return }
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        if (userId.isEmpty()) { edtUserId.error = "Enter User ID / phone number"; edtUserId.requestFocus(); return }
        if (!password.matches(Regex("^\\d{4}$"))) { edtPassword.error = "Enter a 4-digit PIN"; edtPassword.requestFocus(); return }

        val licensedPhone = license.phone.filter { it.isDigit() }.takeLast(10)
        val enteredPhone = userId.filter { it.isDigit() }.takeLast(10)
        if (licensedPhone != enteredPhone) {
            UiFeedback.error(this, "For the first activation, User ID must match the phone number on the installed license.", true)
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

        // Existing users created by an administrator authenticate using their own account.
        // The installed license only needs to be valid; it does not have to contain that user's phone.
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
