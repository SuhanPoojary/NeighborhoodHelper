package com.example.smartneighborhoodhelper.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.util.Constants
import com.example.smartneighborhoodhelper.util.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * ComplaintStatusService — A Started Service that polls Firestore for complaint updates.
 *
 * WHAT IS A STARTED SERVICE?
 *   A Service runs in the background without a UI. A "Started Service" is
 *   one that is launched via startService(Intent) and keeps running until
 *   it decides to stop itself or is stopped by the system.
 *
 * HOW IT WORKS:
 *   1. Started from MainActivity after login via startService()
 *   2. Every 60 seconds, queries Firestore for complaints that were updated
 *      since the last check
 *   3. If a complaint's status changed, shows a local notification
 *   4. START_STICKY — system restarts the service if it's killed
 *
 * SYLLABUS CONCEPTS: Started Service, Background processing, Notifications
 */
class ComplaintStatusService : Service() {

    companion object {
        private const val TAG = "StatusService"
        private const val POLL_INTERVAL_MS = 60_000L  // 60 seconds
        private const val PREF_LAST_CHECK = "last_status_check"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    /**
     * Called when the service is started via startService().
     * START_STICKY tells the system to restart this service if it's killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

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
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Query Firestore for complaints updated since last check.
     * If status changed, show a notification.
     */
    private suspend fun checkForUpdates() {
        val session = SessionManager(this)
        val userId = session.getUserId() ?: return
        val role = session.getUserRole() ?: return
        val communityId = session.getCommunityId() ?: return

        // Get last check timestamp from SharedPreferences
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, System.currentTimeMillis())
        val now = System.currentTimeMillis()

        val db = FirebaseFirestore.getInstance()
        val complaintsRef = db.collection(Constants.COLLECTION_COMPLAINTS)

        // Query: get complaints updated since last check
        val query = if (role == Constants.ROLE_RESIDENT) {
            // Resident only sees their own complaints
            complaintsRef
                .whereEqualTo("reportedBy", userId)
                .whereGreaterThan("updatedAt", lastCheck)
        } else {
            // Admin sees all community complaints
            complaintsRef
                .whereEqualTo("communityId", communityId)
                .whereGreaterThan("updatedAt", lastCheck)
        }

        try {
            val snapshot = query.get().await()
            for (doc in snapshot.documents) {
                val category = doc.getString("category") ?: "Issue"
                val status = doc.getString("status") ?: "Updated"
                val complaintId = doc.id

                NotificationHelper.showComplaintUpdate(
                    context = this@ComplaintStatusService,
                    complaintId = complaintId,
                    title = "Complaint Updated",
                    body = "Your '$category' complaint is now: $status"
                )
            }

            // Update last check timestamp
            prefs.edit().putLong(PREF_LAST_CHECK, now).apply()

        } catch (e: Exception) {
            // FAILED_PRECONDITION for composite index is expected
            // The notification will still work when user opens the app
            Log.w(TAG, "Firestore query failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
