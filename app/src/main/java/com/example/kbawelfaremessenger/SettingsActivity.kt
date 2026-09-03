package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var edtNameColumn: EditText
    private lateinit var edtPhoneColumn: EditText
    private lateinit var edtDefaultMessage: EditText
    private lateinit var edtSmsDelay: EditText
    private lateinit var switchEditMessage: Switch
    private lateinit var switchSkipSent: Switch
    private lateinit var switchConfirmSend: Switch
    private lateinit var switchLogging: Switch
    private lateinit var btnSaveSettings: Button
    private lateinit var btnViewLogs: Button
    private lateinit var btnClearLogs: Button
    private lateinit var btnAuthentication: Button
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initialiseViews()
        loadSettings()
        applyRoleAccess()
        setupButtons()
    }

    private fun initialiseViews() {
        edtNameColumn = findViewById(R.id.edtNameColumn)
        edtPhoneColumn = findViewById(R.id.edtPhoneColumn)
        edtDefaultMessage = findViewById(R.id.edtDefaultMessage)
        edtSmsDelay = findViewById(R.id.edtSmsDelay)
        switchEditMessage = findViewById(R.id.switchEditMessage)
        switchSkipSent = findViewById(R.id.switchSkipSent)
        switchConfirmSend = findViewById(R.id.switchConfirmSend)
        switchLogging = findViewById(R.id.switchLogging)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnViewLogs = findViewById(R.id.btnViewLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnAuthentication = findViewById(R.id.btnAuthentication)
    }

    private fun applyRoleAccess() {
        val admin = SecurityManager.isAdmin(this)
        findViewById<Button>(R.id.btnLicense).visibility = if (admin) View.VISIBLE else View.GONE
        btnAuthentication.visibility = if (admin) View.VISIBLE else View.GONE
        if (!admin) {
            findViewById<TextView>(R.id.txtAuthenticationSection).visibility = View.GONE
        }
    }

    private fun loadSettings() {
        settings = AppSettingsManager.load(this)
        edtNameColumn.setText(settings.nameColumn)
        edtPhoneColumn.setText(settings.phoneColumn)
        edtDefaultMessage.setText(settings.defaultMessage)
        edtSmsDelay.setText(settings.smsDelayMs.toString())
        switchEditMessage.isChecked = settings.editMessageOnScreen
        switchSkipSent.isChecked = settings.skipAlreadySent
        switchConfirmSend.isChecked = settings.confirmBeforeBulkSend
        switchLogging.isChecked = settings.loggingEnabled
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnAdvocateHelper).setOnClickListener {
            startActivity(Intent(this, AdvocateHelperActivity::class.java))
        }
        findViewById<Button>(R.id.btnLicense).setOnClickListener {
            if (SecurityManager.isAdmin(this)) startActivity(Intent(this, LicenseActivity::class.java))
            else Toast.makeText(this, "Administrator access required.", Toast.LENGTH_SHORT).show()
        }
        btnAuthentication.setOnClickListener {
            if (SecurityManager.isAdmin(this)) startActivity(Intent(this, AuthenticationActivity::class.java))
            else Toast.makeText(this, "Administrator access required.", Toast.LENGTH_SHORT).show()
        }
        btnSaveSettings.setOnClickListener {
            val nameColumn = edtNameColumn.text.toString().trim()
            val phoneColumn = edtPhoneColumn.text.toString().trim()
            val defaultMessage = edtDefaultMessage.text.toString().trim()
            if (nameColumn.isEmpty()) { edtNameColumn.error = "Enter Name column"; edtNameColumn.requestFocus(); return@setOnClickListener }
            if (phoneColumn.isEmpty()) { edtPhoneColumn.error = "Enter Phone column"; edtPhoneColumn.requestFocus(); return@setOnClickListener }
            if (defaultMessage.isEmpty()) { edtDefaultMessage.error = "Enter default message"; edtDefaultMessage.requestFocus(); return@setOnClickListener }
            val smsDelay = edtSmsDelay.text.toString().trim().toLongOrNull()
            if (smsDelay == null || smsDelay < 0) { edtSmsDelay.error = "Enter a valid delay in milliseconds"; edtSmsDelay.requestFocus(); return@setOnClickListener }
            settings = settings.copy(nameColumn = nameColumn, phoneColumn = phoneColumn, defaultMessage = defaultMessage, smsDelayMs = smsDelay, editMessageOnScreen = switchEditMessage.isChecked, skipAlreadySent = switchSkipSent.isChecked, confirmBeforeBulkSend = switchConfirmSend.isChecked, loggingEnabled = switchLogging.isChecked)
            AppSettingsManager.save(this, settings)
            AppLogger.success(this, "SETTINGS", "Application settings saved")
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        btnViewLogs.setOnClickListener { Toast.makeText(this, "Log viewer", Toast.LENGTH_SHORT).show() }
        btnClearLogs.setOnClickListener { AppLogger.clear(this); Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
