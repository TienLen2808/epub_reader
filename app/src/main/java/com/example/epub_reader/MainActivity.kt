package com.example.epub_reader

import android.graphics.Color
import android.os.Bundle
import androidx.core.view.marginBottom
import androidx.viewpager2.widget.ViewPager2
import com.example.epub_reader.AdsManager.AdsManager
import com.example.epub_reader.databinding.ActivityMainBinding
import com.example.epub_reader.ui.main.MainViewPagerAdapter
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.dpToPx
import com.example.epub_reader.utils.removeSubviews

class MainActivity : LocalizeBaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPagerAdapter: MainViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMainLayout(binding.main)

        setupViewPager()
        setupBottomNav()
    }

    private fun setupViewPager() {
        viewPagerAdapter = MainViewPagerAdapter(this)
        binding.viewPagerMain.adapter = viewPagerAdapter
        binding.viewPagerMain.offscreenPageLimit = 3
        binding.viewPagerMain.isUserInputEnabled = false

        binding.viewPagerMain.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateBottomNavUI(position)
            }
        })
    }

    private fun setupBottomNav() {
        binding.btnMainFilesTab.setOnClickListener { binding.viewPagerMain.setCurrentItem(0, false) }
        binding.btnMainTransferTab.setOnClickListener { binding.viewPagerMain.setCurrentItem(1, false) }
        binding.btnMainSettingsTab.setOnClickListener { binding.viewPagerMain.setCurrentItem(2, false) }
    }

    private fun updateBottomNavUI(position: Int) {
        val orangeColor = Color.parseColor("#FF7043")
        val grayColor = Color.parseColor("#888888")

        binding.imgNavFilesIcon.setColorFilter(if (position == 0) orangeColor else grayColor)
        binding.tvNavFilesLabel.setTextColor(if (position == 0) orangeColor else grayColor)

        binding.imgNavTransferIcon.setColorFilter(if (position == 1) orangeColor else grayColor)
        binding.tvNavTransferLabel.setTextColor(if (position == 1) orangeColor else grayColor)

        binding.imgNavSettingsIcon.setColorFilter(if (position == 2) orangeColor else grayColor)
        binding.tvNavSettingsLabel.setTextColor(if (position == 2) orangeColor else grayColor)
    }

    override fun loadAd() {
        super.loadAd()
        var showBanner = false

        binding.viewPagerMain.post {
            val canShowBanner = canShowBanner(DisabledAdType.homeBanner)

            if (canShowBanner) {
                showBanner = true
            }

            if (showBanner) {
                AdsManager.getInstance(this).loadApplovinInlineAdaptiveBannerWithTricky(
                    this, DisabledAdType.homeBanner,
                    AdPosition.homeScreen, binding.bannerContainer, AdsManager.RETRY_COUNT, {}
                )
            } else {
                binding.bannerContainer.removeSubviews()
            }

            binding.mrecContainer.removeSubviews()
        }
    }
}
