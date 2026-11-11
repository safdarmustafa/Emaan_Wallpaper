package com.squarenova.emaanwallpapers

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.squarenova.emaanwallpapers.analytics.AnalyticsLifecycle
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager.init

class App : Application() {

    override fun onCreate() {

        super.onCreate()

        // =========================
        // Firebase (Analytics)
        // =========================

        FirebaseApp.initializeApp(this)
        Firebase.analytics.setAnalyticsCollectionEnabled(true)

        // =========================
        // Meta / Facebook SDK
        // =========================

        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        FacebookSdk.setAdvertiserIDCollectionEnabled(true)
        if (BuildConfig.DEBUG) {
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
            Log.d(
                "MetaSDK",
                "init appId=${FacebookSdk.getApplicationId()} " +
                    "clientTokenSet=${!FacebookSdk.getClientToken().isNullOrBlank()}",
            )
        }
        FacebookSdk.fullyInitialize()
        AppEventsLogger.activateApp(this)
        AppEventsLogger.newLogger(this).flush()

        // =========================
        // Premium entitlement (Cashfree subscription-status, cached)
        // =========================

        EntitlementRepository.init(this)

        // =========================
        // Analytics
        // =========================

        init(
            this,
            BuildConfig.MIXPANEL_TOKEN
        )

        AnalyticsLifecycle.trackLaunch(this)

        // Flush analytics when app goes background.
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
