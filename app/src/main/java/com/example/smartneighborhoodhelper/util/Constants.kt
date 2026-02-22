package com.example.smartneighborhoodhelper.util

/**
 * Constants.kt — App-wide constant values.
 *
 * WHY put constants here?
 *   Avoids hardcoding strings throughout the app. If a Firestore collection
 *   name changes, you change it in ONE place, not 15.
 */
object Constants {

    // ── Firestore Collection Names ──
    const val COLLECTION_USERS = "users"
    const val COLLECTION_COMMUNITIES = "communities"
    const val COLLECTION_COMPLAINTS = "complaints"
    const val COLLECTION_PROVIDERS = "serviceProviders"

    // ── Complaint Status Values ──
    const val STATUS_PENDING = "Pending"
    const val STATUS_IN_PROGRESS = "In Progress"
    const val STATUS_RESOLVED = "Resolved"

    // ── User Roles ──
    const val ROLE_ADMIN = "admin"
    const val ROLE_RESIDENT = "resident"

    // ── SharedPreferences ──
    const val PREFS_NAME = "user_session"

    // ── Intent Extras (for passing data between Activities) ──
    const val EXTRA_COMPLAINT_ID = "extra_complaint_id"
    const val EXTRA_COMMUNITY_ID = "extra_community_id"

    // ── Notification ──
    const val NOTIFICATION_CHANNEL_ID = "complaint_updates"
    const val NOTIFICATION_CHANNEL_NAME = "Complaint Updates"

    // ── Complaint Categories ──
    val COMPLAINT_CATEGORIES = listOf(
        "Garbage",
        "Water",
        "Electrical",
        "Road",
        "Drainage",
        "Plumbing",
        "Security",
        "Parking",
        "Noise",
        "Other"
    )
}

