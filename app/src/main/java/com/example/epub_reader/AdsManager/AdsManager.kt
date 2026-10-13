package com.example.epub_reader.AdsManager

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxAdViewConfiguration
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.applovin.sdk.AppLovinSdkUtils
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
import com.example.epub_reader.utils.isAdmobLike
import com.example.epub_reader.utils.netKey
import com.example.epub_reader.utils.remoteAdDisabled
import com.example.epub_reader.utils.safeRunOnUiThread
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONObject
import kotlin.random.Random

class AdsManager(var context: Context) {

    companion object {
        val RETRY_COUNT = 10
        private var instance: AdsManager? = null

        fun getInstance(context: Context): AdsManager {
            if (instance == null) {
                instance = AdsManager(context)
            }
            return instance!!
        }
    }

    var openAppAdId: String = ""

    var applovinInterMainId: String = context.getString(R.string.default_max_inter_main)
    var applovinInterMediumId: String = context.getString(R.string.default_max_inter_medium)
    var applovinInterHighId: String = context.getString(R.string.default_max_inter_high)
    var applovinOpenadsId: String = context.getString(R.string.default_max_open_id)
    var applovinNativeId: String = context.getString(R.string.default_max_native_id)
    var applovinBannerId: String = context.getString(R.string.default_max_banner_id)
    var applovinMrecId: String = context.getString(R.string.default_max_mrec_id)
    var applovinRewardMainId: String = context.getString(R.string.default_max_reward_main)
    var applovinRewardMediumId: String = context.getString(R.string.default_max_reward_medium)
    var applovinRewardHighId: String = context.getString(R.string.default_max_reward_high)

    private var maxNativeAdLoader: MaxNativeAdLoader? = null
    private var maxCurrentNativeAd: MaxAd? = null
    var maxCurrentNativeAdView: MaxNativeAdView? = null

    @Volatile
    private var isLoadingMaxNative = false
    private var nativeStartTimeMax: Long = 0L

    private var maxBannerAdTrickView: MaxAdView? = null
    private var lastTimeExpandBanner: Long = 0
    private var maxBannerAdTrickLoaded: Boolean = false

    private var maxMrecAdTrickView: MaxAdView? = null
    private var lastTimeExpandMrec: Long = 0
    private var maxMrecAdTrickLoaded: Boolean = false

    private lateinit var lastActivityBannerUsing: Activity
    private var cappingBannerExpandTemp: Int? = null
    private var expandBannerPercentTemp: Int? = null
    private lateinit var overlayBannerTricky: FrameLayout
    private var reversedTricky: Boolean = false

    private lateinit var lastActivityMrecUsing: Activity
    private var cappingMrecExpandTemp: Int? = null
    private var expandMrecPercentTemp: Int? = null
    private lateinit var overlayMrecTricky: FrameLayout

    var isLoadingNative: Boolean = false
    private var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager

    var loadNativeAdCompletion: ((Boolean, NativeAd?) -> Unit)? = null
    var currentNativeAd: NativeAd? = null

    var TAG: String = "AdsManager"

    init {
        googleMobileAdsConsentManager = GoogleMobileAdsConsentManager.getInstance(context)
        setAdIds(context)
    }

