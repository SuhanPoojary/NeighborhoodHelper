package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.User
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun getUser(uid: String): User {
        val doc = firestore.collection(Constants.COLLECTION_USERS).document(uid).get().await()
        return doc.toObject(User::class.java) ?: throw Exception("User not found")
    }

    suspend fun updateUser(uid: String, updates: Map<String, Any?>) {
        // Filter nulls so we don't accidentally wipe fields.
        val safeUpdates = updates.filterValues { it != null }
        firestore.collection(Constants.COLLECTION_USERS).document(uid).update(safeUpdates).await()
    }
}

