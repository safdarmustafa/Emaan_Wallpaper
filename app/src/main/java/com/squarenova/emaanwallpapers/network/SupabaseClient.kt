package com.squarenova.emaanwallpapers.network

import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    private const val TAG = "SUPABASE_CLIENT"

    val client by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(url.isNotEmpty() && key.isNotEmpty()) {
            "Missing SUPABASE_URL or SUPABASE_ANON_KEY — add them to local.properties (see local.properties.example)."
        }
        Log.d(TAG, "createSupabaseClient urlHost=${java.net.URI(url).host}")
        createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Postgrest)
            install(Storage)
        }
    }
}