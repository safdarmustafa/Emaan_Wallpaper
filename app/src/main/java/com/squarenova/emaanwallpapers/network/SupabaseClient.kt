package com.squarenova.emaanwallpapers.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = "https://mfxkexboiwhsmpstqbih.supabase.co",
        supabaseKey = "sb_secret_QzLd7INelGnT5pGkLnERkw_jDIvGJUh"
    ) {
        install(Postgrest)
    }
}