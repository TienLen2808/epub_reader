package com.example.epub_reader.ui.onboarding

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.epub_reader.AdsManager.NativeAdsPool
import com.example.epub_reader.BaseApp
import com.example.epub_reader.LocalizeBaseActivity
import com.example.epub_reader.MainActivity
import com.example.epub_reader.R
import com.example.epub_reader.databinding.ActivityOnboardingBinding
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.EventName
import com.example.epub_reader.utils.FirebaseEventTracking
import com.example.epub_reader.utils.ParamsKey
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.remoteAdDisabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * First launch: 3 pages (import books, read & listen, share the library).
 * "Next" goes to the following page, "Get started" on the last page opens the home screen.
 * It is shown once: Constants.ONBOARDING_COMPLETED is saved at the end.
 *
 * Ads:
 * - small native ad at the top of the screen;
 * - full screen native ad once when reaching page 2 (onb2), only when an ad is already loaded.
 */
class OnboardingActivity : LocalizeBaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val pages = listOf(
        OnboardingPage(R.drawable.img_onboarding_import, R.string.onboarding_title_import, R.string.onboarding_desc_import),
        OnboardingPage(R.drawable.img_onboarding_listen, R.string.onboarding_title_listen, R.string.onboarding_desc_listen),
        OnboardingPage(R.drawable.img_onboarding_share, R.string.onboarding_title_share, R.string.onboarding_desc_share)
    )
    private val dots = mutableListOf<View>()

    /** Page whose ad is in the top slot (-1: none yet) */
    private var adShownForPage = -1
    private var isShowingFullScreenAd = false
    private val fullScreenAdPages = mutableSetOf<Int>()
    private val onPoolAdLoaded: () -> Unit = {
        lifecycleScope.launch {
            // Only fills an empty slot: a loaded ad never replaces the one on screen by itself
            if (::binding.isInitialized && binding.onboardingAdContainer.childCount == 0) {
                showPageAd(binding.vpOnboarding.currentItem)
            }
        }
    }

    /** Back goes to the previous page; on the first page it leaves the app as usual */
    private val backToPreviousPage = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.vpOnboarding.currentItem = binding.vpOnboarding.currentItem - 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.vpOnboarding.adapter = OnboardingPagerAdapter(pages)
        createDots()
        binding.vpOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showPage(position)
                // A full screen ad takes the next free native ad; the top slot is refreshed when it closes
                if (!maybeShowFullScreenAd(position)) {
                    showPageAd(position)
                }
                FirebaseEventTracking.logEventToFirebase(
                    this@OnboardingActivity,
                    EventName.app_onboarding,
                    mapOf(ParamsKey.current_screen.name to "tutorial ${position + 1}")
                )
            }
        })
        showPage(binding.vpOnboarding.currentItem)

        binding.btnOnboardingNext.setOnClickListener {
            val current = binding.vpOnboarding.currentItem
            if (current < pages.lastIndex) {
                binding.vpOnboarding.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
        onBackPressedDispatcher.addCallback(this, backToPreviousPage)
    }

    override fun onResume() {
        super.onResume()
        NativeAdsPool.getInstance().onAdSuccessLoaded = onPoolAdLoaded
    }

    override fun onPause() {
        super.onPause()
        val pool = NativeAdsPool.getInstance()
        if (pool.onAdSuccessLoaded === onPoolAdLoaded) pool.onAdSuccessLoaded = null
    }

    /** Called on every resume: fills the ad slot if it is still empty */
    override fun loadAd() {
        super.loadAd()
        if (::binding.isInitialized && binding.onboardingAdContainer.childCount == 0) {
            showPageAd(binding.vpOnboarding.currentItem)
        }
    }

    private fun createDots() {
        val density = resources.displayMetrics.density
        val size = (DOT_SIZE_DP * density).roundToInt()
        val gap = (DOT_GAP_DP * density).roundToInt()
        binding.layoutOnboardingDots.removeAllViews()
        dots.clear()
        pages.indices.forEach { index ->
            val dot = View(this).apply {
                setBackgroundResource(R.drawable.bg_onboarding_dot)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (index > 0) marginStart = gap
                }
            }
            binding.layoutOnboardingDots.addView(dot)
            dots += dot
        }
    }

    private fun showPage(position: Int) {
        dots.forEachIndexed { index, dot -> dot.isSelected = index == position }
        binding.btnOnboardingNext.setText(
            if (position == pages.lastIndex) R.string.onboarding_start else R.string.onboarding_next
        )
        backToPreviousPage.isEnabled = position > 0
    }

    // ---------------------------------------------------------------- Ads

    /** Native ad at the top: a new one for this page when the pool has one ready, otherwise the current one stays */
    private fun showPageAd(position: Int) {
        if (!::binding.isInitialized || isShowingFullScreenAd || isFinishing) return
        val container = binding.onboardingAdContainer
        if (!canShowBanner(DisabledAdType.onboardingBanner)) {
            container.removeAllViews()
            container.isVisible = false
            return
        }
        val hasAd = container.childCount > 0
        if (hasAd && adShownForPage == position) return

        val pool = NativeAdsPool.getInstance()
        if (!pool.hasAvailableItemAd) return // keeps the current ad; an empty slot is filled by onPoolAdLoaded
        if (!container.isVisible && binding.root.isLaidOut) {
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().setDuration(AD_REVEAL_MS))
        }
        pool.availableItemAd?.populateNativeAdView(
            layoutResource = R.layout.native_ad_small,
            type = DisabledAdType.onboardingBanner,
            adFrame = container,
            onAdReady = { adShownForPage = position }
        )
    }

    /** Full screen native ad when reaching page 2 (onb2), once only. Returns true when one is about to show */
    private fun maybeShowFullScreenAd(position: Int): Boolean {
        if (position != 1 || isShowingFullScreenAd || position in fullScreenAdPages) return false
        val pagesMask = PreferencesHelper.getInstance()
            .getInt(Constants.android_onboarding_full_screen_native_ads, FULL_SCREEN_AD_PAGES_DEFAULT)
        if (pagesMask and 1 == 0) return false
        if (remoteAdDisabled(DisabledAdType.onboardingFullscreen) || !NativeAdsPool.getInstance().hasAvailableItemAd) {
            return false
        }

        fullScreenAdPages += position
        isShowingFullScreenAd = true
        lifecycleScope.launch {
            // Let the page settle first
            delay(FULL_SCREEN_AD_DELAY_MS)
            if (isFinishing || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                onFullScreenAdClosed()
                return@launch
            }
            BaseApp.instance.showFullScreenAdDialog(supportFragmentManager, DisabledAdType.onboardingFullscreen) {
                onFullScreenAdClosed()
            }
        }
        return true
    }

    private fun onFullScreenAdClosed() {
        isShowingFullScreenAd = false
        if (isFinishing) return
        // The full screen ad used the pool callback while it was open
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            NativeAdsPool.getInstance().onAdSuccessLoaded = onPoolAdLoaded
        }
        showPageAd(binding.vpOnboarding.currentItem)
    }

    private fun finishOnboarding() {
        if (isFinishing) return
        PreferencesHelper.getInstance().putBoolean(Constants.ONBOARDING_COMPLETED, true)
        val fade = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(Intent(this, MainActivity::class.java), fade.toBundle())
        finish()
    }

    private companion object {
        const val DOT_SIZE_DP = 8f
        const val DOT_GAP_DP = 8f

        /** Full screen ad on page 2 (onb2) only */
        const val FULL_SCREEN_AD_PAGES_DEFAULT = 1
        const val FULL_SCREEN_AD_DELAY_MS = 500L
        const val AD_REVEAL_MS = 250L
    }
}
