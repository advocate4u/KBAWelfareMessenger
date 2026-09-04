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
        if (!LicenseManager.isLicenseValid(this)) {
            openLicenseActivation()
            return
        }
        setContentView(R.layout.activity_login)
        initialiseViews()
        setupLoginScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::btnLogin.isInitialized) {
            if (!LicenseManager.isLicenseValid(this)) openLicenseActivation() else setupLoginScreen()
        }
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
        txtLoginTitle.text = "MyAdv"

        if (hasUser) {
            txtLoginMode.text = "LOGIN"
            txtLoginDescription.text = "Enter your User ID and MyAdv password."
            edtPassword.hint = "Password"
            btnLogin.text = "LOGIN"
        } else {
            txtLoginMode.text = "CREATE YOUR MYADV LOGIN"
            txtLoginDescription.text = "License verified. Create the administrator account for this licensed device. No administrator password is pre-shared."
            edtPassword.hint = "Create Password (minimum 6 characters)"
            btnLogin.text = "CREATE LOGIN & CONTINUE"
            if (edtUserId.text.isNullOrBlank() && license != null) {
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
        if (password.length < 6) { edtPassword.error = "Create a password of at least 6 characters"; edtPassword.requestFocus(); return }

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
