package com.example.smartneighborhoodhelper.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartneighborhoodhelper.data.local.dao.ComplaintDao
import com.example.smartneighborhoodhelper.data.local.dao.UserDao
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.model.User

/**
 * AppDatabase.kt — The Room database for this app.
 *
 * WHAT IS ROOM?
 *   Room is Google's official wrapper around SQLite. Your syllabus requires
 *   "SQLite" — Room IS SQLite, but with compile-time safety and less boilerplate.
 *   Under the hood, Room generates SQLite queries from your @Dao interfaces.
 *
 * SINGLETON PATTERN:
 *   We use a companion object with @Volatile + synchronized to ensure only ONE
 *   database instance exists app-wide. Multiple instances would cause conflicts.
 *
 * ENTITIES:
 *   Each @Entity class = one SQLite table.
 *   - User → "users" table
 *   - Complaint → "complaints" table
 *
 * HOW TO USE:
 *   val db = AppDatabase.getInstance(context)
 *   val complaints = db.complaintDao().getComplaintsByCommunity("abc123")
 */
@Database(
    entities = [User::class, Complaint::class],
    version = 3,
    exportSchema = false  // Set true in production for migration tracking
)
abstract class AppDatabase : RoomDatabase() {

    // Room auto-implements these abstract functions
    abstract fun complaintDao(): ComplaintDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile  // Ensures changes are visible to all threads immediately
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         * synchronized = only one thread can execute this block at a time,
         * preventing two threads from creating two database instances.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_neighborhood_db"  // This is the SQLite file name
                )
                    .fallbackToDestructiveMigration(true)  // On schema change, drop all tables & recreate (OK for development)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

