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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        initialiseViews()
        setupLoginScreen()
    }

    private fun initialiseViews() {
        txtLoginTitle = findViewById(R.id.txtLoginTitle)
        edtUserId = findViewById(R.id.edtUserId)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
    }

    private fun setupLoginScreen() {
        val hasUser = SecurityManager.hasUser(this)

        if (hasUser) {
            txtLoginTitle.text = "MyAdv"
            btnLogin.text = "LOGIN"
        } else {
            txtLoginTitle.text = "Create MyAdv Login"
            btnLogin.text = "CREATE LOGIN"
        }

        btnLogin.setOnClickListener {
            if (hasUser) loginUser() else createUser()
        }
    }

    private fun createUser() {
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()

        if (userId.isEmpty()) {
            edtUserId.error = "Enter User ID"
            edtUserId.requestFocus()
            return
        }
        if (password.isEmpty()) {
            edtPassword.error = "Enter password"
            edtPassword.requestFocus()
            return
        }
        if (password.length < 6) {
            edtPassword.error = "Password must be at least 6 characters"
            edtPassword.requestFocus()
            return
        }

        val created = SecurityManager.createUser(this, userId, password)
        if (created) {
            UiFeedback.success(this, "Login created successfully.")
            openMainActivity()
        } else {
            UiFeedback.error(this, "Unable to create login.", true)
        }
    }

    private fun loginUser() {
        val userId = edtUserId.text.toString().trim()
        val password = edtPassword.text.toString()

        if (userId.isEmpty()) {
            edtUserId.error = "Enter User ID"
            edtUserId.requestFocus()
            return
        }
        if (password.isEmpty()) {
            edtPassword.error = "Enter password"
            edtPassword.requestFocus()
            return
        }

        val authenticated = SecurityManager.authenticate(this, userId, password)
        if (authenticated) {
            AppLogger.info(this, "AUTH", "User login successful.")
            UiFeedback.success(this, "Welcome to MyAdv")
            openMainActivity()
        } else {
            AppLogger.warning(this, "AUTH", "Invalid login attempt.")
            UiFeedback.error(this, "Invalid User ID or password.", true)
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
