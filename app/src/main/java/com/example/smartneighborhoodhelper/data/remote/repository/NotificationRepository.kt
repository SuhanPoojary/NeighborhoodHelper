package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.NotificationItem
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * NotificationRepository — Firestore operations for in-app notifications.
 */
class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val notificationsRef = db.collection(Constants.COLLECTION_NOTIFICATIONS)

    suspend fun createNotification(item: NotificationItem): String {
        val hasId = item.id.isNotBlank()
        val createdAt = if (item.createdAt > 0) item.createdAt else System.currentTimeMillis()

        return if (hasId) {
            // Overwrite only when caller provides a specific id.
            val finalItem = item.copy(createdAt = createdAt)
            notificationsRef.document(item.id).set(finalItem).await()
            item.id
        } else {
            // For NEW docs, use add() -> this is a create operation (no updates).
            val finalItem = item.copy(id = "", createdAt = createdAt)
            val docRef = notificationsRef.add(finalItem).await()
            // Store generated id back into doc for easy querying/debugging.
            notificationsRef.document(docRef.id).update("id", docRef.id).await()
            docRef.id
        }
    }

    suspend fun getNotificationsForUser(userId: String, limit: Long = 50): List<NotificationItem> {
        val snap = notificationsRef
            .whereEqualTo("userId", userId)
            .limit(limit)
            .get()
            .await()
        return snap.documents
            .mapNotNull { it.toObject(NotificationItem::class.java) }
            .sortedByDescending { it.createdAt }
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
}
