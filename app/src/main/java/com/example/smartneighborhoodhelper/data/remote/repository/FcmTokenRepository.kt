package com.example.smartneighborhoodhelper.data.remote.repository
import android.util.Log
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

        Log.d("UID_DEBUG", "Saving for UID: $uid")

        val tokensRef = db.collection("fcmTokens")
            .document(uid)
            .collection("tokens")

        val data = hashMapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // 🔥 STEP 1: Delete old tokens
        tokensRef.get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }

                // 🔥 STEP 2: Save only latest token
                tokensRef.document(token).set(data)
                    .addOnSuccessListener {
                        Log.d("TOKEN_SAVE", "SUCCESS: Token saved (only latest)")
                    }
                    .addOnFailureListener { e ->
                        Log.e("TOKEN_SAVE", "FAILED: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("TOKEN_SAVE", "FAILED to fetch old tokens: ${e.message}", e)
            }
    }
}

