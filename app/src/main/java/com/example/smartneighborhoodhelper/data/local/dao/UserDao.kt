package com.example.smartneighborhoodhelper.data.local.dao

import androidx.room.*
import com.example.smartneighborhoodhelper.data.model.User

/**
 * UserDao.kt — Room DAO for caching the current user's profile locally.
 *
 * WHY cache the user locally?
 *   - Avoids a Firestore read every time we need user info (name, role, communityId)
 *   - Works offline — user can still see their profile without internet
 *   - SharedPreferences stores the session (uid, role), but Room stores the full profile
 */
@Dao
interface UserDao {

    // Save or update the current user's profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Get user by UID
    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUserById(uid: String): User?

    // Delete all cached users (on logout)
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

