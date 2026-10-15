package com.example.epub_reader.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.databinding.ItemOnboardingPageBinding

/** One onboarding page */
data class OnboardingPage(
    @param:DrawableRes val illustration: Int,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int
)

class OnboardingPagerAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val binding: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder =
        PageViewHolder(ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        holder.binding.imgOnboardingIllustration.setImageResource(page.illustration)
        holder.binding.tvOnboardingTitle.setText(page.title)
        holder.binding.tvOnboardingDesc.setText(page.description)
    }

    override fun getItemCount(): Int = pages.size
}
