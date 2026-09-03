package com.example.kbawelfaremessenger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdvocateCaseDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    "advocate_helper.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE advocate_cases (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "case_number TEXT NOT NULL," +
                "client_name TEXT NOT NULL," +
                "client_phone TEXT NOT NULL DEFAULT ''," +
                "court_name TEXT NOT NULL DEFAULT ''," +
                "previous_date TEXT NOT NULL DEFAULT ''," +
                "current_date TEXT NOT NULL DEFAULT ''," +
                "next_date TEXT NOT NULL DEFAULT ''," +
                "current_update TEXT NOT NULL DEFAULT ''," +
                "new_update TEXT NOT NULL DEFAULT ''," +
                "total_fee REAL NOT NULL DEFAULT 0," +
                "amount_received REAL NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")"
        )
        db.execSQL("CREATE INDEX idx_advocate_case_number ON advocate_cases(case_number)")
        db.execSQL("CREATE INDEX idx_advocate_next_date ON advocate_cases(next_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getAllCases(search: String = ""): List<AdvocateCase> {
        val result = mutableListOf<AdvocateCase>()
        val db = readableDatabase
        val q = search.trim()
        val selection = if (q.isEmpty()) null else
            "case_number LIKE ? OR client_name LIKE ? OR client_phone LIKE ? OR court_name LIKE ? OR next_date LIKE ?"
        val pattern = "%$q%"
        val args = if (q.isEmpty()) null else arrayOf(pattern, pattern, pattern, pattern, pattern)
        db.query("advocate_cases", null, selection, args, null, null, "updated_at DESC, id DESC").use { c ->
            while (c.moveToNext()) result += readCase(c)
        }
        return result
    }

    fun getTotalCaseCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM advocate_cases", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun getPendingFeeTotal(): Double = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(CASE WHEN total_fee > amount_received THEN total_fee - amount_received ELSE 0 END), 0) FROM advocate_cases",
        null
    ).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

    fun getTodayHearingCount(): Int {
        val today = dateFormat.format(Calendar.getInstance().time)
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM advocate_cases WHERE next_date = ?",
            arrayOf(today)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun getUpcomingHearingCount(days: Int = 30): Int {
        val today = startOfDay(Calendar.getInstance())
        val end = startOfDay(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) })
        return getAllCases().count { item ->
            val date = parseDate(item.nextDate) ?: return@count false
            date >= today && date <= end
        }
    }

    fun getCasesDueForReminder(): List<AdvocateCase> {
        val today = startOfDay(Calendar.getInstance())
        val tomorrow = startOfDay(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) })
        return getAllCases().filter { item ->
            val date = parseDate(item.nextDate) ?: return@filter false
            date == today || date == tomorrow
        }
    }

    fun insertCase(item: AdvocateCase): Long = writableDatabase.insertOrThrow(
        "advocate_cases", null, values(item, false)
    )

    fun updateCase(item: AdvocateCase): Int = writableDatabase.update(
        "advocate_cases",
        values(item.copy(updatedAt = System.currentTimeMillis()), false),
        "id = ?",
        arrayOf(item.id.toString())
    )

    fun deleteCase(id: Long): Int = writableDatabase.delete(
        "advocate_cases", "id = ?", arrayOf(id.toString())
    )

    /**
     * Merge a backup into the current database.
     * Existing case numbers are updated; new case numbers are inserted.
     * Returns Pair(inserted, updated).
     */
    fun mergeCases(items: List<AdvocateCase>): Pair<Int, Int> {
        val db = writableDatabase
        var inserted = 0
        var updated = 0
        db.beginTransaction()
        try {
            items.forEach { item ->
                val existingId = findIdByCaseNumber(db, item.caseNumber)
                if (existingId == null) {
                    db.insertOrThrow("advocate_cases", null, values(item, false))
                    inserted++
                } else {
                    val current = item.copy(id = existingId, updatedAt = System.currentTimeMillis())
                    db.update("advocate_cases", values(current, false), "id = ?", arrayOf(existingId.toString()))
                    updated++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted to updated
    }

    private fun findIdByCaseNumber(db: SQLiteDatabase, caseNumber: String): Long? =
        db.query(
            "advocate_cases", arrayOf("id"), "case_number = ?", arrayOf(caseNumber),
            null, null, null, "1"
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    private fun readCase(c: android.database.Cursor) = AdvocateCase(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        caseNumber = c.getString(c.getColumnIndexOrThrow("case_number")),
        clientName = c.getString(c.getColumnIndexOrThrow("client_name")),
        clientPhone = c.getString(c.getColumnIndexOrThrow("client_phone")),
        courtName = c.getString(c.getColumnIndexOrThrow("court_name")),
        previousDate = c.getString(c.getColumnIndexOrThrow("previous_date")),
        currentDate = c.getString(c.getColumnIndexOrThrow("current_date")),
        nextDate = c.getString(c.getColumnIndexOrThrow("next_date")),
        currentUpdate = c.getString(c.getColumnIndexOrThrow("current_update")),
        newUpdate = c.getString(c.getColumnIndexOrThrow("new_update")),
        totalFee = c.getDouble(c.getColumnIndexOrThrow("total_fee")),
        amountReceived = c.getDouble(c.getColumnIndexOrThrow("amount_received")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
    )

    private fun values(item: AdvocateCase, includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", item.id)
        put("case_number", item.caseNumber)
        put("client_name", item.clientName)
        put("client_phone", item.clientPhone)
        put("court_name", item.courtName)
        put("previous_date", item.previousDate)
        put("current_date", item.currentDate)
        put("next_date", item.nextDate)
        put("current_update", item.currentUpdate)
        put("new_update", item.newUpdate)
        put("total_fee", item.totalFee)
        put("amount_received", item.amountReceived)
        put("created_at", item.createdAt)
        put("updated_at", item.updatedAt)
    }

    private fun parseDate(value: String): Long? = try {
        dateFormat.parse(value)?.let { startOfDay(it.time) }
    } catch (_: Exception) { null }

    private fun startOfDay(calendar: Calendar): Long = startOfDay(calendar.timeInMillis)

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply { isLenient = false }
    }
}
