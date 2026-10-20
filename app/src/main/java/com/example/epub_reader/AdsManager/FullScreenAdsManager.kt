package com.example.epub_reader.AdsManager

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdRevenueListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.appsflyer.AppsFlyerAdNetworkEventType
import com.example.epub_reader.BaseApp
import com.example.epub_reader.LocalizeBaseActivity
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
import com.example.epub_reader.utils.safeRunOnUiThread
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.launch
import java.util.Date

enum class AdLevel {
    high,
    medium,
    main
}

class FullScreenAdsManager(var context: Context) {
    private var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager

    var interstitialAdDisplaying: Boolean = false
    private var countDownTimer: CountDownTimer? = null
    private var countDown: Int = 0
    private var interstitialAdCompletion: ((Boolean) -> Unit)? = null
    private var rewardedAdCompletion: ((Boolean) -> Unit)? = null
    private var loadAdCompletion: (() -> Unit)? = null

    private var adLoadingFragment: AdLoadingFragment? = null

    private var maxInter: MaxInterstitialAd? = null
    private var maxInterIsLoading: Boolean = false
    private var maxInterShouldShowNow: Boolean = false

    private var maxRewarded: MaxRewardedAd? = null
    private var maxRewardedIsLoading: Boolean = false
    private var maxRewardedDisplaying: Boolean = false
    private var maxRewardedSuccess: Boolean = false

    init {
        googleMobileAdsConsentManager = GoogleMobileAdsConsentManager.getInstance(context)
    }

