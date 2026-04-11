package com.example.smartneighborhoodhelper.data.remote.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Stores per-user FCM tokens in Firestore.
 * Schema:
 *   /fcmTokens/{uid}/tokens/{token} => { token, platform, updatedAt }
 */
class FcmTokenRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun upsertToken(uid: String, token: String) {
        if (uid.isBlank() || token.isBlank()) return

        val ref = db.collection("fcmTokens")
            .document(uid)
            .collection("tokens")
            .document(token)

        val data = hashMapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // Fire-and-forget. If this fails because of rules/auth, it shouldn't crash UI.
        ref.set(data)
    }
}

