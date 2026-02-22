package com.example.smartneighborhoodhelper.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.smartneighborhoodhelper.data.model.Complaint

/**
 * ComplaintDao.kt — Room DAO (Data Access Object) for complaints.
 *
 * WHAT IS A DAO?
 *   It's an interface where you define database operations using annotations.
 *   Room auto-generates the actual SQLite code at compile time via KSP.
 *
 * WHY LiveData return types?
 *   Room can return LiveData directly — the UI observes it, and whenever
 *   the database changes, the UI auto-updates. No manual refresh needed.
 *
 * ANNOTATIONS EXPLAINED:
 *   @Insert(onConflict = REPLACE) → if complaint with same ID exists, overwrite it
 *   @Query → raw SQL (Room verifies it at compile time — catches typos!)
 */
@Dao
interface ComplaintDao {

    // Insert or update a single complaint
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: Complaint)

    // Insert or update multiple complaints at once (used during sync)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(complaints: List<Complaint>)

    // Get all complaints for a community — returns LiveData for auto-updating UI
    @Query("SELECT * FROM complaints WHERE communityId = :communityId ORDER BY createdAt DESC")
    fun getComplaintsByCommunity(communityId: String): LiveData<List<Complaint>>

    // Get only this user's complaints (for Resident dashboard)
    @Query("SELECT * FROM complaints WHERE reportedBy = :userId ORDER BY createdAt DESC")
    fun getComplaintsByUser(userId: String): LiveData<List<Complaint>>

    // Get a single complaint by ID (for detail screen)
    @Query("SELECT * FROM complaints WHERE id = :complaintId")
    fun getComplaintById(complaintId: String): LiveData<Complaint?>

    // Get all complaints not yet synced to Firestore (offline-first)
    @Query("SELECT * FROM complaints WHERE isSynced = 0")
    suspend fun getUnsyncedComplaints(): List<Complaint>

    // Update a complaint (e.g., status change, sync flag)
    @Update
    suspend fun updateComplaint(complaint: Complaint)

    // Delete a complaint
    @Delete
    suspend fun deleteComplaint(complaint: Complaint)

    // Delete all complaints (used when user logs out)
    @Query("DELETE FROM complaints")
    suspend fun deleteAll()
}

