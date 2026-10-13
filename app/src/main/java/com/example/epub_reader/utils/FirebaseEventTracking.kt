package com.example.epub_reader.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class EventName {
    topicClick,
    topicTemplateClick,
    app_onboarding,
    iap_display,
    iap_click,
    iap_purchase,
    ad_loaded,
    ad_displayed,
    ad_revenue_paid,
    ad_closed,
    feature_click,
}

fun EventName.name(): String {
    return when (this) {
        EventName.topicClick -> "topic_click"
        EventName.topicTemplateClick -> "topic_template_click"
        EventName.app_onboarding -> "app_onboarding"
        EventName.iap_display -> "iap_display"
        EventName.iap_click -> "iap_click"
        EventName.iap_purchase -> "iap_purchase"
        EventName.ad_loaded -> "ad_loaded"
        EventName.ad_displayed -> "ad_displayed"
        EventName.ad_revenue_paid -> "ad_revenue_paid"
        EventName.ad_closed -> "ad_closed"
        EventName.feature_click -> "feature_click"
    }
}

enum class ParamsKey {
    current_screen,
    trigger_type,
    product_id,
    product_name,
    value_in_usd,
    price,
    currency,
    ad_format,
    ad_platform,
    ad_network,
    position,
    result,
    duration,
    value,
    feature,
    times,
}

fun ParamsKey.name(): String {
    return when (this) {
        ParamsKey.current_screen -> "current_screen"
        ParamsKey.trigger_type -> "trigger_type"
        ParamsKey.product_id -> "product_id"
        ParamsKey.product_name -> "product_name"
        ParamsKey.value_in_usd -> "value_in_usd"
        ParamsKey.price -> "price"
        ParamsKey.currency -> "currency"
        ParamsKey.ad_format -> "ad_format"
        ParamsKey.ad_platform -> "ad_platform"
        ParamsKey.ad_network -> "ad_network"
        ParamsKey.position -> "position"
        ParamsKey.result -> "result"
        ParamsKey.duration -> "duration"
        ParamsKey.value -> "value"
        ParamsKey.feature -> "feature"
        ParamsKey.times -> "times"
    }
}

enum class AdFormat {
    openApp,
    native,
    banner,
    interstitial,
    videoRewarded
}

fun AdFormat.name(): String {
    return when (this) {
        AdFormat.openApp -> "open_app"
        AdFormat.native -> "native"
        AdFormat.banner -> "banner"
        AdFormat.interstitial -> "interstitial"
        AdFormat.videoRewarded -> "video_rewarded"
    }
}

enum class AdPosition {
    preLoad,
    openApp,
    splashScreen,
    selectLanguage,
    homeScreen,
    topic_screen,
    tutorial_screen,
    perfect_drawing_screen,
    select_mode,
    select_mode_favorite,
    beginner_screen,
    intermediate_screen,
    expert_screen,
    profile_screen,
    setting_screen,
    click_sketch_button,
    click_template,
    back_to_sketch_screen,
    unlock_template,
    sketch_screen,
    click_watch_reward_video_ad,
    sketch_draw_screen,
    language_screen,
    on_boarding_screen,
    complete_late,
    uninstall,
    still_uninstall,
    select_tag_from_browser,
    webview_screen,
    lesson_list_screen,
    interest_screen,
    preview,
    settings
}

fun AdPosition.name(): String {
    return when (this) {
        AdPosition.preLoad -> "pre_load"
        AdPosition.openApp -> "open_app"
        AdPosition.splashScreen -> "splash_screen"
        AdPosition.selectLanguage -> "select_language"
        AdPosition.homeScreen -> "home_screen"
        AdPosition.topic_screen -> "topic_screen"
        AdPosition.tutorial_screen -> "tutorial_screen"
        AdPosition.perfect_drawing_screen -> "perfect_drawing_screen"
        AdPosition.select_mode -> "select_mode"
        AdPosition.select_mode_favorite -> "select_mode_favorite"
        AdPosition.beginner_screen -> "beginner_screen"
        AdPosition.intermediate_screen -> "intermediate_screen"
        AdPosition.expert_screen -> "expert_screen"
        AdPosition.profile_screen -> "profile_screen"
        AdPosition.setting_screen -> "setting_screen"
        AdPosition.click_sketch_button -> "click_sketch_button"
        AdPosition.click_template -> "click_template"
        AdPosition.back_to_sketch_screen -> "back_to_sketch_screen"
        AdPosition.unlock_template -> "unlock_template"
        AdPosition.sketch_screen -> "sketch_screen"
        AdPosition.click_watch_reward_video_ad -> "click_watch_reward_video_ad"
        AdPosition.sketch_draw_screen -> "sketch_draw"
        AdPosition.on_boarding_screen -> "on_boarding_screen"
        AdPosition.language_screen -> "language_screen"
        AdPosition.complete_late -> "complete_late"
        AdPosition.uninstall -> "uninstall"
        AdPosition.still_uninstall -> "still_uninstall"
        AdPosition.select_tag_from_browser -> "select_tag_from_browser"
        AdPosition.webview_screen -> "webview_screen"
        AdPosition.lesson_list_screen -> "lesson_list_screen"
        AdPosition.interest_screen -> "interest_screen"
        AdPosition.preview -> "preview"
        AdPosition.settings -> "settings"
    }
}

