package com.squarenova.emaanwallpapers.data

import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

/**
 * Distinguishes a deleted/missing user row from a network failure.
 * Empty list + successful response → [Missing]. Thrown/failed request → [Unreachable].
 */
sealed class UserAccountLookup<out T> {
    data class Found<T>(val row: T) : UserAccountLookup<T>()
    data object Missing : UserAccountLookup<Nothing>()
    data object Unreachable : UserAccountLookup<Nothing>()
}

object UserAccountQueries {

    suspend inline fun <reified T : Any> lookupByPhone(phone: String): UserAccountLookup<T> {
        val clean = phone.trim()
        if (clean.isBlank()) return UserAccountLookup.Missing
        return try {
            val rows = SupabaseClient.client
                .postgrest["users"]
                .select { filter { eq("phone_number", clean) } }
                .decodeList<T>()
            val row = rows.firstOrNull()
            if (row == null) UserAccountLookup.Missing else UserAccountLookup.Found(row)
        } catch (_: Exception) {
            UserAccountLookup.Unreachable
        }
    }
}
