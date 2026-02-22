package com.example.smartneighborhoodhelper.data.model

/**
 * User.kt — Represents a user in the app (Admin or Resident).
 *
 * This data class serves DUAL purpose:
 *   1. Room @Entity  → cached locally in SQLite for offline access
 *   2. Firestore doc → stored in the "users" collection in the cloud
 *
 * WHY data class?
 *   - Kotlin auto-generates equals(), hashCode(), toString(), copy()
 *   - Perfect for objects that just hold data (no behavior)
 *
 * FIRESTORE MAPPING:
 *   Each field name here matches the Firestore document field name exactly.
 *   Firestore SDK can auto-convert between this class and a document using
 *   toObject(User::class.java) — but only if there's a no-arg constructor.
 *   The default parameter values below provide that no-arg constructor.
 */

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val uid: String = "",           // Firebase Auth UID — unique per user
    val name: String = "",          // Display name entered during signup
    val email: String = "",         // Email used for login
    val phone: String = "",         // Phone number (optional, for contact)
    val role: String = "",          // "admin" or "resident"
    val communityId: String = "",   // ID of the community they belong to
    val pincode: String = "",       // Area pincode — used for community discovery
    val createdAt: Long = 0L       // System.currentTimeMillis() at signup
)

