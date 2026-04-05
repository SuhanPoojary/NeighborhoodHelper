package com.example.smartneighborhoodhelper.ui.community

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.JoinRequestRepository
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * PendingApprovalActivity — shown to a Resident after they request to join via pincode.
 *
 * It polls Firestore for the request status (simple + syllabus-friendly).
 */
class PendingApprovalActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private val repo = JoinRequestRepository()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private lateinit var ivBack: ImageView
    private lateinit var tvCommunityName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnLogout: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_approval)

        session = SessionManager(this)

        ivBack = findViewById(R.id.ivBack)
        tvCommunityName = findViewById(R.id.tvCommunityName)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnLogout = findViewById(R.id.btnLogout)

        ivBack.setOnClickListener {
            goToRoleSelectionAndClearTask()
        }

        btnRefresh.setOnClickListener { refreshStatus() }
        btnLogout.setOnClickListener {
            session.clearSession()
            goToRoleSelectionAndClearTask()
        }

        // ✅ Handle system back (gesture/back button) same as toolbar back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToRoleSelectionAndClearTask()
            }
        })

        refreshStatus()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (!isFinishing) {
                delay(8_000) // every 8 seconds
                refreshStatus(showSpinner = false)
            }
        }
    }

    private fun refreshStatus(showSpinner: Boolean = true) {
        val uid = session.getUserId().orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(this, "Session missing. Please login again.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                if (showSpinner) {
                    progressBar.visibility = View.VISIBLE
                }

                val latest = repo.getLatestForResident(uid)

                progressBar.visibility = View.GONE

                if (latest == null) {
                    tvStatus.text = "No join request found."
                    return@launch
                }

                tvCommunityName.text = latest.communityName.ifBlank { "Community" }
                tvStatus.text = "Status: ${latest.status}"

                if (latest.status == com.example.smartneighborhoodhelper.data.model.JoinRequest.STATUS_APPROVED) {
                    // ✅ Fetch latest user profile to get the communityId set by admin approval
                    val userDoc = db.collection(com.example.smartneighborhoodhelper.util.Constants.COLLECTION_USERS)
                        .document(uid)
                        .get()
                        .await()

                    val approvedCommunityId = userDoc.getString("communityId").orEmpty()
                    if (approvedCommunityId.isNotBlank()) {
                        session.saveCommunityId(approvedCommunityId)
                    }

                    Toast.makeText(this@PendingApprovalActivity, "Approved! Redirecting…", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@PendingApprovalActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }

                if (latest.status == com.example.smartneighborhoodhelper.data.model.JoinRequest.STATUS_DECLINED) {
                    tvStatus.text = "Status: Declined (you can try another community)"

                    // ✅ If declined, don't keep this screen in back stack.
                    // Resident can now go Discover / Join by code again.
                    // We intentionally do NOT auto-navigate, just clean up history.
                    // (Back will take them to RoleSelection.)
                }

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@PendingApprovalActivity, e.message ?: "Failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToRoleSelectionAndClearTask() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
