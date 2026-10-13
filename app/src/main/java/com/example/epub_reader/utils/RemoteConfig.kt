package com.example.epub_reader.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.example.epub_reader.BaseApp
import com.example.epub_reader.R
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

object RemoteConfig {
    internal const val TAG = "RemoteConfig"

    private var updateListeners: ArrayList<() -> Unit> = arrayListOf()
    var i: Boolean = true
    private var c: Boolean = false

    private var applovin: String = "applovin"
    private var mintegral: String = "mintegral"
    private var google: String = "google"
    private var ironsource: String = "ironsource"
    private var unity: String = "unity"
    private var tiktok: String = "tiktok"
    private var tiktokglobal_int: String = "tiktokglobal_int"
    private var facebook: String = "facebook"

    var key_added: String = ""
    private var appFlyersInitialized = false
    private var fetchedRemoteConfig = false
    private var onCompleteCallback: ((Boolean) -> Unit)? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var remoteConfig: FirebaseRemoteConfig? = null

    fun initAndFetchRemoteConfig(context: Context, owner: LifecycleOwner, onComplete: (Boolean) -> Unit) {
        lifecycleOwner = owner
        onCompleteCallback = onComplete

        lifecycleOwner?.lifecycleScope?.launch {
            delay(1_000)
            remoteConfig = getRemoteConfig(context, 3) {
                if (appFlyersInitialized && fetchedRemoteConfig) {
                    remoteConfig?.let { rc ->
                        updatePreferences(context, rc)
                    }
                    onCompleteCallback?.invoke(true)
                    onCompleteCallback = null
                }
            }
        }

        initAppsFlyer(context) {
            if (appFlyersInitialized && fetchedRemoteConfig) {
                remoteConfig?.let { rc ->
                    updatePreferences(context, rc)
                }
                onCompleteCallback?.invoke(true)
                onCompleteCallback = null
            }
        }
    }

    fun isInitialized(): Boolean {
        return appFlyersInitialized && fetchedRemoteConfig
    }

    private fun initAppsFlyer(context: Context, onDone: (Boolean) -> Unit) {
        val saved_key_added = PreferencesHelper.getInstance().getString(Constants.AppsFlyer_key_added)
        val saved_i = PreferencesHelper.getInstance().getBoolean(Constants.AppsFlyer_i)
        if (saved_key_added != null) {
            key_added = saved_key_added
            i = saved_i
            appFlyersInitialized = true
            onDone(true)
        } else {
            appFlyersInitialized = true
            onDone(true)
        }
    }

    private fun getRemoteConfig(context: Context, retryCount: Int, onComplete: (Boolean) -> Unit): FirebaseRemoteConfig? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isEmpty()) {
                Log.e(TAG, "FirebaseApp is not initialized.")
                fetchedRemoteConfig = true
                onComplete(true)
                return null
            }
            val firebaseRemoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings: FirebaseRemoteConfigSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(4)
                .build()
            firebaseRemoteConfig.setConfigSettingsAsync(configSettings)

            firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                fetchedRemoteConfig = true
                onComplete(true)
            }
            firebaseRemoteConfig
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseRemoteConfig", e)
            fetchedRemoteConfig = true
            onComplete(true)
            null
        }
    }

    private fun updatePreferences(context: Context, rc: FirebaseRemoteConfig) {
        try {
            val telegramBotToken = rc.getString("TELEGRAM_BOT_TOKEN")
            PreferencesHelper.getInstance().putString(Constants.TELEGRAM_BOT_TOKEN, telegramBotToken)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addUpdateListener(listener: () -> Unit) {
        if (!updateListeners.contains(listener)) {
            updateListeners.add(listener)
        }
    }

    fun removeUpdateListener(listener: () -> Unit) {
        updateListeners.remove(listener)
    }
}
