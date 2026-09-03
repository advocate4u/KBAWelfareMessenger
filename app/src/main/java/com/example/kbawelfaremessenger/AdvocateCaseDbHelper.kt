package com.example.kbawelfaremessenger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
            while (c.moveToNext()) {
                result += AdvocateCase(
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
            }
        }
        return result
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
}
