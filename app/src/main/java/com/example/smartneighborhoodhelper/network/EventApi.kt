package com.example.smartneighborhoodhelper.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ComplaintCreatedRequest(
    val adminId: String,
    val complaintId: String,
    val residentName: String? = null,
    val complaintTitle: String? = null
)

data class ComplaintUpdatedRequest(
    val residentId: String,
    val complaintId: String,
    val status: String? = null,
    val updatedByName: String? = null
)

data class ComplaintReopenedRequest(
    val adminId: String,
    val complaintId: String,
    val residentName: String? = null
)

data class JoinRequestRequest(
    val adminId: String,
    val residentId: String,
    val residentName: String? = null,
    val communityName: String? = null
)

interface EventApi {
    @POST("events/complaint-created")
    suspend fun complaintCreated(@Body body: ComplaintCreatedRequest): Response<Unit>

    @POST("events/complaint-updated")
    suspend fun complaintUpdated(@Body body: ComplaintUpdatedRequest): Response<Unit>

    @POST("events/complaint-reopened")
    suspend fun complaintReopened(@Body body: ComplaintReopenedRequest): Response<Unit>

    @POST("events/join-request")
    suspend fun joinRequest(@Body body: JoinRequestRequest): Response<Unit>
}

