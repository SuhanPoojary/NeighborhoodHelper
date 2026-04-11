package com.example.smartneighborhoodhelper.data.remote.repository

import android.util.Log
import com.example.smartneighborhoodhelper.data.model.NotificationItem
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * NotificationRepository — Firestore operations for in-app notifications.
 */
class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val notificationsRef = db.collection(Constants.COLLECTION_NOTIFICATIONS)

    private companion object {
        private const val TAG = "NotifRepo"
    }

    /**
     * Create a notification document.
     *
     * IMPORTANT:
     * We avoid the add()+update("id") pattern because it requires an UPDATE permission
     * immediately after create. Our rules allow create but only the owner can update.
     * That makes admin-created notifications fail (admin creates for resident/admin, but can't update).
     *
     * So we always create with a known docId and write everything in ONE set().
     */
    suspend fun createNotification(item: NotificationItem): String {
        val createdAt = if (item.createdAt > 0) item.createdAt else System.currentTimeMillis()
        val docId = item.id.ifBlank { notificationsRef.document().id }

        val finalItem = item.copy(
            id = docId,
            createdAt = createdAt
        )

        Log.d(TAG, "createNotification(): docId=$docId userId=${finalItem.userId} communityId=${finalItem.communityId} type=${finalItem.type}")

        try {
            notificationsRef.document(docId).set(finalItem).await()
            Log.d(TAG, "createNotification(): SUCCESS docId=$docId")
            return docId
        } catch (e: Exception) {
            Log.e(TAG, "createNotification(): FAILED docId=$docId msg=${e.message}", e)
            throw e
        }
    }

    suspend fun getNotificationsForUser(userId: String, limit: Long = 50): List<NotificationItem> {
        Log.d(TAG, "getNotificationsForUser(): userId=$userId limit=$limit")
        val snap = notificationsRef
            .whereEqualTo("userId", userId)
            .limit(limit)
            .get()
            .await()

        val list = snap.documents
            .mapNotNull { it.toObject(NotificationItem::class.java) }
            .sortedByDescending { it.createdAt }

        Log.d(TAG, "getNotificationsForUser(): found=${list.size}")
        return list
    }

    /** Unread notifications count for the badge */
    suspend fun getUnreadCount(userId: String): Int {
        if (userId.isBlank()) return 0
        val snap = notificationsRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()
        return snap.size()
    }

    suspend fun markAsRead(notificationId: String) {
        if (notificationId.isBlank()) return
        notificationsRef.document(notificationId).update("isRead", true).await()
    }

    suspend fun markAllRead(userId: String) {
        if (userId.isBlank()) return
        val snap = notificationsRef.whereEqualTo("userId", userId).get().await()
        val batch = db.batch()
        for (doc in snap.documents) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    /**
     * Realtime listener for a user's notifications.
     *
     * Contract:
     * - Calls onUpdate with the full sorted list whenever anything changes.
     * - Returns a ListenerRegistration; caller MUST remove() it in onDestroyView().
     */
    fun listenForNotificationsForUser(
        userId: String,
        limit: Long = 100,
        onUpdate: (List<NotificationItem>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        if (userId.isBlank()) return null

        Log.d(TAG, "listenForNotificationsForUser(): userId=$userId limit=$limit")

        return notificationsRef
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenForNotificationsForUser(): ERROR msg=${err.message}", err)
                    onError(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(NotificationItem::class.java) }
                    .orEmpty()

                Log.d(TAG, "listenForNotificationsForUser(): update size=${list.size}")
                onUpdate(list)
            }
    }
}
