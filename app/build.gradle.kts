plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    kotlin("plugin.serialization") version "1.9.24" // ✅ Required for Supabase
}

configurations.configureEach {
    resolutionStrategy {
        // checkout POM depends on standard-core "LATEST"; without this Gradle can pick 1.7.x, which pulls
        // com.razorpay:core and breaks AGP (duplicate namespace com.razorpay).
        force("com.razorpay:standard-core:1.6.56")
    }
}

android {
    namespace = "com.squarenova.emaanwallpapers"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.squarenova.emaanwallpapers"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Replace with your Mixpanel Project Token from https://mixpanel.com/settings/project
        buildConfigField("String", "MIXPANEL_TOKEN", "\"${project.findProperty("MIXPANEL_TOKEN") ?: "05b8284523c8db2146e3afff2585c9be"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ✅ Material Design - Compatible with Razorpay (latest stable)
    implementation("com.google.android.material:material:1.12.0")

    // ✅ Razorpay — checkout + standard-core new enough for API 33+ registerReceiver flag.
    // Use standard-core 1.6.x (e.g. 1.6.56): 1.7.x adds com.razorpay:core, and both AARs declare namespace
    // "com.razorpay", which makes AGP fail manifest processing. 1.6.56 has the 5-arg registerReceiver fix.
    implementation("com.razorpay:checkout:1.6.41")
    implementation("com.razorpay:standard-core:1.6.56")

    // Coil gif dependency and Mp4 videos
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // ✅ SUPABASE BOM — controls all supabase versions
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")   // Database
    implementation("io.github.jan-tennert.supabase:storage-kt")     // Image Storage

    // ✅ Ktor engine — required by Supabase
    implementation("io.ktor:ktor-client-android:2.3.12")

    // 🖼️ Coil for images (deduplicated)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // 📐 Compose Foundation (deduplicated — keep only one version)
    implementation("androidx.compose.foundation:foundation:1.6.0")
    implementation("androidx.compose.foundation:foundation-layout:1.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // 🔥 Firebase BOM (keeping for auth-related stuff)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // 🔐 DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 📡 Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 📊 Mixpanel Analytics
    implementation("com.mixpanel.android:mixpanel-android:7.2.2")

    // 📩 Retrofit (Fast2SMS OTP — keeping untouched)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")

    // 🎨 Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}