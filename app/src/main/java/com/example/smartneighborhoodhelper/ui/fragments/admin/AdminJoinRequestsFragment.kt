package com.example.smartneighborhoodhelper.ui.fragments.admin
import android.util.Log

import com.example.smartneighborhoodhelper.data.remote.api.BackendClient
import com.example.smartneighborhoodhelper.data.remote.api.JoinApprovedEvent
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.JoinRequest
import com.example.smartneighborhoodhelper.data.remote.repository.CommunityRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * AdminJoinRequestsFragment — Admin approves/declines resident join requests.
 */
class AdminJoinRequestsFragment : Fragment() {

    private val repo = CommunityRepository()

    private var rootView: View? = null
    private var progressBar: ProgressBar? = null
    private var rv: RecyclerView? = null
    private var empty: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_admin_join_requests, container, false)
        rootView = v
        progressBar = v.findViewById(R.id.progressBar)
        rv = v.findViewById(R.id.rvRequests)
        empty = v.findViewById(R.id.llEmpty)

        rv?.layoutManager = LinearLayoutManager(requireContext())

        v.findViewById<MaterialButton>(R.id.btnRefresh).setOnClickListener { loadRequests() }

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
    }

    private fun loadRequests() {
        val session = SessionManager(requireContext())
        val communityId = session.getCommunityId().orEmpty()

        if (communityId.isBlank()) {
            Toast.makeText(requireContext(), "No community found for admin.", Toast.LENGTH_LONG).show()
            return
        }

        progressBar?.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = repo.getPendingJoinRequests(communityId)
                progressBar?.visibility = View.GONE

                if (list.isEmpty()) {
                    empty?.visibility = View.VISIBLE
                    rv?.visibility = View.GONE
                } else {
                    empty?.visibility = View.GONE
                    rv?.visibility = View.VISIBLE
                    rv?.adapter = JoinRequestAdapter(list)
                }
            } catch (e: Exception) {
                progressBar?.visibility = View.GONE
                Toast.makeText(requireContext(), e.message ?: "Failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmApprove(request: JoinRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Approve request")
            .setMessage("Approve ${request.residentName} to join this community?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Approve") { _, _ -> approve(request) }
            .show()
    }

    private fun confirmDecline(request: JoinRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Decline request")
            .setMessage("Decline ${request.residentName}'s request?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Decline") { _, _ -> decline(request) }
            .show()
    }

    private fun approve(request: JoinRequest) {
        progressBar?.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.approveJoinRequest(request.id)

                Log.d("DEBUG_UID", "Approve UID: ${request.residentUid}")

                if (request.residentUid.isNotBlank()) {
                    BackendClient.api.joinApproved(
                        JoinApprovedEvent(
                            residentId = request.residentUid,
                            communityId = request.communityId
                        )
                    )
                } else {
                    Log.e("DEBUG_UID", "Resident UID EMPTY ❌")
                }

                Toast.makeText(requireContext(), "Approved", Toast.LENGTH_SHORT).show()
                loadRequests()

            } catch (e: Exception) {
                progressBar?.visibility = View.GONE
                Toast.makeText(requireContext(), e.message ?: "Failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun decline(request: JoinRequest) {
        progressBar?.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 🔥 STEP 1: Firestore update
                repo.declineJoinRequest(request.id)

                // 🔥 STEP 2: DEBUG CHECK (VERY IMPORTANT)
                Log.d("DEBUG_UID", "Decline UID: ${request.residentUid}")

                // 🔥 STEP 3: BACKEND CALL
                if (request.residentUid.isNotBlank()) {
                    BackendClient.api.joinDeclined(
                        JoinApprovedEvent(
                            residentId = request.residentUid,   // 🔥 IMPORTANT
                            communityId = request.communityId
                        )
                    )
                } else {
                    Log.e("DEBUG_UID", "Resident UID EMPTY ❌")
                }

                Toast.makeText(requireContext(), "Declined", Toast.LENGTH_SHORT).show()
                loadRequests()

            } catch (e: Exception) {
                progressBar?.visibility = View.GONE
                Toast.makeText(requireContext(), e.message ?: "Failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
        progressBar = null
        rv = null
        empty = null
    }

    private inner class JoinRequestAdapter(
        private val items: List<JoinRequest>
    ) : RecyclerView.Adapter<JoinRequestAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvEmail: TextView = v.findViewById(R.id.tvEmail)
            val tvPhone: TextView = v.findViewById(R.id.tvPhone)
            val tvStatus: TextView = v.findViewById(R.id.tvStatus)
            val btnApprove: MaterialButton = v.findViewById(R.id.btnApprove)
            val btnDecline: MaterialButton = v.findViewById(R.id.btnDecline)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_join_request, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = items[position]
            holder.tvName.text = r.residentName
            holder.tvEmail.text = r.residentEmail.ifBlank { "(email not provided)" }
            holder.tvPhone.text = r.residentPhone.ifBlank { "(phone not provided)" }
            holder.tvStatus.text = r.status
            holder.btnApprove.setOnClickListener { confirmApprove(r) }
            holder.btnDecline.setOnClickListener { confirmDecline(r) }
        }
    }
}
