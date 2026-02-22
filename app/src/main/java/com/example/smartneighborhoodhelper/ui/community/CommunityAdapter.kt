package com.example.smartneighborhoodhelper.ui.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smartneighborhoodhelper.data.model.Community
import com.example.smartneighborhoodhelper.databinding.ItemCommunityBinding

class CommunityAdapter(
    private val onJoinClicked: (Community) -> Unit
) : RecyclerView.Adapter<CommunityAdapter.CommunityVH>() {

    private val items = mutableListOf<Community>()

    fun submitList(list: List<Community>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunityVH {
        val binding = ItemCommunityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommunityVH(binding)
    }

    override fun onBindViewHolder(holder: CommunityVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CommunityVH(private val binding: ItemCommunityBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Community) {
            binding.tvName.text = item.name
            binding.tvArea.text = item.area
            binding.tvMembers.text = "${item.memberCount} members"
            binding.btnJoin.setOnClickListener { onJoinClicked(item) }
        }
    }
}

