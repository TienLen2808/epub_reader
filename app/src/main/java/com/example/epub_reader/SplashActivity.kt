package com.example.epub_reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.applovin.mediation.ads.MaxAdView
import com.example.epub_reader.AdsManager.GoogleMobileAdsConsentManager
import com.example.epub_reader.AdsManager.InlineAdLoader
import com.example.epub_reader.databinding.ActivitySplashBinding
import com.example.epub_reader.ui.LanguageActivity
import com.example.epub_reader.ui.onboarding.OnboardingActivity
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.EventName
import com.example.epub_reader.utils.FirebaseEventTracking
import com.example.epub_reader.utils.ParamsKey
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.Utils
import com.example.epub_reader.utils.isValidActivity
import com.example.epub_reader.utils.remoteAdDisabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Launch screen: orange gradient, logo, "EPUB Reader" and the version.
 *
 * The logo starts where the launch screen drew it (centre of the window, see splash_icon.xml) and then moves up
 * while the texts fade in. Then the language screen opens on the first launch (then the onboarding), the home screen after.
 *
 * Ads (same flow as MemeMixClone, without its "tricky" click tricks):
 * - LocalizeBaseActivity asks for consent (UMP) and starts the ad SDKs, then calls [showAdIfAvailabled].
 * - First launch: adaptive banner at the bottom, plus a 300 x 250 MREC under the name when the screen is tall enough.
 *   They appear only once loaded; the splash keeps them on screen [INLINE_AD_MIN_VISIBLE_MS] then moves on.
 * - Later launches: app-open ad; the next screen opens when it is closed.
 * - Never stuck: offline / premium / no consent → no waiting, and [SPLASH_MAX_MS] at most
 *   (except while the consent form or an ad is on screen).
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : LocalizeBaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var nextScreenOpened = false

    /** Language screen → onboarding on the first launch, home screen after */
    private val nextScreen: Class<*> by lazy {
        val prefs = PreferencesHelper.getInstance()
        when {
            !prefs.getBoolean(Constants.LANGUAGE_SELECTED, false) -> LanguageActivity::class.java
            !prefs.getBoolean(Constants.ONBOARDING_COMPLETED, false) -> OnboardingActivity::class.java
            else -> MainActivity::class.java
        }
    }
    private val isFirstLaunch: Boolean get() = nextScreen != MainActivity::class.java

    private var startedAt = 0L
    private var topInset = 0
    private var bottomInset = 0

    private var adPhaseStarted = false
    private var adPhaseStartedAt = 0L
    private var adPhaseFinished = false
    private var bannersRequested = false
    private var appOpenRequested = false
    private var inlineAdsPending = 0
    private var inlineAdVisibleSince = 0L
    private var bannerView: MaxAdView? = null
    private var mrecView: MaxAdView? = null

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
        applySystemBarInsets()

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

        startedAt = SystemClock.elapsedRealtime()
        // Offline or premium: no ads, the splash only plays its animation
        val adsPossible = !PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM) &&
            Utils.isInternetAvailable(this)
        binding.tvSplashAdNotice.isVisible = adsPossible
        if (!adsPossible) adPhaseFinished = true

        if (isFirstLaunch) {
            FirebaseEventTracking.logEventToFirebase(
                this,
                EventName.app_onboarding,
                mapOf(ParamsKey.current_screen.name to "loading")
            )
        }

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            while (!canLeave()) delay(TICK_MS)
            // If the user left the app meanwhile, continue when it is visible again
            lifecycle.withStarted { openNextScreen() }
        }
    }

    /** The splash never shows the "No Internet" dialog: the app works offline, the ads simply do not load */
    override fun showsNoInternetDialog(): Boolean = false

    override fun onDestroy() {
        bannerView?.destroy()
        bannerView = null
        mrecView?.destroy()
        mrecView = null
        BaseApp.instance.cancelPendingAppOpenAd()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Ads

    /** Called by LocalizeBaseActivity once consent is gathered and the ad SDKs are ready */
    override fun showAdIfAvailabled() = startAdPhase()

    override fun onStartLoadingAd() = startAdPhase()

    private fun startAdPhase() {
        if (adPhaseStarted || adPhaseFinished || nextScreenOpened || !isValidActivity()) return
        adPhaseStarted = true
        adPhaseStartedAt = SystemClock.elapsedRealtime()

        // First launch (like MemeMixClone): banner + MREC, no app-open ad
        if (isFirstLaunch || remoteAdDisabled(DisabledAdType.appOpen)) {
            showSplashBanners()
            return
        }

        val timeLeft = SPLASH_MAX_MS - (adPhaseStartedAt - startedAt)
        if (timeLeft < MIN_APP_OPEN_WAIT_MS) {
            adPhaseFinished = true
            return
        }
        appOpenRequested = true
        BaseApp.instance.showAdIfAvailable(this, minOf(BaseApp.APP_OPEN_MAX_WAIT_MS, timeLeft)) {
            adPhaseFinished = true
        }
    }

    /** Banner at the bottom, MREC under the app name when it fits. Also called by BaseApp.mrecSplashShow() */
    fun showSplashBanners() {
        // Never banner / MREC under an app-open ad
        if (bannersRequested || appOpenRequested || adPhaseFinished || nextScreenOpened || !isValidActivity()) return
        if (!adPhaseStarted) {
            adPhaseStarted = true
            adPhaseStartedAt = SystemClock.elapsedRealtime()
        }
        bannersRequested = true

        val withBanner = canShowBanner(DisabledAdType.splashBanner)
        val withMrec = canShowMrec(DisabledAdType.splashMrec) && mrecFits(withBanner)

        if (withBanner) {
            bannerView = InlineAdLoader.loadBanner(
                this, binding.splashBannerSlot, DisabledAdType.splashBanner, AdPosition.splashScreen
            ) { loaded -> onInlineAdResult(loaded) { revealBanner() } }
            if (bannerView != null) inlineAdsPending++
        }
        if (withMrec) {
            mrecView = InlineAdLoader.loadMrec(
                this, binding.splashMrecSlot, DisabledAdType.splashMrec, AdPosition.splashScreen
            ) { loaded -> onInlineAdResult(loaded) { revealMrec() } }
            if (mrecView != null) inlineAdsPending++
        }
        if (inlineAdsPending == 0) adPhaseFinished = true
    }

    private fun onInlineAdResult(loaded: Boolean, reveal: () -> Unit) {
        inlineAdsPending = (inlineAdsPending - 1).coerceAtLeast(0)
        if (!loaded || nextScreenOpened || !isValidActivity()) return
        reveal()
        if (inlineAdVisibleSince == 0L) inlineAdVisibleSince = SystemClock.elapsedRealtime()
    }

    private fun canLeave(): Boolean {
        // Never under the consent form or an app-open ad: the next screen opens when they are closed
        if (isConsentFormShowing || BaseApp.instance.isShowingAOA()) return false
        if (adPhaseFinished) return true
        val now = SystemClock.elapsedRealtime()
        if (now - startedAt >= SPLASH_MAX_MS) return true
        // Consent answered and ads may not be requested: nothing to wait for
        if (BaseApp.instance.gatherConsentFinished.get() &&
            !GoogleMobileAdsConsentManager.getInstance(this).canRequestAds
        ) return true
        if (bannersRequested) {
            if (inlineAdVisibleSince > 0L) return now - inlineAdVisibleSince >= INLINE_AD_MIN_VISIBLE_MS
            if (inlineAdsPending == 0) return true
            return now - adPhaseStartedAt >= INLINE_AD_MAX_WAIT_MS
        }
        return false
    }

    /** MREC (250 dp) between the name and the version, banner below: is the screen tall enough? */
    private fun mrecFits(withBanner: Boolean): Boolean {
        val rootHeight = binding.root.height
        if (rootHeight == 0) return false
        val bottomPart = if (withBanner) dp(12) + InlineAdLoader.bannerHeightPx(this) else dp(36)
        val needed = topInset + dp(MREC_TOP_GAP_DP) +
            binding.imgSplashLogo.height + dp(20) + binding.tvSplashTitle.height +
            dp(MREC_GAP_DP) + dp(250) + dp(20) +
            binding.tvSplashAdNotice.height + dp(6) + binding.tvSplashVersion.height +
            bottomPart + bottomInset
        return needed <= rootHeight
    }

    private fun revealBanner() {
        TransitionManager.beginDelayedTransition(binding.root, ChangeBounds().setDuration(REVEAL_MS))
        binding.splashBannerSlot.isVisible = true
    }

    /** Logo and name move up, the MREC appears under them */
    private fun revealMrec() {
        val root = binding.root
        val set = ConstraintSet()
        set.clone(root)
        set.connect(R.id.tvSplashTitle, ConstraintSet.BOTTOM, R.id.splashMrecSlot, ConstraintSet.TOP, dp(MREC_GAP_DP))
        set.connect(
            R.id.imgSplashLogo, ConstraintSet.TOP,
            ConstraintSet.PARENT_ID, ConstraintSet.TOP, topInset + dp(MREC_TOP_GAP_DP)
        )
        set.setVerticalBias(R.id.imgSplashLogo, 0.5f)
        set.setVisibility(R.id.splashMrecSlot, View.VISIBLE)
        TransitionManager.beginDelayedTransition(
            root,
            ChangeBounds().setDuration(REVEAL_MS).setInterpolator(DecelerateInterpolator())
        )
        set.applyTo(root)
    }

    // ---------------------------------------------------------------- Layout

    /**
     * The version stays above the navigation bar: 36 dp above it without a banner (layout_goneMarginBottom),
     * 12 dp above the banner when there is one. The banner itself sits on the navigation bar.
     */
    private fun applySystemBarInsets() {
        val baseGoneMargin = (binding.tvSplashVersion.layoutParams as ConstraintLayout.LayoutParams).goneBottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
            binding.tvSplashVersion.updateLayoutParams<ConstraintLayout.LayoutParams> {
                goneBottomMargin = baseGoneMargin + bars.bottom
            }
            binding.splashBannerSlot.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = bars.bottom
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
        listOf(
            binding.tvSplashTitle to 0L,
            binding.tvSplashAdNotice to 120L,
            binding.tvSplashVersion to 120L
        ).forEach { (text, extraDelay) ->
            text.alpha = 0f
            text.translationY = rise
            text.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(INTRO_DELAY_MS + 220L + extraDelay)
                .setDuration(INTRO_TEXT_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun openNextScreen() {
        if (nextScreenOpened || isFinishing || isDestroyed) return
        nextScreenOpened = true
        BaseApp.instance.cancelPendingAppOpenAd()

        val intent = Intent(this, nextScreen)
        if (nextScreen == LanguageActivity::class.java) {
            intent.putExtra(LanguageActivity.EXTRA_FROM_SPLASH, true)
        }
        val fade = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, fade.toBundle())
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        /** Shortest splash: the intro animation */
        const val SPLASH_DURATION_MS = 1700L
        /** Longest splash, whatever happens with consent / network / ads (an ad or the consent form on screen excepted) */
        const val SPLASH_MAX_MS = 10_000L
        /** First launch: banner / MREC stay on screen this long once one of them is shown */
        const val INLINE_AD_MIN_VISIBLE_MS = 2_500L
        /** First launch: no banner / MREC loaded after this long → go on without them */
        const val INLINE_AD_MAX_WAIT_MS = 5_000L
        /** Not worth starting an app-open ad request with less time left than this */
        const val MIN_APP_OPEN_WAIT_MS = 1_500L
        const val TICK_MS = 100L
        const val REVEAL_MS = 300L
        const val MREC_TOP_GAP_DP = 12
        const val MREC_GAP_DP = 24

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