    fun showAdLoadingFragment() {
        try {
            Log.d("AdLog", "⏳ [FullScreenAdsManager] Showing Ad Loading Fragment...")
            val activity = BaseApp.instance.currentActivity
            val fm = (BaseApp.instance.currentActivity as LocalizeBaseActivity).supportFragmentManager
            val existingFragment = fm.findFragmentByTag("AdLoadingFragment")
            if (existingFragment == null) {
                adLoadingFragment = AdLoadingFragment()
                (activity as? LifecycleOwner)?.lifecycleScope?.launchWhenResumed {
                    if (!fm.isStateSaved) {
                        adLoadingFragment?.show(fm, "AdLoadingFragment")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createLoadingTimer() {
        countDownTimer?.cancel()
        countDown = 20

        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 500) {
            override fun onTick(millisUntilFinished: Long) {
                countDown -= 1
                if (countDown == 0) {
                    countDownTimer?.cancel()
                    countDownTimer = null
                    val activity = BaseApp.instance.currentActivity
                    (activity as? LifecycleOwner)?.lifecycleScope?.launch {
                        rewardedAdCompletion?.invoke(true)
                        resetRewardedAdStatus()

                        loadAdCompletion?.let { it() }
                        interstitialAdCompletion?.invoke(false)
                        interstitialAdCompletion = null
                        if (adLoadingFragment != null && adLoadingFragment!!.isAdded) {
                            adLoadingFragment!!.dismissAllowingStateLoss()
                        }
                        adLoadingFragment = null
                    }
                }
            }

            override fun onFinish() {}
        }
        countDownTimer?.start()
    }

    fun showInterstitialApplovinIfAvailable(
        activity: Activity,
        adPosition: AdPosition,
        isOpenAd: Boolean,
        ignoreCooldown: Boolean = false,
        completion: (Boolean) -> Unit
    ) {
        val isPremium = PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)
        val didCooldown = if (ignoreCooldown) true else BaseApp.canShowAd(activity)

        if (!activity.isValidActivity() ||
            isPremium ||
            !googleMobileAdsConsentManager.canRequestAds ||
            interstitialAdDisplaying ||
            !didCooldown
        ) {
            Log.d("AdLog", "⚠️ [Interstitial] Skipped show. Valid: ${activity.isValidActivity()}, Premium: $isPremium, Consent: ${googleMobileAdsConsentManager.canRequestAds}, Displaying: $interstitialAdDisplaying, Cooldown: $didCooldown")
            completion(false)
            return
        }

        if (maxInter?.isReady == true) {
            Log.d("AdLog", "🚀 [Interstitial] Ad is ready. Showing immediately for position: $adPosition")
            showInterstitialApplovinAd(isOpenAd, adPosition, completion)
        } else {
            Log.d("AdLog", "⌛ [Interstitial] Ad not ready yet. Loading now for position: $adPosition")
            interstitialAdCompletion = completion
            maxInterShouldShowNow = true
            loadInterstitialApplovinAd(isOpenAd, adPosition)
            createLoadingTimer()
            showAdLoadingFragment()
        }
    }

    fun loadInterstitialApplovinAd(isOpenAd: Boolean, adPosition: AdPosition) {
        val interstitialAdDisabled = remoteAdDisabled(DisabledAdType.interstitial)
        if (interstitialAdDisabled) return
        if (maxInterIsLoading || maxInter?.isReady == true) return

        val activity = BaseApp.instance.currentActivity
        activity?.safeRunOnUiThread {
            maxInterIsLoading = true
            loadInternalInterAd(AdLevel.high, isOpenAd, adPosition) { success ->
                handleCompleteLoadingInterAd(isOpenAd, adPosition, success) {
                    loadInternalInterAd(AdLevel.medium, isOpenAd, adPosition) { success2 ->
                        handleCompleteLoadingInterAd(isOpenAd, adPosition, success2) {
                            loadInternalInterAd(AdLevel.main, isOpenAd, adPosition) { success3 ->
                                handleCompleteLoadingInterAd(isOpenAd, adPosition, success3)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleCompleteLoadingInterAd(isOpenAd: Boolean, adPosition: AdPosition, success: Boolean, continueWaterfall: (() -> Unit)? = null) {
        val activity = BaseApp.instance.currentActivity
        activity?.safeRunOnUiThread {
            if (success) {
                safeDismissLoading()
                if (maxInterShouldShowNow) {
                    countDownTimer?.cancel()
                    countDownTimer = null
                    showInterstitialApplovinAd(isOpenAd, adPosition) { shown ->
                        interstitialAdCompletion?.let { it(shown) }
                        interstitialAdCompletion = null
                        maxInterShouldShowNow = false
                    }
                }
            } else if (continueWaterfall != null) {
                continueWaterfall.invoke()
            } else {
                maxInterIsLoading = false
                maxInter = null
                safeDismissLoading()

                if (countDown > 0 && maxInterShouldShowNow) {
                    countDownTimer?.cancel()
                    countDownTimer = null
                    interstitialAdCompletion?.invoke(false)
                    interstitialAdCompletion = null
                    maxInterShouldShowNow = false
                }
            }
        }
    }

    private fun loadInternalInterAd(adLevel: AdLevel, isOpenAd: Boolean, adPosition: AdPosition, completion: ((Boolean) -> Unit)? = null) {
        BaseApp.instance.currentActivity?.safeRunOnUiThread {
            val adUnitId = when (adLevel) {
                AdLevel.high -> AdsManager.getInstance(context).applovinInterHighId
                AdLevel.medium -> AdsManager.getInstance(context).applovinInterMediumId
                AdLevel.main -> AdsManager.getInstance(context).applovinInterMainId
            }

            Log.d("AdLog", "📥 [Interstitial] Loading Interstitial level: $adLevel, Unit ID: $adUnitId, Position: $adPosition")
            maxInterIsLoading = true
            val timeLoadStart = System.currentTimeMillis()
            maxInter = MaxInterstitialAd(adUnitId)
            maxInter?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d("AdLog", "✅ [Interstitial] Loaded SUCCESS! Level: $adLevel, Network: ${ad.networkName}, UnitID: $adUnitId")
                    maxInterIsLoading = false
                    BaseApp.instance.currentActivity?.safeRunOnUiThread {
                        maxInter?.setRevenueListener(MaxAdRevenueListener { maxAd ->
                            PaidAdEventCounter.increment(PaidAdEventCounter.Kind.INTERSTITIAL, maxAd.revenue)
                            val activity = BaseApp.instance.currentActivity
                            activity?.safeRunOnUiThread {
                                FirebaseEventTracking.adTracking(
                                    context = context,
                                    event = EventName.ad_revenue_paid,
                                    adFormat = AdFormat.interstitial,
                                    platform = "applovin_max",
                                    network = maxAd.networkName,
                                    position = adPosition,
                                    value = maxAd.revenue
                                )
                                AppsflyerEventTracking.logEventAppsflyerMax(
                                    context = BaseApp.instance.currentActivity ?: BaseApp.instance,
                                    adUnitId = maxAd.adUnitId,
                                    adType = AppsFlyerAdNetworkEventType.INTERSTITIAL.toString(),
                                    revenueUsd = maxAd.revenue,
                                    network = maxAd.networkName
                                )
                            }
                        })

                        FirebaseEventTracking.adTracking(
                            context = context,
                            event = EventName.ad_loaded,
                            adFormat = AdFormat.interstitial,
                            platform = "applovin_max",
                            network = ad.networkName,
                            position = adPosition,
                            result = 1,
                            duration = System.currentTimeMillis() - timeLoadStart
                        )

                        completion?.invoke(true)
                    }
                }

                override fun onAdLoadFailed(adUnitId: String, p1: MaxError) {
                    Log.e("AdLog", "❌ [Interstitial] Load FAILED! Level: $adLevel, UnitID: $adUnitId, Error: ${p1.message} (code: ${p1.code})")
                    maxInterIsLoading = false
                    BaseApp.instance.currentActivity?.safeRunOnUiThread {
                        try {
                            maxInter = null
                            FirebaseEventTracking.adTracking(
                                context = context,
                                event = EventName.ad_loaded,
                                adFormat = AdFormat.interstitial,
                                platform = "applovin_max",
                                network = null,
                                position = adPosition,
                                result = 0,
                                duration = System.currentTimeMillis() - timeLoadStart
                            )
                            completion?.invoke(false)
                        } catch (_: Exception) {}
                    }
                }

                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdHidden(ad: MaxAd) {
                    BaseApp.instance.currentActivity?.safeRunOnUiThread {
                        BaseApp.lastMaxAdShown = Date().time
                        interstitialAdDisplaying = false
                    }
                }

                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
            })

            maxInter?.loadAd()
        }
    }

    fun safeDismissLoading() {
        adLoadingFragment?.let { fragment ->
            if (fragment.isAdded) {
                fragment.dismissAllowingStateLoss()
            }
        }
        adLoadingFragment = null
    }

    var completionShowInterApplovinCalled = false

    fun safeComplete(shown: Boolean, completion: (Boolean) -> Unit) {
        if (!completionShowInterApplovinCalled) {
            completionShowInterApplovinCalled = true
            completion(shown)
        }
    }

    fun showInterstitialApplovinAd(
        isOpenAd: Boolean,
        adPosition: AdPosition,
        completion: (Boolean) -> Unit
    ) {
        val activity = BaseApp.instance.currentActivity
        if (maxInter == null || maxInter?.isReady != true || activity?.isValidActivity() != true || interstitialAdDisplaying) {
            safeComplete(false, completion)
            return
        }

        val timeStart = System.currentTimeMillis()
        interstitialAdDisplaying = true

        maxInter?.setListener(object : MaxAdListener {
            override fun onAdLoaded(p0: MaxAd) {}

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d("AdLog", "📺 [Interstitial] SHOWING Interstitial Ad on screen! Network: ${ad.networkName}, Position: $adPosition")
                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_displayed,
                    adFormat = AdFormat.interstitial,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    result = 1
                )
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d("AdLog", "🙈 [Interstitial] Interstitial Ad CLOSED. Network: ${ad.networkName}")
                interstitialAdDisplaying = false
                BaseApp.lastMaxAdShown = Date().time

                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_closed,
                    adFormat = AdFormat.interstitial,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    duration = System.currentTimeMillis() - timeStart
                )

                maxInter = null
                loadInterstitialApplovinAd(false, adPosition)
                safeComplete(true, completion)
            }

            override fun onAdClicked(ad: MaxAd) {}
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.e("AdLog", "❌ [Interstitial] Display FAILED! Error: ${error.message} (code: ${error.code})")
                interstitialAdDisplaying = false
                maxInter = null
                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_displayed,
                    adFormat = AdFormat.interstitial,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    result = 0
                )
                safeComplete(false, completion)
            }
        })

        maxInter?.showAd()
    }

    fun loadApplovinRewardedAd(adPosition: AdPosition) {
        val rewardedAdAdDisabled = remoteAdDisabled(DisabledAdType.rewarded)
        if (rewardedAdAdDisabled) return
        if (maxRewarded?.isReady == true) return

        loadInternalRewardedAd(AdLevel.high, adPosition) { success ->
            handleCompleteLoadingRewardedAd(adPosition, success) {
                loadInternalRewardedAd(AdLevel.medium, adPosition) { success2 ->
                    handleCompleteLoadingRewardedAd(adPosition, success2) {
                        loadInternalRewardedAd(AdLevel.main, adPosition) { success3 ->
                            handleCompleteLoadingRewardedAd(adPosition, success3)
                        }
                    }
                }
            }
        }
    }

    private fun handleCompleteLoadingRewardedAd(adPosition: AdPosition, success: Boolean, continueWaterfall: (() -> Unit)? = null) {
        val activity = BaseApp.instance.currentActivity
        activity?.safeRunOnUiThread {
            if (success) {
                maxRewardedIsLoading = false
                if (countDown > 0) {
                    countDownTimer?.cancel()
                    countDownTimer = null
                }
                safeDismissLoading()
                rewardedAdCompletion?.let { showRewardedApplovinVideo(activity, adPosition, it) }
            } else if (continueWaterfall != null) {
                continueWaterfall.invoke()
            } else {
                maxRewarded = null
                if (countDown > 0) {
                    countDownTimer?.cancel()
                    countDownTimer = null
                }
                safeDismissLoading()
                rewardedAdCompletion?.invoke(true)
                resetRewardedAdStatus()
            }
        }
    }

    fun loadInternalRewardedAd(adLevel: AdLevel, adPosition: AdPosition, completion: ((Boolean) -> Unit)? = null) {
        maxRewardedIsLoading = true
        val timeLoadStart = System.currentTimeMillis()

        maxRewarded?.setListener(null)
        maxRewarded = null

        val adUnitId = when (adLevel) {
            AdLevel.high -> AdsManager.getInstance(context).applovinRewardHighId
            AdLevel.medium -> AdsManager.getInstance(context).applovinRewardMediumId
            AdLevel.main -> AdsManager.getInstance(context).applovinRewardMainId
        }

        Log.d("AdLog", "📥 [Rewarded] Loading Rewarded Ad level: $adLevel, Unit ID: $adUnitId, Position: $adPosition")
        maxRewarded = MaxRewardedAd.getInstance(adUnitId)
        maxRewarded?.setListener(object : MaxAdListener, MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d("AdLog", "✅ [Rewarded] Loaded SUCCESS! Network: ${ad.networkName}, Unit ID: $adUnitId")
                maxRewardedIsLoading = false
                maxRewarded?.setRevenueListener(MaxAdRevenueListener { maxAd ->
                    PaidAdEventCounter.increment(PaidAdEventCounter.Kind.REWARDED, maxAd.revenue)
                    FirebaseEventTracking.adTracking(
                        context = context,
                        event = EventName.ad_revenue_paid,
                        adFormat = AdFormat.videoRewarded,
                        platform = "applovin_max",
                        network = maxAd.networkName,
                        position = adPosition,
                        value = maxAd.revenue
                    )
                })

                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_loaded,
                    adFormat = AdFormat.videoRewarded,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    result = 1,
                    duration = System.currentTimeMillis() - timeLoadStart
                )

                completion?.invoke(true)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.e("AdLog", "❌ [Rewarded] Load FAILED! Unit ID: $adUnitId, Error: ${error.message} (code: ${error.code})")
                maxRewarded = null
                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_loaded,
                    adFormat = AdFormat.videoRewarded,
                    platform = "applovin_max",
                    network = null,
                    position = adPosition,
                    result = 0,
                    duration = System.currentTimeMillis() - timeLoadStart
                )
                completion?.invoke(false)
            }

            override fun onUserRewarded(ad: MaxAd, reward: com.applovin.mediation.MaxReward) {
                maxRewardedSuccess = true
            }

            override fun onAdDisplayed(ad: MaxAd) {}
            override fun onAdHidden(ad: MaxAd) {
                BaseApp.lastMaxAdShown = Date().time
                rewardedAdCompletion?.invoke(maxRewardedSuccess)
                resetRewardedAdStatus()
            }

            override fun onAdClicked(ad: MaxAd) {}
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                rewardedAdCompletion?.invoke(true)
                resetRewardedAdStatus()
            }
        })

        maxRewarded?.loadAd()
    }

    fun showRewardedApplovinVideo(
        activity: Activity,
        adPosition: AdPosition,
        completion: (Boolean) -> Unit
    ) {
        if (maxRewardedDisplaying) return
        if (!activity.isValidActivity()) {
            completion(true)
            resetRewardedAdStatus()
            return
        }

        if (maxRewarded == null || maxRewarded?.isReady != true) {
            rewardedAdCompletion = completion
            loadApplovinRewardedAd(adPosition)
            createLoadingTimer()
            showAdLoadingFragment()
            return
        }

        val timeStart = System.currentTimeMillis()
        maxRewardedSuccess = false
        maxRewardedDisplaying = true

        maxRewarded?.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(p0: MaxAd) {}
            override fun onAdDisplayed(ad: MaxAd) {
                Log.d("AdLog", "📺 [Rewarded] SHOWING Rewarded Ad on screen! Network: ${ad.networkName}, Position: $adPosition")
                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_displayed,
                    adFormat = AdFormat.videoRewarded,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    result = 1
                )
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d("AdLog", "🙈 [Rewarded] Rewarded Ad CLOSED. Network: ${ad.networkName}, Success: $maxRewardedSuccess")
                BaseApp.lastMaxAdShown = Date().time
                maxRewarded = null
                completion(maxRewardedSuccess)
                resetRewardedAdStatus()

                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_closed,
                    adFormat = AdFormat.videoRewarded,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    duration = System.currentTimeMillis() - timeStart
                )

                loadApplovinRewardedAd(adPosition)
            }

            override fun onAdClicked(ad: MaxAd) {}
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                maxRewarded = null
                completion(true)
                resetRewardedAdStatus()

                FirebaseEventTracking.adTracking(
                    context = context,
                    event = EventName.ad_displayed,
                    adFormat = AdFormat.videoRewarded,
                    platform = "applovin_max",
                    network = ad.networkName,
                    position = adPosition,
                    result = 0
                )
            }

            override fun onUserRewarded(ad: MaxAd, reward: com.applovin.mediation.MaxReward) {
                maxRewardedSuccess = true
            }
        })

        maxRewarded?.showAd()
    }

    fun showRewardedApplovinAdIfAvailable(
        activity: Activity,
        adPosition: AdPosition,
        completion: (Boolean) -> Unit
    ) {
        if (maxRewardedDisplaying) return
        val isPremium = PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)

        if (!activity.isValidActivity() || isPremium || !googleMobileAdsConsentManager.canRequestAds) {
            completion(true)
            resetRewardedAdStatus()
            return
        }

        if (maxRewarded?.isReady == true) {
            showRewardedApplovinVideo(activity, adPosition, completion)
        } else {
            rewardedAdCompletion = completion
            loadApplovinRewardedAd(adPosition)
            createLoadingTimer()
            showAdLoadingFragment()
        }
    }

    fun resetRewardedAdStatus() {
        maxRewardedIsLoading = false
        maxRewardedDisplaying = false
        rewardedAdCompletion = null
        maxRewardedSuccess = false
    }
}
