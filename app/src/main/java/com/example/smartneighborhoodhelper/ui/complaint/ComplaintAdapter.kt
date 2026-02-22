package com.example.smartneighborhoodhelper.ui.complaint
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.databinding.ItemComplaintBinding
import com.example.smartneighborhoodhelper.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
/**
 * ComplaintAdapter — RecyclerView adapter for complaint list items.
 * Uses ListAdapter + DiffUtil for efficient updates.
 * Displays a clean card with category emoji, description, image preview, and status badge.
 */
class ComplaintAdapter(
    private val onClick: (Complaint) -> Unit
) : ListAdapter<Complaint, ComplaintAdapter.VH>(DIFF) {
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Complaint>() {
            override fun areItemsTheSame(a: Complaint, b: Complaint) = a.id == b.id
            override fun areContentsTheSame(a: Complaint, b: Complaint) = a == b
        }
        /** Map category names to emoji icons for a clean visual look */
        fun getCategoryEmoji(category: String): String {
            return when (category.lowercase()) {
                "garbage"    -> "\uD83D\uDDD1\uFE0F"   // Wastebasket
                "water"      -> "\uD83D\uDCA7"          // Droplet
                "electrical" -> "\u26A1"                 // Zap
                "road"       -> "\uD83D\uDEE3\uFE0F"   // Road
                "drainage"   -> "\uD83C\uDF0A"          // Wave
                "plumbing"   -> "\uD83D\uDD27"          // Wrench
                "security"   -> "\uD83D\uDD12"          // Lock
                "parking"    -> "\uD83C\uDD7F\uFE0F"   // P button
                "noise"      -> "\uD83D\uDD0A"          // Speaker loud
                "other"      -> "\uD83D\uDCCB"          // Clipboard
                else         -> "\uD83D\uDCCB"          // Clipboard
            }
        }
    }
    inner class VH(private val b: ItemComplaintBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(complaint: Complaint) {
            val ctx = b.root.context
            // Category emoji icon
            b.tvCategoryIcon.text = getCategoryEmoji(complaint.category)
            // Category name & description
            b.tvCategory.text = complaint.category
            b.tvDescription.text = complaint.description.ifBlank { "No description provided" }
            b.tvDate.text = getTimeAgo(complaint.createdAt)
            b.tvStatus.text = complaint.status
            // Status badge colors — soft pastels
            when (complaint.status) {
                Constants.STATUS_PENDING -> {
                    b.tvStatus.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_pending)
                    b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_pending_text))
                }
                Constants.STATUS_IN_PROGRESS -> {
                    b.tvStatus.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_in_progress)
                    b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_in_progress_text))
                }
                Constants.STATUS_RESOLVED -> {
                    b.tvStatus.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_resolved)
                    b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_resolved_text))
                }
            }
            // Image — supports both URL (Firebase Storage) and Base64 (embedded)
            if (complaint.imageUrl.isNotBlank()) {
                b.cardImage.visibility = View.VISIBLE
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
                        b.ivThumb.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        b.cardImage.visibility = View.GONE
                    }
                } else {
                    // URL from Firebase Storage
                    Glide.with(ctx).load(complaint.imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.bg_input_white)
                        .into(b.ivThumb)
                }
            } else {
                b.cardImage.visibility = View.GONE
            }
            // Location (manual text)
            if (complaint.locationText.isNotBlank()) {
                b.llLocation.visibility = View.VISIBLE
                b.tvLocation.text = complaint.locationText
            } else {
                b.llLocation.visibility = View.GONE
            }
            b.root.setOnClickListener { onClick(complaint) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemComplaintBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
    /** Helper: convert timestamp to "2h ago", "3d ago" etc. */
    private fun getTimeAgo(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