enum class Feature {
    click_sketch_button,
    click_profile_button,
    click_topic,
    click_template,
    click_template_show_reward_ads,
    click_template_favorite_show_reward_ads,
    click_unlock_template,
    click_unlock_template_favorite,
    click_draw_button,
    click_complete_button,
    view_perfect_drawing_screen,
    click_beginner,
    click_intermediate,
    click_expert,
    click_lesson,
    click_item_favorite,
    uninstall_screen_loading,
    uninstall_screen_confirm,
    uninstall_screen_feedback,
    uninstall_click_confirm_uninstall,
    uninstall_click_confirm_agree,
    uninstall_click_feedback_cancel,
    uninstall_click_feedback_uninstall,
    uninstall_click_feedback_back,
    click_explore,
    click_explore_template,
}

fun Feature.name(): String {
    return when (this) {
        Feature.click_sketch_button -> "click_sketch_button"
        Feature.click_profile_button -> "click_profile_button"
        Feature.click_topic -> "click_topic"
        Feature.click_template -> "click_template"
        Feature.click_unlock_template -> "click_unlock_template"
        Feature.click_draw_button -> "click_draw_button"
        Feature.click_complete_button -> "click_complete_button"
        Feature.view_perfect_drawing_screen -> "view_perfect_drawing_screen"
        Feature.click_beginner -> "click_beginner"
        Feature.click_intermediate -> "click_intermediate"
        Feature.click_expert -> "click_expert"
        Feature.click_lesson -> "click_lesson"
        Feature.click_template_show_reward_ads -> "click_template_show_reward_ads"
        Feature.click_item_favorite -> "click_item_favorite"
        Feature.click_unlock_template_favorite -> "click_unlock_template_favorite"
        Feature.click_template_favorite_show_reward_ads -> "click_template_favorite_show_reward_ads"
        Feature.uninstall_screen_loading -> "uninstall_screen_loading"
        Feature.uninstall_screen_confirm -> "uninstall_screen_confirm"
        Feature.uninstall_screen_feedback -> "uninstall_screen_feedback"
        Feature.uninstall_click_confirm_agree -> "uninstall_click_confirm_agree"
        Feature.uninstall_click_confirm_uninstall -> "uninstall_click_confirm_uninstall"
        Feature.uninstall_click_feedback_cancel -> "uninstall_click_feedback_cancel"
        Feature.uninstall_click_feedback_uninstall -> "uninstall_click_feedback_uninstall"
        Feature.uninstall_click_feedback_back -> "uninstall_click_feedback_back"
        Feature.click_explore -> "click_explore"
        Feature.click_explore_template -> "click_explore_template"
    }
}

class FirebaseEventTracking {
    companion object {
        fun logEventToFirebase(context: Context, event: EventName, params: Map<String, Any>) {
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    val bundle = Bundle().apply {
                        for ((key, value) in params) {
                            when (value) {
                                is Int -> putInt(key, value)
                                is String -> putString(key, value)
                                is Float -> putFloat(key, value)
                                else -> {}
                            }
                        }
                    }
                    FirebaseAnalytics.getInstance(context).logEvent(event.name(), bundle)
                }
            } catch (e: Exception) {
                Log.e("FirebaseEventTracking", "logEventToFirebase failed: ${e.message}")
            }
        }

        fun adTracking(
            context: Context,
            event: EventName,
            adFormat: AdFormat,
            platform: String? = null,
            network: String? = null,
            position: AdPosition,
            value: Double? = null,
            duration: Long? = null,
            result: Int? = null
        ) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (FirebaseApp.getApps(context).isNotEmpty()) {
                        val bundle = Bundle().apply {
                            putString(ParamsKey.ad_format.name(), adFormat.name())
                            putString(ParamsKey.position.name(), position.name())
                            platform?.let { putString(ParamsKey.ad_platform.name(), it) }
                            network?.let { putString(ParamsKey.ad_network.name(), it) }
                            value?.let { putDouble(ParamsKey.value.name(), it) }
                            duration?.let { putLong(ParamsKey.duration.name(), it) }
                            result?.let { putInt(ParamsKey.result.name(), it) }
                        }
                        FirebaseAnalytics.getInstance(context).logEvent(event.name(), bundle)
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseEventTracking.adTracking", e.message.toString())
                }
            }
        }

        fun featureTracking(context: Context, feature: Feature) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (FirebaseApp.getApps(context).isNotEmpty()) {
                        val times = featureCount(context, feature)
                        val eventName = EventName.feature_click.name()
                        val bundle = Bundle().apply {
                            putString(ParamsKey.feature.name(), feature.name())
                            putInt(ParamsKey.times.name(), times)
                        }
                        FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle)
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseEventTracking.adTracking", e.message.toString())
                }
            }
        }

        private fun featureCount(context: Context, feature: Feature): Int {
            val preferenceKey = Constants.FEATURE_USED_COUNT
            val list = PreferencesHelper.getInstance().getString(preferenceKey)?.split(",")
            var countList: MutableList<Int> = if (list.isNullOrEmpty()) Feature.entries.map { 0 }.toMutableList() else list.map { it.toInt() }.toMutableList()
            if (countList.size < Feature.entries.size) {
                countList = Feature.entries.map { if (it.ordinal < countList.size) countList[it.ordinal] else 0 }.toMutableList()
            }

            val index = feature.ordinal
            if (feature.ordinal < countList.size) {
                countList[index] += 1
            }

            var stringList = ""
            countList.forEachIndexed { idx, i ->
                stringList += (if (idx > 0) "," else "") + "$i"
            }

            PreferencesHelper.getInstance().putString(preferenceKey, stringList)

            return if (index < countList.size) countList[index] else 0
        }
    }
}
