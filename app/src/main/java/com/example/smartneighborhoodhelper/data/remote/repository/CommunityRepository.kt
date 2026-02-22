package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.Community
import com.example.smartneighborhoodhelper.util.Constants
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
}
