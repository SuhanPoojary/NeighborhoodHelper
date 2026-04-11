package com.example.smartneighborhoodhelper.ui.fragments.resident

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.NotificationItem
import com.example.smartneighborhoodhelper.data.remote.repository.NotificationRepository
import com.example.smartneighborhoodhelper.databinding.FragmentNotificationsBinding
import com.example.smartneighborhoodhelper.databinding.ItemNotificationBinding
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
    private val notificationRepo = NotificationRepository()

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
                val notifications = notificationRepo.getNotificationsForUser(userId)
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                showNotifications(notifications)
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE

                // Firestore can throw FAILED_PRECONDITION (e.g., missing index) even when
                // cached data still loads. Avoid confusing the user with a toast.
                val msg = e.message.orEmpty()
                val isIndexError = msg.contains("FAILED_PRECONDITION", ignoreCase = true)
                        || msg.contains("requires an index", ignoreCase = true)

                if (!isIndexError) {
                    Toast.makeText(requireContext(), "Error loading: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showNotifications(items: List<NotificationItem>) {
        if (items.isEmpty()) {
            binding.llEmpty.visibility = View.VISIBLE
            binding.rvNotifications.visibility = View.GONE
        } else {
            binding.llEmpty.visibility = View.GONE
            binding.rvNotifications.visibility = View.VISIBLE
            binding.rvNotifications.adapter = NotificationListAdapter(items) { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    notificationRepo.markAsRead(item.id)
                }
                if (item.complaintId.isNotBlank()) {
                    val intent = Intent(requireContext(), ComplaintDetailActivity::class.java)
                    intent.putExtra(Constants.EXTRA_COMPLAINT_ID, item.complaintId)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Inner adapter for notification items ──
    inner class NotificationListAdapter(
        private val items: List<NotificationItem>,
        private val onClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationListAdapter.VH>() {

        inner class VH(val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val n = items[position]
            val b = holder.b

            b.tvNotifIcon.text = when (n.type) {
                Constants.NOTIF_NEW_COMPLAINT -> "📝"
                Constants.NOTIF_PROVIDER_ASSIGNED -> "🛠"
                Constants.NOTIF_STATUS_CHANGED -> "🔔"
                Constants.NOTIF_REOPENED -> "⚠"
                Constants.NOTIF_JOIN_REQUEST -> "👥"
                Constants.NOTIF_JOIN_APPROVED -> "✅"
                Constants.NOTIF_JOIN_REJECTED -> "❌"
                Constants.NOTIF_COMPLAINT_CHANGED -> "✏"
                else -> "🔔"
            }

            b.tvNotifTitle.text = n.title.ifBlank { "Update" }
            b.tvNotifBody.text = n.message.ifBlank { "Tap to view details." }
            b.tvNotifTime.text = getTimeAgo(n.createdAt)

            // Visual cue for read/unread
            b.root.alpha = if (n.isRead) 0.6f else 1.0f
            b.tvNotifTitle.setTypeface(null, if (n.isRead) Typeface.NORMAL else Typeface.BOLD)

            b.root.setOnClickListener { onClick(n) }
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