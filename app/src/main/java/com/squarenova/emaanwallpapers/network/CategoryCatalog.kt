package com.squarenova.emaanwallpapers.network

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "CategoryCatalog"

/** A filter chip: [slug] matches `wallpapers/reels/ringtones.category`, [label] is shown. */
@Serializable
data class ContentCategory(
    val slug: String,
    val label: String,
)

@Serializable
private data class CategoryRow(
    val name: String,
    val label: String? = null,
    val sort_order: Int = 0,
)

/**
 * Categories are managed from the admin panel (`public.categories`).
 * Order of preference: network → last cached list → bundled defaults.
 */
object CategoryCatalog {

    const val TYPE_WALLPAPER = "wallpaper"
    const val TYPE_REEL = "reel"
    const val TYPE_RINGTONE = "ringtone"

    private const val PREFS = "content_categories"

    private val json = Json { ignoreUnknownKeys = true }

    /** Used only when the app has never reached the server (first launch offline). */
    private val defaults: Map<String, List<ContentCategory>> = mapOf(
        TYPE_WALLPAPER to listOf(
            ContentCategory("kaaba", "Kaaba"),
            ContentCategory("madinah", "Madinah"),
            ContentCategory("quran", "Quran"),
            ContentCategory("mosque", "Mosque"),
            ContentCategory("islamic_quotes", "Islamic Quotes"),
            ContentCategory("ramadan", "Ramadan"),
            ContentCategory("allah", "Allah"),
            ContentCategory("general", "General"),
        ),
        TYPE_REEL to listOf(
            ContentCategory("ramadan", "Ramadan"),
            ContentCategory("dua", "Dua"),
            ContentCategory("quran", "Quran"),
            ContentCategory("prayer", "Prayer"),
            ContentCategory("makkah", "Makkah"),
            ContentCategory("madinah", "Madinah"),
            ContentCategory("mosque", "Mosque"),
            ContentCategory("islamic_night", "Islamic Night"),
            ContentCategory("peaceful", "Peaceful"),
            ContentCategory("architecture", "Architecture"),
        ),
        TYPE_RINGTONE to listOf(
            ContentCategory("nasheed", "Nasheed"),
            ContentCategory("islamic", "Islamic"),
            ContentCategory("adhan", "Adhan"),
        ),
    )

    /** Content rows store the slug; legacy ringtone rows use capitalised values. */
    fun matches(contentCategory: String?, slug: String): Boolean =
        contentCategory?.trim().equals(slug, ignoreCase = true)

    /** Instant, offline-safe list for first composition. */
    fun cached(context: Context, type: String): List<ContentCategory> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(type, null)
        val fromCache = raw?.let {
            runCatching { json.decodeFromString<List<ContentCategory>>(it) }.getOrNull()
        }
        return fromCache?.takeIf { it.isNotEmpty() } ?: defaults[type].orEmpty()
    }

    /** Fetches active categories for [type]; falls back to [cached] on any failure. */
    suspend fun fetch(context: Context, type: String): List<ContentCategory> {
        val remote = try {
            SupabaseClient.client.postgrest["categories"]
                .select {
                    filter {
                        eq("content_type", type)
                        eq("is_active", true)
                    }
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<CategoryRow>()
                .mapNotNull { row ->
                    val slug = row.name.trim().lowercase()
                    if (slug.isEmpty()) return@mapNotNull null
                    ContentCategory(
                        slug = slug,
                        label = row.label?.trim()?.takeIf { it.isNotEmpty() }
                            ?: slug.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    )
                }
                .distinctBy { it.slug }
        } catch (e: Exception) {
            Log.w(TAG, "categories fetch failed for type=$type — using cache", e)
            return cached(context, type)
        }

        if (remote.isEmpty()) {
            Log.w(TAG, "no active categories for type=$type — using cache")
            return cached(context, type)
        }

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(type, json.encodeToString(remote))
            .apply()
        return remote
    }
}
