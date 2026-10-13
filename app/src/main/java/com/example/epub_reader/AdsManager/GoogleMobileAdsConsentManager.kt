package com.example.epub_reader.AdsManager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.epub_reader.BaseApp
import com.example.epub_reader.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.android.ump.UserMessagingPlatform.OnConsentFormLoadFailureListener
import com.google.android.ump.UserMessagingPlatform.OnConsentFormLoadSuccessListener

class GoogleMobileAdsConsentManager private constructor(context: Context) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    fun interface OnConsentGatheringCompleteListener {
        fun consentGatheringComplete(error: FormError?)
    }

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() =
            consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener,
    ) {
        val paramsBuilder = ConsentRequestParameters.Builder()

        if (BuildConfig.DEBUG) {
            val debugBuilder = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)

            BaseApp.TEST_DEVICE_HASHED_ID.let {
                debugBuilder.addTestDeviceHashedId(it)
            }

            val debugSettings = debugBuilder.build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val params = paramsBuilder.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (consentInformation.isConsentFormAvailable) {
                    UserMessagingPlatform.loadConsentForm(
                        activity,
                        { consentForm ->
                            consentForm.show(activity) { formError ->
                                onConsentGatheringCompleteListener.consentGatheringComplete(formError)
                            }
                        },
                        { formError ->
                            onConsentGatheringCompleteListener.consentGatheringComplete(formError)
                        }
                    )
                } else {
                    onConsentGatheringCompleteListener.consentGatheringComplete(null)
                }
            },
            { requestConsentError ->
                onConsentGatheringCompleteListener.consentGatheringComplete(requestConsentError)
            }
        )
    }

    fun showConsentForm(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener,
    ) {
        UserMessagingPlatform.loadConsentForm(activity, object : OnConsentFormLoadSuccessListener {
            override fun onConsentFormLoadSuccess(p0: ConsentForm) {
                p0.show(activity, onConsentFormDismissedListener)
            }
        }, object : OnConsentFormLoadFailureListener {
            override fun onConsentFormLoadFailure(p0: FormError) {
                Log.e("GoogleMobileAdsConsentManager", "${p0.message}")
            }
        })
    }

    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener,
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
    }

    companion object {
        @Volatile
        private var instance: GoogleMobileAdsConsentManager? = null

        fun getInstance(context: Context) =
            instance
                ?: synchronized(this) {
                    instance ?: GoogleMobileAdsConsentManager(context.applicationContext).also { instance = it }
                }
    }
}
