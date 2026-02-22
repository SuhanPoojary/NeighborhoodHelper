package com.example.smartneighborhoodhelper.ui.complaint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.databinding.ActivityReportComplaintBinding
import com.example.smartneighborhoodhelper.util.Constants
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
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
    private var currentStep = 0
    private var selectedCategory = ""
    private var photoUri: Uri? = null
    private var tempPhotoUri: Uri? = null
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
    // ── Permission launcher (camera only now) ──
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) launchCamera()
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
     * Compress and convert an image URI to a Base64 string.
     * Resizes to max 800px width and compresses to ~70% JPEG quality.
     * Result is typically 100-300KB which fits in a Firestore document (max 1MB).
     */
    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            // Resize if too large (max 800px width)
            val maxWidth = 800
            val bitmap = if (originalBitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / originalBitmap.width
                val newHeight = (originalBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, maxWidth, newHeight, true)
            } else {
                originalBitmap
            }
            // Compress to JPEG at 70% quality
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
                    // If "Other" is selected, require custom text
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
                    val loc = binding.etLocation.text?.toString().orEmpty().trim()
                    if (loc.isBlank()) {
                        Toast.makeText(this, "Please enter the location", Toast.LENGTH_SHORT).show()
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
        binding.tvReviewLocation.text = binding.etLocation.text?.toString().orEmpty().ifBlank { "Not provided" }
        binding.tvReviewPriority.text = if (binding.rbUrgent.isChecked) "\uD83D\uDD34 Urgent" else "\uD83D\uDFE2 Normal"
        if (photoUri != null) {
            Glide.with(this).load(photoUri).centerCrop().into(binding.ivReviewPhoto)
        }
    }
    private fun submitComplaint() {
        val userId = session.getUserId() ?: return
        val communityId = session.getCommunityId() ?: return
        val description = binding.etDescription.text?.toString().orEmpty().trim()
        val manualLocation = binding.etLocation.text?.toString().orEmpty().trim()
        showLoading(true)
        lifecycleScope.launch {
            try {
                // Convert image to Base64 (no Firebase Storage needed!)
                var imageBase64 = ""
                if (photoUri != null) {
                    imageBase64 = uriToBase64(photoUri!!) ?: ""
                }
                val complaint = Complaint(
                    title = selectedCategory,
                    description = description,
                    category = selectedCategory,
                    imageUrl = imageBase64,
                    latitude = 0.0,
                    longitude = 0.0,
                    locationText = manualLocation,
                    status = Constants.STATUS_PENDING,
                    reportedBy = userId,
                    communityId = communityId
                )
                val id = repo.reportComplaint(complaint)
                showLoading(false)
                Toast.makeText(
                    this@ReportComplaintActivity,
                    "Complaint submitted successfully!",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(this@ReportComplaintActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@ReportComplaintActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnNext.isEnabled = !show
        binding.btnPrevious.isEnabled = !show
    }
}
