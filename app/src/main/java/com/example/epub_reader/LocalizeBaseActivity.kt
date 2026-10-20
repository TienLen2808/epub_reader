package com.example.epub_reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.epub_reader.AdsManager.NativeAdsPool
import com.example.epub_reader.ui.RateAppDialog
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.RemoteConfig
import com.example.epub_reader.utils.Utils
import com.example.epub_reader.utils.getStatusBarHeight
import com.example.epub_reader.utils.isValidActivity
import com.example.epub_reader.utils.remoteAdDisabled
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

open class LocalizeBaseActivity : AppCompatActivity() {
    protected var COUNTER_TIME_MILLISECONDS = 13000L

    protected lateinit var currentLanguage: Locale

    private var didSetupAd: Boolean = false
    private val remoteAdListener = { updateAdListener() }

    protected val isMobileAdsInitializeCalled = AtomicBoolean(false)
    protected val gatherConsentCalled = AtomicBoolean(false)

    protected var admobinitialized: Boolean = false
    protected var remoteConfigFetched: Boolean = false
    protected val isAppLovinInitializeCalled = AtomicBoolean(false)
    protected var applovinInitialized: Boolean = false
    protected var isLoadingRemoteConfig: Boolean = false
    protected var isLoadedOpenAd = false
    protected var consentInformation: ConsentInformation? = null

    protected val remoteShowMrec by lazy { PreferencesHelper.getInstance().getBoolean("android_native_to_mrec_applovin", true) }
    protected val remoteShowBanner by lazy { PreferencesHelper.getInstance().getBoolean("android_banner_armaster_screen", true) }
    var isAdSystemInitialized = false

    /** The UMP consent form is on screen (the splash does not leave while the user answers it) */
    var isConsentFormShowing = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NativeAdsPool.getInstance().removeAdsFromParent()

