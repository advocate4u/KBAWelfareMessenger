package com.example.kbawelfaremessenger

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthenticationActivity : AppCompatActivity() {
    private lateinit var usersContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityManager.isAdmin(this)) {
            Toast.makeText(this, "Administrator access required.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_authentication)
        supportActionBar?.title = if (SecurityManager.isSuperAdmin(this)) "Super Admin • Access Management" else "Admin • User Management"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        usersContainer = findViewById(R.id.usersContainer)
        findViewById<Button>(R.id.btnAddUser).setOnClickListener { showAddUserDialog() }
        refreshUsers()
    }

    override fun onResume() {
        super.onResume()
        if (::usersContainer.isInitialized) refreshUsers()
    }

    private fun refreshUsers() {
        usersContainer.removeAllViews()
        SecurityManager.listUsers(this).forEach { user ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                text = "${user.userId}  •  ${user.role.name}"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)
            if (user.userId != SecurityManager.currentUserId(this)) {
                row.addView(Button(this).apply {
                    text = "DELETE"
                    setOnClickListener { confirmDelete(user.userId) }
                })
            }
            usersContainer.addView(row)
        }
    }

    private fun showAddUserDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }

        val userId = EditText(this).apply {
            hint = "User ID / phone number"
            setSingleLine(true)
        }

        val password = EditText(this).apply {
            hint = "Password (minimum 6 characters)"
            setSingleLine(true)
            inputType = 0x81
        }

        val roleSpinner = Spinner(this)
        val roleOptions = if (SecurityManager.isSuperAdmin(this)) {
            listOf("USER", "ADMIN")
        } else {
            listOf("USER")
        }
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)

        layout.addView(userId)
        layout.addView(password)
        layout.addView(roleSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (SecurityManager.isSuperAdmin(this)) "Create Account" else "Add User")
            .setMessage(
                if (SecurityManager.isSuperAdmin(this))
                    "SUPER ADMIN can grant either ADMIN or USER access. SUPER ADMIN access cannot be granted from this screen."
                else
                    "ADMIN can create USER accounts only."
            )
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                val id = userId.text.toString().trim()
                val pass = password.text.toString()
                val selectedRole = if (roleSpinner.selectedItem.toString() == "ADMIN") UserRole.ADMIN else UserRole.USER
                when {
                    id.isBlank() -> Toast.makeText(this, "Enter User ID.", Toast.LENGTH_SHORT).show()
                    pass.length < 6 -> Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    !SecurityManager.addUser(this, id, pass, selectedRole) -> Toast.makeText(this, "Unable to create account. Check the User ID, role permission, or existing account.", Toast.LENGTH_LONG).show()
                    else -> {
                        Toast.makeText(this, "${selectedRole.name} account created.", Toast.LENGTH_SHORT).show()
                        refreshUsers()
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(userId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Delete account '$userId'? This cannot be undone.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                if (SecurityManager.deleteUser(this, userId)) {
                    Toast.makeText(this, "Account deleted.", Toast.LENGTH_SHORT).show()
                    refreshUsers()
                } else Toast.makeText(this, "Unable to delete account.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
