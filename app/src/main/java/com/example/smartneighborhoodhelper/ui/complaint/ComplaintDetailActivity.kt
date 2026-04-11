package com.example.smartneighborhoodhelper.ui.complaint

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.example.smartneighborhoodhelper.data.model.NotificationItem
import com.example.smartneighborhoodhelper.data.model.ServiceProvider
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.data.remote.repository.ProviderRepository
import com.example.smartneighborhoodhelper.data.remote.repository.CommunityRepository
import com.example.smartneighborhoodhelper.data.remote.repository.NotificationRepository
import com.example.smartneighborhoodhelper.data.remote.repository.BackendEventRepository
import com.example.smartneighborhoodhelper.databinding.ActivityComplaintDetailBinding
import com.example.smartneighborhoodhelper.databinding.DialogImagePreviewBinding
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
    private val communityRepo = CommunityRepository()
    private val notificationRepo = NotificationRepository()
    private val backendEvents = BackendEventRepository()
    private var complaintId = ""
    private var providerList = listOf<ServiceProvider>()
    private var currentProviderPhone = ""  // Stored for the Call Provider button
    private var currentComplaint: Complaint? = null

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
        currentComplaint = complaint

        // Image — supports Base64 (embedded) and URL (Firebase Storage)
        if (complaint.imageUrl.isNotBlank()) {
            binding.ivComplaintImage.visibility = View.VISIBLE

            // ✅ Tap to view full image
            binding.ivComplaintImage.setOnClickListener {
                showFullImageDialog(complaint)
            }

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
                } catch (_: Exception) {
                    binding.ivComplaintImage.visibility = View.GONE
                }
            } else {
                // URL from Firebase Storage
                Glide.with(this).load(complaint.imageUrl).fitCenter().into(binding.ivComplaintImage)
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

        // ✅ Open in Maps button (only if we have GPS coordinates)
        val hasCoords = complaint.latitude != 0.0 && complaint.longitude != 0.0
        binding.btnOpenInMaps.visibility = if (hasCoords) View.VISIBLE else View.GONE
        if (hasCoords) {
            binding.btnOpenInMaps.setOnClickListener {
                openInMaps(complaint.latitude, complaint.longitude, complaint.locationText)
            }
        }

        // Reported By — fetch user name from Firestore
        if (complaint.reportedBy.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(complaint.reportedBy).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name").orEmpty().trim()
                    val email = doc.getString("email").orEmpty().trim()

                    // Prefer name; then email; then UID
                    binding.tvReportedBy.text = when {
                        name.isNotBlank() -> name
                        email.isNotBlank() -> email
                        else -> complaint.reportedBy
                    }
                }
                .addOnFailureListener {
                    binding.tvReportedBy.text = complaint.reportedBy
                }
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

            // ✅ UX: Admin can't move to In Progress/Resolved unless provider is assigned
            binding.btnMarkInProgress.isEnabled = hasProvider
            binding.btnMarkInProgress.alpha = if (hasProvider) 1.0f else 0.45f

            binding.btnMarkResolved.isEnabled = hasProvider
            binding.btnMarkResolved.alpha = if (hasProvider) 1.0f else 0.45f

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
                if (!hasProvider) {
                    Toast.makeText(this, "Assign a service provider before marking In Progress", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                updateStatus(complaint.id, Constants.STATUS_IN_PROGRESS)
            }

            binding.btnMarkResolved.setOnClickListener {
                if (!hasProvider) {
                    Toast.makeText(this, "Assign a service provider before marking Resolved", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
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
                // ✅ Optimistic UI: hide immediately so it feels instant
                binding.llResidentConfirm.visibility = View.GONE
                confirmResolution(complaint.id, true)
            }
            binding.btnConfirmNo.setOnClickListener {
                binding.llResidentConfirm.visibility = View.GONE
                confirmResolution(complaint.id, false)
            }
        } else {
            // ensure hidden in all other cases
            binding.llResidentConfirm.visibility = View.GONE
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
     * Also wires up the Call Provider button.
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

                    // Store phone for call button
                    currentProviderPhone = provider.phone

                    // Wire up Call Provider button
                    binding.btnCallProvider.setOnClickListener {
                        callProvider(currentProviderPhone)
                    }
                } else {
                    binding.cardProviderInfo.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.cardProviderInfo.visibility = View.GONE
            }
        }
    }

    /**
     * Open the phone dialer with the provider's number.
     * Uses ACTION_DIAL (no permission needed — just opens the dialer).
     */
    private fun callProvider(phone: String) {
        if (phone.isBlank()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            if (openComplaintDetail) {
                putExtra("openComplaint", true)
                putExtra("complaintId", complaintId)
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

                        // Notify resident that provider was removed
                        val complaint = currentComplaint
                        val residentId = complaint?.reportedBy.orEmpty()
                        if (residentId.isNotBlank()) {
                            val notif = NotificationItem(
                                userId = residentId,
                                communityId = complaint?.communityId.orEmpty(),
                                complaintId = complaintId,
                                type = Constants.NOTIF_STATUS_CHANGED,
                                title = "Provider removed",
                                message = "Assigned provider was removed. Admin will reassign soon.",
                                isRead = false
                            )
                            runCatching { notificationRepo.createNotification(notif) }
                        }

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

                // Notify resident about assignment
                val complaint = currentComplaint
                val residentId = complaint?.reportedBy.orEmpty()
                if (residentId.isNotBlank()) {
                    val notif = NotificationItem(
                        userId = residentId,
                        communityId = complaint?.communityId.orEmpty(),
                        complaintId = complaintId,
                        type = Constants.NOTIF_PROVIDER_ASSIGNED,
                        title = "Provider assigned",
                        message = "${provider.name} (${provider.category}) is assigned to your complaint.",
                        isRead = false
                    )
                    runCatching { notificationRepo.createNotification(notif) }

                    // ✅ Push notification via external backend
                    launch {
                        backendEvents.complaintUpdated(
                            residentId = residentId,
                            adminId = session.getUserId().orEmpty(),
                            complaintId = complaintId,
                            communityId = complaint?.communityId.orEmpty(),
                            status = Constants.STATUS_IN_PROGRESS
                        )
                    }
                }

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

                // Notify resident about status change
                val complaint = currentComplaint
                val residentId = complaint?.reportedBy.orEmpty()
                if (residentId.isNotBlank()) {
                    val notif = NotificationItem(
                        userId = residentId,
                        communityId = complaint?.communityId.orEmpty(),
                        complaintId = complaintId,
                        type = Constants.NOTIF_STATUS_CHANGED,
                        title = "Status updated",
                        message = "Your ${complaint?.category.orEmpty()} complaint is now $newStatus.",
                        isRead = false
                    )
                    runCatching { notificationRepo.createNotification(notif) }

                    // ✅ Push notification via external backend
                    launch {
                        backendEvents.complaintUpdated(
                            residentId = residentId,
                            adminId = session.getUserId().orEmpty(),
                            complaintId = complaintId,
                            communityId = complaint?.communityId.orEmpty(),
                            status = newStatus
                        )
                    }
                }

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

                val complaint = currentComplaint
                val communityId = complaint?.communityId.orEmpty()

                // ✅ Fetch adminId once (used for in-app + push)
                val community = runCatching { communityRepo.getCommunityById(communityId) }.getOrNull()
                val adminId = community?.adminUid.orEmpty()

                // ✅ Notify admin (resident action) — appears in ADMIN Notifications tab
                if (adminId.isNotBlank()) {
                    runCatching {
                        val category = complaint?.category.orEmpty().ifBlank { "Complaint" }
                        val notif = NotificationItem(
                            userId = adminId,
                            communityId = communityId,
                            complaintId = complaintId,
                            type = if (confirmed) Constants.NOTIF_COMPLAINT_CHANGED else Constants.NOTIF_REOPENED,
                            title = if (confirmed) "Resident confirmed resolution" else "Resident reopened complaint",
                            message = if (confirmed)
                                "Resident confirmed the '$category' issue is resolved."
                            else
                                "Resident says the '$category' issue is NOT fixed. Complaint reopened.",
                            isRead = false
                        )
                        notificationRepo.createNotification(notif)
                    }

                    // ✅ Push notification via external backend (resident reopened)
                    if (!confirmed) {
                        launch {
                            backendEvents.complaintReopened(
                                adminId = adminId,
                                residentId = session.getUserId().orEmpty(),
                                complaintId = complaintId,
                                communityId = communityId
                            )
                        }
                    }
                }

                val msg = if (confirmed) "Thanks for confirming!" else "Complaint reopened."
                Toast.makeText(this@ComplaintDetailActivity, msg, Toast.LENGTH_SHORT).show()
                loadComplaint()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                // If we failed, show confirm buttons again so user can retry
                binding.llResidentConfirm.visibility = View.VISIBLE
                Toast.makeText(this@ComplaintDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Opens Google Maps (or any Maps app) at the given coordinates.
     * This helps admin locate the complaint area on a real map.
     */
    private fun openInMaps(lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label.ifBlank { "Complaint Location" })

        // Prefer google.navigation?q= for turn-by-turn, but geo: works universally.
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)

        // Preferred: Google Maps if installed
        intent.setPackage("com.google.android.apps.maps")

        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback: any map app
            val fallback = Intent(Intent.ACTION_VIEW, geoUri)
            startActivity(fallback)
        }
    }

    private fun showFullImageDialog(complaint: Complaint) {
        if (complaint.imageUrl.isBlank()) return

        val dialogBinding = DialogImagePreviewBinding.inflate(layoutInflater)

        if (complaint.imageUrl.startsWith("data:") || complaint.imageUrl.length > 500) {
            runCatching {
                val base64Str = if (complaint.imageUrl.contains(",")) complaint.imageUrl.substringAfter(",") else complaint.imageUrl
                val bytes = Base64.decode(base64Str, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                dialogBinding.ivPreview.setImageBitmap(bitmap)
            }
        } else {
            Glide.with(this).load(complaint.imageUrl).fitCenter().into(dialogBinding.ivPreview)
        }

        AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .show()
    }
}
