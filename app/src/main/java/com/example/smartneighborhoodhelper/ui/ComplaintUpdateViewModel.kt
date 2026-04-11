package com.example.smartneighborhoodhelper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartneighborhoodhelper.network.ApiClient
import com.example.smartneighborhoodhelper.network.ComplaintUpdatedRequest
import kotlinx.coroutines.launch

class ComplaintUpdateViewModel : ViewModel() {

    fun notifyResidentOnComplaintUpdated(
        residentId: String,
        complaintId: String,
        status: String,
        updatedByName: String
    ) {
        viewModelScope.launch {
            ApiClient.api.complaintUpdated(
                ComplaintUpdatedRequest(
                    residentId = residentId,
                    complaintId = complaintId,
                    status = status,
                    updatedByName = updatedByName
                )
            )
        }
    }
}

