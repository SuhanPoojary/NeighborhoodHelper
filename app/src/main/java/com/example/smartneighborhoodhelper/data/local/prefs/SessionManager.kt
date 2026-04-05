package com.example.smartneighborhoodhelper.data.local.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * SessionManager.kt — Wrapper around SharedPreferences for user session data.
 *
 * WHAT IS SharedPreferences?
 *   A simple key-value store that persists across app restarts.
 *   Stored as an XML file in the app's private directory.
 *   Perfect for small data like login tokens, user role, settings.
 *
 * WHY NOT Room for this?
 *   Room is for structured data with queries (complaints, users).
 *   SharedPreferences is simpler and faster for single key-value lookups.
 *   Your syllabus requires BOTH — so we use each where it fits best.
 *
 * HOW IT WORKS:
 *   - After login/signup → saveUserSession(uid, role, communityId)
 *   - On app start → isLoggedIn() checks if session exists
 *   - On logout → clearSession() removes all saved data
 *
 * USAGE:
 *   val session = SessionManager(context)
 *   session.saveUserSession("abc123", "admin", "community456")
 *   if (session.isLoggedIn()) { ... }
 */
class SessionManager(context: Context) {

    // getSharedPreferences("name", MODE_PRIVATE) creates/opens a private XML file
    // MODE_PRIVATE = only this app can read it
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        // Constants for preference keys — avoids typos
        private const val PREFS_NAME = "user_session"
        private const val KEY_UID = "uid"
        private const val KEY_ROLE = "role"
        private const val KEY_COMMUNITY_ID = "community_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }

    // ── Onboarding ──

    /** Check if user has already seen the onboarding screens */
    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    /** Mark onboarding as seen (so it never shows again) */
    fun setOnboardingSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
    }

    // ── User Session ──

    /**
     * Save user session after successful login or signup.
     */
    fun saveUserSession(uid: String, name: String, email: String, role: String, communityId: String) {
        prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_ROLE, role)
            .putString(KEY_COMMUNITY_ID, communityId)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    /** Update just the communityId (after creating/joining a community) */
    fun saveCommunityId(communityId: String) {
        prefs.edit().putString(KEY_COMMUNITY_ID, communityId).apply()
    }

    /** Check if user is currently logged in */
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    /** Get the stored user ID */
    fun getUserId(): String? = prefs.getString(KEY_UID, null)

    /** Get the stored user name */
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    /** Get the stored role ("admin" or "resident") — used to decide which dashboard to show */
    fun getUserRole(): String? = prefs.getString(KEY_ROLE, null)

    /** Get the stored community ID */
    fun getCommunityId(): String? = prefs.getString(KEY_COMMUNITY_ID, null)

    /** Get the stored user email */
    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Get the stored user phone */
    fun getUserPhone(): String? = prefs.getString(KEY_PHONE, null)

    /**
     * Clear all session data — called on logout.
     * clear() removes ALL keys in this SharedPreferences file.
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
