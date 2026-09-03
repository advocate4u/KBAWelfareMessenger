package com.example.kbawelfaremessenger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private var editingId = 0L
    private var editingCreatedAt = 0L
    private val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

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
        save.setOnClickListener { saveCase() }

        adapter = AdvocateCaseAdapter(emptyList(), { fill(it) }, { remove(it) })
        findViewById<RecyclerView>(R.id.recyclerCases).apply {
            layoutManager = LinearLayoutManager(this@AdvocateHelperActivity)
            adapter = this@AdvocateHelperActivity.adapter
            isNestedScrollingEnabled = false
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { load(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        load()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) refreshDashboard()
    }

    private fun pick(target: EditText) {
        val c = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                c.set(y, m, d)
                target.setText(format.format(c.time))
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveCase() {
        val cn = caseNumber.text.toString().trim()
        val client = clientName.text.toString().trim()
        val fee = totalFee.text.toString().trim().toDoubleOrNull() ?: 0.0
        val paid = received.text.toString().trim().toDoubleOrNull() ?: 0.0

        if (cn.isEmpty()) { caseNumber.error = "Enter case number"; return }
        if (client.isEmpty()) { clientName.error = "Enter client name"; return }
        if (fee < 0 || paid < 0 || (fee > 0 && paid > fee)) {
            Toast.makeText(this, "Invalid fee amounts.", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val createdAt = if (editingId == 0L || editingCreatedAt == 0L) now else editingCreatedAt
        val item = AdvocateCase(
            id = editingId,
            caseNumber = cn,
            clientName = client,
            clientPhone = clientPhone.text.toString().trim(),
            courtName = court.text.toString().trim(),
            previousDate = previousDate.text.toString().trim(),
            currentDate = currentDate.text.toString().trim(),
            nextDate = nextDate.text.toString().trim(),
            currentUpdate = currentUpdate.text.toString().trim(),
            newUpdate = newUpdate.text.toString().trim(),
            totalFee = fee,
            amountReceived = paid,
            createdAt = createdAt,
            updatedAt = now
        )

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
        caseNumber.requestFocus()
    }

    private fun remove(item: AdvocateCase) {
        AlertDialog.Builder(this)
            .setTitle("Delete Case")
            .setMessage("Delete ${item.caseNumber} for ${item.clientName}?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                db.deleteCase(item.id)
                load(search.text.toString())
            }
            .show()
    }

    private fun clearForm() {
        editingId = 0L
        editingCreatedAt = 0L
        listOf(caseNumber, clientName, clientPhone, court, previousDate, currentDate, nextDate, currentUpdate, newUpdate, totalFee, received)
            .forEach { it.text.clear() }
        save.text = "SAVE CASE"
    }

    private fun load(q: String = "") {
        val items = db.getAllCases(q)
        adapter.submitList(items)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val all = db.getAllCases()
        val today = startOfDay(Calendar.getInstance()).timeInMillis
        val tomorrow = startOfDay(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }).timeInMillis

        val todayCount = all.count { parseDate(it.nextDate) == today }
        val upcomingCount = all.count { parseDate(it.nextDate)?.let { date -> date >= tomorrow } == true }

        totalCases.text = db.getTotalCaseCount().toString()
        todayHearings.text = todayCount.toString()
        upcomingHearings.text = upcomingCount.toString()
        pendingFees.text = currencyFormat.format(db.getPendingFeeTotal())
    }

    private fun parseDate(value: String): Long? = try {
        val parts = value.split("-")
        if (parts.size != 3) return null
        Calendar.getInstance().apply {
            set(Calendar.YEAR, parts[2].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, parts[0].toInt())
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) {
        null
    }

    private fun startOfDay(calendar: Calendar): Calendar = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