    fun setAdIds(context: Context) {
        applovinInterMainId = PreferencesHelper.getInstance().getString(Constants.max_inter_main) ?: context.getString(R.string.default_max_inter_main)
        applovinInterMediumId = PreferencesHelper.getInstance().getString(Constants.max_inter_medium) ?: context.getString(R.string.default_max_inter_medium)
        applovinInterHighId = PreferencesHelper.getInstance().getString(Constants.max_inter_high) ?: context.getString(R.string.default_max_inter_high)
        applovinOpenadsId = PreferencesHelper.getInstance().getString(Constants.android_max_open_app) ?: context.getString(R.string.default_max_open_id)
        applovinNativeId = PreferencesHelper.getInstance().getString(Constants.android_max_native) ?: context.getString(R.string.default_max_native_id)
        applovinBannerId = PreferencesHelper.getInstance().getString(Constants.android_max_banner) ?: context.getString(R.string.default_max_banner_id)
        applovinMrecId = PreferencesHelper.getInstance().getString(Constants.android_max_mrec) ?: context.getString(R.string.default_max_mrec_id)
        applovinRewardMainId = PreferencesHelper.getInstance().getString(Constants.max_reward_main) ?: context.getString(R.string.default_max_reward_main)
        applovinRewardMediumId = PreferencesHelper.getInstance().getString(Constants.max_reward_medium) ?: context.getString(R.string.default_max_reward_medium)
        applovinRewardHighId = PreferencesHelper.getInstance().getString(Constants.max_reward_high) ?: context.getString(R.string.default_max_reward_high)
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
        val binder = MaxNativeAdViewBinder.Builder(layoutResource ?: R.layout.native_custom_ad_view)
            .setTitleTextViewId(R.id.title_text_view)
            .setBodyTextViewId(R.id.body_text_view)
            .setAdvertiserTextViewId(R.id.advertiser_textView)
            .setIconImageViewId(R.id.icon_image_view)
            .setMediaContentViewGroupId(R.id.media_view_container)
            .setCallToActionButtonId(R.id.cta_button)
            .build()

        return MaxNativeAdView(binder, context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AppLovinSdkUtils.dpToPx(context, 300)
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }
    }

