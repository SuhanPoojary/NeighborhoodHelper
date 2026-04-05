package com.example.smartneighborhoodhelper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smartneighborhoodhelper.ui.community.PendingApprovalActivity
import com.example.smartneighborhoodhelper.util.NotificationHelper

/**
 * NotificationReceiver — BroadcastReceiver that shows real phone notifications.
 *
 * WHAT IS A BroadcastReceiver?
 *   A component that listens for broadcast messages (Intents) sent by
 *   other parts of the app or the Android system itself.
 *   Think of it like a radio receiver — it's always listening for a specific signal.
 *
 * HOW IT WORKS IN THIS APP:
 *   1. ComplaintStatusService polls Firestore every 60 seconds
 *   2. When it finds a status change, it sends a LOCAL BROADCAST with complaint details
 *   3. This receiver catches that broadcast
 *   4. It calls NotificationHelper to show a REAL phone notification
 *      (appears in notification bar, lock screen, with sound + vibration)
 *
 * WHY USE A RECEIVER INSTEAD OF DIRECT NOTIFICATION?
 *   - Demonstrates the BroadcastReceiver pattern (syllabus requirement)
 *   - Separates "detecting changes" (Service) from "showing notification" (Receiver)
 *   - The receiver can be registered/unregistered dynamically
 *
 * SYLLABUS CONCEPTS: BroadcastReceiver, onReceive(), Intent extras
 */
class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifReceiver"

        /** Action string that the Service uses to send broadcasts to this receiver */
        const val ACTION_COMPLAINT_UPDATED = "com.example.smartneighborhoodhelper.COMPLAINT_UPDATED"

        /** ✅ Action for join-request status changes (Resident waiting approval) */
        const val ACTION_JOIN_REQUEST_UPDATED = "com.example.smartneighborhoodhelper.JOIN_REQUEST_UPDATED"

        /** Intent extra keys */
        const val EXTRA_COMPLAINT_ID = "notif_complaint_id"
        const val EXTRA_TITLE = "notif_title"
        const val EXTRA_BODY = "notif_body"

        /** For join request status */
        const val EXTRA_JOIN_STATUS = "notif_join_status"
    }

    /**
     * Called when a broadcast matching our IntentFilter is received.
     *
     * @param context The Context in which the receiver is running
     * @param intent  The Intent with complaint data (ID, title, body)
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            ACTION_COMPLAINT_UPDATED -> {
                val complaintId = intent.getStringExtra(EXTRA_COMPLAINT_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Complaint Updated"
                val body = intent.getStringExtra(EXTRA_BODY) ?: "A complaint status has changed."

                Log.d(TAG, "Received complaint broadcast — showing notification for: $complaintId")

                // Show a real phone notification (notification bar, sound, vibration)
                NotificationHelper.showComplaintUpdate(
                    context = context,
                    complaintId = complaintId,
                    title = title,
                    body = body
                )
            }

            ACTION_JOIN_REQUEST_UPDATED -> {
                val status = intent.getStringExtra(EXTRA_JOIN_STATUS).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Join Request Update"
                val body = intent.getStringExtra(EXTRA_BODY) ?: "Your join request status changed: $status"

                Log.d(TAG, "Received join-request broadcast — showing notification: $status")

                // On tap, open PendingApprovalActivity so user can see status + redirect.
                val openIntent = Intent(context, PendingApprovalActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

                NotificationHelper.showGenericUpdate(
                    context = context,
                    notificationId = ("join_request_" + status).hashCode(),
                    title = title,
                    body = body,
                    intent = openIntent
                )
            }
        }
    }
}
