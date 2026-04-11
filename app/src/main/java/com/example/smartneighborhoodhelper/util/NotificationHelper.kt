package com.example.smartneighborhoodhelper.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * NotificationHelper — Creates notification channels and shows REAL phone notifications.
 *
 * WHAT HAPPENS:
 *   When a complaint status changes (Pending → In Progress → Resolved) or
 *   a provider is assigned, a notification pops up on the phone's notification bar
 *   just like WhatsApp, Instagram, etc.
 *
 * HOW:
 *   1. createChannel() — creates a HIGH importance channel (sound + vibration + heads-up)
 *   2. showComplaintUpdate() — builds and shows a notification
 *   3. Tapping the notification opens ComplaintDetailActivity
 *
 * IMPORTANCE_HIGH = heads-up popup + sound (like a message notification)
 * IMPORTANCE_DEFAULT = silent, just shows in the tray
 */
object NotificationHelper {

    /**
     * Create the notification channel (required for Android 8+).
     * IMPORTANCE_HIGH makes it pop up on screen with sound — like real apps.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH    // ← Heads-up + sound
            ).apply {
                description = "Get notified when your complaint status changes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)  // vibrate pattern
                setShowBadge(true)  // show badge on app icon
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Show a real phone notification for a complaint event.
     *
     * This notification:
     *   - Appears in the phone's notification bar (even when app is closed)
     *   - Makes a sound + vibrates
     *   - Shows a heads-up popup
     *   - Tapping it opens ComplaintDetailActivity
     *   - Auto-dismisses when tapped
     */
    fun showComplaintUpdate(
        context: Context,
        complaintId: String,
        title: String,
        body: String
    ) {
        if (!canPostNotifications(context)) return
        // Intent to open complaint detail when notification is tapped
        val intent = Intent(context, ComplaintDetailActivity::class.java).apply {
            putExtra(Constants.EXTRA_COMPLAINT_ID, complaintId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, complaintId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Default notification sound (like SMS tone)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)     // ← Heads-up popup
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)               // dismiss when tapped
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)  // LED light
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(complaintId.hashCode(), notification)
    }

    /**
     * Show a generic high-priority notification.
     * Used for events that are NOT tied to a complaintId (e.g., Join Request Approved).
     */
    fun showGenericUpdate(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        intent: Intent
    ) {
        if (!canPostNotifications(context)) return
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, notification)
    }

    /**
     * Build a smart notification message based on the complaint event type.
     * Returns Pair(title, body) for the notification.
     *
     * TRIGGER POINTS & MESSAGES:
     *
     * FOR ADMIN:
     *   1. New complaint reported by resident     → "New Complaint Reported 📋"
     *   2. Resident reopened a resolved complaint  → "Complaint Reopened 🔁"
     *   3. Resident confirmed resolution           → "Resolution Confirmed 👍"
     *
     * FOR RESIDENT:
     *   1. Admin marked complaint In Progress      → "Complaint In Progress 🔄"
     *   2. Admin assigned a provider               → "Provider Assigned 🔧"
     *   3. Admin marked complaint Resolved         → "Complaint Resolved ✅"
     *   4. Admin removed provider / changed status → "Complaint Updated"
     */
    fun buildSmartMessage(
        category: String,
        newStatus: String,
        hasProvider: Boolean,
        role: String
    ): Pair<String, String> {
        return when {

            // ═══════════════════════════════════════════
            // ADMIN NOTIFICATIONS (events from residents)
            // ═══════════════════════════════════════════

            // 1. A resident just submitted a new complaint
            role == Constants.ROLE_ADMIN && newStatus == Constants.STATUS_PENDING && !hasProvider -> {
                "New Complaint Reported 📋" to
                    "A resident reported a '$category' issue in your community. Tap to view and assign a provider."
            }

            // 2. A resident rejected the resolution → complaint reopened back to Pending
            role == Constants.ROLE_ADMIN && newStatus == Constants.STATUS_PENDING && hasProvider -> {
                "Complaint Reopened 🔁" to
                    "A resident says the '$category' issue is NOT fixed. The complaint has been reopened."
            }

            // 3. A resident confirmed the resolution
            role == Constants.ROLE_ADMIN && newStatus == Constants.STATUS_RESOLVED -> {
                "Resolution Confirmed 👍" to
                    "A resident confirmed that the '$category' issue has been resolved. Great work!"
            }

            // Admin: any other update on community complaints
            role == Constants.ROLE_ADMIN -> {
                "Community Update" to
                    "A '$category' complaint in your community was updated. Status: $newStatus"
            }

            // ═══════════════════════════════════════════
            // RESIDENT NOTIFICATIONS (events from admin)
            // ═══════════════════════════════════════════

            // 4. Admin assigned a provider + status moved to In Progress
            newStatus == Constants.STATUS_IN_PROGRESS && hasProvider -> {
                "Provider Assigned 🔧" to
                    "A service provider has been assigned to your '$category' issue and work is in progress."
            }

            // 5. Admin marked In Progress (without provider yet)
            newStatus == Constants.STATUS_IN_PROGRESS -> {
                "Complaint In Progress 🔄" to
                    "Your '$category' issue is now being looked at. A provider may be assigned soon."
            }

            // 6. Admin marked complaint as Resolved
            newStatus == Constants.STATUS_RESOLVED -> {
                "Complaint Resolved ✅" to
                    "Your '$category' issue has been marked as resolved. Please open the app to confirm if it's fixed."
            }

            // 7. Complaint went back to Pending (admin reset or provider removed)
            newStatus == Constants.STATUS_PENDING -> {
                "Complaint Status Updated" to
                    "Your '$category' complaint status has been changed to Pending."
            }

            // Generic fallback for any other status change
            else -> {
                "Complaint Updated" to
                    "Your '$category' complaint status changed to: $newStatus"
            }
        }
    }
}
