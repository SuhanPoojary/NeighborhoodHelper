package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.Community
import com.example.smartneighborhoodhelper.data.model.JoinRequest
import com.example.smartneighborhoodhelper.data.model.NotificationItem
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * CommunityRepository — Firestore operations for communities.
 */
class CommunityRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val communitiesRef = firestore.collection(Constants.COLLECTION_COMMUNITIES)
    private val usersRef = firestore.collection(Constants.COLLECTION_USERS)
    private val joinRequestsRef = firestore.collection(Constants.COLLECTION_JOIN_REQUESTS)
    private val notificationsRef = firestore.collection(Constants.COLLECTION_NOTIFICATIONS)

    /**
     * Create a new community with a unique 6-digit code.
     */
    suspend fun createCommunity(
        name: String,
        area: String,
        pincode: String,
        adminUid: String
    ): Community {
        val trimmedName = name.trim()
        val trimmedArea = area.trim()
        val trimmedPincode = pincode.trim()
        require(trimmedName.isNotEmpty()) { "Community name required" }
        require(trimmedArea.isNotEmpty()) { "Area required" }
        require(trimmedPincode.length == 6) { "Invalid pincode" }

        val code = generateUniqueCode()

        val docRef = communitiesRef.document()
        val community = Community(
            id = docRef.id,
            name = trimmedName,
            area = trimmedArea,
            pincode = trimmedPincode,
            code = code,
            adminUid = adminUid,
            memberCount = 1,
            createdAt = System.currentTimeMillis()
        )

        // Save community
        docRef.set(community).await()

        // Update admin user profile with communityId
        usersRef.document(adminUid)
            .set(mapOf("communityId" to community.id), SetOptions.merge())
            .await()

        return community
    }

    /** Find a community by its 6-digit join code */
    suspend fun findByCode(code: String): Community? {
        val snap = communitiesRef.whereEqualTo("code", code.trim()).limit(1).get().await()
        val doc = snap.documents.firstOrNull() ?: return null
        // Ensure id is set
        val c = doc.toObject(Community::class.java) ?: return null
        return if (c.id.isNotBlank()) c else c.copy(id = doc.id)
    }

    /** Find all communities matching a pincode (for auto-discover) */
    suspend fun findByPincode(pincode: String): List<Community> {
        val snap = communitiesRef.whereEqualTo("pincode", pincode.trim()).get().await()
        return snap.documents.mapNotNull { doc ->
            val c = doc.toObject(Community::class.java) ?: return@mapNotNull null
            if (c.id.isNotBlank()) c else c.copy(id = doc.id)
        }
    }

    /**
     * Join a community: set user's communityId and increment memberCount.
     */
    suspend fun joinCommunity(userId: String, communityId: String) {
        val uid = userId.trim()
        val cid = communityId.trim()
        require(uid.isNotEmpty()) { "Invalid user" }
        require(cid.isNotEmpty()) { "Invalid community" }

        firestore.runTransaction { tx ->
            val userDoc = usersRef.document(uid)
            val communityDoc = communitiesRef.document(cid)

            // ⚠️ Firestore rule: ALL reads MUST happen BEFORE any writes
            val current = tx.get(communityDoc).getLong("memberCount") ?: 0L

            // Now do writes
            tx.set(userDoc, mapOf("communityId" to cid), SetOptions.merge())
            tx.update(communityDoc, "memberCount", current + 1)
        }.await()
    }

    /** Fetch a community by its document ID. */
    suspend fun getCommunityById(communityId: String): Community? {
        if (communityId.isBlank()) return null
        val doc = communitiesRef.document(communityId).get().await()
        val c = doc.toObject(Community::class.java) ?: return null
        return if (c.id.isNotBlank()) c else c.copy(id = doc.id)
    }

    /**
     * Generates a unique numeric 6-digit code by checking Firestore.
     * Small scale: fine for college project.
     */
    private suspend fun generateUniqueCode(maxAttempts: Int = 10): String {
        repeat(maxAttempts) {
            val candidate = Random.nextInt(100000, 1000000).toString()
            val exists = communitiesRef.whereEqualTo("code", candidate).limit(1).get().await()
            if (exists.isEmpty) return candidate
        }
        throw Exception("Could not generate unique community code. Try again.")
    }

    /**
     * Create a join request (pincode join flow).
     *
     * Resident does NOT join immediately. Admin must approve.
     */
    suspend fun createJoinRequest(
        communityId: String,
        residentUid: String,
        residentName: String,
        residentEmail: String,
        communityName: String = "",
    ): JoinRequest {
        val cid = communityId.trim()
        val uid = residentUid.trim()
        require(cid.isNotEmpty()) { "Invalid community" }
        require(uid.isNotEmpty()) { "Invalid user" }

        // Prevent duplicate pending requests for same user+community
        val existing = joinRequestsRef
            .whereEqualTo("communityId", cid)
            .whereEqualTo("residentUid", uid)
            .whereEqualTo("status", JoinRequest.STATUS_PENDING)
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            val doc = existing.documents.first()
            val jr = doc.toObject(JoinRequest::class.java) ?: JoinRequest()
            return if (jr.id.isNotBlank()) jr else jr.copy(id = doc.id)
        }

        val docRef = joinRequestsRef.document()
        val now = System.currentTimeMillis()
        val request = JoinRequest(
            id = docRef.id,
            communityId = cid,
            communityName = communityName,
            residentUid = uid,
            residentName = residentName,
            residentEmail = residentEmail,
            status = JoinRequest.STATUS_PENDING,
            createdAt = now,
            updatedAt = now,
        )

        docRef.set(request).await()
        return request
    }

    /** Admin: Get pending join requests for a community */
    suspend fun getPendingJoinRequests(communityId: String): List<JoinRequest> {
        val cid = communityId.trim()
        if (cid.isBlank()) return emptyList()

        val snap = joinRequestsRef
            .whereEqualTo("communityId", cid)
            .whereEqualTo("status", JoinRequest.STATUS_PENDING)
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            val jr = doc.toObject(JoinRequest::class.java) ?: return@mapNotNull null
            if (jr.id.isNotBlank()) jr else jr.copy(id = doc.id)
        }.sortedByDescending { it.createdAt }
    }

    /**
     * Admin approves a join request:
     *  - mark request Approved
     *  - set user's communityId
     *  - increment community memberCount
     */
    suspend fun approveJoinRequest(requestId: String) {
        val rid = requestId.trim()
        require(rid.isNotEmpty()) { "Invalid request" }

        // We'll capture these inside the transaction and use them after commit
        var residentUid = ""
        var communityId = ""

        firestore.runTransaction { tx ->
            val reqDoc = joinRequestsRef.document(rid)
            val reqSnap = tx.get(reqDoc)

            val status = reqSnap.getString("status") ?: JoinRequest.STATUS_PENDING
            if (status != JoinRequest.STATUS_PENDING) return@runTransaction

            communityId = reqSnap.getString("communityId") ?: ""
            residentUid = reqSnap.getString("residentUid") ?: ""
            require(communityId.isNotBlank()) { "Request missing community" }
            require(residentUid.isNotBlank()) { "Request missing resident" }

            val userDoc = usersRef.document(residentUid)
            val communityDoc = communitiesRef.document(communityId)

            // Reads before writes (Firestore rule)
            val currentMembers = tx.get(communityDoc).getLong("memberCount") ?: 0L

            // Writes
            tx.update(reqDoc, mapOf(
                "status" to JoinRequest.STATUS_APPROVED,
                "updatedAt" to System.currentTimeMillis(),
                "approvedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(userDoc, mapOf("communityId" to communityId), SetOptions.merge())
            tx.update(communityDoc, "memberCount", currentMembers + 1)
        }.await()

        // ✅ create an in-app notification for the resident (outside transaction)
        if (residentUid.isNotBlank() && communityId.isNotBlank()) {
            val item = NotificationItem(
                userId = residentUid,
                communityId = communityId,
                complaintId = "",
                type = Constants.NOTIF_JOIN_APPROVED,
                title = "Join request approved",
                message = "Your request to join the community was approved.",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )
            notificationsRef.add(item).await()
        }
    }

    /** Admin declines a join request (no membership change) */
    suspend fun declineJoinRequest(requestId: String) {
        val rid = requestId.trim()
        require(rid.isNotEmpty()) { "Invalid request" }

        // ✅ Only update the join request status (single doc write)
        // This is enough for syllabus/demo and avoids extra permission failures.
        joinRequestsRef.document(rid)
            .set(
                mapOf(
                    "status" to JoinRequest.STATUS_DECLINED,
                    "updatedAt" to System.currentTimeMillis(),
                    "declinedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        // (Optional) In-app notification can be re-enabled once rules are finalized.
    }
}
