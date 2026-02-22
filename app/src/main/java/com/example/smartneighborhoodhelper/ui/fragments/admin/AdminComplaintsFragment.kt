package com.example.smartneighborhoodhelper.ui.fragments.admin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.Complaint
import com.example.smartneighborhoodhelper.data.remote.repository.ComplaintRepository
import com.example.smartneighborhoodhelper.databinding.FragmentComplaintListBinding
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintAdapter
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity
import com.example.smartneighborhoodhelper.util.Constants
import kotlinx.coroutines.launch

/**
 * AdminComplaintsFragment — Shows ALL complaints in the community.
 * Admin can search, filter by status, and tap to manage.
 */
class AdminComplaintsFragment : Fragment() {

    private var _binding: FragmentComplaintListBinding? = null
    private val binding get() = _binding!!
    private val repo = ComplaintRepository()
    private lateinit var adapter: ComplaintAdapter
    private var allComplaints = listOf<Complaint>()
    private var currentFilter = "All"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComplaintListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ComplaintAdapter { complaint ->
            val intent = Intent(requireContext(), ComplaintDetailActivity::class.java)
            intent.putExtra(Constants.EXTRA_COMPLAINT_ID, complaint.id)
            startActivity(intent)
        }
        binding.rvComplaints.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComplaints.adapter = adapter

        // Filter spinner
        val filters = listOf("All", Constants.STATUS_PENDING, Constants.STATUS_IN_PROGRESS, Constants.STATUS_RESOLVED)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filters)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = spinnerAdapter
        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentFilter = filters[pos]
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilters() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadComplaints()
    }

    private fun loadComplaints() {
        val communityId = SessionManager(requireContext()).getCommunityId().orEmpty()
        if (communityId.isBlank()) return

        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allComplaints = repo.getComplaintsByCommunity(communityId)
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                applyFilters()
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString().orEmpty().lowercase()
        var filtered = allComplaints

        if (currentFilter != "All") {
            filtered = filtered.filter { it.status == currentFilter }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.category.lowercase().contains(query) || it.description.lowercase().contains(query)
            }
        }

        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvComplaints.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
