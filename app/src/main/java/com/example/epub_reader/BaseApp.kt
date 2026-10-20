package com.example.epub_reader

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdRevenueListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.appsflyer.AppsFlyerAdNetworkEventType
import com.example.epub_reader.AdsManager.AdsManager
import com.example.epub_reader.AdsManager.FullScreenAdFragment
import com.example.epub_reader.AdsManager.FullScreenAdsManager
import com.example.epub_reader.AdsManager.GoogleMobileAdsConsentManager
import com.example.epub_reader.AdsManager.NativeAdsPool
import com.example.epub_reader.AdsManager.PaidAdEventCounter
import com.example.epub_reader.utils.AdFormat
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.AppsflyerEventTracking
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.EventName
import com.example.epub_reader.utils.FirebaseEventTracking
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.isValidActivity
import com.example.epub_reader.utils.remoteAdDisabled
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.lang.ref.WeakReference
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class BaseApp : Application(), DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    companion object {
        const val TEST_DEVICE_HASHED_ID = "ABCDEF012345"
        var lastAdShown: Long? = null
        var lastMaxAdShown: Long? = null

        /** How long the splash waits for the app-open ad to load before going on without it */
        const val APP_OPEN_MAX_WAIT_MS = 5_000L

        lateinit var instance: BaseApp
            private set

        fun setCurrentActivity(application: Application, activity: Activity?) {
            if (application is BaseApp) {
                val app = application.applicationContext as BaseApp
                app.currentActivity = activity
                if (app.versionName == null) {
                    activity?.let {
                        val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                        app.versionName = packageInfo.versionName
                    }
                }
            }
        }

        fun canShowAd(context: Context): Boolean {
            val timeBetweenAds = PreferencesHelper.getInstance().getInt(Constants.TIME_BETWEEN_ADS, 15)
            val last = lastMaxAdShown ?: lastAdShown
            return last?.let { Date().time - it > timeBetweenAds * 1000 } ?: true
        }
    }

    var currentActivity: Activity? = null
    var versionName: String? = null
    var activityTrace = ""
    var sdkInitialized = false
    var android_onboarding_max_clicks = 1000
    val gatherConsentFinished = AtomicBoolean(false)
    var fullScreenAdsManager: FullScreenAdsManager? = null
    private val appOpenAdManager = AppOpenAdManager()

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        initFirebase()
        PreferencesHelper.init(this)
        NativeAdsPool.init(this)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        fullScreenAdsManager = FullScreenAdsManager(this)
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app == null || FirebaseApp.getApps(this).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:100000000000:android:0000000000000000000000")
                        .setApiKey("AIzaSyDummyApiKeyForFirebaseAppInit")
                        .setProjectId("epub-reader")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            }
        } catch (e: Exception) {
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:100000000000:android:0000000000000000000000")
                    .setApiKey("AIzaSyDummyApiKeyForFirebaseAppInit")
                    .setProjectId("epub-reader")
                    .build()
                FirebaseApp.initializeApp(this, options)
            } catch (e2: Exception) {
                Log.e("BaseApp", "Failed to initialize FirebaseApp", e2)
            }
        }
    }

    fun preloadFullscreenAds(activity: Activity) {
        fullScreenAdsManager?.loadInterstitialApplovinAd(false, AdPosition.homeScreen)
        fullScreenAdsManager?.loadApplovinRewardedAd(AdPosition.homeScreen)
    }

    // ---------------------------------------------------------------- App-open ad (splash)

    /**
     * Shows the MAX app-open ad over [activity], loading it first when needed (at most [maxWaitMs]).
     * [completion] is called once: true when an ad was shown and closed, false when there was none to show.
     */
    fun showAdIfAvailable(activity: Activity, maxWaitMs: Long = APP_OPEN_MAX_WAIT_MS, completion: (Boolean) -> Unit) {
        val isPremium = PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)
        if (isPremium || remoteAdDisabled(DisabledAdType.appOpen)) {
            completion(false)
            return
        }
        appOpenAdManager.show(activity, maxWaitMs, completion)
    }

    /** The screen that asked for the app-open ad is going away: an ad that loads later must not cover the next screen */
    fun cancelPendingAppOpenAd() {
        appOpenAdManager.cancelPending()
    }

    /** An app-open ad or an interstitial is on screen */
    fun isShowingAOA(): Boolean {
        return fullScreenAdsManager?.interstitialAdDisplaying == true || appOpenAdManager.isShowing
    }

    /** Splash without waiting for the app-open ad: banner / MREC now, the app-open ad is loaded for later */
    fun loadAd(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        mrecSplashShow(activity)
        appOpenAdManager.load()
        onComplete?.invoke(true)
    }

    fun mrecSplashShow(activity: Activity) {
        (activity as? SplashActivity)?.showSplashBanners()
    }

    /**
     * Full screen native ad (FullScreenAdFragment). Only shown when a native ad is already loaded, so nobody
     * waits on an empty screen. [onClose] is always called once, also when nothing is shown.
     */
    fun showFullScreenAdDialog(manager: FragmentManager, type: DisabledAdType, onClose: (() -> Unit)? = null) {
        if (remoteAdDisabled(type) || !NativeAdsPool.getInstance().hasLoadedAd() || manager.isStateSaved) {
            onClose?.invoke()
            return
        }
        FullScreenAdFragment().apply {
            bindTo(type)
        }.show(manager, onClose)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * MAX app-open ad. One request at a time: the ad is shown only over the activity that asked for it, only while
     * that activity is in front, and never after the request was cancelled or timed out.
     */
    private inner class AppOpenAdManager {
        private val handler = Handler(Looper.getMainLooper())
        private var appOpenAd: MaxAppOpenAd? = null
        private var isLoading = false
        private var loadStartedAt = 0L
        private var shownAt = 0L
        private var pending: Request? = null
        private var showing: Request? = null

        var isShowing = false
            private set

        /** A screen waiting for the ad: its callback is called once */
        private inner class Request(activity: Activity, private val completion: (Boolean) -> Unit) {
            val activity = WeakReference(activity)
            private var done = false
            val timeout = Runnable {
                if (pending === this) {
                    pending = null
                    finish(false)
                }
            }

            fun finish(shown: Boolean) {
                if (done) return
                done = true
                handler.removeCallbacks(timeout)
                completion(shown)
            }

            fun drop() {
                done = true
                handler.removeCallbacks(timeout)
            }
        }

        private val consentManager: GoogleMobileAdsConsentManager
            get() = GoogleMobileAdsConsentManager.getInstance(this@BaseApp)

        private val adListener = object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                isLoading = false
                track(EventName.ad_loaded, ad.networkName, result = 1, duration = SystemClock.elapsedRealtime() - loadStartedAt)
                pending?.let { showNow(it) }
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                isLoading = false
                Log.w("BaseApp", "App-open ad failed to load: ${error.message}")
                track(EventName.ad_loaded, null, result = 0, duration = SystemClock.elapsedRealtime() - loadStartedAt)
                val request = pending ?: return
                pending = null
                request.finish(false)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                track(EventName.ad_displayed, ad.networkName, result = 1)
            }

            override fun onAdHidden(ad: MaxAd) {
                lastMaxAdShown = Date().time
                isShowing = false
                track(EventName.ad_closed, ad.networkName, duration = SystemClock.elapsedRealtime() - shownAt)
                showing?.finish(true)
                showing = null
            }

            override fun onAdClicked(ad: MaxAd) {}

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                isShowing = false
                track(EventName.ad_displayed, ad.networkName, result = 0)
                showing?.finish(false)
                showing = null
            }
        }

        private val revenueListener = MaxAdRevenueListener { ad ->
            PaidAdEventCounter.increment(PaidAdEventCounter.Kind.APP_OPEN, ad.revenue)
            FirebaseEventTracking.adTracking(
                context = this@BaseApp,
                event = EventName.ad_revenue_paid,
                adFormat = AdFormat.openApp,
                platform = "applovin_max",
                network = ad.networkName,
                position = AdPosition.openApp,
                value = ad.revenue
            )
            AppsflyerEventTracking.logEventAppsflyerMax(
                context = currentActivity ?: this@BaseApp,
                adUnitId = ad.adUnitId,
                adType = AppsFlyerAdNetworkEventType.APP_OPEN.toString(),
                revenueUsd = ad.revenue,
                network = ad.networkName
            )
        }

        fun load() {
            if (isLoading || appOpenAd?.isReady == true) return
            if (!consentManager.canRequestAds || PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)) return

            val adUnitId = AdsManager.getInstance(this@BaseApp).applovinOpenadsId
            val ad = appOpenAd?.takeIf { it.adUnitId == adUnitId } ?: MaxAppOpenAd(adUnitId).also {
                appOpenAd?.destroy()
                it.setListener(adListener)
                it.setRevenueListener(revenueListener)
                appOpenAd = it
            }
            isLoading = true
            loadStartedAt = SystemClock.elapsedRealtime()
            ad.loadAd()
        }

        fun show(activity: Activity, maxWaitMs: Long, completion: (Boolean) -> Unit) {
            if (isShowing || !consentManager.canRequestAds || !canShowAd(activity)) {
                completion(false)
                return
            }
            cancelPending()
            val request = Request(activity, completion)
            pending = request
            if (appOpenAd?.isReady == true) {
                showNow(request)
                return
            }
            load()
            if (!isLoading) {
                pending = null
                request.finish(false)
                return
            }
            handler.postDelayed(request.timeout, maxWaitMs)
        }

        fun cancelPending() {
            pending?.drop()
            pending = null
        }

        private fun showNow(request: Request) {
            pending = null
            val activity = request.activity.get()
            val ad = appOpenAd
            val inFront = (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
            if (activity == null || !activity.isValidActivity() || !inFront || ad?.isReady != true) {
                request.finish(false)
                return
            }
            handler.removeCallbacks(request.timeout)
            showing = request
            isShowing = true
            shownAt = SystemClock.elapsedRealtime()
            ad.showAd()
        }

        private fun track(event: EventName, network: String?, result: Int? = null, duration: Long? = null) {
            FirebaseEventTracking.adTracking(
                context = this@BaseApp,
                event = event,
                adFormat = AdFormat.openApp,
                platform = "applovin_max",
                network = network,
                position = AdPosition.openApp,
                duration = duration,
                result = result
            )
        }
    }
}
