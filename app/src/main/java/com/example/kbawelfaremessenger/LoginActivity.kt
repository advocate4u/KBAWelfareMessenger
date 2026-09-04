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

        val licensedPhone = license.phone.filter { it.isDigit() }.takeLast(10)
        val existingUser = SecurityManager.currentUserId(this)
        val existingUserMatchesLicense = existingUser?.let {
            it.filter(Char::isDigit).takeLast(10) == licensedPhone
        } == true

        // A local login belongs to the primary licensed phone. If a license was
        // changed to another primary phone, discard the old activation and start
        // the normal first-login creation flow for the new licensed identity.
        if (SecurityManager.hasUser(this) && !existingUserMatchesLicense) {
            SecurityManager.resetLocalLogin(this)
        }

        val hasMatchingUser = SecurityManager.hasUser(this) &&
            SecurityManager.currentUserId(this)?.filter(Char::isDigit)?.takeLast(10) == licensedPhone

        txtLoginDescription.visibility = View.GONE
        edtUserId.visibility = View.VISIBLE
        edtPassword.visibility = View.VISIBLE
        btnLogin.visibility = View.VISIBLE
        btnLicense.visibility = View.VISIBLE

        // The login identity always comes from the primary license phone.
        // It is deliberately non-editable for both CREATE LOGIN and LOGIN.
        edtUserId.setText(licensedPhone)
        edtUserId.setSelection(edtUserId.text.length)
        edtUserId.isEnabled = false
        edtUserId.isFocusable = false
        edtUserId.isFocusableInTouchMode = false
        edtUserId.isClickable = false
        edtUserId.isLongClickable = false

        if (hasMatchingUser) {
            txtLoginMode.text = "LOGIN"
            edtPassword.hint = "Password"
            btnLogin.text = "LOGIN"
            btnLogin.setOnClickListener { loginUser() }
        } else {
            txtLoginMode.text = "CREATE LOGIN"
            edtPassword.hint = "Create Password (4 digits)"
            btnLogin.text = "CREATE LOGIN"
            btnLogin.setOnClickListener { activateFirstLogin() }
        }

        btnLicense.text = "INSTALL / CHANGE LICENSE"
        btnLicense.setOnClickListener { openLicenseActivation() }
    }

    private fun activateFirstLogin() {
        val license = LicenseManager.getValidLicense(this) ?: run { openLicenseActivation(); return }
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        val licensedPhone = license.phone.filter { it.isDigit() }.takeLast(10)
        val enteredPhone = userId.filter { it.isDigit() }.takeLast(10)

        if (enteredPhone != licensedPhone) {
            UiFeedback.error(this, "The licensed primary phone number is fixed and cannot be changed.", true)
            return
        }
        if (!password.matches(Regex("^\\d{4}$"))) {
            edtPassword.error = "Enter a 4-digit PIN"
            edtPassword.requestFocus()
            return
        }

        if (SecurityManager.createUser(this, userId, password)) {
            UiFeedback.success(this, "Login created successfully. Welcome to MyAdv.")
            openMainActivity()
        } else {
            UiFeedback.error(this, "Unable to create login. Verify the license and PIN.", true)
        }
    }

    private fun loginUser() {
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()
        if (userId.isEmpty()) { edtUserId.error = "Licensed phone number is required"; return }
        if (password.isEmpty()) { edtPassword.error = "Enter password"; edtPassword.requestFocus(); return }
        if (!LicenseManager.isLicenseValid(this)) { openLicenseActivation(); return }

        if (SecurityManager.authenticate(this, userId, password)) {
            AppLogger.info(this, "AUTH", "User login successful.")
            UiFeedback.success(this, "Welcome to MyAdv")
            openMainActivity()
        } else {
            AppLogger.warning(this, "AUTH", "Invalid login attempt.")
            UiFeedback.error(this, "Invalid password.", true)
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
