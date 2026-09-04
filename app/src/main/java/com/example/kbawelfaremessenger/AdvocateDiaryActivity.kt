package com.example.kbawelfaremessenger

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AdvocateDiaryActivity : AppCompatActivity() {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val prefsName = "advocate_diary"
    private val keyEntries = "entries"
    private lateinit var date: EditText
    private lateinit var title: EditText
    private lateinit var court: EditText
    private lateinit var caseNumber: EditText
    private lateinit var client: EditText
    private lateinit var notes: EditText
    private lateinit var list: LinearLayout
    private var editingId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!LicenseManager.isFeatureEnabled(this, "advocate_diary")) {
            Toast.makeText(this, "Advocate Diary is not enabled in this license.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_advocate_diary)
        supportActionBar?.title = "Advocate Diary"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        date = findViewById(R.id.diaryDate); title = findViewById(R.id.diaryTitle)
        court = findViewById(R.id.diaryCourt); caseNumber = findViewById(R.id.diaryCaseNumber)
        client = findViewById(R.id.diaryClient); notes = findViewById(R.id.diaryNotes)
        list = findViewById(R.id.diaryList)
        date.setText(LocalDate.now().format(fmt))
        date.setOnClickListener { pickDate() }
        findViewById<Button>(R.id.diarySave).setOnClickListener { saveEntry() }
        findViewById<Button>(R.id.diaryClear).setOnClickListener { clearForm() }
        renderEntries()
    }

    private fun pickDate() {
        val d = runCatching { LocalDate.parse(date.text.toString(), fmt) }.getOrDefault(LocalDate.now())
        DatePickerDialog(this, { _, y, m, day -> date.setText(LocalDate.of(y, m + 1, day).format(fmt)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    private fun saveEntry() {
        val t = title.text.toString().trim()
        if (t.isBlank()) { title.error = "Enter diary title"; title.requestFocus(); return }
        val d = runCatching { LocalDate.parse(date.text.toString(), fmt) }.getOrNull()
        if (d == null) { date.error = "Use yyyy-MM-dd"; return }
        val entries = readEntries()
        val obj = if (editingId != null) find(entries, editingId!!) ?: JSONObject() else JSONObject()
        obj.put("id", editingId ?: System.currentTimeMillis())
            .put("date", d.format(fmt)).put("title", t).put("court", court.text.toString().trim())
            .put("caseNumber", caseNumber.text.toString().trim()).put("client", client.text.toString().trim())
            .put("notes", notes.text.toString().trim())
        if (editingId == null) entries.put(obj)
        saveEntries(entries)
        AppLogger.success(this, "DIARY", if (editingId == null) "Diary entry created" else "Diary entry updated")
        Toast.makeText(this, if (editingId == null) "Diary entry saved" else "Diary entry updated", Toast.LENGTH_SHORT).show()
        clearForm(); renderEntries()
    }

    private fun renderEntries() {
        list.removeAllViews()
        val entries = readEntries()
        val rows = mutableListOf<JSONObject>()
        for (i in 0 until entries.length()) entries.optJSONObject(i)?.let(rows::add)
        rows.sortByDescending { it.optString("date") + it.optLong("id").toString().padStart(16, '0') }
        if (rows.isEmpty()) {
            list.addView(TextView(this).apply { text = "No diary entries yet."; setPadding(8, 16, 8, 16) })
            return
        }
        rows.forEach { e ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12) }
            val head = TextView(this).apply { text = "${e.optString("date")}  •  ${e.optString("title")}"; textSize = 17f; setTypeface(null, 1) }
            val detail = TextView(this).apply {
                text = listOf(e.optString("caseNumber").takeIf { it.isNotBlank() }?.let { "Case: $it" }, e.optString("court").takeIf { it.isNotBlank() }?.let { "Court: $it" }, e.optString("client").takeIf { it.isNotBlank() }?.let { "Client: $it" }, e.optString("notes").takeIf { it.isNotBlank() }?.let { "Notes: $it" }).filterNotNull().joinToString("\n")
                setPadding(0, 6, 0, 6)
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val edit = Button(this).apply { text = "EDIT"; setOnClickListener { load(e) } }
            val del = Button(this).apply { text = "DELETE"; setOnClickListener { delete(e.optLong("id")) } }
            actions.addView(edit, LinearLayout.LayoutParams(0, -2, 1f)); actions.addView(del, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(head); card.addView(detail); card.addView(actions)
            list.addView(card)
            list.addView(View(this).apply { minimumHeight = 8 })
        }
    }

    private fun load(e: JSONObject) {
        editingId = e.optLong("id")
        date.setText(e.optString("date")); title.setText(e.optString("title")); court.setText(e.optString("court"))
        caseNumber.setText(e.optString("caseNumber")); client.setText(e.optString("client")); notes.setText(e.optString("notes"))
        findViewById<Button>(R.id.diarySave).text = "UPDATE ENTRY"
        findViewById<Button>(R.id.diaryClear).text = "CANCEL EDIT"
        findViewById<ScrollView>(R.id.diaryScroll).post { findViewById<ScrollView>(R.id.diaryScroll).smoothScrollTo(0, 0) }
    }

    private fun delete(id: Long) {
        AlertDialog.Builder(this).setTitle("Delete diary entry?").setMessage("This entry will be permanently removed from this device.")
            .setNegativeButton("CANCEL", null).setPositiveButton("DELETE") { _, _ ->
                val entries = readEntries(); for (i in entries.length() - 1 downTo 0) if (entries.optJSONObject(i)?.optLong("id") == id) entries.remove(i)
                saveEntries(entries); AppLogger.info(this, "DIARY", "Diary entry deleted"); renderEntries()
            }.show()
    }

    private fun clearForm() {
        editingId = null; date.setText(LocalDate.now().format(fmt)); title.text.clear(); court.text.clear(); caseNumber.text.clear(); client.text.clear(); notes.text.clear()
        findViewById<Button>(R.id.diarySave).text = "SAVE ENTRY"; findViewById<Button>(R.id.diaryClear).text = "CLEAR"
    }

    private fun readEntries(): JSONArray = runCatching { JSONArray(getSharedPreferences(prefsName, 0).getString(keyEntries, "[]")) }.getOrDefault(JSONArray())
    private fun saveEntries(a: JSONArray) { getSharedPreferences(prefsName, 0).edit().putString(keyEntries, a.toString()).apply() }
    private fun find(a: JSONArray, id: Long): JSONObject? { for (i in 0 until a.length()) if (a.optJSONObject(i)?.optLong("id") == id) return a.optJSONObject(i); return null }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
