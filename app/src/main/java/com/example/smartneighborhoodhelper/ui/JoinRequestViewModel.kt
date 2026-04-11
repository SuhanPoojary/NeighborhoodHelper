package com.example.smartneighborhoodhelper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartneighborhoodhelper.network.ApiClient
import com.example.smartneighborhoodhelper.network.JoinRequestRequest
import kotlinx.coroutines.launch

class JoinRequestViewModel : ViewModel() {

    fun notifyAdminOnJoinRequest(
        adminId: String,
        residentId: String,
        residentName: String,
        communityName: String
    ) {
        viewModelScope.launch {
            ApiClient.api.joinRequest(
                JoinRequestRequest(
                    adminId = adminId,
                    residentId = residentId,
                    residentName = residentName,
                    communityName = communityName
                )
            )
        }
    }
}

