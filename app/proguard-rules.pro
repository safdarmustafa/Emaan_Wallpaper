# Emaan Wallpapers — release shrinking rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# BuildConfig
-keep class com.squarenova.emaanwallpapers.BuildConfig { *; }

# WebView JS bridges (Cashfree / Facebook)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin serialization (Supabase models)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.squarenova.emaanwallpapers.**$$serializer { *; }
-keepclassmembers class com.squarenova.emaanwallpapers.** {
    *** Companion;
}
-keepclasseswithmembers class com.squarenova.emaanwallpapers.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-dontwarn coil.**

# Mixpanel / Facebook
-dontwarn com.mixpanel.**
-dontwarn com.facebook.**

# EntitlementDebugLog is a Kotlin object (not Java static). Methods already
# no-op when !BuildConfig.DEBUG, so no -assumenosideeffects needed here.

-dontwarn org.slf4j.impl.StaticLoggerBinder
