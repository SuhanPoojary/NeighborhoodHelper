package com.example.smartneighborhoodhelper.data.remote.repository
import android.net.Uri
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
/**
 * ComplaintRepository — Handles complaint CRUD + image upload to Firebase.
 */
class ComplaintRepository {
    private val db = FirebaseFirestore.getInstance()
    private val complaintsRef = db.collection(Constants.COLLECTION_COMPLAINTS)
    private val storage = FirebaseStorage.getInstance()
    /**
     * Upload an image to Firebase Storage and return the download URL.
     */
    suspend fun uploadImage(imageUri: Uri): String {
        val fileName = "complaints/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(imageUri).await()
        return ref.downloadUrl.await().toString()
    }
    /**
     * Submit a new complaint to Firestore.
     */
    suspend fun reportComplaint(complaint: Complaint): String {
        val docRef = complaintsRef.document()
        val finalComplaint = complaint.copy(
            id = docRef.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        docRef.set(finalComplaint).await()
        return docRef.id
    }
    /**
     * Get all complaints for a community, ordered by most recent first.
     * NOTE: We sort in-memory to avoid needing a Firestore composite index.
     * (whereEqualTo + orderBy on different fields requires a composite index
     *  that must be manually created in Firebase Console — FAILED_PRECONDITION error.)
     */
    suspend fun getComplaintsByCommunity(communityId: String): List<Complaint> {
        val snap = complaintsRef
            .whereEqualTo("communityId", communityId)
            .get()
            .await()
        return snap.documents
            .mapNotNull { it.toObject(Complaint::class.java) }
            .sortedByDescending { it.createdAt }
    }
    /**
     * Get complaints reported by a specific user, ordered by most recent.
     * NOTE: We sort in-memory to avoid needing a Firestore composite index.
     */
    suspend fun getComplaintsByUser(userId: String): List<Complaint> {
        val snap = complaintsRef
            .whereEqualTo("reportedBy", userId)
            .get()
            .await()
        return snap.documents
            .mapNotNull { it.toObject(Complaint::class.java) }
            .sortedByDescending { it.createdAt }
    }
    /**
     * Get a single complaint by ID.
     */
    suspend fun getComplaintById(complaintId: String): Complaint? {
        val doc = complaintsRef.document(complaintId).get().await()
        return doc.toObject(Complaint::class.java)
    }
    /**
     * Update complaint status (Admin action).
     */
    suspend fun updateStatus(complaintId: String, newStatus: String) {
        complaintsRef.document(complaintId).update(
            mapOf(
                "status" to newStatus,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }
    /**
     * Assign a service provider to a complaint (Admin action).
     */
    suspend fun assignProvider(complaintId: String, providerId: String) {
        val updates = mutableMapOf<String, Any>(
            "assignedProvider" to providerId,
            "updatedAt" to System.currentTimeMillis()
        )
        // Only set status to "In Progress" if actually assigning (not removing)
        if (providerId.isNotBlank()) {
            updates["status"] = Constants.STATUS_IN_PROGRESS
        }
        complaintsRef.document(complaintId).update(updates).await()
    }
    /**
     * Resident confirms resolution.
     */
    suspend fun confirmResolution(complaintId: String, confirmed: Boolean) {
        if (confirmed) {
            complaintsRef.document(complaintId).update(
                mapOf(
                    "resolvedConfirmedByResident" to true,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        } else {
            // Reopen complaint
            complaintsRef.document(complaintId).update(
                mapOf(
                    "status" to Constants.STATUS_PENDING,
                    "resolvedConfirmedByResident" to false,
                    "assignedProvider" to "",
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        }
    }
}
