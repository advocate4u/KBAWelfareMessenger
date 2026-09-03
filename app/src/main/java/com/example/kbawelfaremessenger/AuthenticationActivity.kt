package com.example.kbawelfaremessenger

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
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
        supportActionBar?.title = "User Management"
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
        val userId = android.widget.EditText(this).apply { hint = "User ID"; singleLine = true }
        val password = android.widget.EditText(this).apply { hint = "Password (minimum 6 characters)"; singleLine = true; inputType = 0x81 }
        layout.addView(userId)
        layout.addView(password)
        AlertDialog.Builder(this)
            .setTitle("Add User")
            .setMessage("New accounts are created as USER accounts.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                val id = userId.text.toString().trim()
                val pass = password.text.toString()
                when {
                    id.isBlank() -> Toast.makeText(this, "Enter User ID.", Toast.LENGTH_SHORT).show()
                    pass.length < 6 -> Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    !SecurityManager.addUser(this, id, pass) -> Toast.makeText(this, "Unable to create user. ID may already exist.", Toast.LENGTH_LONG).show()
                    else -> { Toast.makeText(this, "User created.", Toast.LENGTH_SHORT).show(); refreshUsers() }
                }
            }
            .show()
    }

    private fun confirmDelete(userId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Delete user '$userId'? This cannot be undone.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                if (SecurityManager.deleteUser(this, userId)) {
                    Toast.makeText(this, "User deleted.", Toast.LENGTH_SHORT).show()
                    refreshUsers()
                } else Toast.makeText(this, "Unable to delete user.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
