package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository.kt — Handles all authentication logic.
 *
 * WHAT IS A REPOSITORY?
 *   In MVVM, the Repository is the single source of truth for data.
 *   The ViewModel calls the Repository; the Repository talks to Firebase.
 *   The ViewModel NEVER directly touches Firebase — this separation makes
 *   the code testable and the data layer swappable.
 *
 * COROUTINES + await():
 *   Firebase methods return Task<T>. The .await() extension (from
 *   kotlinx-coroutines-play-services) converts it to a suspend function.
 *   This lets us write async code that looks synchronous.
 *
 * WILL BE EXPANDED IN FEATURE 1 (Auth).
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Get the currently signed-in user's UID, or null if not signed in */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /** Check if a user is currently signed in */
    fun isUserSignedIn(): Boolean = auth.currentUser != null

    /**
     * Sign up a new user with email and password.
     * Also creates a user document in Firestore.
     *
     * @return the created User object
     * @throws Exception if signup fails (e.g., email already in use)
     */
    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        phone: String,
        role: String,
        pincode: String
    ): User {
        // Step 1: Create Firebase Auth account
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Signup failed: no UID returned")

        // Step 2: Create user document in Firestore
        val user = User(
            uid = uid,
            name = name,
            email = email,
            phone = phone,
            role = role,
            communityId = "",  // Set later when they create/join a community
            pincode = pincode,
            createdAt = System.currentTimeMillis()
        )
        firestore.collection("users").document(uid).set(user).await()

        return user
    }

    /**
     * Sign in an existing user.
     *
     * @return the User object from Firestore
     * @throws Exception if login fails (wrong password, no account, etc.)
     */
    suspend fun signIn(email: String, password: String): User {
        // Step 1: Authenticate with Firebase
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Login failed: no UID returned")

        // Step 2: Fetch user profile from Firestore
        val doc = firestore.collection("users").document(uid).get().await()
        return doc.toObject(User::class.java) ?: throw Exception("User profile not found")
    }

    /** Sign out the current user */
    fun signOut() {
        auth.signOut()
    }
}

