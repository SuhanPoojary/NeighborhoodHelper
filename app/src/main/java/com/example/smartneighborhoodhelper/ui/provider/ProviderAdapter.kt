package com.example.smartneighborhoodhelper.ui.provider

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartneighborhoodhelper.data.model.ServiceProvider
import com.example.smartneighborhoodhelper.databinding.ItemProviderBinding
import com.example.smartneighborhoodhelper.ui.complaint.ComplaintAdapter

/**
 * ProviderAdapter — RecyclerView adapter for service provider list.
 *
 * Each card shows:
 *   - Category emoji icon (reuses ComplaintAdapter.getCategoryEmoji)
 *   - Provider name, skill, phone
 *   - Edit and Delete action buttons
 */
class ProviderAdapter(
    private val onEdit: (ServiceProvider) -> Unit,
    private val onDelete: (ServiceProvider) -> Unit,
    private val onCall: (ServiceProvider) -> Unit
) : ListAdapter<ServiceProvider, ProviderAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ServiceProvider>() {
            override fun areItemsTheSame(a: ServiceProvider, b: ServiceProvider) = a.id == b.id
            override fun areContentsTheSame(a: ServiceProvider, b: ServiceProvider) = a == b
        }
    }

    inner class VH(private val b: ItemProviderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(provider: ServiceProvider) {
            b.tvProviderIcon.text = ComplaintAdapter.getCategoryEmoji(provider.category)
            b.tvProviderName.text = provider.name
            b.tvProviderCategory.text = provider.category
            b.tvProviderPhone.text = provider.phone

            b.ivEdit.setOnClickListener { onEdit(provider) }
            b.ivDelete.setOnClickListener { onDelete(provider) }
            b.root.setOnClickListener { onCall(provider) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}