    fun loadApplovinInlineAdaptiveBannerWithTricky(
        activity: Activity,
        type: DisabledAdType,
        adPosition: AdPosition,
        bannerContainer: FrameLayout,
        retryCount: Int = RETRY_COUNT,
        onAdLoaded: (() -> Unit),
        cappingBannerExpand: Int? = null,
        expandPercent: Int? = null,
        reversedTricky: Boolean = false
    ) {
        if (retryCount < 0) return

        cappingBannerExpandTemp = cappingBannerExpand
        expandBannerPercentTemp = expandPercent
        lastActivityBannerUsing = activity
        this.reversedTricky = reversedTricky

        lastActivityBannerUsing.safeRunOnUiThread {
            val remoteDisabled = remoteAdDisabled(type)
            overlayBannerTricky = bannerContainer
            if (!googleMobileAdsConsentManager.canRequestAds || remoteDisabled) {
                overlayBannerTricky.visibility = View.GONE
                return@safeRunOnUiThread
            }
            overlayBannerTricky.visibility = View.VISIBLE
            overlayBannerTricky.setBackgroundColor(activity.getColor(if (BuildConfig.DEBUG) R.color.yellow_button_disabled else R.color.transparent))

            var needCreateNew = false
            val current = maxBannerAdTrickView

            if (current != null) {
                (current.parent as? ViewGroup)?.removeView(current)
                maxBannerAdTrickLoaded = true
            } else {
                needCreateNew = true
            }

            if (needCreateNew) {
                val width = ViewGroup.LayoutParams.MATCH_PARENT
                val heightDp = MaxAdFormat.BANNER.getAdaptiveSize(context).height
                val heightPx = AppLovinSdkUtils.dpToPx(context, heightDp)

                val config = MaxAdViewConfiguration.builder()
                    .setAdaptiveType(MaxAdViewConfiguration.AdaptiveType.ANCHORED)
                    .build()
                maxBannerAdTrickView = MaxAdView(applovinBannerId, config)
                maxBannerAdTrickView?.setBackgroundColor(Color.TRANSPARENT)
                maxBannerAdTrickView?.layoutParams = FrameLayout.LayoutParams(width, heightPx)

                maxBannerAdTrickView?.setRevenueListener { maxAd ->
                    PaidAdEventCounter.increment(PaidAdEventCounter.Kind.BANNER, maxAd.revenue)
                }

                maxBannerAdTrickView?.setListener(object : MaxAdViewAdListener {
                    override fun onAdLoaded(ad: MaxAd) {
                        lastActivityBannerUsing.safeRunOnUiThread {
                            maxBannerAdTrickLoaded = true
                            onAdLoaded()
                        }
                    }

                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                        lastActivityBannerUsing.safeRunOnUiThread {
                            maxBannerAdTrickLoaded = false
                        }
                    }

                    override fun onAdDisplayed(ad: MaxAd) {}
                    override fun onAdHidden(ad: MaxAd) {}
                    override fun onAdClicked(ad: MaxAd) {}
                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                    override fun onAdExpanded(ad: MaxAd) {}
                    override fun onAdCollapsed(ad: MaxAd) {}
                })

                maxBannerAdTrickView?.loadAd()
            }

            overlayBannerTricky.removeAllViews()
            maxBannerAdTrickView?.let { safeView ->
                overlayBannerTricky.addView(safeView)
            } ?: run {
                overlayBannerTricky.visibility = View.GONE
            }

            if (!needCreateNew && maxBannerAdTrickLoaded) {
                onAdLoaded()
            }
        }
    }

    fun loadApplovinMrecWithTricky(
        activity: Activity,
        type: DisabledAdType,
        adPosition: AdPosition,
        retryCount: Int = RETRY_COUNT,
        mrecContainer: FrameLayout,
        onAdLoaded: (() -> Unit),
        cappingBannerExpand: Int? = null,
        expandPercent: Int? = null
    ) {
        if (retryCount < 0) return

        lastActivityMrecUsing = activity
        cappingMrecExpandTemp = cappingBannerExpand
        expandMrecPercentTemp = expandPercent

        lastActivityMrecUsing.safeRunOnUiThread {
            overlayMrecTricky = mrecContainer
            val remoteDisabled = remoteAdDisabled(type)
            if (!googleMobileAdsConsentManager.canRequestAds || remoteDisabled) {
                overlayMrecTricky.visibility = View.GONE
                return@safeRunOnUiThread
            }
            overlayMrecTricky.visibility = View.VISIBLE
            overlayMrecTricky.setBackgroundColor(activity.getColor(if (BuildConfig.DEBUG) R.color.blue_button_disabled else R.color.transparent))

            val widthPx = AppLovinSdkUtils.dpToPx(context, 300)
            val heightPx = AppLovinSdkUtils.dpToPx(context, 250)

            var needCreateNew = false
            val current = maxMrecAdTrickView

            if (current != null) {
                (current.parent as? ViewGroup)?.removeView(current)
                maxMrecAdTrickLoaded = true
            } else {
                needCreateNew = true
            }

            if (needCreateNew) {
                maxMrecAdTrickView = MaxAdView(applovinMrecId, MaxAdFormat.MREC, activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        widthPx,
                        heightPx,
                        Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    )
                    setBackgroundColor(Color.TRANSPARENT)
                }

                maxMrecAdTrickView?.setRevenueListener { maxAd ->
                    PaidAdEventCounter.increment(PaidAdEventCounter.Kind.MREC, maxAd.revenue)
                }

                maxMrecAdTrickView?.setListener(object : MaxAdViewAdListener {
                    override fun onAdLoaded(ad: MaxAd) {
                        lastActivityMrecUsing.safeRunOnUiThread {
                            maxMrecAdTrickLoaded = true
                            onAdLoaded()
                        }
                    }

                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                        lastActivityMrecUsing.safeRunOnUiThread {
                            maxMrecAdTrickLoaded = false
                        }
                    }

                    override fun onAdDisplayed(ad: MaxAd) {}
                    override fun onAdHidden(ad: MaxAd) {}
                    override fun onAdClicked(ad: MaxAd) {}
                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                    override fun onAdExpanded(ad: MaxAd) {}
                    override fun onAdCollapsed(ad: MaxAd) {}
                })

                maxMrecAdTrickView?.loadAd()
            }

            overlayMrecTricky.removeAllViews()
            maxMrecAdTrickView?.let { safeView ->
                overlayMrecTricky.addView(safeView)
            } ?: run {
                overlayMrecTricky.visibility = View.GONE
            }

            if (!needCreateNew && maxMrecAdTrickLoaded) {
                onAdLoaded()
            }
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
    }
}
