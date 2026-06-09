package com.squarenova.emaanwallpapers.network

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val TAG = "WallpaperCatalog"

data class WallpaperRow(
    val id: String,
    val category: String? = null,
    val url: String,
    val type: String,
)

fun WallpaperRow.typeNorm(): String = type.trim().lowercase()

fun WallpaperRow.isStaticWallpaper(): Boolean = when (typeNorm()) {
    "static", "image", "img", "gif", "photo", "picture", "png", "jpg", "jpeg", "webp" -> true
    else -> false
}

fun WallpaperRow.isLiveWallpaper(): Boolean = when (typeNorm()) {
    "live", "video", "mp4", "mov", "m3u8" -> true
    else -> false
}

fun WallpaperRow.matchesCategory(selected: String?): Boolean {
    if (selected.isNullOrBlank()) return true
    val cat = category?.trim().orEmpty()
    return cat.equals(selected.trim(), ignoreCase = true)
}

object WallpaperCatalog {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchAll(): List<WallpaperRow> {
        val result = try {
            SupabaseClient.client.postgrest["wallpapers"].select(columns = Columns.ALL) {
                order("created_at", Order.DESCENDING)
            }
        } catch (e: Exception) {
            Log.e(TAG, "wallpapers select failed (table missing, RLS, network, or bad URL?)", e)
            return emptyList()
        }

        val raw = result.data.trim()
        Log.d(TAG, "raw length=${raw.length} prefix=${raw.take(180)}")

        if (raw.isEmpty() || raw == "[]") {
            Log.w(
                TAG,
                "Empty array from PostgREST - confirm public.wallpapers has rows and anon can SELECT"
            )
            return emptyList()
        }

        val root = try {
            lenientJson.parseToJsonElement(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON from wallpapers endpoint", e)
            return emptyList()
        }

        if (root !is JsonArray) {
            Log.e(TAG, "Expected top-level JSON array, got=${root::class.simpleName}: ${raw.take(400)}")
            return emptyList()
        }

        var fallback = 0
        val list = ArrayList<WallpaperRow>(root.size)
        for (el in root) {
            val obj = el as? JsonObject ?: continue
            val url = extractUrl(obj) ?: continue
            val typeRaw = extractText(
                obj,
                "type", "media_type", "wallpaper_type", "kind", "mime_category"
            )
            val type = normalizeWallpaperType(typeRaw, url)
            val category = extractText(obj, "category", "cat", "group")
            val id = extractId(obj) ?: "wallpaper_${fallback++}"
            list.add(WallpaperRow(id = id, category = category, url = url, type = type))
        }

        Log.d(
            TAG,
            "parsed count=${list.size} distinct_types=${list.map { it.type }.distinct()}"
        )
        return list
    }

    private fun jsonPrimitiveContent(p: JsonPrimitive?): String? {
        if (p == null) return null
        return p.content.trim().takeIf { it.isNotEmpty() }
    }

    private fun extractText(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val p = obj[k] as? JsonPrimitive ?: continue
            jsonPrimitiveContent(p)?.let { return it }
        }
        return null
    }

    private fun normalizeWallpaperType(fromDb: String?, url: String): String {
        val trimmed = fromDb?.trim().orEmpty().lowercase()
        if (trimmed.isNotEmpty() && trimmed != "null") {
            return trimmed
        }
        val path = url.substringBefore("?").lowercase()
        val liveByExt = path.endsWith(".mp4") || path.endsWith(".mov") ||
            path.endsWith(".webm") || path.endsWith(".m4v") ||
            path.contains("/live/")
        return if (liveByExt) "live" else "static"
    }

    private fun extractUrl(obj: JsonObject): String? {
        val keys = arrayOf(
            "url", "image_url", "imageUrl", "link", "src", "file_url", "fileUrl",
            "media_url", "mediaUrl", "path", "public_url", "storage_path"
        )
        for (k in keys) {
            val s = extractText(obj, k) ?: continue
            if (s.startsWith("http", ignoreCase = true) || s.startsWith("/")) return s
        }
        return null
    }

    private fun extractId(obj: JsonObject): String? {
        val v = obj["id"] ?: return null
        return jsonPrimitiveContent(v as? JsonPrimitive)
    }
}
