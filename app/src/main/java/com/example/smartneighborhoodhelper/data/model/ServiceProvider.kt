package com.example.smartneighborhoodhelper.data.model

/**
 * ServiceProvider.kt — A service provider assigned by admin to fix complaints.
 *
 * Examples: "Raj Electricals", "City Plumbing Services"
 * Stored in Firestore "serviceProviders" collection.
 *
 * NOT a Room entity — managed only by admin, always fetched fresh.
 */
data class ServiceProvider(
    val id: String = "",            // Firestore document ID
    val name: String = "",          // Provider name: "Raj Electricals"
    val phone: String = "",         // Contact number
    val category: String = "",      // "Plumbing", "Electrical", "Roads", etc.
    val communityId: String = ""    // Which community this provider serves
)

