package com.example.kbawelfaremessenger

/**
 * Lightweight case record used by Advocate Helper.
 *
 * The module intentionally stores the current case position rather than
 * maintaining a complete case-history timeline.
 */
data class AdvocateCase(
    val id: Long = 0L,
    val caseNumber: String,
    val clientName: String,
    val clientPhone: String,
    val courtName: String,
    val previousDate: String,
    val currentDate: String,
    val nextDate: String,
    val currentUpdate: String,
    val newUpdate: String,
    val totalFee: Double,
    val amountReceived: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val balance: Double
        get() = (totalFee - amountReceived).coerceAtLeast(0.0)
}
