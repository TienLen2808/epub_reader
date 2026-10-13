package com.example.epub_reader

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.epub_reader.AdsManager.FullScreenAdsManager
import com.example.epub_reader.AdsManager.NativeAdsPool
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.PreferencesHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class BaseApp : Application(), DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    companion object {
        const val TEST_DEVICE_HASHED_ID = "ABCDEF012345"
        var lastAdShown: Long? = null
        var lastMaxAdShown: Long? = null

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

    fun loadAd(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        onComplete?.invoke(true)
    }

    fun mrecSplashShow(activity: Activity) {
    }

    fun showFullScreenAdDialog(manager: FragmentManager, type: DisabledAdType, onClose: (() -> Unit)? = null) {
        onClose?.invoke()
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
}
