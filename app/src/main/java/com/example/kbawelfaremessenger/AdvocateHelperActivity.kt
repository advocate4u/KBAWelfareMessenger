package com.example.kbawelfaremessenger

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class AdvocateHelperActivity : AppCompatActivity() {
    private lateinit var db: AdvocateCaseDbHelper
    private lateinit var adapter: AdvocateCaseAdapter
    private lateinit var caseNumber: EditText
    private lateinit var clientName: EditText
    private lateinit var clientPhone: EditText
    private lateinit var court: EditText
    private lateinit var previousDate: EditText
    private lateinit var currentDate: EditText
    private lateinit var nextDate: EditText
    private lateinit var currentUpdate: EditText
    private lateinit var newUpdate: EditText
    private lateinit var totalFee: EditText
    private lateinit var received: EditText
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private lateinit var save: Button
    private lateinit var totalCases: TextView
    private lateinit var todayHearings: TextView
    private lateinit var upcomingHearings: TextView
    private lateinit var pendingFees: TextView
    private lateinit var currentBalance: TextView
    private lateinit var licenseStatus: TextView
    private val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply { isLenient = false }
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private var editingId = 0L
    private var editingCreatedAt = 0L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_advocate_helper)
        supportActionBar?.title = "Advocate Helper"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        db = AdvocateCaseDbHelper(this)

        caseNumber = findViewById(R.id.edtCaseNumber)
        clientName = findViewById(R.id.edtClientName)
        clientPhone = findViewById(R.id.edtClientPhone)
        court = findViewById(R.id.edtCourtName)
        previousDate = findViewById(R.id.edtPreviousDate)
        currentDate = findViewById(R.id.edtCurrentDate)
        nextDate = findViewById(R.id.edtNextDate)
        currentUpdate = findViewById(R.id.edtCurrentUpdate)
        newUpdate = findViewById(R.id.edtNewUpdate)
        totalFee = findViewById(R.id.edtTotalFee)
        received = findViewById(R.id.edtAmountReceived)
        currentBalance = findViewById(R.id.txtCurrentBalance)
        licenseStatus = findViewById(R.id.txtLicenseStatus)
        search = findViewById(R.id.edtCaseSearch)
        empty = findViewById(R.id.txtEmptyCases)
        save = findViewById(R.id.btnSaveCase)
        totalCases = findViewById(R.id.txtTotalCases)
        todayHearings = findViewById(R.id.txtTodayHearings)
        upcomingHearings = findViewById(R.id.txtUpcomingHearings)
        pendingFees = findViewById(R.id.txtPendingFees)

        previousDate.setOnClickListener { pick(previousDate) }
        currentDate.setOnClickListener { pick(currentDate) }
        nextDate.setOnClickListener { pick(nextDate) }
        findViewById<Button>(R.id.btnClearForm).setOnClickListener { clearForm() }
        findViewById<Button>(R.id.btnReminderSettings).setOnClickListener { openNotificationSettings() }
        findViewById<Button>(R.id.btnBackupRestore).setOnClickListener { startActivity(Intent(this, BackupRestoreActivity::class.java)) }
        findViewById<Button>(R.id.btnSendNextDateMessage).setOnClickListener { sendNextDateMessage() }
        findViewById<Button>(R.id.btnPaymentReminder).setOnClickListener { sendPaymentReminder() }
        save.setOnClickListener { saveCase() }

        val feeWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateBalancePreview() }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        totalFee.addTextChangedListener(feeWatcher)
        received.addTextChangedListener(feeWatcher)

        adapter = AdvocateCaseAdapter(emptyList(), { fill(it) }, { remove(it) })
        findViewById<RecyclerView>(R.id.recyclerCases).apply {
            layoutManager = LinearLayoutManager(this@AdvocateHelperActivity)
            adapter = this@AdvocateHelperActivity.adapter
            isNestedScrollingEnabled = false
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { load(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        requestNotificationPermission()
        scheduleReminderWorker()
        refreshLicenseStatus()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) {
            refreshDashboard()
            refreshLicenseStatus()
        }
    }

    private fun refreshLicenseStatus() {
        val license = LicenseManager.getInstalledLicense(this)
        licenseStatus.text = if (license == null) "License: NOT ACTIVATED" else {
            val valid = LicenseManager.isLicenseValid(this)
            "License: ${if (valid) "ACTIVE" else "EXPIRED"}  •  ID: ${license.licenseId}  •  Expiry: ${license.expiryDate}"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun pick(target: EditText) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d -> c.set(y, m, d); target.setText(format.format(c.time)) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveCase() {
        val cn = caseNumber.text.toString().trim()
        val client = clientName.text.toString().trim()
        val fee = totalFee.text.toString().trim().toDoubleOrNull() ?: 0.0
        val paid = received.text.toString().trim().toDoubleOrNull() ?: 0.0
        if (cn.isEmpty()) { caseNumber.error = "Enter case number"; return }
        if (client.isEmpty()) { clientName.error = "Enter client name"; return }
        if (fee < 0 || paid < 0 || (fee > 0 && paid > fee)) { Toast.makeText(this, "Invalid fee amounts.", Toast.LENGTH_SHORT).show(); return }
        val now = System.currentTimeMillis()
        val createdAt = if (editingId == 0L || editingCreatedAt == 0L) now else editingCreatedAt
        val item = AdvocateCase(editingId, cn, client, clientPhone.text.toString().trim(), court.text.toString().trim(), previousDate.text.toString().trim(), currentDate.text.toString().trim(), nextDate.text.toString().trim(), currentUpdate.text.toString().trim(), newUpdate.text.toString().trim(), fee, paid, createdAt, now)
        if (editingId == 0L) db.insertCase(item) else db.updateCase(item)
        Toast.makeText(this, if (editingId == 0L) "Case saved." else "Case updated.", Toast.LENGTH_SHORT).show()
        clearForm()
        load(search.text.toString())
    }

    private fun fill(item: AdvocateCase) {
        editingId = item.id
        editingCreatedAt = item.createdAt
        caseNumber.setText(item.caseNumber)
        clientName.setText(item.clientName)
        clientPhone.setText(item.clientPhone)
        court.setText(item.courtName)
        previousDate.setText(item.previousDate)
        currentDate.setText(item.currentDate)
        nextDate.setText(item.nextDate)
        currentUpdate.setText(item.currentUpdate)
        newUpdate.setText(item.newUpdate)
        totalFee.setText(if (item.totalFee == 0.0) "" else item.totalFee.toString())
        received.setText(if (item.amountReceived == 0.0) "" else item.amountReceived.toString())
        save.text = "UPDATE CASE"
        updateBalancePreview()
        caseNumber.requestFocus()
    }

    private fun remove(item: AdvocateCase) {
        AlertDialog.Builder(this).setTitle("Delete Case").setMessage("Delete ${item.caseNumber} for ${item.clientName}?")
            .setNegativeButton("CANCEL", null).setPositiveButton("DELETE") { _, _ -> db.deleteCase(item.id); load(search.text.toString()) }.show()
    }

    private fun clearForm() {
        editingId = 0L
        editingCreatedAt = 0L
        listOf(caseNumber, clientName, clientPhone, court, previousDate, currentDate, nextDate, currentUpdate, newUpdate, totalFee, received).forEach { it.text.clear() }
        save.text = "SAVE CASE"
        updateBalancePreview()
    }

    private fun updateBalancePreview() {
        val fee = totalFee.text.toString().toDoubleOrNull() ?: 0.0
        val paid = received.text.toString().toDoubleOrNull() ?: 0.0
        currentBalance.text = "Balance: ${currencyFormat.format((fee - paid).coerceAtLeast(0.0))}"
    }

    private fun load(q: String = "") {
        val items = db.getAllCases(q)
        adapter.submitList(items)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        refreshDashboard()
    }

    private fun refreshDashboard() {
        totalCases.text = db.getTotalCaseCount().toString()
        todayHearings.text = db.getTodayHearingCount().toString()
        upcomingHearings.text = db.getUpcomingHearingCount(30).toString()
        pendingFees.text = currencyFormat.format(db.getPendingFeeTotal())
    }

    private fun sendNextDateMessage() {
        val phone = clientPhone.text.toString().trim()
        val name = clientName.text.toString().trim()
        val date = nextDate.text.toString().trim()
        val caseNo = caseNumber.text.toString().trim()
        if (phone.isEmpty()) { clientPhone.error = "Enter client phone"; return }
        if (date.isEmpty()) { nextDate.error = "Enter next date"; return }
        openSms(phone, "Dear $name, your next hearing for Case No. $caseNo is scheduled on $date. Please be available as required. - KBA Welfare Messenger")
    }

    private fun sendPaymentReminder() {
        val phone = clientPhone.text.toString().trim()
        val name = clientName.text.toString().trim()
        val caseNo = caseNumber.text.toString().trim()
        val fee = totalFee.text.toString().toDoubleOrNull() ?: 0.0
        val paid = received.text.toString().toDoubleOrNull() ?: 0.0
        val balance = (fee - paid).coerceAtLeast(0.0)
        if (phone.isEmpty()) { clientPhone.error = "Enter client phone"; return }
        if (balance <= 0.0) { Toast.makeText(this, "No pending payment for this case.", Toast.LENGTH_SHORT).show(); return }
        openSms(phone, "Dear $name, payment of ${currencyFormat.format(balance)} is pending for Case No. $caseNo. Kindly arrange the pending amount at your convenience. - KBA Welfare Messenger")
    }

    private fun openSms(phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        try {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$cleanPhone")
                putExtra("sms_body", message)
            })
        } catch (_: Exception) { Toast.makeText(this, "No SMS app is available.", Toast.LENGTH_SHORT).show() }
    }

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<AdvocateReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("advocate_hearing_reminders", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { db.close(); super.onDestroy() }
}
