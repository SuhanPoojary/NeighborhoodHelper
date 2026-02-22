package com.example.smartneighborhoodhelper.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartneighborhoodhelper.R

/**
 * OnboardingAdapter.kt — RecyclerView adapter for ViewPager2 onboarding pages.
 *
 * WHAT IS ViewPager2?
 *   ViewPager2 shows swipeable pages. It uses a RecyclerView adapter internally.
 *   Each "page" is a RecyclerView item that takes the full screen width.
 *
 * Each page has: title, description, and an image resource.
 */

/** Data class representing one onboarding page */
data class OnboardingPage(
    val title: String,
    val description: String,
    val imageRes: Int  // Drawable resource ID
)

class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    /** ViewHolder holds references to views in item_onboarding_page.xml */
    inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivOnboardingImage)
        val tvTitle: TextView = view.findViewById(R.id.tvOnboardingTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvOnboardingDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        holder.tvTitle.text = page.title
        holder.tvDesc.text = page.description
        holder.ivImage.setImageResource(page.imageRes)
    }

    override fun getItemCount(): Int = pages.size
}

