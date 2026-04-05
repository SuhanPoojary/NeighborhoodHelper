package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.JoinRequest
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * JoinRequestRepository — Read join requests for a resident (so they can see status).
 *
 * NOTE ABOUT INDEXES:
 * Firestore throws FAILED_PRECONDITION when a query needs a composite index.
 * To keep the college project simple (no manual index creation), we avoid
 * orderBy() here and sort in-memory.
 */
class JoinRequestRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val joinRequestsRef = db.collection(Constants.COLLECTION_JOIN_REQUESTS)

    /** Latest join request for a user (any status) */
    suspend fun getLatestForResident(residentUid: String): JoinRequest? {
        val uid = residentUid.trim()
        if (uid.isBlank()) return null

        // Fetch a small set then pick the latest by createdAt.
        // This avoids needing a composite index for where + orderBy.
        val snap = joinRequestsRef
            .whereEqualTo("residentUid", uid)
            .limit(20)
            .get()
            .await()

        val doc = snap.documents
            .maxByOrNull { it.getLong("createdAt") ?: 0L }
            ?: return null

        val jr = doc.toObject(JoinRequest::class.java) ?: return null
        return if (jr.id.isNotBlank()) jr else jr.copy(id = doc.id)
    }

    /** Returns true if the resident currently has at least one PENDING join request. */
    suspend fun hasPendingForResident(residentUid: String): Boolean {
        val uid = residentUid.trim()
        if (uid.isBlank()) return false

        val snap = joinRequestsRef
            .whereEqualTo("residentUid", uid)
            .whereEqualTo("status", JoinRequest.STATUS_PENDING)
            .limit(1)
            .get()
            .await()

        return !snap.isEmpty
    }
}
