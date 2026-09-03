package com.example.kbawelfaremessenger

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AdvocateReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val db = AdvocateCaseDbHelper(applicationContext)
        return try {
            val cases = db.getCasesDueForReminder()
            if (cases.isNotEmpty()) {
                AdvocateReminderNotification.show(
                    applicationContext,
                    cases.size
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }
}
