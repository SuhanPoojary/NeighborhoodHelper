package com.example.smartneighborhoodhelper.data.remote.repository

import android.util.Log
import com.example.smartneighborhoodhelper.data.remote.api.BackendClient
import com.example.smartneighborhoodhelper.data.remote.api.ComplaintCreatedEvent
import com.example.smartneighborhoodhelper.data.remote.api.ComplaintReopenedEvent
import com.example.smartneighborhoodhelper.data.remote.api.ComplaintUpdatedEvent
import com.example.smartneighborhoodhelper.data.remote.api.JoinRequestEvent

class BackendEventRepository {

    private val api = BackendClient.api

    suspend fun complaintCreated(adminId: String, residentId: String, complaintId: String, communityId: String, category: String?) {
        try {
            val res = api.complaintCreated(
                ComplaintCreatedEvent(
                    adminId = adminId,
                    residentId = residentId,
                    complaintId = complaintId,
                    communityId = communityId,
                    category = category
                )
            )
            Log.d("BACKEND_EVENT", "complaintCreated ok=${res.ok} event=${res.event} err=${res.error}")
        } catch (e: Exception) {
            Log.w("BACKEND_EVENT", "complaintCreated failed: ${e.message}")
        }
    }

    suspend fun complaintUpdated(residentId: String, adminId: String, complaintId: String, communityId: String, status: String?) {
        try {
            val res = api.complaintUpdated(
                ComplaintUpdatedEvent(
                    residentId = residentId,
                    adminId = adminId,
                    complaintId = complaintId,
                    communityId = communityId,
                    status = status
                )
            )
            Log.d("BACKEND_EVENT", "complaintUpdated ok=${res.ok} event=${res.event} err=${res.error}")
        } catch (e: Exception) {
            Log.w("BACKEND_EVENT", "complaintUpdated failed: ${e.message}")
        }
    }

    suspend fun complaintReopened(adminId: String, residentId: String, complaintId: String, communityId: String) {
        try {
            val res = api.complaintReopened(
                ComplaintReopenedEvent(
                    adminId = adminId,
                    residentId = residentId,
                    complaintId = complaintId,
                    communityId = communityId
                )
            )
            Log.d("BACKEND_EVENT", "complaintReopened ok=${res.ok} event=${res.event} err=${res.error}")
        } catch (e: Exception) {
            Log.w("BACKEND_EVENT", "complaintReopened failed: ${e.message}")
        }
    }

    suspend fun joinRequest(adminId: String, residentId: String, communityId: String) {
        try {
            val res = api.joinRequest(JoinRequestEvent(adminId = adminId, residentId = residentId, communityId = communityId))
            Log.d("BACKEND_EVENT", "joinRequest ok=${res.ok} event=${res.event} err=${res.error}")
        } catch (e: Exception) {
            Log.w("BACKEND_EVENT", "joinRequest failed: ${e.message}")
        }
    }
}

