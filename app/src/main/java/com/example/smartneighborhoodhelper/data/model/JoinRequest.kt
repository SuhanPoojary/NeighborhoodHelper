package com.example.smartneighborhoodhelper.data.model

/**
 * JoinRequest.kt — Resident's request to join a community discovered by pincode.
 *
 * Flow:
 *  - Resident taps "Join" on a community card (pincode discovery)
 *  - App creates a joinRequest doc
 *  - Admin reviews and approves/declines
 *  - On approval: user's communityId is set + community memberCount increments
 */
data class JoinRequest(
    val id: String = "",
    val communityId: String = "",
    val communityName: String = "",   // optional denormalized display
    val residentUid: String = "",
    val residentName: String = "",
    val residentEmail: String = "",
    val residentPhone: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val STATUS_PENDING = "Pending"
        const val STATUS_APPROVED = "Approved"
        const val STATUS_DECLINED = "Declined"
    }
}
