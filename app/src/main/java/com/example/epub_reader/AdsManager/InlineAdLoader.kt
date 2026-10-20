package com.example.epub_reader.AdsManager

import android.app.Activity
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxAdViewConfiguration
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinSdkUtils
import com.appsflyer.AppsFlyerAdNetworkEventType
import com.example.epub_reader.BaseApp
import com.example.epub_reader.utils.AdFormat
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.AppsflyerEventTracking
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.EventName
import com.example.epub_reader.utils.FirebaseEventTracking
import com.example.epub_reader.utils.remoteAdDisabled

/**
 * MAX banner (adaptive) and MREC (300 x 250) for one screen.
 *
 * Every call creates its own MaxAdView inside [container]; the screen calls destroy() on it in onDestroy.
 * The container stays as the screen left it: the screen shows it in `onResult(true)`, so a request that fails
 * never leaves an empty box. The ad view only covers its own container (no overlay, no enlarged click area).
 */
object InlineAdLoader {

    /** Adaptive banner (full width). Returns null when no request was made (no consent, premium, turned off remotely). */
    fun loadBanner(
        activity: Activity,
        container: FrameLayout,
        type: DisabledAdType,
        adPosition: AdPosition,
        onResult: (loaded: Boolean) -> Unit
    ): MaxAdView? = load(activity, MaxAdFormat.BANNER, container, type, adPosition, onResult)

    /** MREC, 300 x 250 dp, centred in [container] */
    fun loadMrec(
        activity: Activity,
        container: FrameLayout,
        type: DisabledAdType,
        adPosition: AdPosition,
        onResult: (loaded: Boolean) -> Unit
    ): MaxAdView? = load(activity, MaxAdFormat.MREC, container, type, adPosition, onResult)

    /** Height of the adaptive banner on this device, in px */
    fun bannerHeightPx(activity: Activity): Int =
        AppLovinSdkUtils.dpToPx(activity, MaxAdFormat.BANNER.getAdaptiveSize(activity).height)

    private fun load(
        activity: Activity,
        format: MaxAdFormat,
        container: FrameLayout,
        type: DisabledAdType,
        adPosition: AdPosition,
        onResult: (Boolean) -> Unit
    ): MaxAdView? {
        val context = activity.applicationContext
        if (!GoogleMobileAdsConsentManager.getInstance(context).canRequestAds || remoteAdDisabled(type)) {
            return null
        }

        val ads = AdsManager.getInstance(context)
        val isMrec = format == MaxAdFormat.MREC
        val adView = if (isMrec) {
            MaxAdView(ads.applovinMrecId, MaxAdFormat.MREC)
        } else {
            val config = MaxAdViewConfiguration.builder()
                .setAdaptiveType(MaxAdViewConfiguration.AdaptiveType.ANCHORED)
                .build()
            MaxAdView(ads.applovinBannerId, MaxAdFormat.BANNER, config)
        }
        val size = if (isMrec) MaxAdFormat.MREC.size else MaxAdFormat.BANNER.getAdaptiveSize(activity)
        adView.layoutParams = FrameLayout.LayoutParams(
            if (isMrec) AppLovinSdkUtils.dpToPx(context, size.width) else ViewGroup.LayoutParams.MATCH_PARENT,
            AppLovinSdkUtils.dpToPx(context, size.height),
            Gravity.CENTER
        )
        adView.setBackgroundColor(Color.TRANSPARENT)

        val revenueKind = if (isMrec) PaidAdEventCounter.Kind.MREC else PaidAdEventCounter.Kind.BANNER
        adView.setRevenueListener { ad ->
            PaidAdEventCounter.increment(revenueKind, ad.revenue)
            FirebaseEventTracking.adTracking(
                context = context,
                event = EventName.ad_revenue_paid,
                adFormat = AdFormat.banner,
                platform = "applovin_max",
                network = ad.networkName,
                position = adPosition,
                value = ad.revenue
            )
            AppsflyerEventTracking.logEventAppsflyerMax(
                context = BaseApp.instance.currentActivity ?: context,
                adUnitId = ad.adUnitId,
                adType = AppsFlyerAdNetworkEventType.BANNER.toString(),
                revenueUsd = ad.revenue,
                network = ad.networkName
            )
        }

        val startedAt = SystemClock.elapsedRealtime()
        var answered = false
        adView.setListener(object : MaxAdViewAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                track(context, EventName.ad_loaded, ad.networkName, adPosition, 1, SystemClock.elapsedRealtime() - startedAt)
                if (!answered) {
                    answered = true
                    onResult(true)
                }
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                track(context, EventName.ad_loaded, null, adPosition, 0, SystemClock.elapsedRealtime() - startedAt)
                if (!answered) {
                    answered = true
                    onResult(false)
                }
            }

            override fun onAdDisplayed(ad: MaxAd) {}
            override fun onAdHidden(ad: MaxAd) {}
            override fun onAdClicked(ad: MaxAd) {}
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
            override fun onAdExpanded(ad: MaxAd) {}
            override fun onAdCollapsed(ad: MaxAd) {}
        })

        container.removeAllViews()
        container.addView(adView)
        adView.loadAd()
        return adView
    }

    private fun track(
        context: android.content.Context,
        event: EventName,
        network: String?,
        adPosition: AdPosition,
        result: Int,
        duration: Long
    ) {
        FirebaseEventTracking.adTracking(
            context = context,
            event = event,
            adFormat = AdFormat.banner,
            platform = "applovin_max",
            network = network,
            position = adPosition,
            result = result,
            duration = duration
        )
    }
}
