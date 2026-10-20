package com.example.epub_reader.AdsManager

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.appsflyer.AppsFlyerAdNetworkEventType
import com.example.epub_reader.BaseApp
import com.example.epub_reader.BuildConfig
import com.example.epub_reader.R
import com.example.epub_reader.utils.AdFormat
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.AppsflyerEventTracking
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.EventName
import com.example.epub_reader.utils.FirebaseEventTracking
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.remoteAdDisabled
import com.google.firebase.analytics.FirebaseAnalytics

class ApplovinNativeAdManager(var context: Context) {

    companion object {
        private var instance: ApplovinNativeAdManager? = null

        fun getInstance(context: Context): ApplovinNativeAdManager {
            if (instance == null) {
                instance = ApplovinNativeAdManager(context)
            }
            return instance!!
        }
    }

    val RETRY_COUNT = 10
    val RETRY_DELAY_MS = 1000L
    var maxNativeAdLoader: MaxNativeAdLoader? = null
    var maxCurrentNativeAd: MaxAd? = null
    var maxCurrentNativeAdView: MaxNativeAdView? = null

    @Volatile
    var loadingMaxNativeTime: Long? = null
    private var nativeStartTimeMax: Long = 0L

    private var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager? = null

    var adDidLoaded: Boolean = false
    var adLoadedSuccess: Boolean = false
    var onAdReady: (() -> Unit)? = null
    var onAdReload: (() -> Unit)? = null
    var onAdLoadFailed: (() -> Unit)? = null

    val hasParent: Boolean
        get() = maxCurrentNativeAdView?.parent != null

    val adReady: Boolean
        get() = adLoadedSuccess && !hasParent

    private var layoutResource: Int? = null
    private var adPosition: AdPosition = AdPosition.preLoad
    private var type: DisabledAdType = DisabledAdType.nativeAfterInterstitial
    private var adContainer: FrameLayout? = null
    private var trickyScale: Int = 0

    var TAG: String = "ApplovinNativeAdManager"

    init {
        googleMobileAdsConsentManager = GoogleMobileAdsConsentManager.getInstance(context)
    }

