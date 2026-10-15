package com.example.epub_reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.example.epub_reader.databinding.ActivitySplashBinding
import com.example.epub_reader.ui.onboarding.OnboardingActivity
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.PreferencesHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launch screen: orange gradient, logo, "EPUB Reader" and the version.
 *
 * The logo starts where the launch screen drew it (centre of the window, see splash_icon.xml) and then moves up
 * while the texts fade in. After [SPLASH_DURATION_MS] the onboarding opens on the first launch, the home screen after.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var nextScreenOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // White status bar / navigation bar icons on the orange background
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSplashVersion.text = getString(R.string.splash_version, BuildConfig.VERSION_NAME)
        keepVersionAboveNavigationBar()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: the system launch screen (solid orange + logo) fades out over this screen
            splashScreen.setOnExitAnimationListener { launchScreen ->
                launchScreen.animate()
                    .alpha(0f)
                    .setDuration(LAUNCH_SCREEN_FADE_MS)
                    .withEndAction { launchScreen.remove() }
                    .start()
            }
        }

        if (savedInstanceState == null) {
            binding.root.doOnPreDraw { playIntro() }
        }

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            // If the user left the app meanwhile, continue when it is visible again
            lifecycle.withStarted { openNextScreen() }
        }
    }

    private fun keepVersionAboveNavigationBar() {
        val baseMargin = (binding.tvSplashVersion.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.tvSplashVersion.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseMargin + bars.bottom
            }
            insets
        }
    }

    /** Logo from the window centre (launch screen position and size) to its place, then the texts appear */
    private fun playIntro() {
        val root = binding.root
        val logo = binding.imgSplashLogo
        if (logo.width == 0 || logo.height == 0 || root.width == 0 || root.height == 0) return

        val density = resources.displayMetrics.density
        // splash_icon.xml draws the logo at 1 viewport unit = 1dp with this point at the window centre
        logo.pivotX = logo.width * LOGO_ANCHOR_X
        logo.pivotY = logo.height * LOGO_ANCHOR_Y
        val startScale = LOGO_VIEWPORT_WIDTH * density / logo.width
        logo.scaleX = startScale
        logo.scaleY = startScale
        logo.translationX = root.width / 2f - (logo.left + logo.pivotX)
        logo.translationY = root.height / 2f - (logo.top + logo.pivotY)

        logo.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(INTRO_DELAY_MS)
            .setDuration(INTRO_MOVE_MS)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        val rise = 14 * density
        listOf(binding.tvSplashTitle, binding.tvSplashVersion).forEachIndexed { index, text ->
            text.alpha = 0f
            text.translationY = rise
            text.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(INTRO_DELAY_MS + 220L + index * 120L)
                .setDuration(INTRO_TEXT_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun openNextScreen() {
        if (nextScreenOpened || isFinishing || isDestroyed) return
        nextScreenOpened = true
        val onboardingDone = PreferencesHelper.getInstance().getBoolean(Constants.ONBOARDING_COMPLETED, false)
        val next = if (onboardingDone) MainActivity::class.java else OnboardingActivity::class.java
        val fade = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(Intent(this, next), fade.toBundle())
        finish()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1700L
        const val LAUNCH_SCREEN_FADE_MS = 200L
        // The logo starts moving once the Android 12+ launch screen has faded out
        const val INTRO_DELAY_MS = 220L
        const val INTRO_MOVE_MS = 650L
        const val INTRO_TEXT_MS = 450L

        // ic_splash_logo.xml: viewport 135 x 162; centre of the logo (the point splash_icon.xml puts at the window centre)
        const val LOGO_VIEWPORT_WIDTH = 135f
        const val LOGO_ANCHOR_X = 72.66f / 135f
        const val LOGO_ANCHOR_Y = 89.16f / 162f
    }
}
