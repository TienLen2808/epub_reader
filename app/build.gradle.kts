plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.epub_reader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.epub_reader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            resValue("string", "admob_app_id", "\"ca-app-pub-4669810164009246~7480873187\"")
            resValue("string", "default_max_open_id", "\"5f58c8db75747cfa\"")
            resValue("string", "default_max_native_id", "\"15b909f408c626f2\"")
            resValue("string", "default_max_banner_id", "\"5982a3ee39a2f8e9\"")
            resValue("string", "default_max_mrec_id", "\"02511d2412a86912\"")
            resValue("string", "default_max_inter_main", "\"5a630d7c67516ee6\"")
            resValue("string", "default_max_inter_medium", "\"88c333852e5ce848\"")
            resValue("string", "default_max_inter_high", "\"222370f9e560cc53\"")
            resValue("string", "default_max_reward_main", "\"e02225da5a0e0d48\"")
            resValue("string", "default_max_reward_medium", "\"6c63a0f985187ea5\"")
            resValue("string", "default_max_reward_high", "\"5d0556e2dfd36efa\"")
        }
        debug {
            resValue("string", "admob_app_id", "\"ca-app-pub-4669810164009246~7480873187\"")
            resValue("string", "default_max_open_id", "\"5f58c8db75747cfa\"")
            resValue("string", "default_max_native_id", "\"15b909f408c626f2\"")
            resValue("string", "default_max_banner_id", "\"5982a3ee39a2f8e9\"")
            resValue("string", "default_max_mrec_id", "\"02511d2412a86912\"")
            resValue("string", "default_max_inter_main", "\"5a630d7c67516ee6\"")
            resValue("string", "default_max_inter_medium", "\"88c333852e5ce848\"")
            resValue("string", "default_max_inter_high", "\"222370f9e560cc53\"")
            resValue("string", "default_max_reward_main", "\"e02225da5a0e0d48\"")
            resValue("string", "default_max_reward_medium", "\"6c63a0f985187ea5\"")
            resValue("string", "default_max_reward_high", "\"5d0556e2dfd36efa\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Ads and UMP
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("com.google.android.ump:user-messaging-platform:2.1.0")

    // AppLovin MAX
    implementation("com.applovin:applovin-sdk:13.6.4")
    implementation("com.applovin.mediation:google-adapter:25.4.0.0")
    implementation("com.applovin.mediation:google-ad-manager-adapter:25.4.0.0")
    implementation("com.applovin.mediation:mintegral-adapter:17.1.81.0")
    implementation("com.applovin.mediation:facebook-adapter:6.22.0.0")
    implementation("com.applovin.mediation:bytedance-adapter:8.3.0.3.0")

    // Glide & Transformations
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-messaging")

    // AppsFlyer
    implementation("com.appsflyer:af-android-sdk:6.15.1")

    // Review
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}