package com.example.epub_reader.utils

import com.example.epub_reader.BaseApp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

enum class DisabledAdType {
    appOpen,
    interstitial,
    nativeAfterInterstitial,
    rewarded,
    splashMrec,
    splashBanner,
    languageMrec,
    languageNative,
    onboardingBanner,
    onboardingMrec,
    onboardingFullscreen,
    interestBanner,
    interestList,
    homeBanner,
    homeMrec,
    categoriesBanner,
    categoriesList,
    categoryDetailsBanner,
    categoryDetailsList,
    lessonBanner,
    lessonList,
    previewBanner,
    previewMrec,
    selectModeBanner,
    selectModeMrec,
    sketchDrawBanner,
    perfectDrawBanner,
    perfectDrawMrec,
    exploreScreen,
    exploreNative,
    webView,
    profileBanner,
    profileNative,
    profileList,
    uninstallBanner,
    uninstallMrec,
    stillUninstallBanner,
    stillUninstallMrec,
    settings
}

data class DisabledAds(
    @SerializedName("app_open") var appOpen: List<String> = emptyList(),
    @SerializedName("interstitial") var interstitial: List<String> = emptyList(),
    @SerializedName("native_after_interstitial") var nativeAfterInterstitial: List<String> = emptyList(),
    @SerializedName("rewarded") var rewarded: List<String> = emptyList(),
    @SerializedName("splash_mrec") var splashMrec: List<String> = emptyList(),
    @SerializedName("splash_banner") var splashBanner: List<String> = emptyList(),
    @SerializedName("language_mrec") var languageMrec: List<String> = emptyList(),
    @SerializedName("language_native") var languageNative: List<String> = emptyList(),
    @SerializedName("onboarding_banner") var onboardingBanner: List<String> = emptyList(),
    @SerializedName("onboarding_mrec") var onboardingMrec: List<String> = emptyList(),
    @SerializedName("onboarding_fullscreen") var onboardingFullscreen: List<String> = emptyList(),
    @SerializedName("interest_banner") var interestBanner: List<String> = emptyList(),
    @SerializedName("interest_list") var interestList: List<String> = emptyList(),
    @SerializedName("home_banner") var homeBanner: List<String> = emptyList(),
    @SerializedName("home_mrec") var homeMrec: List<String> = emptyList(),
    @SerializedName("categories_banner") var categoriesBanner: List<String> = emptyList(),
    @SerializedName("categories_list") var categoriesList: List<String> = emptyList(),
    @SerializedName("category_details_banner") var categoryDetailsBanner: List<String> = emptyList(),
    @SerializedName("category_details_list") var categoryDetailsList: List<String> = emptyList(),
    @SerializedName("lesson_banner") var lessonBanner: List<String> = emptyList(),
    @SerializedName("lesson_list") var lessonList: List<String> = emptyList(),
    @SerializedName("preview_banner") var previewBanner: List<String> = emptyList(),
    @SerializedName("preview_mrec") var previewMrec: List<String> = emptyList(),
    @SerializedName("select_mode_banner") var selectModeBanner: List<String> = emptyList(),
    @SerializedName("select_mode_mrec") var selectModeMrec: List<String> = emptyList(),
    @SerializedName("sketch_draw_banner") var sketchDrawBanner: List<String> = emptyList(),
    @SerializedName("perfect_draw_banner") var perfectDrawBanner: List<String> = emptyList(),
    @SerializedName("perfect_draw_mrec") var perfectDrawMrec: List<String> = emptyList(),
    @SerializedName("explore_screen") var exploreScreen: List<String> = emptyList(),
    @SerializedName("explore_native") var exploreNative: List<String> = emptyList(),
    @SerializedName("web_view") var webView: List<String> = emptyList(),
    @SerializedName("profile_banner") var profileBanner: List<String> = emptyList(),
    @SerializedName("profile_native") var profileNative: List<String> = emptyList(),
    @SerializedName("profile_list") var profileList: List<String> = emptyList(),
    @SerializedName("uninstall_banner") var uninstallBanner: List<String> = emptyList(),
    @SerializedName("uninstall_mrec") var uninstallMrec: List<String> = emptyList(),
    @SerializedName("still_uninstall_banner") var stillUninstallBanner: List<String> = emptyList(),
    @SerializedName("still_uninstall_mrec") var stillUninstallMrec: List<String> = emptyList(),
    @SerializedName("settings") var settings: List<String> = emptyList()
) {
    constructor(jsonString: String) : this() {
        if (jsonString.isNotEmpty()) {
            val gson = Gson()
            val parsed = gson.fromJson(jsonString, DisabledAds::class.java)
            if (parsed != null) {
                appOpen = parsed.appOpen
                interstitial = parsed.interstitial
                nativeAfterInterstitial = parsed.nativeAfterInterstitial
                rewarded = parsed.rewarded
                splashMrec = parsed.splashMrec
                splashBanner = parsed.splashBanner
                languageMrec = parsed.languageMrec
                languageNative = parsed.languageNative
                onboardingBanner = parsed.onboardingBanner
                onboardingMrec = parsed.onboardingMrec
                onboardingFullscreen = parsed.onboardingFullscreen
                interestBanner = parsed.interestBanner
                interestList = parsed.interestList
                homeBanner = parsed.homeBanner
                homeMrec = parsed.homeMrec
                categoriesBanner = parsed.categoriesBanner
                categoriesList = parsed.categoriesList
                categoryDetailsBanner = parsed.categoryDetailsBanner
                categoryDetailsList = parsed.categoryDetailsList
                lessonBanner = parsed.lessonBanner
                lessonList = parsed.lessonList
                previewBanner = parsed.previewBanner
                previewMrec = parsed.previewMrec
                selectModeBanner = parsed.selectModeBanner
                selectModeMrec = parsed.selectModeMrec
                sketchDrawBanner = parsed.sketchDrawBanner
                perfectDrawBanner = parsed.perfectDrawBanner
                perfectDrawMrec = parsed.perfectDrawMrec
                exploreScreen = parsed.exploreScreen
                exploreNative = parsed.exploreNative
                webView = parsed.webView
                profileBanner = parsed.profileBanner
                profileNative = parsed.profileNative
                profileList = parsed.profileList
                uninstallBanner = parsed.uninstallBanner
                uninstallMrec = parsed.uninstallMrec
                stillUninstallBanner = parsed.stillUninstallBanner
                stillUninstallMrec = parsed.stillUninstallMrec
                settings = parsed.settings
            }
        }
    }
}

