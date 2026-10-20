package com.example.epub_reader.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.epub_reader.AdsManager.NativeAdsPool
import com.example.epub_reader.LocalizeBaseActivity
import com.example.epub_reader.MainActivity
import com.example.epub_reader.R
import com.example.epub_reader.databinding.ActivityLanguageBinding
import com.example.epub_reader.ui.onboarding.OnboardingActivity
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.EventName
import com.example.epub_reader.utils.FirebaseEventTracking
import com.example.epub_reader.utils.LocalizeHelper
import com.example.epub_reader.utils.ParamsKey
import com.example.epub_reader.utils.PreferencesHelper
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Language list. Opened by the splash on the first launch (Done → onboarding, Back leaves the app)
 * or from Settings (Back / Done close it).
 *
 * Native ad under the list (from NativeAdsPool, preloaded during the splash), shown once an ad is loaded.
 */
class LanguageActivity : LocalizeBaseActivity() {
    companion object {
        const val EXTRA_FROM_SPLASH = "EXTRA_FROM_SPLASH"
        private const val EXTRA_LANGUAGE_SELECTED = "LANGUAGE_SELECTED"
    }

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var adapter: LanguagesAdapter
    private var isFromSplash = false

    /** The native ad is on screen: it stays (the pool refreshes it in place) */
    private var adShown = false
    private val onPoolAdLoaded: () -> Unit = { lifecycleScope.launch { loadAd() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFromSplash = intent.getBooleanExtra(EXTRA_FROM_SPLASH, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFromSplash) {
                    finishAffinity()
                } else {
                    finish()
                }
            }
        })
        enableEdgeToEdge()

        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMainLayout(binding.root)

        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.size() > 0) {
            currentLanguage = locales[0] ?: Locale.getDefault()
        } else {
            currentLanguage = Locale.getDefault()
        }

        if (isFromSplash) {
            FirebaseEventTracking.logEventToFirebase(
                this,
                EventName.app_onboarding,
                mapOf(ParamsKey.current_screen.name to "language")
            )
        }

        binding.apply {
            btnBack.visibility = if (isFromSplash) View.GONE else View.VISIBLE
            btnBack.setOnClickListener {
                finish()
            }
            btnDone.setOnClickListener {
                confirmAndProceed()
            }

            recyclerView.layoutManager = LinearLayoutManager(this@LanguageActivity)
            val useDefault = PreferencesHelper.getInstance().getBoolean(Constants.SYSTEM_DEFAULT_LANGUAGE, true)
            val selectedLanguage = when {
                useDefault -> ""
                else -> currentLanguage.language
            }
            adapter = LanguagesAdapter(selectedLanguage) { locale ->
                if (locale != null) {
                    onItemClick(locale)
                } else {
                    resetToSystemLanguage()
                }
            }

            recyclerView.adapter = adapter
            val index = LocalizeHelper.indexOfLanguage(currentLanguage)
            if (index in 0 until LocalizeHelper.numberOfLanguages()) {
                recyclerView.scrollToPosition(index)
            }
        }
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

    /** Called on every resume and when the pool loads an ad: fills the ad slot once */
    override fun loadAd() {
        super.loadAd()
        if (!::binding.isInitialized || adShown) return
        if (!canShowBanner(DisabledAdType.languageNative)) {
            binding.adContainer.visibility = View.GONE
            return
        }
        val pool = NativeAdsPool.getInstance()
        if (!pool.hasAvailableItemAd) return // waits for onAdSuccessLoaded
        pool.availableItemAd?.populateNativeAdView(
            layoutResource = R.layout.native_ad_language,
            type = DisabledAdType.languageNative,
            adFrame = binding.adContainer,
            onAdReady = { adShown = true }
        )
    }

    private fun confirmAndProceed() {
        PreferencesHelper.getInstance().putBoolean(Constants.LANGUAGE_SELECTED, true)
        if (isFromSplash) {
            val onboardingDone = PreferencesHelper.getInstance().getBoolean(Constants.ONBOARDING_COMPLETED, false)
            val next = if (onboardingDone) MainActivity::class.java else OnboardingActivity::class.java
            val fade = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(Intent(this, next), fade.toBundle())
        }
        finish()
    }

    private fun onItemClick(locale: Locale) {
        intent.putExtra(EXTRA_LANGUAGE_SELECTED, true)

        PreferencesHelper.getInstance().putBoolean(Constants.SYSTEM_DEFAULT_LANGUAGE, false)
        currentLanguage = locale

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(currentLanguage))
        Handler(Looper.getMainLooper()).postDelayed({
            adapter.updateLanguage(locale.language)
        }, 200)
    }

    private fun resetToSystemLanguage() {
        PreferencesHelper.getInstance().putBoolean(Constants.SYSTEM_DEFAULT_LANGUAGE, true)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        Handler(Looper.getMainLooper()).postDelayed({
            adapter.updateLanguage("")
        }, 200)
    }
}
