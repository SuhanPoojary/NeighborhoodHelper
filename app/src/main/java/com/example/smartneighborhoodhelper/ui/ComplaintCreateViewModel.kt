package com.example.smartneighborhoodhelper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartneighborhoodhelper.network.ApiClient
import com.example.smartneighborhoodhelper.network.ComplaintCreatedRequest
import kotlinx.coroutines.launch

class ComplaintCreateViewModel : ViewModel() {

    fun notifyAdminOnComplaintCreated(
        adminId: String,
        complaintId: String,
        residentName: String,
        complaintTitle: String
    ) {
        viewModelScope.launch {
            ApiClient.api.complaintCreated(
                ComplaintCreatedRequest(
                    adminId = adminId,
                    complaintId = complaintId,
                    residentName = residentName,
                    complaintTitle = complaintTitle
                )
            )
        }
    }
}

