package com.example.epub_reader.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.appsflyer.AFAdRevenueData
import com.appsflyer.AFInAppEventParameterName
import com.appsflyer.AdRevenueScheme
import com.appsflyer.AppsFlyerLib
import com.appsflyer.MediationNetwork
import com.google.android.gms.ads.AdValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppsflyerEventTracking {

    companion object {
        private var TAG: String = "AppsflyerEventTracking"
        private val appsflyer: AppsFlyerLib = AppsFlyerLib.getInstance()

        fun logEventAppsflyer(
            context: Context?,
            ad_unit: String?,
            ad_type: String,
            adValue: AdValue,
            network: String?
        ) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val adRevenueData = AFAdRevenueData(
                        network ?: "",
                        MediationNetwork.GOOGLE_ADMOB,
                        "USD",
                        adValue.valueMicros / 1000000.0
                    )

                    val additionalParameters: MutableMap<String, Any> = HashMap()
                    additionalParameters[AdRevenueScheme.COUNTRY] = "US"
                    additionalParameters[AdRevenueScheme.AD_UNIT] = ad_unit ?: "USD"
                    additionalParameters[AdRevenueScheme.AD_TYPE] = ad_type
                    additionalParameters["value"] = adValue.valueMicros / 1000000.0
                    appsflyer.logAdRevenue(adRevenueData, additionalParameters)
                } catch (e: Exception) {
                    Log.e("AppsflyerEventTracking", e.message.toString())
                }
            }
        }

        fun logEventAppsflyerMax(
            context: Context?,
            adUnitId: String,
            adType: String,
            revenueUsd: Double,
            network: String?
        ) {
            try {
                val adRevenueData = AFAdRevenueData(
                    network ?: "applovin",
                    MediationNetwork.APPLOVIN_MAX,
                    "USD",
                    revenueUsd
                )

                val additionalParameters: MutableMap<String, Any> = HashMap()
                additionalParameters[AdRevenueScheme.AD_UNIT] = adUnitId
                additionalParameters[AdRevenueScheme.AD_TYPE] = adType
                additionalParameters["ad_platform"] = "Applovin"
                additionalParameters["ad_monetizationNetwork"] = network ?: "applovin"
                additionalParameters["value"] = revenueUsd

                appsflyer.logAdRevenue(adRevenueData, additionalParameters)
            } catch (e: Exception) {
                Log.e(TAG, "AppsFlyer MAX revenue log error", e)
            }
        }
    }
}
