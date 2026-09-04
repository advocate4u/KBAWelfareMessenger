package com.example.kbawelfaremessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var edtNameColumn: EditText; private lateinit var edtPhoneColumn: EditText; private lateinit var edtDefaultMessage: EditText; private lateinit var edtSmsDelay: EditText
    private lateinit var switchEditMessage: Switch; private lateinit var switchSkipSent: Switch; private lateinit var switchConfirmSend: Switch; private lateinit var switchLogging: Switch
    private lateinit var btnSaveSettings: Button; private lateinit var btnViewLogs: Button; private lateinit var btnClearLogs: Button; private lateinit var btnAuthentication: Button
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_settings)
        supportActionBar?.title = "Settings"; supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initialiseViews(); loadSettings(); applyRoleAccess(); setupButtons(); addDiaryLauncher()
    }
    private fun initialiseViews() {
        edtNameColumn=findViewById(R.id.edtNameColumn); edtPhoneColumn=findViewById(R.id.edtPhoneColumn); edtDefaultMessage=findViewById(R.id.edtDefaultMessage); edtSmsDelay=findViewById(R.id.edtSmsDelay)
        switchEditMessage=findViewById(R.id.switchEditMessage); switchSkipSent=findViewById(R.id.switchSkipSent); switchConfirmSend=findViewById(R.id.switchConfirmSend); switchLogging=findViewById(R.id.switchLogging)
        btnSaveSettings=findViewById(R.id.btnSaveSettings); btnViewLogs=findViewById(R.id.btnViewLogs); btnClearLogs=findViewById(R.id.btnClearLogs); btnAuthentication=findViewById(R.id.btnAuthentication)
    }
    private fun applyRoleAccess() {
        val admin=SecurityManager.isAdmin(this); findViewById<Button>(R.id.btnLicense).visibility=if(admin) View.VISIBLE else View.GONE; btnAuthentication.visibility=if(admin) View.VISIBLE else View.GONE
        if(!admin) findViewById<TextView>(R.id.txtAuthenticationSection).visibility=View.GONE
    }
    private fun loadSettings() {
        settings=AppSettingsManager.load(this); edtNameColumn.setText(settings.nameColumn); edtPhoneColumn.setText(settings.phoneColumn); edtDefaultMessage.setText(settings.defaultMessage); edtSmsDelay.setText(settings.smsDelayMs.toString())
        switchEditMessage.isChecked=settings.editMessageOnScreen; switchSkipSent.isChecked=settings.skipAlreadySent; switchConfirmSend.isChecked=settings.confirmBeforeBulkSend; switchLogging.isChecked=settings.loggingEnabled
    }
    private fun setupButtons() {
        findViewById<Button>(R.id.btnAdvocateHelper).setOnClickListener { if(LicenseManager.isFeatureEnabled(this,"advocate_helper")) startActivity(Intent(this,AdvocateHelperActivity::class.java)) else Toast.makeText(this,"Advocate Helper is not enabled in this license.",Toast.LENGTH_LONG).show() }
        findViewById<Button>(R.id.btnLicense).setOnClickListener { if(SecurityManager.isAdmin(this)) startActivity(Intent(this,LicenseActivity::class.java)) else Toast.makeText(this,"Administrator access required.",Toast.LENGTH_SHORT).show() }
        btnAuthentication.setOnClickListener { if(SecurityManager.isAdmin(this)) startActivity(Intent(this,AuthenticationActivity::class.java)) else Toast.makeText(this,"Administrator access required.",Toast.LENGTH_SHORT).show() }
        btnSaveSettings.setOnClickListener {
            val nameColumn=edtNameColumn.text.toString().trim(); val phoneColumn=edtPhoneColumn.text.toString().trim(); val defaultMessage=edtDefaultMessage.text.toString().trim()
            if(nameColumn.isEmpty()){edtNameColumn.error="Enter Name column";return@setOnClickListener}; if(phoneColumn.isEmpty()){edtPhoneColumn.error="Enter Phone column";return@setOnClickListener}; if(defaultMessage.isEmpty()){edtDefaultMessage.error="Enter default message";return@setOnClickListener}
            val smsDelay=edtSmsDelay.text.toString().trim().toLongOrNull(); if(smsDelay==null||smsDelay<0){edtSmsDelay.error="Enter a valid delay in milliseconds";return@setOnClickListener}
            settings=settings.copy(nameColumn=nameColumn,phoneColumn=phoneColumn,defaultMessage=defaultMessage,smsDelayMs=smsDelay,editMessageOnScreen=switchEditMessage.isChecked,skipAlreadySent=switchSkipSent.isChecked,confirmBeforeBulkSend=switchConfirmSend.isChecked,loggingEnabled=switchLogging.isChecked)
            AppSettingsManager.save(this,settings); AppLogger.success(this,"SETTINGS","Application settings saved"); Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show(); finish()
        }
        btnViewLogs.setOnClickListener { showLogs() }
        btnClearLogs.setOnClickListener { AlertDialog.Builder(this).setTitle("Clear logs?").setMessage("All local application logs will be deleted.").setNegativeButton("CANCEL",null).setPositiveButton("CLEAR"){_,_->AppLogger.clear(this);Toast.makeText(this,"Logs cleared",Toast.LENGTH_SHORT).show()}.show() }
    }
    private fun addDiaryLauncher() {
        // android.R.id.content is a FrameLayout, not a LinearLayout. Casting it
        // directly caused SettingsActivity to crash as soon as it was opened.
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        val scrollView = (0 until contentRoot.childCount)
            .asSequence()
            .map { contentRoot.getChildAt(it) }
            .filterIsInstance<ScrollView>()
            .firstOrNull() ?: return
        val content = scrollView.getChildAt(0) as? LinearLayout ?: return
        val button = Button(this).apply {
            text = "OPEN ADVOCATE DIARY"
            setOnClickListener {
                if (LicenseManager.isFeatureEnabled(this@SettingsActivity, "advocate_diary")) {
                    startActivity(Intent(this@SettingsActivity, AdvocateDiaryActivity::class.java))
                } else {
                    Toast.makeText(this@SettingsActivity, "Advocate Diary is not enabled in this license.", Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(button, minOf(3, content.childCount))
    }
    private fun showLogs() {
        if(!LicenseManager.isFeatureEnabled(this,"sms_logs")&&!SecurityManager.isAdmin(this)){Toast.makeText(this,"Logs are not enabled in this license.",Toast.LENGTH_LONG).show();return}
        val view=TextView(this).apply{text=AppLogger.read(this@SettingsActivity);textSize=13f;setPadding(24,12,24,12);setTextIsSelectable(true)}
        AlertDialog.Builder(this).setTitle("Application Logs").setView(ScrollView(this).apply{addView(view)}).setPositiveButton("CLOSE",null).show()
    }
    override fun onSupportNavigateUp():Boolean{finish();return true}
}
