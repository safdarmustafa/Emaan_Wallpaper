package com.squarenova.emaanwallpapers.network

import android.content.Context
import android.util.Log
import com.squarenova.emaanwallpapers.i18n.localizedLabel
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
    val labelHi: String? = null,
) {
    fun displayLabel(language: String?): String = localizedLabel(label, labelHi, language)
}

@Serializable
private data class CategoryRow(
    val name: String,
    val label: String? = null,
    val label_hi: String? = null,
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
            ContentCategory("kaaba", "Kaaba", "काबा"),
            ContentCategory("madinah", "Madinah", "मदीना"),
            ContentCategory("quran", "Quran", "कुरान"),
            ContentCategory("mosque", "Mosque", "मस्जिद"),
            ContentCategory("islamic_quotes", "Islamic Quotes", "इस्लामिक कोट्स"),
            ContentCategory("ramadan", "Ramadan", "रमज़ान"),
            ContentCategory("allah", "Allah", "अल्लाह"),
            ContentCategory("general", "General", "सामान्य"),
        ),
        TYPE_REEL to listOf(
            ContentCategory("ramadan", "Ramadan", "रमज़ान"),
            ContentCategory("dua", "Dua", "दुआ"),
            ContentCategory("quran", "Quran", "कुरान"),
            ContentCategory("prayer", "Prayer", "नमाज़"),
            ContentCategory("makkah", "Makkah", "मक्का"),
            ContentCategory("madinah", "Madinah", "मदीना"),
            ContentCategory("mosque", "Mosque", "मस्जिद"),
            ContentCategory("islamic_night", "Islamic Night", "इस्लामिक रात"),
            ContentCategory("peaceful", "Peaceful", "सुखद"),
            ContentCategory("architecture", "Architecture", "आर्किटेक्चर"),
        ),
        TYPE_RINGTONE to listOf(
            ContentCategory("nasheed", "Nasheed", "नशीद"),
            ContentCategory("islamic", "Islamic", "इस्लामिक"),
            ContentCategory("adhan", "Adhan", "अज़ान"),
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
                    val english = row.label?.trim()?.takeIf { it.isNotEmpty() }
                        ?: slug.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    ContentCategory(
                        slug = slug,
                        label = english,
                        labelHi = row.label_hi?.trim()?.takeIf { it.isNotEmpty() },
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
