package com.example.smartneighborhoodhelper.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity

/**
 * NotificationHelper — Creates notification channels and builds notifications.
 *
 * WHAT IS A NotificationChannel?
 *   Starting from Android 8.0 (API 26), all notifications must belong to a channel.
 *   Users can control notification settings per-channel in system settings.
 *
 * HOW IT WORKS:
 *   1. createChannel() — called once at app start (safe to call multiple times)
 *   2. showComplaintUpdate() — shows a notification when a complaint status changes
 *   3. Tapping the notification opens ComplaintDetailActivity with the complaint ID
 */
object NotificationHelper {

    /**
     * Create the notification channel (required for Android 8+).
     * Safe to call multiple times — Android ignores if channel already exists.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for complaint status updates"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Show a notification for a complaint status change.
     *
     * @param context Application context
     * @param complaintId Firestore document ID of the complaint
     * @param title Notification title (e.g. "Complaint Updated")
     * @param body Notification body (e.g. "Your 'Road' complaint is now In Progress")
     */
    fun showComplaintUpdate(
        context: Context,
        complaintId: String,
        title: String,
        body: String
    ) {
        // Intent to open complaint detail when notification is tapped
        val intent = Intent(context, ComplaintDetailActivity::class.java).apply {
            putExtra(Constants.EXTRA_COMPLAINT_ID, complaintId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, complaintId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(complaintId.hashCode(), notification)
    }
}
