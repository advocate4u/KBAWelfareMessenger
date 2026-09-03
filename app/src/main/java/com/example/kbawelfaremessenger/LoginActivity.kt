package com.example.kbawelfaremessenger

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    companion object {

        private const val PREFS_NAME =
            "KBA_WELFARE_LOGIN"

        private const val KEY_PASSWORD_HASH =
            "password_hash"

        private const val DEFAULT_USERNAME =
            "admin"

        /*
         * Initial password.
         *
         * Change this before distributing the APK if required.
         */
        private const val DEFAULT_PASSWORD =
            "KBA@2026"
    }

    private lateinit var edtUsername: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnChangePassword: Button
    private lateinit var txtLoginStatus: TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_login
        )

        initialiseViews()

        initialiseDefaultPassword()

        setupButtons()
    }

    private fun initialiseViews() {

        edtUsername =
            findViewById(R.id.edtUsername)

        edtPassword =
            findViewById(R.id.edtPassword)

        btnLogin =
            findViewById(R.id.btnLogin)

        btnChangePassword =
            findViewById(R.id.btnChangePassword)

        txtLoginStatus =
            findViewById(R.id.txtLoginStatus)

        edtUsername.setText(
            DEFAULT_USERNAME
        )
    }

    private fun initialiseDefaultPassword() {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        if (
            !prefs.contains(
                KEY_PASSWORD_HASH
            )
        ) {

            prefs.edit()
                .putString(
                    KEY_PASSWORD_HASH,
                    sha256(DEFAULT_PASSWORD)
                )
                .apply()
        }
    }

    private fun setupButtons() {

        btnLogin.setOnClickListener {

            login()
        }

        btnChangePassword.setOnClickListener {

            changePassword()
        }
    }

    private fun login() {

        val username =
            edtUsername.text
                .toString()
                .trim()

        val password =
            edtPassword.text
                .toString()

        if (username.isEmpty()) {

            txtLoginStatus.text =
                "Please enter username."

            edtUsername.requestFocus()

            return
        }

        if (password.isEmpty()) {

            txtLoginStatus.text =
                "Please enter password."

            edtPassword.requestFocus()

            return
        }

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val storedHash =
            prefs.getString(
                KEY_PASSWORD_HASH,
                sha256(DEFAULT_PASSWORD)
            )

        val enteredHash =
            sha256(password)

        if (
            username.equals(
                DEFAULT_USERNAME,
                ignoreCase = true
            ) &&
            enteredHash == storedHash
        ) {

            txtLoginStatus.text =
                "Login successful."

            Toast.makeText(
                this,
                "Welcome",
                Toast.LENGTH_SHORT
            ).show()

            startActivity(
                android.content.Intent(
                    this,
                    MainActivity::class.java
                )
            )

            finish()

        } else {

            txtLoginStatus.text =
                "Invalid username or password."

            edtPassword.selectAll()
            edtPassword.requestFocus()
        }
    }

    private fun changePassword() {

        val dialogView =
            layoutInflater.inflate(
                R.layout.activity_login_change_password,
                null
            )

        /*
         * This layout is intentionally NOT used because
         * the project is being kept to the requested files.
         *
         * Instead use a simple programmatic dialog below.
         */

        val oldPassword =
            EditText(this).apply {
                hint = "Current password"
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

        val newPassword =
            EditText(this).apply {
                hint = "New password"
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

        val confirmPassword =
            EditText(this).apply {
                hint = "Confirm new password"
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

        val container =
            android.widget.LinearLayout(this).apply {

                orientation =
                    android.widget.LinearLayout.VERTICAL

                setPadding(
                    50,
                    20,
                    50,
                    10
                )

                addView(
                    oldPassword,
                    android.widget.LinearLayout.LayoutParams(
                        -1,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                addView(
                    newPassword,
                    android.widget.LinearLayout.LayoutParams(
                        -1,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                addView(
                    confirmPassword,
                    android.widget.LinearLayout.LayoutParams(
                        -1,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Change") { _, _ ->

                val old =
                    oldPassword.text.toString()

                val newPass =
                    newPassword.text.toString()

                val confirm =
                    confirmPassword.text.toString()

                val prefs =
                    getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )

                val storedHash =
                    prefs.getString(
                        KEY_PASSWORD_HASH,
                        sha256(DEFAULT_PASSWORD)
                    )

                when {

                    sha256(old) != storedHash -> {

                        Toast.makeText(
                            this,
                            "Current password is incorrect.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    newPass.length < 6 -> {

                        Toast.makeText(
                            this,
                            "New password must contain at least 6 characters.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    newPass != confirm -> {

                        Toast.makeText(
                            this,
                            "New passwords do not match.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {

                        prefs.edit()
                            .putString(
                                KEY_PASSWORD_HASH,
                                sha256(newPass)
                            )
                            .apply()

                        Toast.makeText(
                            this,
                            "Password changed successfully.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun sha256(
        value: String
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        val bytes =
            digest.digest(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )

        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }
}
