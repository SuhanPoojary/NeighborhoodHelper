package com.example.smartneighborhoodhelper.ui.fragments.shared

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.databinding.FragmentProfileBinding
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager(requireContext())

        // Populate user info
        val name = session.getUserName().orEmpty()
        binding.tvName.text = name.ifBlank { "User" }
        binding.tvAvatar.text = if (name.isNotBlank()) name.first().uppercase() else "U"
        binding.tvEmail.text = FirebaseAuth.getInstance().currentUser?.email ?: "—"
        binding.tvRole.text = (session.getUserRole() ?: "user").replaceFirstChar { it.uppercase() }

        // Settings options — stubs for now
        binding.optEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile — coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.optChangePassword.setOnClickListener {
            Toast.makeText(requireContext(), "Change Password — coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.optHelp.setOnClickListener {
            Toast.makeText(requireContext(), "Help & Support — coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.optAbout.setOnClickListener {
            Toast.makeText(requireContext(), "Smart Neighborhood Helper v1.0", Toast.LENGTH_SHORT).show()
        }

        // Logout with confirmation dialog
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout") { _, _ ->
                    session.clearSession()
                    FirebaseAuth.getInstance().signOut()

                    val intent = Intent(requireContext(), RoleSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    activity?.finish()
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

