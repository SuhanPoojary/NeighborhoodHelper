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
    const val COLLECTION_NOTIFICATIONS = "notifications"
    const val COLLECTION_JOIN_REQUESTS = "joinRequests"

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
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    // ── Notification ──
    // NOTE: Changed from "complaint_updates" to "complaint_updates_v2"
    // because Android caches channel settings. The old channel had IMPORTANCE_DEFAULT
    // (silent). This new one has IMPORTANCE_HIGH (sound + vibration + heads-up popup).
    const val NOTIFICATION_CHANNEL_ID = "complaint_updates_v2"
    const val NOTIFICATION_CHANNEL_NAME = "Complaint Updates"

    // Notification event types for in-app feed + push payload mapping
    const val NOTIF_NEW_COMPLAINT = "new_complaint"
    const val NOTIF_PROVIDER_ASSIGNED = "provider_assigned"
    const val NOTIF_STATUS_CHANGED = "status_changed"
    const val NOTIF_REOPENED = "reopened"
    const val NOTIF_JOIN_REQUEST = "join_request"
    const val NOTIF_JOIN_APPROVED = "join_approved"
    const val NOTIF_JOIN_REJECTED = "join_rejected"

    // Notification filters
    const val FILTER_ALL = "all"
    const val FILTER_UNREAD = "unread"

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