fun remoteAdDisabled(adType: DisabledAdType): Boolean {
    val isPremium = PreferencesHelper.getInstance().getBoolean(Constants.IS_PREMIUM)
    if (isPremium) {
        return true
    }

    val string = PreferencesHelper.getInstance().getString(Constants.DISABLED_ADS)
    if (string.isNullOrEmpty()) {
        return false
    }
    val disabledAds = DisabledAds(jsonString = string)

    val versions: List<String> = when (adType) {
        DisabledAdType.appOpen -> disabledAds.appOpen
        DisabledAdType.interstitial -> disabledAds.interstitial
        DisabledAdType.nativeAfterInterstitial -> disabledAds.nativeAfterInterstitial
        DisabledAdType.rewarded -> disabledAds.rewarded
        DisabledAdType.splashMrec -> disabledAds.splashMrec
        DisabledAdType.splashBanner -> disabledAds.splashBanner
        DisabledAdType.languageMrec -> disabledAds.languageMrec
        DisabledAdType.languageNative -> disabledAds.languageNative
        DisabledAdType.onboardingBanner -> disabledAds.onboardingBanner
        DisabledAdType.onboardingMrec -> disabledAds.onboardingMrec
        DisabledAdType.onboardingFullscreen -> disabledAds.onboardingFullscreen
        DisabledAdType.interestBanner -> disabledAds.interestBanner
        DisabledAdType.interestList -> disabledAds.interestList
        DisabledAdType.homeBanner -> disabledAds.homeBanner
        DisabledAdType.homeMrec -> disabledAds.homeMrec
        DisabledAdType.categoriesBanner -> disabledAds.categoriesBanner
        DisabledAdType.categoriesList -> disabledAds.categoriesList
        DisabledAdType.categoryDetailsBanner -> disabledAds.categoryDetailsBanner
        DisabledAdType.categoryDetailsList -> disabledAds.categoryDetailsList
        DisabledAdType.lessonBanner -> disabledAds.lessonBanner
        DisabledAdType.lessonList -> disabledAds.lessonList
        DisabledAdType.previewBanner -> disabledAds.previewBanner
        DisabledAdType.previewMrec -> disabledAds.previewMrec
        DisabledAdType.selectModeBanner -> disabledAds.selectModeBanner
        DisabledAdType.selectModeMrec -> disabledAds.selectModeMrec
        DisabledAdType.sketchDrawBanner -> disabledAds.sketchDrawBanner
        DisabledAdType.perfectDrawBanner -> disabledAds.perfectDrawBanner
        DisabledAdType.perfectDrawMrec -> disabledAds.perfectDrawMrec
        DisabledAdType.exploreScreen -> disabledAds.exploreScreen
        DisabledAdType.exploreNative -> disabledAds.exploreNative
        DisabledAdType.webView -> disabledAds.webView
        DisabledAdType.profileBanner -> disabledAds.profileBanner
        DisabledAdType.profileNative -> disabledAds.profileNative
        DisabledAdType.profileList -> disabledAds.profileList
        DisabledAdType.uninstallBanner -> disabledAds.uninstallBanner
        DisabledAdType.uninstallMrec -> disabledAds.uninstallMrec
        DisabledAdType.stillUninstallBanner -> disabledAds.stillUninstallBanner
        DisabledAdType.stillUninstallMrec -> disabledAds.stillUninstallMrec
        DisabledAdType.settings -> disabledAds.settings
    }

    BaseApp.instance.versionName?.let { versionName ->
        return versions.contains(versionName)
    }

    return false
}
