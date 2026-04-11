package com.example.smartneighborhoodhelper.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.JoinRequest
import com.example.smartneighborhoodhelper.receiver.NotificationReceiver
import com.example.smartneighborhoodhelper.util.Constants
import com.example.smartneighborhoodhelper.util.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * ComplaintStatusService — A Started Service that polls Firestore for complaint updates
 * and sends REAL phone notifications (notification bar, sound, vibration).
 *
 * FLOW:
 *   1. Started from MainActivity after login via startService()
 *   2. Every 60 seconds, queries Firestore for complaints updated since last check
 *   3. For each updated complaint, sends a BROADCAST to NotificationReceiver
 *   4. NotificationReceiver catches the broadcast and shows a phone notification
 *
 * WHY Service → BroadcastReceiver → Notification?
 *   - Demonstrates Started Service (syllabus requirement)
 *   - Demonstrates BroadcastReceiver (syllabus requirement)
 *   - Demonstrates Notifications (syllabus requirement)
 *   - Clean separation: Service detects changes, Receiver shows notifications
 *
 * SYLLABUS CONCEPTS: Started Service, START_STICKY, BroadcastReceiver, Notifications
 */
class ComplaintStatusService : Service() {

    companion object {
        private const val TAG = "StatusService"

        // Default polling interval (safe for normal usage)
        private const val POLL_INTERVAL_DEFAULT_MS = 60_000L  // 60 seconds

        // Demo mode polling interval (fast for viva/demo)
        private const val POLL_INTERVAL_DEMO_MS = 10_000L     // 10 seconds

        private const val PREF_LAST_COMPLAINT_CHECK = "last_status_check"

        // ✅ Join request polling keys
        private const val PREF_LAST_JOIN_REQUEST_CHECK = "last_join_request_check"
        private const val PREF_LAST_JOIN_STATUS = "last_join_status"

        // ✅ Toggle: set true to speed up polling (SharedPreferences)
        private const val PREF_DEMO_MODE_FAST_POLL = "demo_fast_poll"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    /**
     * Called when the service is started via startService().
     * START_STICKY tells the system to restart this service if it's killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pollMs = getPollIntervalMs()
        Log.d(TAG, "Service started — will poll Firestore every ${pollMs / 1000}s")

        // Create notification channel (safe to call multiple times)
        NotificationHelper.createChannel(this)

        // Start polling if not already running
        if (pollingJob == null || pollingJob?.isActive != true) {
            startPolling()
        }

        return START_STICKY
    }

    /**
     * Not a bound service — return null.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Reads the polling interval from SharedPreferences.
     * If demo mode is enabled, we poll faster so notifications feel real-time in demos.
     */
    private fun getPollIntervalMs(): Long {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val demoFast = prefs.getBoolean(PREF_DEMO_MODE_FAST_POLL, true)
        return if (demoFast) POLL_INTERVAL_DEMO_MS else POLL_INTERVAL_DEFAULT_MS
    }

    /**
     * Poll Firestore every 60 seconds for complaint status changes.
     */
    private fun startPolling() {
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkForUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                }

                // Compute delay each loop (so you can change demo flag without restarting app)
                delay(getPollIntervalMs())
            }
        }
    }

    /**
     * Poll both:
     *  1) Complaints updated since last check
     *  2) Join request status updates for resident (Pending→Approved/Declined)
     */
    private suspend fun checkForUpdates() {
        val session = SessionManager(this)
        val userId = session.getUserId() ?: return
        val role = session.getUserRole() ?: return
        val communityId = session.getCommunityId() ?: return

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val db = FirebaseFirestore.getInstance()

        // ─────────────────────────────────────────────
        // 1) Complaints polling (existing)
        // ─────────────────────────────────────────────
        val lastComplaintCheck = prefs.getLong(PREF_LAST_COMPLAINT_CHECK, 0L)
        val complaintsRef = db.collection(Constants.COLLECTION_COMPLAINTS)

        val complaintQuery = if (role == Constants.ROLE_RESIDENT) {
            complaintsRef
                .whereEqualTo("reportedBy", userId)
                .whereGreaterThan("updatedAt", lastComplaintCheck)
        } else {
            complaintsRef
                .whereEqualTo("communityId", communityId)
                .whereGreaterThan("updatedAt", lastComplaintCheck)
        }

        try {
            val snapshot = complaintQuery.get().await()

            for (doc in snapshot.documents) {
                val category = doc.getString("category") ?: "Issue"
                val status = doc.getString("status") ?: "Updated"
                val complaintId = doc.id
                val hasProvider = !doc.getString("assignedProvider").isNullOrBlank()

                // Build smart notification message
                val (title, body) = NotificationHelper.buildSmartMessage(
                    category = category,
                    newStatus = status,
                    hasProvider = hasProvider,
                    role = role
                )

                // Send broadcast to NotificationReceiver → it will show the phone notification
                val broadcastIntent = Intent(NotificationReceiver.ACTION_COMPLAINT_UPDATED).apply {
                    setPackage(packageName)  // restrict to our app only
                    putExtra(NotificationReceiver.EXTRA_COMPLAINT_ID, complaintId)
                    putExtra(NotificationReceiver.EXTRA_TITLE, title)
                    putExtra(NotificationReceiver.EXTRA_BODY, body)
                }
                sendBroadcast(broadcastIntent)

                Log.d(TAG, "Broadcast sent for complaint: $complaintId → $title")
            }

            // Update last check timestamp
            prefs.edit().putLong(PREF_LAST_COMPLAINT_CHECK, now).apply()

        } catch (e: Exception) {
            Log.w(TAG, "Complaint query failed: ${e.message}")
        }

        // ─────────────────────────────────────────────
        // 2) Join request polling (Resident only)
        // ─────────────────────────────────────────────
        if (role == Constants.ROLE_RESIDENT) {
            val lastJoinCheck = prefs.getLong(PREF_LAST_JOIN_REQUEST_CHECK, 0L)
            val lastKnownStatus = prefs.getString(PREF_LAST_JOIN_STATUS, "") ?: ""

            // Only make this query if resident hasn't joined a community yet.
            // Once communityId is set, PendingApprovalActivity won't be used.
            if (communityId.isBlank()) {
                try {
                    val joinSnap = db.collection(Constants.COLLECTION_JOIN_REQUESTS)
                        .whereEqualTo("residentUid", userId)
                        .whereGreaterThan("updatedAt", lastJoinCheck)
                        .limit(10)
                        .get()
                        .await()

                    // If there are multiple, take the most recently updated
                    val latest = joinSnap.documents
                        .maxByOrNull { it.getLong("updatedAt") ?: 0L }

                    if (latest != null) {
                        val status = latest.getString("status").orEmpty()
                        val communityName = latest.getString("communityName").orEmpty().ifBlank { "Community" }

                        // Send only if status actually changed to avoid spamming
                        if (status.isNotBlank() && status != lastKnownStatus && status != JoinRequest.STATUS_PENDING) {
                            val (title, body) = when (status) {
                                JoinRequest.STATUS_APPROVED ->
                                    "Join request approved ✅" to "You’ve been approved to join $communityName. Tap to open the app."
                                JoinRequest.STATUS_DECLINED ->
                                    "Join request declined ❌" to "Your request to join $communityName was declined. You can try another community."
                                else ->
                                    "Join request updated" to "Status changed to: $status"
                            }

                            val broadcast = Intent(NotificationReceiver.ACTION_JOIN_REQUEST_UPDATED).apply {
                                setPackage(packageName)
                                putExtra(NotificationReceiver.EXTRA_JOIN_STATUS, status)
                                putExtra(NotificationReceiver.EXTRA_TITLE, title)
                                putExtra(NotificationReceiver.EXTRA_BODY, body)
                            }
                            sendBroadcast(broadcast)
                        }

                        // Advance last join check to latest updatedAt
                        val latestUpdatedAt = latest.getLong("updatedAt") ?: now
                        prefs.edit()
                            .putLong(PREF_LAST_JOIN_REQUEST_CHECK, latestUpdatedAt)
                            .putString(PREF_LAST_JOIN_STATUS, status)
                            .apply()
                    } else {
                        // no docs, still bump timestamp a little to avoid re-check spikes
                        prefs.edit().putLong(PREF_LAST_JOIN_REQUEST_CHECK, now).apply()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "JoinRequest query failed: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
