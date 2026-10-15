package com.example.epub_reader.ui.onboarding

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.epub_reader.MainActivity
import com.example.epub_reader.R
import com.example.epub_reader.databinding.ActivityOnboardingBinding
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.PreferencesHelper
import kotlin.math.roundToInt

/**
 * First launch: 3 pages (import books, read & listen, share the library).
 * "Next" goes to the following page, "Get started" on the last page opens the home screen.
 * It is shown once: Constants.ONBOARDING_COMPLETED is saved at the end.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val pages = listOf(
        OnboardingPage(R.drawable.img_onboarding_import, R.string.onboarding_title_import, R.string.onboarding_desc_import),
        OnboardingPage(R.drawable.img_onboarding_listen, R.string.onboarding_title_listen, R.string.onboarding_desc_listen),
        OnboardingPage(R.drawable.img_onboarding_share, R.string.onboarding_title_share, R.string.onboarding_desc_share)
    )
    private val dots = mutableListOf<View>()

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
    }
}
