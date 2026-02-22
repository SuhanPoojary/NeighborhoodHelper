package com.example.smartneighborhoodhelper.data.remote.repository

import com.example.smartneighborhoodhelper.data.model.ServiceProvider
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * ProviderRepository — CRUD operations for service providers in Firestore.
 *
 * Service providers are data entities (not app users). Only admins manage them.
 * Stored in Firestore under "serviceProviders" collection.
 */
class ProviderRepository {

    private val db = FirebaseFirestore.getInstance()
    private val providersRef = db.collection(Constants.COLLECTION_PROVIDERS)

    /**
     * Add a new service provider to Firestore.
     * Returns the generated document ID.
     */
    suspend fun addProvider(provider: ServiceProvider): String {
        val docRef = providersRef.document()
        val finalProvider = provider.copy(id = docRef.id)
        docRef.set(finalProvider).await()
        return docRef.id
    }

    /**
     * Get all providers for a community.
     * Sorted alphabetically by name.
     */
    suspend fun getProvidersByCommunity(communityId: String): List<ServiceProvider> {
        val snap = providersRef
            .whereEqualTo("communityId", communityId)
            .get()
            .await()
        return snap.documents
            .mapNotNull { it.toObject(ServiceProvider::class.java) }
            .sortedBy { it.name }
    }

    /**
     * Get a single provider by ID.
     */
    suspend fun getProviderById(providerId: String): ServiceProvider? {
        val doc = providersRef.document(providerId).get().await()
        return doc.toObject(ServiceProvider::class.java)
    }

    /**
     * Update an existing provider.
     */
    suspend fun updateProvider(provider: ServiceProvider) {
        providersRef.document(provider.id).set(provider).await()
    }

    /**
     * Delete a provider.
     */
    suspend fun deleteProvider(providerId: String) {
        providersRef.document(providerId).delete().await()
    }
}

