package com.squarenova.emaanwallpapers

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.ui.subscription.SubscriptionManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {

        super.onCreate()

        // =========================
        // Firebase (Analytics + Crashlytics)
        // =========================

        FirebaseApp.initializeApp(this)
        Firebase.analytics.setAnalyticsCollectionEnabled(true)
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // =========================
        // Meta / Facebook SDK
        // =========================

        FacebookSdk.sdkInitialize(applicationContext)

        AppEventsLogger.activateApp(this)

        // =========================
        // Subscription Manager
        // =========================

        SubscriptionManager.init(this)

        // =========================
        // Analytics
        // =========================

        AnalyticsManager.init(
            this,
            BuildConfig.MIXPANEL_TOKEN
        )

        try {
            AnalyticsManager.trackEvent("App Launched")
            AnalyticsManager.track("meta_test_event")
            AnalyticsManager.flush()
        } catch (_: Exception) {
            // Analytics must never block or crash app startup.
        }

        // Flush analytics when app goes background
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {

                override fun onStop(owner: LifecycleOwner) {
                    try {
                        AnalyticsManager.flush()
                    } catch (_: Exception) {
                        // Ignore analytics flush failures.
                    }
                }
            }
        )

        // =========================
        // Coil Image Loader
        // =========================

        val imageLoader = ImageLoader.Builder(this)

            .memoryCache {

                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }

            .diskCache {

                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150 * 1024 * 1024)
                    .build()
            }

            .okHttpClient {

                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }

            .crossfade(true)

            .respectCacheHeaders(false)

            .build()

        Coil.setImageLoader(imageLoader)
    }
}