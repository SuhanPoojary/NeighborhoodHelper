package com.example.smartneighborhoodhelper.ui.complaint

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.model.ServiceProvider
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.data.remote.repository.ProviderRepository
import com.example.smartneighborhoodhelper.databinding.ActivityComplaintDetailBinding
import com.example.smartneighborhoodhelper.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ComplaintDetailActivity — Shows full complaint details with status timeline.
 * Admin sees action buttons (Mark In Progress / Resolved).
 * Resident sees confirmation buttons when resolved.
 */
class ComplaintDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComplaintDetailBinding
    private lateinit var session: SessionManager
    private val repo = ComplaintRepository()
    private val providerRepo = ProviderRepository()
    private var complaintId = ""
    private var providerList = listOf<ServiceProvider>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComplaintDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        complaintId = intent.getStringExtra(Constants.EXTRA_COMPLAINT_ID).orEmpty()

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (complaintId.isBlank()) {
            Toast.makeText(this, "Complaint not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadComplaint()
    }

    private fun loadComplaint() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val complaint = repo.getComplaintById(complaintId)
                binding.progressBar.visibility = View.GONE

                if (complaint == null) {
                    Toast.makeText(this@ComplaintDetailActivity, "Complaint not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                populateUI(complaint)
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ComplaintDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun populateUI(complaint: Complaint) {
        // Image — supports Base64 (embedded) and URL (Firebase Storage)
        if (complaint.imageUrl.isNotBlank()) {
            binding.ivComplaintImage.visibility = View.VISIBLE
            if (complaint.imageUrl.startsWith("data:") || complaint.imageUrl.length > 500) {
                // Base64 encoded image
                try {
                    val base64Str = if (complaint.imageUrl.contains(",")) {
                        complaint.imageUrl.substringAfter(",")
                    } else {
                        complaint.imageUrl
                    }
                    val bytes = Base64.decode(base64Str, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.ivComplaintImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    binding.ivComplaintImage.visibility = View.GONE
                }
            } else {
                // URL from Firebase Storage
                Glide.with(this).load(complaint.imageUrl).centerCrop().into(binding.ivComplaintImage)
            }
        } else {
            binding.ivComplaintImage.visibility = View.GONE
        }

        // Category + Status
        binding.tvCategory.text = complaint.category
        binding.tvStatus.text = complaint.status
        applyStatusStyle(complaint.status)

        // Description
        binding.tvDescription.text = complaint.description.ifBlank { "No description provided" }

        // Location — use manual locationText field
        if (complaint.locationText.isNotBlank()) {
            binding.tvLocation.text = complaint.locationText
        } else {
            binding.tvLocation.text = "Not provided"
        }

        // Reported By — fetch user name from Firestore
        if (complaint.reportedBy.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(complaint.reportedBy).get()
                .addOnSuccessListener { doc ->
                    binding.tvReportedBy.text = doc.getString("name") ?: complaint.reportedBy
                }
                .addOnFailureListener { binding.tvReportedBy.text = complaint.reportedBy }
        }

        // Date
        binding.tvDate.text = if (complaint.createdAt > 0) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))
        } else "Unknown"

        // Status Timeline
        buildTimeline(complaint)

        // ── Show assigned provider info to BOTH admin and resident ──
        if (complaint.assignedProvider.isNotBlank()) {
            loadProviderInfoCard(complaint.assignedProvider)
        } else {
            binding.cardProviderInfo.visibility = View.GONE
        }

        // Admin actions
        val role = session.getUserRole()
        if (role == Constants.ROLE_ADMIN) {
            binding.llAdminActions.visibility = View.VISIBLE
            binding.llResidentConfirm.visibility = View.GONE

            val hasProvider = complaint.assignedProvider.isNotBlank()

            // Show current provider card + Remove button if assigned
            if (hasProvider) {
                binding.llAssignedProvider.visibility = View.VISIBLE
                viewLifecycleOwner_safe {
                    try {
                        val provider = providerRepo.getProviderById(complaint.assignedProvider)
                        binding.tvAssignedProviderName.text = provider?.let {
                            "${it.name} (${it.category}) — ${it.phone}"
                        } ?: complaint.assignedProvider
                    } catch (e: Exception) {
                        binding.tvAssignedProviderName.text = complaint.assignedProvider
                    }
                }

                // Change label to "Change Provider"
                binding.tvAssignLabel.text = "Change Service Provider"
                binding.btnAssignProvider.text = "Change Provider"

                // Remove provider button
                binding.btnRemoveProvider.setOnClickListener {
                    removeProvider(complaint.id)
                }
            } else {
                binding.llAssignedProvider.visibility = View.GONE
                binding.tvAssignLabel.text = "Assign Service Provider"
                binding.btnAssignProvider.text = "Assign Provider"
            }

            // Load providers into spinner
            loadProvidersForSpinner(complaint)

            // Hide buttons based on current status
            when (complaint.status) {
                Constants.STATUS_IN_PROGRESS -> {
                    binding.btnMarkInProgress.visibility = View.GONE
                }
                Constants.STATUS_RESOLVED -> {
                    binding.btnMarkInProgress.visibility = View.GONE
                    binding.btnMarkResolved.visibility = View.GONE
                    binding.llAssignSection.visibility = View.GONE
                }
            }

            binding.btnMarkInProgress.setOnClickListener {
                updateStatus(complaint.id, Constants.STATUS_IN_PROGRESS)
            }
            binding.btnMarkResolved.setOnClickListener {
                updateStatus(complaint.id, Constants.STATUS_RESOLVED)
            }
            binding.btnAssignProvider.setOnClickListener {
                assignSelectedProvider(complaint.id)
            }
        }

        // Resident confirmation (only when resolved and not yet confirmed)
        if (role == Constants.ROLE_RESIDENT
            && complaint.status == Constants.STATUS_RESOLVED
            && !complaint.resolvedConfirmedByResident
            && complaint.reportedBy == session.getUserId()
        ) {
            binding.llResidentConfirm.visibility = View.VISIBLE

            binding.btnConfirmYes.setOnClickListener {
                confirmResolution(complaint.id, true)
            }
            binding.btnConfirmNo.setOnClickListener {
                confirmResolution(complaint.id, false)
            }
        }
    }

    /**
     * Helper to run coroutine safely (avoids crash if activity is destroyed).
     */
    private fun viewLifecycleOwner_safe(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try { block() } catch (_: Exception) {}
        }
    }

    /**
     * Load provider details into the info card (visible to both admin & resident).
     */
    private fun loadProviderInfoCard(providerId: String) {
        binding.cardProviderInfo.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val provider = providerRepo.getProviderById(providerId)
                if (provider != null) {
                    binding.tvProviderInfoIcon.text = ComplaintAdapter.getCategoryEmoji(provider.category)
                    binding.tvProviderInfoName.text = provider.name
                    binding.tvProviderInfoCategory.text = provider.category
                    binding.tvProviderInfoPhone.text = "📞 ${provider.phone}"
                } else {
                    binding.cardProviderInfo.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.cardProviderInfo.visibility = View.GONE
            }
        }
    }

    /**
     * Remove the assigned provider from a complaint.
     */
    private fun removeProvider(complaintId: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove Provider")
            .setMessage("Are you sure you want to remove the assigned provider?")
            .setPositiveButton("Remove") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    try {
                        repo.assignProvider(complaintId, "")  // clear provider
                        Toast.makeText(this@ComplaintDetailActivity, "Provider removed", Toast.LENGTH_SHORT).show()
                        loadComplaint()
                    } catch (e: Exception) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@ComplaintDetailActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Load available service providers into the assign spinner.
     */
    private fun loadProvidersForSpinner(complaint: Complaint) {
        lifecycleScope.launch {
            try {
                providerList = providerRepo.getProvidersByCommunity(complaint.communityId)
                if (providerList.isEmpty()) {
                    binding.spinnerProvider.visibility = View.GONE
                    binding.btnAssignProvider.visibility = View.GONE
                    return@launch
                }

                val displayNames = listOf("-- Select Provider --") +
                    providerList.map { "${it.name} (${it.category})" }

                val adapter = ArrayAdapter(
                    this@ComplaintDetailActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    displayNames
                )
                binding.spinnerProvider.adapter = adapter

                // Pre-select if already assigned
                if (complaint.assignedProvider.isNotBlank()) {
                    val index = providerList.indexOfFirst { it.id == complaint.assignedProvider }
                    if (index >= 0) binding.spinnerProvider.setSelection(index + 1)
                }
            } catch (e: Exception) {
                // No providers available — hide spinner
                binding.spinnerProvider.visibility = View.GONE
                binding.btnAssignProvider.visibility = View.GONE
            }
        }
    }

    /**
     * Assign the selected provider to this complaint.
     */
    private fun assignSelectedProvider(complaintId: String) {
        val selectedIndex = binding.spinnerProvider.selectedItemPosition
        if (selectedIndex <= 0 || selectedIndex > providerList.size) {
            Toast.makeText(this, "Please select a provider", Toast.LENGTH_SHORT).show()
            return
        }

        val provider = providerList[selectedIndex - 1]
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                repo.assignProvider(complaintId, provider.id)
                Toast.makeText(
                    this@ComplaintDetailActivity,
                    "Assigned ${provider.name}",
                    Toast.LENGTH_SHORT
                ).show()
                loadComplaint() // Reload UI
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ComplaintDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyStatusStyle(status: String) {
        when (status) {
            Constants.STATUS_PENDING -> {
                binding.tvStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_status_pending)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_pending_text))
            }
            Constants.STATUS_IN_PROGRESS -> {
                binding.tvStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_status_in_progress)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_in_progress_text))
            }
            Constants.STATUS_RESOLVED -> {
                binding.tvStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_status_resolved)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_resolved_text))
            }
        }
    }

    private fun buildTimeline(complaint: Complaint) {
        binding.llTimeline.removeAllViews()

        val steps = listOf(
            "Submitted" to true,
            "In Progress" to (complaint.status == Constants.STATUS_IN_PROGRESS || complaint.status == Constants.STATUS_RESOLVED),
            "Resolved" to (complaint.status == Constants.STATUS_RESOLVED)
        )

        for ((label, reached) in steps) {
            val tv = TextView(this).apply {
                text = if (reached) "✅  $label" else "⬜  $label"
                textSize = 15f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (reached) R.color.status_resolved_text else R.color.text_secondary
                    )
                )
                setPadding(0, 8, 0, 8)
            }
            binding.llTimeline.addView(tv)
        }
    }

    private fun updateStatus(complaintId: String, newStatus: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                repo.updateStatus(complaintId, newStatus)
                Toast.makeText(this@ComplaintDetailActivity, "Status updated to: $newStatus", Toast.LENGTH_SHORT).show()
                loadComplaint() // Reload UI
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ComplaintDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmResolution(complaintId: String, confirmed: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                repo.confirmResolution(complaintId, confirmed)
                val msg = if (confirmed) "Thanks for confirming!" else "Complaint reopened."
                Toast.makeText(this@ComplaintDetailActivity, msg, Toast.LENGTH_SHORT).show()
                loadComplaint()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ComplaintDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
