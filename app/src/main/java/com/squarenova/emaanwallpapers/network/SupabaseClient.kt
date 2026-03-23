package com.squarenova.emaanwallpapers.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = "https://uxodfjjytcsrjnxwqbvg.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV4b2Rmamp5dGNzcmpueHdxYnZnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM2OTgzMjEsImV4cCI6MjA4OTI3NDMyMX0.i3BlgJkG9iq0-vgTs9cZ9ndDSVH7T3TH8uEhbdIOWW0"
    ) {
        install(Postgrest)
        install(Storage)
    }
}