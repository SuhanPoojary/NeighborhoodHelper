package com.example.smartneighborhoodhelper.ui.community
import android.util.Log
import com.example.smartneighborhoodhelper.data.remote.api.BackendClient
import com.example.smartneighborhoodhelper.data.remote.api.JoinRequestEvent
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.CommunityRepository
import com.example.smartneighborhoodhelper.databinding.ActivityDiscoverCommunitiesBinding
import com.example.smartneighborhoodhelper.databinding.DialogJoinByCodeBinding
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * DiscoverCommunitiesActivity.kt — Shows communities matching resident's pincode.
 * TODO: Fully implement in Feature 2 (Community Discovery & Joining).
 */
class DiscoverCommunitiesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscoverCommunitiesBinding
    private lateinit var sessionManager: SessionManager
    private val repo = CommunityRepository()

    private lateinit var adapter: CommunityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoverCommunitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        val pincode = intent.getStringExtra("pincode")?.trim().orEmpty()
        binding.tvPincode.text = if (pincode.isNotBlank()) "Pincode: $pincode" else ""

        binding.ivBack.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }

        adapter = CommunityAdapter { community ->
            // ✅ Pincode discovery join → request approval
            requestToJoinCommunity(community.id, community.name)
        }

        binding.rvCommunities.layoutManager = LinearLayoutManager(this)
        binding.rvCommunities.adapter = adapter

        binding.tvJoinByCode.setOnClickListener {
            showJoinByCodeDialog()
        }

        if (pincode.length == 6) {
            loadCommunities(pincode)
        } else {
            // No pincode provided — still allow join by code
            adapter.submitList(emptyList())
            Toast.makeText(this, "Enter community code to join.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadCommunities(pincode: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val list = repo.findByPincode(pincode)
                adapter.submitList(list)
                showLoading(false)

                if (list.isEmpty()) {
                    Toast.makeText(
                        this@DiscoverCommunitiesActivity,
                        "No communities found. Try Join using community code.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@DiscoverCommunitiesActivity, e.message ?: "Failed to load", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Pincode discovery join flow:
     * Resident sends join request → admin approves/declines.
     */
    private fun requestToJoinCommunity(communityId: String, communityName: String) {
        val uid = sessionManager.getUserId().orEmpty()
        val residentName = sessionManager.getUserName().orEmpty()

        // ✅ use the email we already saved in session (from login/signup)
        // fallback to FirebaseAuth current user email if needed
        val residentEmail = sessionManager.getUserEmail().orEmpty()
            .ifBlank { FirebaseAuth.getInstance().currentUser?.email.orEmpty() }

        if (uid.isBlank()) {
            Toast.makeText(this, "Session missing. Please login again.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                showLoading(true)
                repo.createJoinRequest(
                    communityId = communityId,
                    residentUid = uid,
                    residentName = residentName,
                    residentEmail = residentEmail,
                    communityName = communityName
                )
                // 🔥 BACKEND NOTIFICATION CALL (ADD THIS)
                try {
                    val community = repo.getCommunityById(communityId)

                    BackendClient.api.joinRequest(
                        JoinRequestEvent(
                            adminId = community?.adminUid ?: "",   // 🔥 IMPORTANT
                            residentId = uid,
                            communityId = communityId
                        )
                    )

                    Log.d("JOIN_API", "Join request API called")

                } catch (e: Exception) {
                    Log.e("JOIN_API", "API failed", e)
                }

                showLoading(false)
                Toast.makeText(this@DiscoverCommunitiesActivity, "Request sent to admin.", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@DiscoverCommunitiesActivity, PendingApprovalActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@DiscoverCommunitiesActivity, e.message ?: "Request failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Code join flow: instant join (no approval) — keeps your current behavior.
     */
    private fun joinCommunity(communityId: String) {
        val uid = sessionManager.getUserId()
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Session missing. Please login again.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                showLoading(true)
                repo.joinCommunity(uid, communityId)
                sessionManager.saveCommunityId(communityId)

                val intent = Intent(this@DiscoverCommunitiesActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@DiscoverCommunitiesActivity, e.message ?: "Join failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showJoinByCodeDialog() {
        val dialogBinding = DialogJoinByCodeBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Join", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = dialogBinding.etCode.text?.toString().orEmpty().trim()
                dialogBinding.tilCode.error = null
                if (code.length != 6) {
                    dialogBinding.tilCode.error = "Enter 6-digit code"
                    return@setOnClickListener
                }

                // Fetch community by code then join
                lifecycleScope.launch {
                    try {
                        showLoading(true)
                        val community = repo.findByCode(code)
                        if (community == null) {
                            showLoading(false)
                            Toast.makeText(this@DiscoverCommunitiesActivity, "Invalid code", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        dialog.dismiss()
                        joinCommunity(community.id)
                    } catch (e: Exception) {
                        showLoading(false)
                        Toast.makeText(this@DiscoverCommunitiesActivity, e.message ?: "Failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
