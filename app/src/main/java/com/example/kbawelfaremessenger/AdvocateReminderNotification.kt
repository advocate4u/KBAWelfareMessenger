package com.example.kbawelfaremessenger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object AdvocateReminderNotification {
    private const val CHANNEL_ID = "advocate_hearing_reminders"
    private const val CHANNEL_NAME = "Hearing Reminders"
    private const val NOTIFICATION_ID = 42001

    fun show(context: Context, count: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Advocate Helper")
            .setContentText("You have $count hearing reminder${if (count == 1) "" else "s"}.")
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
