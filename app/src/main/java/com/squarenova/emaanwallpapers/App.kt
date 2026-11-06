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

        // TEMP META DEBUG — remove after investigation
        FacebookSdk.setIsDebugEnabled(true)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.REQUESTS)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.INCLUDE_RAW_RESPONSES)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.DEVELOPER_ERRORS)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.GRAPH_API_DEBUG_INFO)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.GRAPH_API_DEBUG_WARNING)
        Log.d(
            "MetaDebug",
            "pre-init isInitialized=${FacebookSdk.isInitialized()} " +
                "isFullyInitialized=${FacebookSdk.isFullyInitialized()}",
        )
        // END TEMP META DEBUG

        FacebookSdk.fullyInitialize()
        // TEMP META DEBUG — remove after investigation
        Log.d(
            "MetaDebug",
            "post-fullyInitialize isInitialized=${FacebookSdk.isInitialized()} " +
                "isFullyInitialized=${FacebookSdk.isFullyInitialized()} " +
                "applicationId=${FacebookSdk.getApplicationId()} " +
                "clientTokenSet=${!FacebookSdk.getClientToken().isNullOrBlank()} " +
                "autoLog=${FacebookSdk.getAutoLogAppEventsEnabled()} " +
                "advertiserIdCollection=${FacebookSdk.getAdvertiserIDCollectionEnabled()}",
        )
        Log.d("MetaDebug", "calling AppEventsLogger.activateApp(Application)")
        // END TEMP META DEBUG
        AppEventsLogger.activateApp(this)
        // TEMP META DEBUG — remove after investigation
        Log.d("MetaDebug", "AppEventsLogger.activateApp(Application) returned")
        // END TEMP META DEBUG

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

        try {
            AnalyticsManager.flush()
        } catch (e: Exception) {
            // Analytics must never block or crash app startup.
        }

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