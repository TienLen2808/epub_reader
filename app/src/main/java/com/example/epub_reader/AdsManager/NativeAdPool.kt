package com.example.epub_reader.AdsManager

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.example.epub_reader.BaseApp
import com.example.epub_reader.R
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.isAdmobLike
import com.example.epub_reader.utils.netKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NativeAdsPool(var context: Context) {

    companion object {
        @Volatile
        private var instance: NativeAdsPool? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = NativeAdsPool(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): NativeAdsPool {
            return instance ?: throw IllegalStateException(
                "NativeAdsPool not initialized. Call init(context) in Application.onCreate()"
            )
        }
    }

    private var itemAdMangers: MutableList<ApplovinNativeAdManager> = mutableListOf()
    private val itemAdMangersLock = Any()

    private var job: Job? = null
    private var startTime = System.currentTimeMillis()
    var contentTop: Int = 0
    var contentBottom: Int = 3000

    var contentLeft: Int = 0
    var contentRight: Int = 3000
    var currentAdIndex = -1
    var currentSkipReloadingAdIndex = -1

    val availableItemAd: ApplovinNativeAdManager?
        get() = getItemAd()
    val hasAvailableItemAd: Boolean
        get() = hasItemAd()

    var onAdSuccessLoaded: (() -> Unit)? = null

    fun createAdManagers(recreate: Boolean = false) {
        synchronized(itemAdMangersLock) {
            if (recreate) {
                itemAdMangers.forEach { it.removeAdFromParent() }
                itemAdMangers.clear()
            }

            if (BaseApp.instance.gatherConsentFinished.get() && itemAdMangers.isEmpty()) {
                for (i in 0..<3) {
                    itemAdMangers.add(ApplovinNativeAdManager(context).apply {
                        loadApplovinNativeAd(
                            context,
                            R.layout.native_custom_ad_banner,
                            AdPosition.preLoad
                        ) { success ->
                            if (success) {
                                onAdSuccessLoaded?.invoke()
                            }
                        }
                    })
                }
                startTimer()
            }
        }
    }

    fun startTimer() {
        stopTimer()
        var reloadTime = PreferencesHelper.getInstance().getInt("android_native_ad_reload_time_cap", 15)
        if (reloadTime < 15) {
            reloadTime = 15
        }
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(1000)
                if (!hasLoadedAd()) {
                    startTime = System.currentTimeMillis()
                }
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= reloadTime * 1000) {
                    withContext(Dispatchers.Main) {
                        reloadAds()
                        startTime = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun stopTimer() {
        job?.cancel()
        job = null
    }

    fun hasLoadedAd(): Boolean {
        val adLoadedItems = itemAdMangers.filter { it.adLoadedSuccess }
        return adLoadedItems.isNotEmpty()
    }

    fun reloadAds() {
        try {
            synchronized(itemAdMangersLock) {
                currentSkipReloadingAdIndex++
                if (currentSkipReloadingAdIndex >= itemAdMangers.size) {
                    currentSkipReloadingAdIndex = 0
                }
                val adLoadedItems = itemAdMangers.filter { it.adLoadedSuccess }
                if (adLoadedItems.size > 1) {
                    if (itemAdMangers[currentSkipReloadingAdIndex].adLoadedSuccess) {
                        for (i in itemAdMangers.indices) {
                            if (i != currentSkipReloadingAdIndex) {
                                itemAdMangers[i].reloadAd()
                            }
                        }
                    } else {
                        for (i in 1 until adLoadedItems.size) {
                            adLoadedItems[i].reloadAd()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasItemAd(): Boolean = synchronized(itemAdMangersLock) {
        for (i in itemAdMangers.indices) {
            if (itemAdMangers[i].adReady) {
                return@synchronized true
            }
        }
        false
    }

    private fun getItemAd(): ApplovinNativeAdManager? = synchronized(itemAdMangersLock) {
        var availableAd: ApplovinNativeAdManager? = null

        if (itemAdMangers.isEmpty()) {
            createAdManagers()
        }

        currentAdIndex++
        if (currentAdIndex > itemAdMangers.size - 1) {
            currentAdIndex = 0
        }
        for (i in currentAdIndex until itemAdMangers.size) {
            if (itemAdMangers[i].adReady) {
                availableAd = itemAdMangers[i]
                break
            }
        }

        if (availableAd == null) {
            for (i in 0 until currentAdIndex) {
                if (itemAdMangers[i].adReady) {
                    availableAd = itemAdMangers[i]
                    currentAdIndex = i
                    break
                }
            }
        }

        if (availableAd == null) {
            for (i in itemAdMangers.indices) {
                if (itemAdMangers[i].adLoadedSuccess) {
                    availableAd = itemAdMangers[i]
                    currentAdIndex = i
                    break
                }
            }
        }

        if (availableAd == null) {
            for (i in currentAdIndex until itemAdMangers.size) {
                if (!itemAdMangers[i].hasParent) {
                    availableAd = itemAdMangers[i]
                    break
                }
            }

            if (availableAd == null && itemAdMangers.size > currentAdIndex) {
                availableAd = itemAdMangers[currentAdIndex]
                availableAd?.removeAdFromParent()
            }
        }

        availableAd
    }

    fun removeAdsFromParent() {
        synchronized(itemAdMangersLock) {
            itemAdMangers.forEach { it.removeAdFromParent() }
        }
    }

    fun summaryLog() {
        synchronized(itemAdMangersLock) {
            Log.e(
                "NativeAdsPool", "items = ${itemAdMangers.size} \n" +
                        "loaded = ${itemAdMangers.filter { it.adLoadedSuccess }.size} " +
                        "\ninUse = ${itemAdMangers.filter { it.hasParent }.size}"
            )
        }
    }

    private var externalAd: MaxAd? = null
    private var externalAdView: MaxNativeAdView? = null
    private var cappingExpandTemp: Int? = null
    private var expandPercentTemp: Int? = null
    private var lastTimeExpand: Long = 0

    fun trickyManager(top: Int, bottom: Int, left: Int, right: Int): ApplovinNativeAdManager? {
        var result: ApplovinNativeAdManager? = null
        val displayingAds = synchronized(itemAdMangersLock) {
            itemAdMangers.filter { it.adLoadedSuccess && it.hasParent }
        }

        if (displayingAds.isNotEmpty()) {
            for (ad in displayingAds) {
                ad.maxCurrentNativeAdView?.let {
                    val location = IntArray(2)
                    it.getLocationInWindow(location)
                    val x = location[0]
                    val y = location[1]

                    if (y > top - 50 && y + it.measuredHeight < bottom + 50) {
                        result = ad
                    } else if (left >= 0 && right >= 0 && x > left - 50 && x + it.measuredWidth < right + 50) {
                        result = ad
                    }
                }
            }
        }
        return result
    }

    var trickyOverlay: FrameLayout? = null
    private val trickyRunnable = object : Runnable {
        override fun run() {
            showHideTrickyOverlay(contentTop, contentBottom, contentLeft, contentRight)
            Handler(Looper.getMainLooper()).postDelayed(this, 3000L)
        }
    }

    fun showHideTrickyOverlay(top: Int, bottom: Int, left: Int, right: Int) {
        val manager = trickyManager(top, bottom, left, right)
        val trickyAd = manager?.maxCurrentNativeAd
        val trickyAdView = manager?.maxCurrentNativeAdView
        if (trickyAd == null || trickyAdView == null || trickyOverlay == null) {
            return
        }

        val net = trickyAd.networkName ?: ""
        val netKey = net.netKey()
        val trickyConfig = getExpandConfigOfNetwork(context, netKey)

        val cappingTime = cappingExpandTemp ?: trickyConfig.first
        val scalePercent = expandPercentTemp ?: trickyConfig.second
        val closeTime = trickyConfig.third

        val canExpandCapping = (System.currentTimeMillis() - lastTimeExpand) > (cappingTime * 1000L)
        val canCollapse = (System.currentTimeMillis() - lastTimeExpand) > (closeTime * 1000L)
        val isAdmobLike = net.isAdmobLike()

        if (isAdmobLike && canExpandCapping && (cappingTime < 998) && (BaseApp.instance.android_onboarding_max_clicks > 0)) {
            lastTimeExpand = System.currentTimeMillis()
            trickyOverlay!!.tag = manager
            expandTrickyOverlay(trickyOverlay!!, trickyAdView, scalePercent)
        } else if (canCollapse) {
            collapseTrickyOverlay()
        }
    }

    private fun expandTrickyOverlay(
        overlay: FrameLayout,
        adView: MaxNativeAdView,
        expandPercent: Int = 100
    ) {
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.post {
            if (overlay.elevation < 1000f) overlay.elevation = 1000f
            val clamped = expandPercent.coerceIn(0, 100)
            val screenH = overlay.resources.displayMetrics.heightPixels
            val desiredRectH = ((screenH * (clamped / 100f)).toInt()).coerceAtLeast(1)
            val overlayH = if (overlay.height > 0) overlay.height else screenH
            val rectH = minOf(desiredRectH, overlayH)

            val left = 0
            val top = overlayH - rectH
            val right = overlay.width
            val bottom = overlayH

            val ctaButton = adView.findViewById<Button>(R.id.cta_button)
            overlay.touchDelegate = MyTouchDelegate(Rect(left, top, right, bottom), adView, ctaButton)
        }
    }

    fun collapseTrickyOverlay() {
        trickyOverlay?.let { overlay ->
            try {
                overlay.touchDelegate = null
            } catch (_: Throwable) {
            }
            overlay.setOnTouchListener(null)
            overlay.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun getExpandConfigOfNetwork(context: Context, network: String): Triple<Int, Int, Int> {
        if (network != "facebook") {
            return Triple(9999, 0, 10)
        }
        try {
            val config = PreferencesHelper.getInstance().getString(Constants.android_native_ad_expand)
            if (config != null) {
                val configJson = JSONObject(config)
                if (configJson.has(network)) {
                    val applovin = configJson.getJSONObject(network)
                    return Triple(applovin.getInt("time_cap"), applovin.getInt("scale"), applovin.getInt("time_close"))
                } else if (configJson.has("other")) {
                    val applovin = configJson.getJSONObject("other")
                    return Triple(applovin.getInt("time_cap"), applovin.getInt("scale"), applovin.getInt("time_close"))
                }
            }
            return Triple(9999, 0, 5)
        } catch (e: Exception) {
            return Triple(9999, 0, 5)
        }
    }

    fun onNativeAdClick() {
        collapseTrickyOverlay()
    }

    private fun removeAdManagerForView(view: View) {
        if (view is MaxNativeAdView) {
            Handler(Looper.getMainLooper()).postDelayed({
                val adManager2 = synchronized(itemAdMangersLock) {
                    itemAdMangers.firstOrNull { it.maxCurrentNativeAdView == view }
                }
                if (adManager2 != null) {
                    getInstance().collapseTrickyOverlay()
                    adManager2.removeAdFromParent()
                    adManager2.reloadAd()
                }
            }, 500)
        }
    }

    private class MyTouchDelegate(bounds: Rect, val delegateView: View, val ctaButton: Button?) : TouchDelegate(bounds, delegateView) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            var handled = super.onTouchEvent(event)
            if (!handled) {
                getInstance().removeAdManagerForView(delegateView)
                ctaButton?.performClick()
                if (BaseApp.instance.android_onboarding_max_clicks > 0) {
                    BaseApp.instance.android_onboarding_max_clicks--
                }
                handled = true
            } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                getInstance().removeAdManagerForView(delegateView)
                ctaButton?.performClick()
                if (BaseApp.instance.android_onboarding_max_clicks > 0) {
                    BaseApp.instance.android_onboarding_max_clicks--
                }
            }
            return handled
        }
    }
}
