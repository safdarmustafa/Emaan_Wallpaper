import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    kotlin("plugin.serialization") version "1.9.24" // ✅ Required for Supabase
}

android {
    namespace = "com.squarenova.emaanwallpapers"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.squarenova.emaanwallpapers"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
            localProps.load(it)
        }
        fun prop(name: String): String {
            val fromLocal = localProps.getProperty(name)?.trim().orEmpty()
            if (fromLocal.isNotEmpty()) return fromLocal
            return (project.findProperty(name) as String?)?.trim().orEmpty()
        }

        fun String.escapeForBuildConfig(): String =
            replace("\\", "\\\\").replace("\"", "\\\"")

        // Secrets: define in local.properties (gitignored). See local.properties.example at repo root.
        buildConfigField("String", "SUPABASE_URL", "\"${prop("SUPABASE_URL").escapeForBuildConfig()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${prop("SUPABASE_ANON_KEY").escapeForBuildConfig()}\"")
        buildConfigField("String", "MIXPANEL_TOKEN", "\"${prop("MIXPANEL_TOKEN").escapeForBuildConfig()}\"")
        // Must match the backend CASHFREE_ENV secret (TEST | PRODUCTION).
        buildConfigField("String", "CASHFREE_ENV", "\"${prop("CASHFREE_ENV").ifBlank { "TEST" }.escapeForBuildConfig()}\"")
    }

    signingConfigs {
        val keystoreProps = Properties()
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use {
            keystoreProps.load(it)
        }
        create("release") {
            val storeFilePath = keystoreProps.getProperty("storeFile")?.trim().orEmpty()
            if (storeFilePath.isNotEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release").takeIf {
                it.storeFile != null
            } ?: signingConfigs.getByName("debug")
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
    // ✅ Material Design (latest stable). AppCompat is required for per-app language on API 24–32.
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Cashfree Subscription SDK (UPI Autopay mandate via UPI Intent)
    implementation("com.cashfree.pg:api:2.5.0")

    // for setting ringtone
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coil dependency and Mp4 videos
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
    implementation("com.google.firebase:firebase-analytics-ktx")
   // implementation("com.google.firebase:firebase-crashlytics-ktx")

    // 🔐 DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.browser:browser:1.8.0")

    // 📡 Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 📊 Mixpanel Analytics
    implementation("com.mixpanel.android:mixpanel-android:7.2.2")

    // facebook sdk or meta sdk dependency
    implementation("com.facebook.android:facebook-android-sdk:18.3.0")
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
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}