    fun pushTracking(
        event: EventName, adFormat: AdFormat, adPosition: AdPosition, result: Int,
        platform: String?, network: String?, duration: Long?
    ) {
        try {
            if (duration != null) {
                FirebaseEventTracking.adTracking(
                    context = context,
                    event = event,
                    adFormat = adFormat,
                    position = adPosition,
                    result = result,
                    duration = duration
                )
            } else {
                FirebaseEventTracking.adTracking(
                    context = context,
                    event = event,
                    adFormat = adFormat,
                    platform = platform,
                    network = network,
                    position = adPosition,
                    result = result
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseEventTracking.adTracking", e.message.toString())
        }
    }

    private fun createMaxNativeView(context: Context, layoutResource: Int? = null): MaxNativeAdView {
        val layout = layoutResource ?: R.layout.native_custom_ad_view
        return MaxNativeAdView(nativeAdViewBinder(layout), context)
    }

    /**
     * Views of the native ad layouts. ad_options_view receives the AdChoices icon of the ad network
     * (layouts without that id simply get no AdChoices container).
     */
    private fun nativeAdViewBinder(layoutResource: Int): MaxNativeAdViewBinder {
        return MaxNativeAdViewBinder.Builder(layoutResource)
            .setTitleTextViewId(R.id.title_text_view)
            .setBodyTextViewId(R.id.body_text_view)
            .setAdvertiserTextViewId(R.id.advertiser_textView)
            .setIconImageViewId(R.id.icon_image_view)
            .setMediaContentViewGroupId(R.id.media_view_container)
            .setOptionsContentViewGroupId(R.id.ad_options_view)
            .setCallToActionButtonId(R.id.cta_button)
            .build()
    }

    fun loadApplovinNativeAd(
        context: Context,
        layoutResource: Int? = null,
        adPosition: AdPosition,
        retryCount: Int = RETRY_COUNT,
        completion: ((Boolean) -> Unit)? = null
    ) {
        if (googleMobileAdsConsentManager?.canRequestAds != true) {
            completion?.invoke(false)
            return
        }
        if (PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)) {
            completion?.invoke(false)
            return
        }
        if (retryCount < 0) {
            completion?.invoke(false)
            return
        }

        this.layoutResource = layoutResource
        this.adPosition = adPosition

        val nativeUnitId = AdsManager.getInstance(context).applovinNativeId
        Log.d("AdLog", "📥 [NativeAd] Start loading Native Ad. Unit ID: $nativeUnitId, Position: $adPosition")

        if (maxCurrentNativeAd != null && maxCurrentNativeAdView != null) {
            adLoadedSuccess = true
            completion?.invoke(true)
            return
        }

        var remainingRetries = retryCount
        var t0 = System.currentTimeMillis()

        loadingMaxNativeTime = System.currentTimeMillis()
        maxCurrentNativeAdView = createMaxNativeView(context, layoutResource).apply {
            visibility = View.INVISIBLE
        }

        maxNativeAdLoader = MaxNativeAdLoader(AdsManager.getInstance(context).applovinNativeId)

        maxNativeAdLoader?.setRevenueListener { maxAd ->
            PaidAdEventCounter.increment(PaidAdEventCounter.Kind.NATIVE, maxAd.revenue)
            try {
                AppsflyerEventTracking.logEventAppsflyerMax(
                    context = BaseApp.instance.currentActivity ?: BaseApp.instance,
                    adUnitId = maxAd.adUnitId,
                    adType = AppsFlyerAdNetworkEventType.NATIVE.toString(),
                    revenueUsd = maxAd.revenue,
                    network = maxAd.networkName
                )
                FirebaseEventTracking.adTracking(
                    context = BaseApp.instance.currentActivity ?: BaseApp.instance,
                    event = EventName.ad_revenue_paid,
                    adFormat = AdFormat.native,
                    platform = "applovin_max",
                    network = maxAd.networkName,
                    position = adPosition,
                    value = maxAd.revenue
                )
            } catch (_: Exception) {}
        }

        maxNativeAdLoader?.setNativeAdListener(object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(adView: MaxNativeAdView?, ad: MaxAd) {
                Log.d("AdLog", "✅ [NativeAd] Loaded SUCCESS! Network: ${ad.networkName}, Position: $adPosition, Title: ${ad.nativeAd?.title}")
                loadingMaxNativeTime = null
                pushTracking(
                    EventName.ad_loaded, AdFormat.native, adPosition, 1, null, null,
                    System.currentTimeMillis() - t0
                )

                try {
                    maxCurrentNativeAdView?.visibility = View.VISIBLE
                    if (maxCurrentNativeAd != null && maxNativeAdLoader != null) {
                        maxNativeAdLoader?.destroy(maxCurrentNativeAd)
                    }
                    maxCurrentNativeAd = ad
                    maxCurrentNativeAdView = adView
                    nativeStartTimeMax = System.currentTimeMillis()

                    pushTracking(
                        EventName.ad_displayed, AdFormat.native, adPosition, 1,
                        "applovin_max", ad.networkName, null
                    )

                    adDidLoaded = true
                    adLoadedSuccess = true
                    completion?.invoke(true)
                    onAdReady?.invoke()
                } catch (e: Exception) {
                    pushTracking(
                        EventName.ad_displayed, AdFormat.native, adPosition, 0,
                        "applovin_max", ad.networkName, null
                    )
                    adDidLoaded = true
                    completion?.invoke(false)
                }
            }

            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.e("AdLog", "❌ [NativeAd] Load FAILED! UnitID: $adUnitId, Error: ${error.message} (code: ${error.code})")
                loadingMaxNativeTime = null
                pushTracking(
                    EventName.ad_loaded, AdFormat.native, adPosition, 0, null, null,
                    System.currentTimeMillis() - t0
                )
                pushTracking(
                    EventName.ad_displayed, AdFormat.native, adPosition, 0,
                    "applovin_max", null, null
                )

                adDidLoaded = true
                completion?.invoke(false)
                onAdLoadFailed?.invoke()

                if (remainingRetries > 0) {
                    remainingRetries -= 1
                    Handler(Looper.getMainLooper()).postDelayed({
                        t0 = System.currentTimeMillis()
                        maxNativeAdLoader?.loadAd(maxCurrentNativeAdView)
                    }, RETRY_DELAY_MS)
                }
            }

            override fun onNativeAdClicked(ad: MaxAd) {
                isClicked = true
                NativeAdsPool.getInstance().onNativeAdClick()
                reloadAd()
            }

            override fun onNativeAdExpired(p0: MaxAd) {
                super.onNativeAdExpired(p0)
                destroyLoader()
                loadApplovinNativeAd(context, layoutResource, adPosition, RETRY_COUNT, completion)
            }
        })

