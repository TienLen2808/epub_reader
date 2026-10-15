package com.example.epub_reader

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.epub_reader.AdsManager.ApplovinNativeAdManager
import com.example.epub_reader.databinding.ActivityMainBinding
import com.example.epub_reader.ui.main.MainViewPagerAdapter
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.removeSubviews

class MainActivity : LocalizeBaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPagerAdapter: MainViewPagerAdapter
    private val homeNativeAdManager by lazy { ApplovinNativeAdManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light (iOS like) UI: transparent system bars with dark icons on every Android version
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMainLayout(binding.main)

        setupViewPager()
        setupBottomNav()
        updateBottomNavUI(binding.viewPagerMain.currentItem)
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
        val selectedColor = ContextCompat.getColor(this, R.color.ios_tab_selected)
        val normalColor = ContextCompat.getColor(this, R.color.ios_tab_unselected)

        tintTab(binding.imgNavFilesIcon, binding.tvNavFilesLabel, if (position == 0) selectedColor else normalColor)
        tintTab(binding.imgNavTransferIcon, binding.tvNavTransferLabel, if (position == 1) selectedColor else normalColor)
        tintTab(binding.imgNavSettingsIcon, binding.tvNavSettingsLabel, if (position == 2) selectedColor else normalColor)

        // Status bar area takes the color of the screen header (iOS bar gray on "Các tập tin" / "Chuyển tệp")
        binding.main.setBackgroundColor(
            ContextCompat.getColor(this, if (position == 2) R.color.white else R.color.ios_bar_bg)
        )
    }

    private fun tintTab(icon: ImageView, label: TextView, color: Int) {
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color))
        label.setTextColor(color)
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
                homeNativeAdManager.loadApplovinNativeAd(
                    context = this@MainActivity,
                    layoutResource = R.layout.native_custom_ad_banner,
                    adPosition = AdPosition.homeScreen
                ) { success ->
                    if (success) {
                        homeNativeAdManager.populateNativeAdView(
                            layoutResource = R.layout.native_custom_ad_banner,
                            type = DisabledAdType.homeBanner,
                            adFrame = binding.bannerContainer
                        )
                    } else {
                        binding.bannerContainer.removeSubviews()
                    }
                }
            } else {
                binding.bannerContainer.removeSubviews()
            }

            binding.mrecContainer.removeSubviews()
        }
    }

    override fun onDestroy() {
        homeNativeAdManager.destroyLoader()
        super.onDestroy()
    }
}