        supportActionBar?.hide()
        actionBar?.hide()

        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.size() > 0) {
            currentLanguage = locales[0] ?: Locale.getDefault()
        } else {
            currentLanguage = Locale.getDefault()
        }
    }

    fun setupMainLayout(mainLayout: View, additional: ((WindowInsetsCompat) -> Unit)? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val statusBarHeight = getStatusBarHeight()
            v.setPadding(insets.getInsets(WindowInsetsCompat.Type.systemBars()).left, statusBarHeight, insets.getInsets(WindowInsetsCompat.Type.systemBars()).right, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            additional?.invoke(insets)
            insets
        }
    }

    var networkCallback: Any? = null
    open fun shouldRunBaseOnStart(): Boolean = true

    /** "No Internet" dialog when the connection is lost. The splash turns it off: it never blocks the app offline */
    open fun showsNoInternetDialog(): Boolean = true

    override fun onStart() {
        super.onStart()
        networkCallback = Utils.NetworkDialogManager.observeInternet(
            this,
            onAvailable = {
                Utils.NetworkDialogManager.hideNoInternetDialog()

                if (!BaseApp.instance.sdkInitialized) {
                    if (!isLoadingRemoteConfig) {
                        isLoadingRemoteConfig = true
                        RemoteConfig.initAndFetchRemoteConfig(applicationContext, this, { isComplete ->
                            if (isComplete) {
                                remoteConfigFetched = true
                                loadOpenAdIfReady()
                            } else {
                                isLoadingRemoteConfig = false
                            }
                        })
                        configAdsmob()
                    }
                } else {
                    lifecycleScope.launch {
                        if (RemoteConfig.isInitialized()) {
                            delay(2000)
                            remoteConfigFetched = true
                            isLoadedOpenAd = false
                            applovinInitialized = true
                            loadOpenAdIfReady()
                        }
                    }
                }
            },
            onLost = {
                if (showsNoInternetDialog()) {
                    Utils.NetworkDialogManager.showNoInternetDialog(this)
                }
            }
        )
    }

    open fun onAdSystemInitialized() {
        if (!isAdSystemInitialized) {
            isAdSystemInitialized = true
            BaseApp.instance.preloadFullscreenAds(this)
            NativeAdsPool.getInstance().createAdManagers()
            NativeAdsPool.getInstance().startTimer()
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            networkCallback?.let {
                Utils.NetworkDialogManager.unregisterInternetObserver(this, it)
                networkCallback = null
            }
        } catch (e: RuntimeException) {
            Log.e("NetworkCallback", "Failed to unregister network callback", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Utils.NetworkDialogManager.hideNoInternetDialog()
        RemoteConfig.removeUpdateListener { remoteAdListener }
    }

    override fun onResume() {
        super.onResume()
        var current: Locale? = null
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.size() > 0) {
            current = locales[0] ?: Locale.getDefault()
        } else {
            current = Locale.getDefault()
        }

        if (currentLanguage != current) {
            Handler(Looper.getMainLooper()).postDelayed({
                restartActivity()
            }, 200)
        }
        BaseApp.setCurrentActivity(application, this)

        if (BaseApp.instance.gatherConsentFinished.get()) {
            maybeInitSdks()
        } else {
            configAdsmob()
        }

        if (!didSetupAd) {
            didSetupAd = true
            setupAd()
        }
        loadAd()
    }

    protected fun restartActivity() {
        recreate()
    }

    fun getCurrentLanguage(): String {
        val current: Locale
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.size() > 0) {
            current = locales[0] ?: Locale.getDefault()
        } else {
            current = Locale.getDefault()
        }
        return current.language
    }

    fun setGlideImage(imageView: ImageView, resourceId: Int) {
        try {
            if (isValidActivity()) {
                Glide.with(imageView)
                    .load(resourceId)
                    .into(imageView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkViewAfterSetGlider(view: View, resourceId: Int, isBackground: Boolean = false) {
        if (isValidActivity()) {
            if (view.measuredWidth > 0 && view.measuredHeight > 0) {
                applyGlide(view, resourceId, isBackground)
            } else {
                view.post {
                    applyGlide(view, resourceId, isBackground)
                }
            }
        }
    }

    private fun applyGlide(view: View, resourceId: Int, isBackground: Boolean) {
        if (isBackground) {
            setGlideBackground(view, resourceId)
        } else if (view is ImageView) {
            setGlideImage(view, resourceId)
        }
    }

    fun setGlideBackground(view: View, resourceId: Int) {
        try {
            if (isValidActivity()) {
                Glide.with(view)
                    .load(resourceId)
                    .override(view.measuredWidth, view.measuredHeight)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            view.background = resource
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            view.background = placeholder
                        }
                    })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val playStoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingCompletion?.invoke()
        pendingCompletion = null
        waitingForLauncher = false
    }

    fun showFeedBackDiaLog(completion: () -> Unit) {
        completion()
    }

    private var pendingCompletion: (() -> Unit)? = null
    private var waitingForLauncher = false

    fun showRatingDialog(inSettings: Boolean = false, completion: () -> Unit) {
        val preferences = PreferencesHelper.getInstance()
        if (!inSettings && preferences.getBoolean("has_rated", false)) {
            completion.invoke()
            return
        }
        pendingCompletion = completion

        if (!(this.isFinishing || this.isDestroyed)) {
            try {
                RateAppDialog.show(supportFragmentManager,
                    onDismissOutSize = {
                        if (!waitingForLauncher) {
                            pendingCompletion?.invoke()
                            pendingCompletion = null
                        }
                    },
                    onRate = { rating ->
                        if (rating >= 4) {
                            waitingForLauncher = true
                            try {
                                val marketUri = Uri.parse("market://details?id=$packageName")
                                val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                                    )
                                }
                                playStoreLauncher.launch(marketIntent)
                                preferences.putBoolean("has_rated", true)
                            } catch (e: ActivityNotFoundException) {
                                val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                                if (webIntent.resolveActivity(packageManager) != null) {
                                    playStoreLauncher.launch(webIntent)
                                    preferences.putBoolean("has_rated", true)
                                } else {
                                    pendingCompletion?.invoke()
                                    pendingCompletion = null
                                }
                            }
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.thanks_for_your_review),
                                Toast.LENGTH_SHORT
                            ).show()
                            pendingCompletion?.invoke()
                            pendingCompletion = null
                            preferences.putBoolean("has_rated", true)
                        }
                    })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isChromeOS(): Boolean {
        return packageManager.hasSystemFeature("org.chromium.arc")
    }

    private fun configAdsmob() {
        if (!BaseApp.instance.sdkInitialized && !gatherConsentCalled.get()) {
            gatherConsentCalled.set(true)

            val debugSettings = ConsentDebugSettings.Builder(this)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId("33BE2250B43518CCDA7DE426D04EE231")
                .build()

            val params = if (BuildConfig.DEBUG) ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings)
                .setTagForUnderAgeOfConsent(false)
                .build() else ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()

            consentInformation = UserMessagingPlatform.getConsentInformation(this)

            consentInformation?.requestConsentInfoUpdate(
                this,
                params,
                {
                    if (consentInformation?.isConsentFormAvailable == true) {
                        UserMessagingPlatform.loadConsentForm(
                            this,
                            { consentForm ->
                                if (this@LocalizeBaseActivity.isValidActivity()) {
                                    if (consentInformation?.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                                        isConsentFormShowing = true
                                        consentForm.show(this) {
                                            isConsentFormShowing = false
                                            BaseApp.instance.gatherConsentFinished.set(true)
                                            maybeInitSdks()
                                            NativeAdsPool.getInstance().createAdManagers(true)
                                        }
                                    } else {
                                        BaseApp.instance.gatherConsentFinished.set(true)
                                        maybeInitSdks()
                                    }
                                }
                            },
                            { formError ->
                                Log.w("LOG_TAG", "UMP Form Load Error: ${formError.message}")
                                if (this@LocalizeBaseActivity.isValidActivity()) {
                                    BaseApp.instance.gatherConsentFinished.set(true)
                                    maybeInitSdks()
                                }
                            }
                        )
                    } else {
                        if (this@LocalizeBaseActivity.isValidActivity()) {
                            BaseApp.instance.gatherConsentFinished.set(true)
                            maybeInitSdks()
                        }
                    }
                },
                { requestConsentError ->
                    if (this@LocalizeBaseActivity.isValidActivity()) {
                        Log.w("LOG_TAG", "UMP Consent Error: ${requestConsentError.message}")
                        BaseApp.instance.gatherConsentFinished.set(true)
                        maybeInitSdks()
                    }
                }
            )
        }
    }

    private fun initializeAppLovinSdk() {
        if (isAppLovinInitializeCalled.getAndSet(true)) return

        val sdkKey = "tLDN-4zvZMQH-2GVQZJDCM9m64JGikmI2deEhULNkz2EALdgz3SnrbOlIHXkqZvqIg5FCvD5hodyKRk4FQle9h"
        val app = application
        val sdk = AppLovinSdk.getInstance(app)

        val initConfig = AppLovinSdkInitializationConfiguration
            .builder(sdkKey)
            .setMediationProvider("max")
            .setTestDeviceAdvertisingIds(listOf("cOeb012f-f632-4f17-9594-cbf102c6b358"))
            .build()

        sdk.initialize(initConfig) { sdkConfig: AppLovinSdkConfiguration ->
            applovinInitialized = true
            if (AppLovinSdk.getInstance(app).isInitialized) {
                loadOpenAdIfReady()
            }
        }
    }

    private fun loadOpenAdIfReady() {
        if (RemoteConfig.isInitialized()) {
            if (remoteConfigFetched) {
                lifecycleScope.launch {
                    delay(1500)
                    if (BaseApp.instance.gatherConsentFinished.get()) {
                        onAdSystemInitialized()
                    }
                }
            }
            if (remoteConfigFetched && !isLoadedOpenAd) {
                if (applovinInitialized) {
                    isLoadedOpenAd = true
                    loadAppOpenAd()
                }
            }
        }
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(BaseApp.TEST_DEVICE_HASHED_ID))
                .build()
        )

        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@LocalizeBaseActivity) {
                admobinitialized = true
                loadOpenAdIfReady()
            }
        }
    }

    private fun maybeInitSdks() {
        if (!BaseApp.instance.sdkInitialized && (consentInformation?.canRequestAds() == true)) {
            initializeMobileAdsSdk()
            initializeAppLovinSdk()
            BaseApp.instance.sdkInitialized = true
        } else if (RemoteConfig.isInitialized()) {
            onAdSystemInitialized()
        }
    }

    fun loadAppOpenAd() {
        val remoteDisabled = remoteAdDisabled(DisabledAdType.appOpen)
        val openAppInterstitial = PreferencesHelper.getInstance().getBoolean(
            Constants.OPENAPP_INTERSTITIAL
        )
        val timerDelay = PreferencesHelper.getInstance().getInt("android_delay_splash_timer") * 1000
        COUNTER_TIME_MILLISECONDS = timerDelay.toLong()
        val splash_banner_mrec_aoa = PreferencesHelper.getInstance().getBoolean("android_splash_banner_mrec_aoa")
        if (remoteDisabled) {
            if (splash_banner_mrec_aoa) {
                if (this@LocalizeBaseActivity.isValidActivity()) {
                    runOnUiThread {
                        onStartLoadingAd()
                        loadAdWithRetry()
                    }
                }
            } else {
                (application as? BaseApp)?.mrecSplashShow(this)
            }
            return
        }

        if (PreferencesHelper.getInstance().getBoolean(Constants.wait_for_ads_on_first_run, true)) {
            showAdIfAvailabled()
        } else {
            if (this@LocalizeBaseActivity.isValidActivity()) {
                runOnUiThread {
                    onStartLoadingAd()
                    loadAdWithRetry()
                }
            }
        }
    }

    open fun onStartLoadingAd() {}
    open fun showAdIfAvailabled() {}

    fun loadAdWithRetry() {
        (application as? BaseApp)?.loadAd(this) { success ->
            if (!success) {
                if (this@LocalizeBaseActivity.isValidActivity()) {
                    loadAdWithRetry()
                }
            }
        }
    }

    private fun setupAd() {
        if (isDestroyed || isFinishing) return
        RemoteConfig.addUpdateListener(remoteAdListener)
        loadAd()
    }

    open fun loadAd() {
        if (isDestroyed || isFinishing) return
    }

    open fun updateAdListener() {
        if (isDestroyed || isFinishing) return
        loadAd()
    }

    open fun canShowBanner(type: DisabledAdType): Boolean {
        return remoteShowBanner && !remoteAdDisabled(type)
    }

    open fun canShowMrec(type: DisabledAdType): Boolean {
        return remoteShowMrec && !remoteAdDisabled(type)
    }
}
