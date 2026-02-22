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
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.databinding.FragmentResidentDashboardBinding
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintAdapter
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity
import com.example.smartneighborhoodhelper.ui.complaint.ReportComplaintActivity
import com.example.smartneighborhoodhelper.util.Constants
import kotlinx.coroutines.launch

/**
 * ResidentDashboardFragment — placeholder for now.
 * Feature 3 will replace this with the resident dashboard UI.
 */
class ResidentDashboardFragment : Fragment() {

    private var _binding: FragmentResidentDashboardBinding? = null
    private val binding get() = _binding!!
    private val repo = ComplaintRepository()
    private lateinit var adapter: ComplaintAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResidentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager(requireContext())
        binding.tvWelcome.text = "Welcome, ${session.getUserName() ?: "Resident"}!"

        // Setup RecyclerView
        adapter = ComplaintAdapter { complaint ->
            val intent = Intent(requireContext(), ComplaintDetailActivity::class.java)
            intent.putExtra(Constants.EXTRA_COMPLAINT_ID, complaint.id)
            startActivity(intent)
        }
        binding.rvComplaints.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComplaints.adapter = adapter

        // Report Issue button
        binding.btnReportIssue.setOnClickListener {
            startActivity(Intent(requireContext(), ReportComplaintActivity::class.java))
        }

        loadCommunityInfo()
    }

    override fun onResume() {
        super.onResume()
        loadComplaints()
    }

    private fun loadComplaints() {
        val session = SessionManager(requireContext())
        val userId = session.getUserId().orEmpty()
        if (userId.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val complaints = repo.getComplaintsByUser(userId)
                    .sortedWith(compareBy<Complaint> {
                        when (it.status) {
                            Constants.STATUS_PENDING -> 0
                            Constants.STATUS_IN_PROGRESS -> 1
                            Constants.STATUS_RESOLVED -> 2
                            else -> 3
                        }
                    }.thenByDescending { it.createdAt })
                if (_binding == null) return@launch

                adapter.submitList(complaints)

                val total = complaints.size
                val pending = complaints.count { it.status == Constants.STATUS_PENDING }
                val resolved = complaints.count { it.status == Constants.STATUS_RESOLVED }

                binding.tvTotalCount.text = total.toString()
                binding.tvPendingCount.text = pending.toString()
                binding.tvResolvedCount.text = resolved.toString()

                if (complaints.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvComplaints.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvComplaints.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(requireContext(), "Error loading complaints: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCommunityInfo() {
        val session = SessionManager(requireContext())
        val communityId = session.getCommunityId().orEmpty()
        if (communityId.isBlank()) return

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("communities").document(communityId).get()
            .addOnSuccessListener { snap ->
                if (_binding == null) return@addOnSuccessListener
                if (snap.exists()) {
                    binding.tvCommunityName.text = snap.getString("name") ?: "My Community"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