        adDidLoaded = false
        maxNativeAdLoader?.loadAd(maxCurrentNativeAdView)
    }

    fun populateNativeAdView(
        layoutResource: Int,
        type: DisabledAdType,
        adFrame: FrameLayout,
        trickyOverlay: FrameLayout? = null,
        extraExpandDp: Int = 100,
        onAdReady: (() -> Unit)? = null,
        onAdReload: (() -> Unit)? = null
    ) {
        val remoteDisabled = remoteAdDisabled(type)
        if (googleMobileAdsConsentManager?.canRequestAds != true || remoteDisabled) {
            adFrame.visibility = View.GONE
            return
        }
        if (PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)) {
            adFrame.visibility = View.GONE
            return
        }

        this.type = type
        this.adContainer = adFrame
        this.trickyScale = extraExpandDp

        if (maxCurrentNativeAdView == null) return

        this.onAdReload = onAdReload

        if (maxCurrentNativeAd == null) {
            adFrame.removeAllViews()
            this.onAdReady = {
                renderNativeAd(layoutResource, adFrame, trickyOverlay, extraExpandDp)
                onAdReady?.invoke()
            }
            return
        }

        this.onAdReady = onAdReady

        if (maxCurrentNativeAdView?.parent === adFrame) {
            adFrame.visibility = View.VISIBLE
            onAdReady?.invoke()
            return
        }

        renderNativeAd(layoutResource, adFrame, trickyOverlay, extraExpandDp)
        onAdReady?.invoke()
    }

    private fun renderNativeAd(
        layoutResource: Int,
        adFrame: FrameLayout,
        trickyOverlay: FrameLayout?,
        extraExpandDp: Int
    ) {
        Log.d("AdLog", "📺 [NativeAd] RENDER / SHOWING Native Ad in Frame! Type: $type, Position: $adPosition, Network: ${maxCurrentNativeAd?.networkName}")
        (maxCurrentNativeAdView?.parent as? ViewGroup)?.removeView(maxCurrentNativeAdView)
        adFrame.removeAllViews()

        val expandedAdView = MaxNativeAdView(nativeAdViewBinder(layoutResource), context)
        maxNativeAdLoader?.render(expandedAdView, maxCurrentNativeAd)
        maxCurrentNativeAdView = expandedAdView

        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        adFrame.addView(maxCurrentNativeAdView, params)
        adFrame.visibility = View.VISIBLE
    }

    private var isClicked = false

    fun removeAdFromParent() {
        (maxCurrentNativeAdView?.parent as? ViewGroup)?.removeView(maxCurrentNativeAdView)
        adContainer = null
        NativeAdsPool.getInstance().summaryLog()
    }

    fun reloadAd() {
        if (adDidLoaded) {
            onAdReload?.invoke()
            adLoadedSuccess = false
            maxNativeAdLoader?.loadAd(maxCurrentNativeAdView)
        }
    }

    fun destroyLoader() {
        (maxCurrentNativeAdView?.parent as? ViewGroup)?.removeView(maxCurrentNativeAdView)
        maxCurrentNativeAdView = null

        maxCurrentNativeAd?.let {
            maxNativeAdLoader?.destroy(it)
        }
        maxCurrentNativeAd = null

        maxNativeAdLoader?.destroy()
        maxNativeAdLoader = null

        adLoadedSuccess = false
    }
}
