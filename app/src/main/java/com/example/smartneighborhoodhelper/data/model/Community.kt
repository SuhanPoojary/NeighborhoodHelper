package com.example.smartneighborhoodhelper.data.model

/**
 * Community.kt — Represents a neighborhood community.
 *
 * Created by an Admin. Residents join using the 6-digit [code].
 * Stored in Firestore "communities" collection.
 *
 * NOT a Room entity — communities are always fetched from Firestore
 * (they change rarely, and we always need the latest version).
 */
data class Community(
    val id: String = "",            // Firestore document ID
    val name: String = "",          // e.g. "Sunrise Apartments"
    val area: String = "",          // e.g. "Green Park"
    val pincode: String = "",       // Auto-discover by pincode
    val code: String = "",          // 6-digit join code (e.g. "482917")
    val adminUid: String = "",      // UID of the admin who created this community
    val memberCount: Long = 0L,      // quick display (optional)
    val createdAt: Long = 0L       // System.currentTimeMillis() at creation
)

