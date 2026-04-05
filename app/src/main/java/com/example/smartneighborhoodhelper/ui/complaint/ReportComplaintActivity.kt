package com.example.smartneighborhoodhelper.ui.complaint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.model.NotificationItem
import com.example.smartneighborhoodhelper.data.remote.repository.CommunityRepository
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.data.remote.repository.NotificationRepository
import com.example.smartneighborhoodhelper.databinding.ActivityReportComplaintBinding
import com.example.smartneighborhoodhelper.util.Constants
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * ReportComplaintActivity - 3-step wizard for reporting an issue.
 *
 * Step 1: Select category
 * Step 2: Add photo, location (manual text), description, priority
 * Step 3: Review & submit
 *
 * IMAGE STRATEGY:
 *   Images are compressed to ~200KB, converted to Base64, and stored
 *   directly in Firestore. This avoids needing Firebase Storage (paid plan)
 *   and works reliably on any Firebase plan.
 *
 * LOCATION STRATEGY:
 *   User types their location manually (e.g. "Block A, Near Gate 2").
 *   More reliable than GPS which often fails indoors / on emulators.
 */
class ReportComplaintActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportComplaintBinding
    private lateinit var session: SessionManager
    private val repo = ComplaintRepository()
    private val communityRepo = CommunityRepository()
    private val notificationRepo = NotificationRepository()

    private var currentStep = 0
    private var selectedCategory = ""

    private var photoUri: Uri? = null
    private var tempPhotoUri: Uri? = null

    // ✅ GPS fields (stored for Firestore; user sees only address text)
    private var gpsLatitude: Double? = null
    private var gpsLongitude: Double? = null
    private var gpsAddressText: String = ""

    // ── Camera launcher ──
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            photoUri = tempPhotoUri
            binding.ivPhoto.setImageURI(photoUri)
            binding.ivPhoto.visibility = View.VISIBLE
            binding.llAddPhoto.visibility = View.GONE
        }
    }

    // ── Gallery launcher ──
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            binding.ivPhoto.setImageURI(photoUri)
            binding.ivPhoto.visibility = View.VISIBLE
            binding.llAddPhoto.visibility = View.GONE
        }
    }

    // ── Permission launcher (camera + location) ──
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val fineLocGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (cameraGranted) {
            launchCamera()
        }

        if (fineLocGranted) {
            fetchAndFillCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportComplaintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupCategoryChips()
        setupPhotoClick()
        setupButtons()
        setupPriorityColors()
        setupLocationUi()
    }

    private fun setupLocationUi() {
        // Button to fetch GPS location and reverse-geocode into a readable address
        binding.btnUseLocation.setOnClickListener {
            requestLocationAndFetch()
        }
    }

    private fun requestLocationAndFetch() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            // Request only location; we keep camera permission request for photo flow.
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        fetchAndFillCurrentLocation()
    }

    /**
     * Fetch last known location and reverse-geocode it to a readable address.
     * We do NOT show coordinates to the user, but we store them for Firestore.
     */
    private fun fetchAndFillCurrentLocation() {
        binding.btnUseLocation.isEnabled = false
        binding.btnUseLocation.text = "Fetching…"

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val locationEnabled = LocationManagerCompat.isLocationEnabled(lm)
        if (!locationEnabled) {
            resetLocationButton()
            Toast.makeText(this, "Please turn ON Location (GPS) and try again.", Toast.LENGTH_LONG).show()
            return
        }

        val fused = LocationServices.getFusedLocationProviderClient(this)

        fun handleLocation(lat: Double, lng: Double) {
            gpsLatitude = lat
            gpsLongitude = lng
            lifecycleScope.launch {
                val address = reverseGeocode(lat, lng)
                gpsAddressText = address
                binding.etAutoLocation.setText(address)
                resetLocationButton(done = true)
            }
        }

        try {
            val cts = CancellationTokenSource()

            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        handleLocation(loc.latitude, loc.longitude)
                        return@addOnSuccessListener
                    }

                    // Fallback: try lastLocation
                    fused.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) {
                                handleLocation(last.latitude, last.longitude)
                            } else {
                                resetLocationButton()
                                Toast.makeText(
                                    this,
                                    "Location not available. Go outdoors / enable GPS & Internet, then try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            resetLocationButton()
                            Toast.makeText(this, e.message ?: "Failed to get location", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    resetLocationButton()
                    Toast.makeText(this, e.message ?: "Failed to get location", Toast.LENGTH_LONG).show()
                }

        } catch (_: SecurityException) {
            resetLocationButton()
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetLocationButton(done: Boolean = false) {
        binding.btnUseLocation.isEnabled = true
        binding.btnUseLocation.text = if (done) "Update" else "Use Current"
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@ReportComplaintActivity, Locale.getDefault())
                val list = geocoder.getFromLocation(lat, lng, 1)
                val first = list?.firstOrNull()

                // Build a clean, readable line (no coordinates)
                val locality = first?.locality.orEmpty()
                val subLocality = first?.subLocality.orEmpty()
                val adminArea = first?.adminArea.orEmpty()
                val feature = first?.featureName.orEmpty()

                val parts = listOf(feature, subLocality, locality, adminArea)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (parts.isNotEmpty()) parts.joinToString(", ") else "Current location detected"
            } catch (_: Exception) {
                "Current location detected"
            }
        }
    }

    // ── Category Chips (Step 1) ──
    private fun setupCategoryChips() {
        val categories = Constants.COMPLAINT_CATEGORIES
        binding.llCategories.removeAllViews()
        for (cat in categories) {
            val chip = TextView(this).apply {
                text = cat
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                background = ContextCompat.getDrawable(context, R.drawable.bg_category_chip)
                setPadding(48, 36, 48, 36)
                isSelected = false
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 12
                layoutParams = params
                setOnClickListener {
                    for (i in 0 until binding.llCategories.childCount) {
                        val child = binding.llCategories.getChildAt(i) as TextView
                        child.isSelected = false
                        child.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    }
                    isSelected = true
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                    selectedCategory = cat

                    // Show custom input when "Other" is selected
                    binding.etCustomCategory.visibility = if (cat == "Other") View.VISIBLE else View.GONE
                }
            }
            binding.llCategories.addView(chip)
        }
    }

    // ── Priority radio button colors ──
    private fun setupPriorityColors() {
        binding.rgPriority.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbUrgent) {
                binding.rbUrgent.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                binding.rbNormal.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                binding.rbNormal.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                binding.rbUrgent.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
    }

    // ── Photo picker ──
    private fun setupPhotoClick() {
        binding.framePhoto.setOnClickListener { showPhotoOptions() }
        binding.ivPhoto.setOnClickListener { showPhotoOptions() }
    }

    private fun showPhotoOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        val file = File(cacheDir, "complaint_${System.currentTimeMillis()}.jpg")
        tempPhotoUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        cameraLauncher.launch(tempPhotoUri!!)
    }

    /**
     * Convert image Uri to a small Base64 thumbnail safe for Firestore.
     * Firestore has a ~1MB document limit, so we must keep this small.
     */
    private fun uriToBase64Thumbnail(uri: Uri, maxWidth: Int = 480, jpegQuality: Int = 45): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val bitmap = if (originalBitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / originalBitmap.width
                val newHeight = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, maxWidth, newHeight, true)
            } else {
                originalBitmap
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
            val bytes = stream.toByteArray()

            // Safety: avoid huge strings (Firestore doc limit). About 450KB raw bytes -> ~600KB base64.
            if (bytes.size > 450_000) return null

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxWidth = 800
            val bitmap = if (originalBitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / originalBitmap.width
                val newHeight = (originalBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, maxWidth, newHeight, true)
            } else {
                originalBitmap
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val bytes = stream.toByteArray()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    // ── Step navigation buttons ──
    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            when (currentStep) {
                0 -> {
                    if (selectedCategory.isBlank()) {
                        Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (selectedCategory == "Other") {
                        val custom = binding.etCustomCategory.text?.toString().orEmpty().trim()
                        if (custom.isBlank()) {
                            binding.etCustomCategory.error = "Please specify the issue type"
                            return@setOnClickListener
                        }
                        selectedCategory = custom
                    }
                    goToStep(1)
                }

                1 -> {
                    val desc = binding.etDescription.text?.toString().orEmpty().trim()
                    if (desc.isBlank()) {
                        Toast.makeText(this, "Please add a description", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // ✅ Require either GPS auto-location OR manual landmark
                    val autoLoc = binding.etAutoLocation.text?.toString().orEmpty().trim()
                    val landmark = binding.etLocation.text?.toString().orEmpty().trim()
                    if (autoLoc.isBlank() && landmark.isBlank()) {
                        Toast.makeText(this, "Please add location (Use Current Location or type a landmark)", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    populateReview()
                    goToStep(2)
                }

                2 -> {
                    if (!binding.cbConfirm.isChecked) {
                        Toast.makeText(this, "Please confirm the details", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    submitComplaint()
                }
            }
        }

        binding.btnPrevious.setOnClickListener {
            if (currentStep > 0) goToStep(currentStep - 1)
        }
    }

    private fun goToStep(step: Int) {
        currentStep = step
        binding.viewFlipper.displayedChild = step

        val steps = listOf(binding.tvStep1, binding.tvStep2, binding.tvStep3)
        val lines = listOf(binding.line1, binding.line2)
        for (i in steps.indices) {
            if (i <= step) {
                steps[i].background = ContextCompat.getDrawable(this, R.drawable.bg_circle_blue)
                steps[i].setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                steps[i].background = ContextCompat.getDrawable(this, R.drawable.bg_circle_green)
                steps[i].setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
        for (i in lines.indices) {
            lines[i].setBackgroundColor(ContextCompat.getColor(this, if (i < step) R.color.primary else R.color.divider))
        }

        binding.tvStepLabel.text = when (step) {
            0 -> "Step 1: Select Category"
            1 -> "Step 2: Add Details"
            2 -> "Step 3: Review & Submit"
            else -> ""
        }

        binding.btnPrevious.visibility = if (step > 0) View.VISIBLE else View.GONE
        binding.btnNext.text = if (step == 2) "Submit Complaint" else "Next"
    }

    private fun populateReview() {
        binding.tvReviewCategory.text = selectedCategory
        binding.tvReviewDescription.text = binding.etDescription.text?.toString().orEmpty()

        val autoLoc = binding.etAutoLocation.text?.toString().orEmpty().trim()
        val landmark = binding.etLocation.text?.toString().orEmpty().trim()
        binding.tvReviewLocation.text = when {
            landmark.isNotBlank() && autoLoc.isNotBlank() -> "$landmark\n($autoLoc)"
            landmark.isNotBlank() -> landmark
            autoLoc.isNotBlank() -> autoLoc
            else -> "Not provided"
        }

        binding.tvReviewPriority.text = if (binding.rbUrgent.isChecked) "\uD83D\uDD34 Urgent" else "\uD83D\uDFE2 Normal"
        if (photoUri != null) {
            Glide.with(this).load(photoUri).centerCrop().into(binding.ivReviewPhoto)
        }
    }

    private fun submitComplaint() {
        val userId = session.getUserId() ?: return
        val communityId = session.getCommunityId() ?: return

        val description = binding.etDescription.text?.toString().orEmpty().trim()
        val landmark = binding.etLocation.text?.toString().orEmpty().trim()
        val autoLoc = binding.etAutoLocation.text?.toString().orEmpty().trim()

        // What we store as display location text
        val locationText = when {
            landmark.isNotBlank() && autoLoc.isNotBlank() -> "$landmark\n($autoLoc)"
            landmark.isNotBlank() -> landmark
            autoLoc.isNotBlank() -> autoLoc
            else -> ""
        }

        val lat = gpsLatitude ?: 0.0
        val lng = gpsLongitude ?: 0.0

        showLoading(true)
        lifecycleScope.launch {
            try {
                var imageBase64 = ""
                if (photoUri != null) {
                    val thumb = uriToBase64Thumbnail(photoUri!!)
                    if (thumb == null) {
                        showLoading(false)
                        Toast.makeText(
                            this@ReportComplaintActivity,
                            "Image is too large. Please choose a smaller photo (or take a new one).",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    imageBase64 = thumb
                }

                val complaint = Complaint(
                    title = selectedCategory,
                    description = description,
                    category = selectedCategory,
                    imageUrl = imageBase64,
                    latitude = lat,
                    longitude = lng,
                    locationText = locationText,
                    status = Constants.STATUS_PENDING,
                    reportedBy = userId,
                    communityId = communityId
                )

                val id = repo.reportComplaint(complaint)

                // ✅ Create an in-app notification for the RESIDENT too (so Notifications tab isn't empty)
                runCatching {
                    val residentNotif = NotificationItem(
                        userId = userId,
                        communityId = communityId,
                        complaintId = id,
                        type = Constants.NOTIF_NEW_COMPLAINT,
                        title = "Complaint submitted",
                        message = "Your ${selectedCategory} complaint was submitted successfully.",
                        isRead = false
                    )
                    notificationRepo.createNotification(residentNotif)
                }

                // Notify admin about new complaint
                val community = communityRepo.getCommunityById(communityId)
                val adminId = community?.adminUid.orEmpty()
                if (adminId.isNotBlank()) {
                    val notif = NotificationItem(
                        userId = adminId,
                        communityId = communityId,
                        complaintId = id,
                        type = Constants.NOTIF_NEW_COMPLAINT,
                        title = "New complaint submitted",
                        message = "New ${selectedCategory} complaint reported.",
                        isRead = false
                    )
                    notificationRepo.createNotification(notif)
                }

                showLoading(false)
                Toast.makeText(this@ReportComplaintActivity, "Complaint submitted successfully!", Toast.LENGTH_LONG).show()

                val intent = Intent(this@ReportComplaintActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                showLoading(false)

                // ✅ User-friendly error, no raw Firebase exception noise
                val msg = when {
                    (e.message ?: "").contains("PERMISSION_DENIED", ignoreCase = true) ->
                        "Permission denied. Please login again and retry."

                    (e.message ?: "").contains("too large", ignoreCase = true) ||
                        (e.message ?: "").contains("Resource exhausted", ignoreCase = true) ->
                        "Image is too large. Please select a smaller photo."

                    else -> "Failed to submit complaint. Please try again."
                }

                Toast.makeText(this@ReportComplaintActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnNext.isEnabled = !show
        binding.btnPrevious.isEnabled = !show
    }
}
