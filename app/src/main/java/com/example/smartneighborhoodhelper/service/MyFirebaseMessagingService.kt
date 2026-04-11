package com.example.smartneighborhoodhelper.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.FcmTokenRepository
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * Receives FCM messages and displays a real phone notification.
 * Also stores/refreshes device tokens in Firestore for per-user targeting.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d("FCM_TOKEN", "onNewToken(): ${token.take(12)}... len=${token.length}")

        // Token refresh can happen anytime. We can only map it to a user if the user is logged in.
        val uid = SessionManager(applicationContext).getUserId() ?: run {
            Log.d("FCM_TOKEN", "No session uid yet; token not stored to Firestore")
            return
        }
        FcmTokenRepository().upsertToken(uid, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(
            "FCM_PUSH",
            "onMessageReceived(): from=${message.from} dataKeys=${message.data.keys} hasNotification=${message.notification != null}"
        )

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Update"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: "You have a new update."

        val data = message.data
        val complaintId = data["complaintId"].orEmpty()
        val target = data["target"].orEmpty() // e.g. "complaint_detail" (sent by backend)

        showNotification(title, body, complaintId, target)
    }

    private fun showNotification(title: String, body: String, complaintId: String, target: String) {
        if (!canPostNotifications()) return
        ensureChannel()

        // Safe routing:
        // - Only deep-link to details if backend explicitly says so AND we have an id.
        // - Otherwise open dashboard (MainActivity).
        val openComplaintDetail = target.equals("complaint_detail", ignoreCase = true) && complaintId.isNotBlank()

        val intent = if (openComplaintDetail) {
            Intent(this, ComplaintDetailActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Constants.EXTRA_COMPLAINT_ID, complaintId)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            // Keep requestCode stable if it's a complaint detail, otherwise randomize to avoid intent re-use issues.
            if (openComplaintDetail) complaintId.hashCode() else (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (openComplaintDetail) complaintId.hashCode() else {
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        }
        nm.notify(notificationId, n)
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Complaint updates and community alerts"
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        // IMPORTANT: must match backend FCM payload android.notification.channelId
        // and the channel created by NotificationHelper.
        const val CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID
        const val CHANNEL_NAME = Constants.NOTIFICATION_CHANNEL_NAME
    }
}
