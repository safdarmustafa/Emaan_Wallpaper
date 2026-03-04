package com.squarenova.emaanwallpapers.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = "https://mfxkexboiwhsmpstqbih.supabase.co",      // e.g. https://abcd1234.supabase.co
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1meGtleGJvaXdoc21wc3RxYmloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIxMzE5MTksImV4cCI6MjA4NzcwNzkxOX0.GkCec2EIpkTRFTh9fCLA8usN2_QLxMKHKaYXAxoBWww"  // from Settings → API → anon public
    ) {
        install(Postgrest)  // ✅ for users table
        install(Storage)    // ✅ for wallpaper images
    }
}