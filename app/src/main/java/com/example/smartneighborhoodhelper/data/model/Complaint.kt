package com.example.smartneighborhoodhelper.data.model

/**
 * Complaint.kt — Represents a complaint/issue reported by a resident.
 *
 * This is the CORE entity of the app. Dual-stored:
 *   1. Room @Entity  → offline cache (works without internet)
 *   2. Firestore doc → cloud sync (real-time updates for admin)
 *
 * STATUS FLOW:
 *   "Pending" → "In Progress" → "Resolved"
 *   - Resident creates complaint → status = "Pending"
 *   - Admin assigns a provider  → status = "In Progress"
 *   - Admin marks resolved      → status = "Resolved"
 *   - Resident confirms          → resolvedConfirmedByResident = true
 *
 * SYNC STRATEGY:
 *   When offline, complaints save to Room with isSynced = false.
 *   When connectivity returns, a sync method pushes them to Firestore.
 */

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "complaints")
data class Complaint(
    @PrimaryKey
    val id: String = "",                    // Firestore document ID (or UUID if offline)
    val title: String = "",                 // Short title: "Broken streetlight"
    val description: String = "",           // Detailed description
    val category: String = "",              // e.g. "Plumbing", "Electrical", "Roads"
    val imageUrl: String = "",              // Base64 encoded image OR Firebase Storage URL
    val latitude: Double = 0.0,             // GPS latitude of the issue
    val longitude: Double = 0.0,            // GPS longitude of the issue
    val locationText: String = "",          // Manual location description (e.g. "Block A, Near Gate 2")
    val status: String = "Pending",         // "Pending" | "In Progress" | "Resolved"
    val reportedBy: String = "",            // UID of the resident who reported
    val communityId: String = "",           // Community this complaint belongs to
    val assignedProvider: String = "",      // Service provider ID (set by admin)
    val createdAt: Long = 0L,              // When complaint was created
    val updatedAt: Long = 0L,              // When status was last changed
    val resolvedConfirmedByResident: Boolean = false,  // Resident confirmed resolution
    val isSynced: Boolean = true           // false = pending upload to Firestore
)

