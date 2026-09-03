package com.example.kbawelfaremessenger

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class BackupRestoreActivity : AppCompatActivity() {
    private lateinit var txtInfo: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var db: AdvocateCaseDbHelper
    private var pendingRestore: List<AdvocateCase>? = null

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) requestPassword("Create encrypted backup") { password -> writeBackup(uri, password) }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) requestPassword("Open encrypted backup") { password -> readBackup(uri, password) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_restore)
        supportActionBar?.title = "Backup & Restore"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        db = AdvocateCaseDbHelper(this)
        txtInfo = findViewById(R.id.txtBackupInfo)
        refreshInfo()

        findViewById<Button>(R.id.btnCreateBackup).setOnClickListener {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            createBackup.launch("KBAWelfareBackup_$stamp.kba")
        }
        findViewById<Button>(R.id.btnRestoreBackup).setOnClickListener {
            openBackup.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
    }

    private fun refreshInfo() {
        executor.execute {
            val count = db.getTotalCaseCount()
            runOnUiThread { txtInfo.text = "Current saved cases: $count\n\nBackups are encrypted with your password. Restore uses MERGE mode: new cases are added and matching case numbers are updated only after you confirm." }
        }
    }

    private fun requestPassword(title: String, onPassword: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Backup password (minimum 6 characters)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Use the same password when restoring this .kba file.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CONTINUE") { _, _ ->
                val password = input.text.toString()
                if (password.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_LONG).show()
                } else onPassword(password)
            }
            .show()
    }

    private fun writeBackup(uri: Uri, password: String) {
        executor.execute {
            try {
                val cases = db.getAllCases()
                contentResolver.openOutputStream(uri)?.use { AdvocateBackupManager.writeBackup(it, cases, password) }
                    ?: throw IllegalStateException("Unable to open selected file.")
                runOnUiThread { Toast.makeText(this, "Encrypted backup created: ${cases.size} cases", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Backup failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun readBackup(uri: Uri, password: String) {
        executor.execute {
            try {
                val cases = contentResolver.openInputStream(uri)?.use { AdvocateBackupManager.readBackup(it, password) }
                    ?: throw IllegalStateException("Unable to open selected backup.")
                pendingRestore = cases
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Confirm Restore")
                        .setMessage("Backup contains ${cases.size} valid cases.\n\nRestore will MERGE this data with the cases already on this device. Matching case numbers will be updated. Nothing will be deleted.\n\nContinue?")
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("RESTORE & MERGE") { _, _ -> performRestore() }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Restore failed: wrong password or invalid backup.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun performRestore() {
        val cases = pendingRestore ?: return
        pendingRestore = null
        executor.execute {
            try {
                val (inserted, updated) = db.mergeCases(cases)
                runOnUiThread {
                    Toast.makeText(this, "Restore complete: $inserted added, $updated updated.", Toast.LENGTH_LONG).show()
                    refreshInfo()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Restore failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        executor.shutdownNow()
        db.close()
        super.onDestroy()
    }
}
