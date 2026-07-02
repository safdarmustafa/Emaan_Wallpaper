package com.squarenova.emaanwallpapers.ui.ringtone.player

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves the *actual* playback duration of an audio source (remote URL or local file)
 * by reading its metadata, since the value stored in the backend can be inaccurate.
 * Results are cached in-memory per source so each track is inspected only once.
 */
object AudioDurationResolver {

    private val cache = HashMap<String, Long>()

    suspend fun resolveMs(url: String, localFile: File?): Long? = withContext(Dispatchers.IO) {
        cache[url]?.let { return@withContext it }

        val duration = readDuration(localFile?.takeIf { it.exists() }?.absolutePath)
            ?: readDuration(url)

        if (duration != null && duration > 0) {
            cache[url] = duration
        }
        duration
    }

    private fun readDuration(source: String?): Long? {
        if (source.isNullOrBlank()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            if (source.startsWith("http")) {
                retriever.setDataSource(source, HashMap())
            } else {
                retriever.setDataSource(source)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
