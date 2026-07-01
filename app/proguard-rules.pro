# Emaan Wallpapers — release shrinking rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# BuildConfig
-keep class com.squarenova.emaanwallpapers.BuildConfig { *; }

# Razorpay
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn com.razorpay.**

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

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }

# Coil
-dontwarn coil.**

# Mixpanel / Facebook
-dontwarn com.mixpanel.**
-dontwarn com.facebook.**

# Strip debug-only log classes in release (optional shrink)
-assumenosideeffects class com.squarenova.emaanwallpapers.data.MandateDebugLog {
    public static *** note(...);
    public static *** entitlementCheck(...);
}
-assumenosideeffects class com.squarenova.emaanwallpapers.data.EntitlementDebugLog {
    public static *** check(...);
    public static *** navigation(...);
}

-dontwarn org.slf4j.impl.StaticLoggerBinder
