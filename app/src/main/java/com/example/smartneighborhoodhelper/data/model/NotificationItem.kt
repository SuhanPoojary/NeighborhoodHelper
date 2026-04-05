package com.example.smartneighborhoodhelper.data.model

/**
 * NotificationItem represents an in-app notification event stored in Firestore.
 */
data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val communityId: String = "",
    val complaintId: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = 0L
)

