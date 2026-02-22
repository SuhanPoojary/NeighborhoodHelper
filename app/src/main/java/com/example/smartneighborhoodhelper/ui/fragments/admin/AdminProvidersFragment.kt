package com.example.smartneighborhoodhelper.ui.fragments.admin
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.model.ServiceProvider
import com.example.smartneighborhoodhelper.data.remote.repository.ProviderRepository
import com.example.smartneighborhoodhelper.databinding.FragmentAdminProvidersBinding
import com.example.smartneighborhoodhelper.ui.provider.ProviderAdapter
import com.example.smartneighborhoodhelper.util.Constants
import kotlinx.coroutines.launch
/**
 * AdminProvidersFragment - Manages service providers (CRUD).
 *
 * Admin can:
 *   - View all providers for the community
 *   - Add a new provider (name, phone, skill)
 *   - Edit an existing provider
 *   - Delete a provider
 *   - Tap to call a provider
 */
class AdminProvidersFragment : Fragment() {
    private var _binding: FragmentAdminProvidersBinding? = null
    private val binding get() = _binding!!
    private val repo = ProviderRepository()
    private lateinit var adapter: ProviderAdapter
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminProvidersBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup RecyclerView
        adapter = ProviderAdapter(
            onEdit = { provider -> showAddEditDialog(provider) },
            onDelete = { provider -> confirmDelete(provider) },
            onCall = { provider -> callProvider(provider) }
        )
        binding.rvProviders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProviders.adapter = adapter
        // Add Provider button
        binding.btnAddProvider.setOnClickListener {
            showAddEditDialog(null)
        }
    }
    override fun onResume() {
        super.onResume()
        loadProviders()
    }
    /**
     * Load all providers for this community from Firestore.
     */
    private fun loadProviders() {
        val communityId = SessionManager(requireContext()).getCommunityId().orEmpty()
        if (communityId.isBlank()) return
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val providers = repo.getProvidersByCommunity(communityId)
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                adapter.submitList(providers)
                binding.tvProviderCount.text = "${providers.size} provider${if (providers.size != 1) "s" else ""}"
                if (providers.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvProviders.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvProviders.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /**
     * Show dialog to add or edit a service provider.
     * If provider is null = Add mode, otherwise = Edit mode.
     */
    private fun showAddEditDialog(existingProvider: ServiceProvider?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_provider, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etName = dialogView.findViewById<EditText>(R.id.etProviderName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etProviderPhone)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        // Setup category spinner
        val etCustomCategory = dialogView.findViewById<EditText>(R.id.etCustomCategory)

        // Setup category spinner
        val categories = Constants.COMPLAINT_CATEGORIES
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        spinner.adapter = spinnerAdapter

        // Show/hide custom category when "Other" is selected
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                etCustomCategory.visibility = if (categories[pos] == "Other") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // If editing, pre-fill fields
        if (existingProvider != null) {
            tvTitle.text = "Edit Provider"
            btnSave.text = "Update Provider"
            etName.setText(existingProvider.name)
            etPhone.setText(existingProvider.phone)
            val catIndex = categories.indexOf(existingProvider.category)
            if (catIndex >= 0) {
                spinner.setSelection(catIndex)
            } else {
                // Custom category — select "Other" and fill in
                val otherIndex = categories.indexOf("Other")
                if (otherIndex >= 0) spinner.setSelection(otherIndex)
                etCustomCategory.setText(existingProvider.category)
                etCustomCategory.visibility = View.VISIBLE
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val selectedCategory = spinner.selectedItem?.toString().orEmpty()
            val customCategory = etCustomCategory.text.toString().trim()

            // Resolve final category
            val category = if (selectedCategory == "Other") {
                if (customCategory.isBlank()) {
                    etCustomCategory.error = "Please specify the skill"
                    return@setOnClickListener
                }
                customCategory
            } else {
                selectedCategory
            }

            // Validation
            if (name.isBlank()) {
                etName.error = "Name is required"
                return@setOnClickListener
            }
            if (phone.isBlank()) {
                etPhone.error = "Phone is required"
                return@setOnClickListener
            }
            if (phone.length < 10) {
                etPhone.error = "Enter a valid phone number"
                return@setOnClickListener
            }

            val communityId = SessionManager(requireContext()).getCommunityId().orEmpty()

            val provider = ServiceProvider(
                id = existingProvider?.id ?: "",
                name = name,
                phone = phone,
                category = category,
                communityId = communityId
            )

            dialog.dismiss()
            saveProvider(provider, existingProvider != null)
        }
        dialog.show()
    }
    /**
     * Save (add or update) a provider to Firestore.
     */
    private fun saveProvider(provider: ServiceProvider, isUpdate: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isUpdate) {
                    repo.updateProvider(provider)
                    Toast.makeText(requireContext(), "Provider updated!", Toast.LENGTH_SHORT).show()
                } else {
                    repo.addProvider(provider)
                    Toast.makeText(requireContext(), "Provider added!", Toast.LENGTH_SHORT).show()
                }
                loadProviders() // Refresh list
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /**
     * Show confirmation dialog before deleting a provider.
     */
    private fun confirmDelete(provider: ServiceProvider) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Provider")
            .setMessage("Are you sure you want to delete ${provider.name}?")
            .setPositiveButton("Delete") { _, _ -> deleteProvider(provider) }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun deleteProvider(provider: ServiceProvider) {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.deleteProvider(provider.id)
                Toast.makeText(requireContext(), "Provider deleted", Toast.LENGTH_SHORT).show()
                loadProviders()
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /**
     * Open phone dialer to call a provider.
     */
    private fun callProvider(provider: ServiceProvider) {
        if (provider.phone.isNotBlank()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${provider.phone}"))
            startActivity(intent)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}