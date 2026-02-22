package com.example.smartneighborhoodhelper.ui.fragments.resident
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.databinding.FragmentNotificationsBinding
import com.example.smartneighborhoodhelper.databinding.ItemNotificationBinding
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintAdapter
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity
import com.example.smartneighborhoodhelper.util.Constants
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
/**
 * NotificationsFragment - Shows recent complaint updates as a notification-like list.
 *
 * This displays all complaints that have been updated (status changed, provider assigned, etc.)
 * sorted by most recent update first. Tapping a notification opens the complaint detail.
 */
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val repo = ComplaintRepository()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        loadNotifications()
    }
    override fun onResume() {
        super.onResume()
        loadNotifications()
    }
    private fun loadNotifications() {
        val session = SessionManager(requireContext())
        val userId = session.getUserId() ?: return
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val complaints = repo.getComplaintsByUser(userId)
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                // Show complaints that have been updated (status != Pending or has provider)
                val notifications = complaints
                    .filter { it.updatedAt > it.createdAt || it.status != Constants.STATUS_PENDING || it.assignedProvider.isNotBlank() }
                    .sortedByDescending { it.updatedAt }
                if (notifications.isEmpty()) {
                    // Show all recent complaints as notifications (even new ones)
                    showNotifications(complaints.take(20))
                } else {
                    showNotifications(notifications.take(20))
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showNotifications(complaints: List<Complaint>) {
        if (complaints.isEmpty()) {
            binding.llEmpty.visibility = View.VISIBLE
            binding.rvNotifications.visibility = View.GONE
        } else {
            binding.llEmpty.visibility = View.GONE
            binding.rvNotifications.visibility = View.VISIBLE
            binding.rvNotifications.adapter = NotificationListAdapter(complaints) { complaint ->
                val intent = Intent(requireContext(), ComplaintDetailActivity::class.java)
                intent.putExtra(Constants.EXTRA_COMPLAINT_ID, complaint.id)
                startActivity(intent)
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // ── Inner adapter for notification items ──
    inner class NotificationListAdapter(
        private val items: List<Complaint>,
        private val onClick: (Complaint) -> Unit
    ) : RecyclerView.Adapter<NotificationListAdapter.VH>() {
        inner class VH(val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val b = holder.b
            // Icon based on status
            b.tvNotifIcon.text = when (c.status) {
                Constants.STATUS_PENDING -> "\u23F3"     // Hourglass
                Constants.STATUS_IN_PROGRESS -> "\uD83D\uDD27"  // Wrench
                Constants.STATUS_RESOLVED -> "\u2705"    // Check mark
                else -> "\uD83D\uDD14"                   // Bell
            }
            // Title
            b.tvNotifTitle.text = when {
                c.status == Constants.STATUS_RESOLVED -> "Complaint Resolved"
                c.status == Constants.STATUS_IN_PROGRESS -> "Complaint In Progress"
                c.assignedProvider.isNotBlank() -> "Provider Assigned"
                else -> "Complaint Submitted"
            }
            // Body
            b.tvNotifBody.text = "Your '${c.category}' complaint is now: ${c.status}"
            // Time ago
            b.tvNotifTime.text = getTimeAgo(c.updatedAt)
            b.root.setOnClickListener { onClick(c) }
        }
        override fun getItemCount() = items.size
        private fun getTimeAgo(timestamp: Long): String {
            if (timestamp == 0L) return ""
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
                else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            }
        }
    }
}