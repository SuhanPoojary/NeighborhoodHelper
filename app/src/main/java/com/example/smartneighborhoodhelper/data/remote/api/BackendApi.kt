package com.example.smartneighborhoodhelper.data.remote.api

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface BackendApi {

    @POST("events/complaint-created")
    suspend fun complaintCreated(@Body body: ComplaintCreatedEvent): BackendEventResponse

    @POST("events/complaint-updated")
    suspend fun complaintUpdated(@Body body: ComplaintUpdatedEvent): BackendEventResponse

    @POST("events/complaint-reopened")
    suspend fun complaintReopened(@Body body: ComplaintReopenedEvent): BackendEventResponse

    @POST("events/join-request")
    suspend fun joinRequest(@Body body: JoinRequestEvent): BackendEventResponse

    @POST("events/join-approved")
    suspend fun joinApproved(@Body body: JoinApprovedEvent): BackendEventResponse

    @POST("events/join-declined")
    suspend fun joinDeclined(@Body body: JoinApprovedEvent): BackendEventResponse
}



data class ComplaintCreatedEvent(
    val adminId: String,
    val residentId: String,
    val complaintId: String,
    val communityId: String? = null,
    val category: String? = null
)

data class ComplaintUpdatedEvent(
    val residentId: String,
    val adminId: String,
    val complaintId: String,
    val communityId: String? = null,
    val status: String? = null
)

data class ComplaintReopenedEvent(
    val adminId: String,
    val residentId: String,
    val complaintId: String,
    val communityId: String? = null
)

data class JoinRequestEvent(
    val adminId: String,
    val residentId: String,
    val communityId: String
)

data class BackendEventResponse(
    val ok: Boolean? = null,
    val event: String? = null,
    val result: Any? = null,
    val error: String? = null,
    val message: String? = null
)

data class JoinApprovedEvent(
    val residentId: String,
    val communityId: String
)

