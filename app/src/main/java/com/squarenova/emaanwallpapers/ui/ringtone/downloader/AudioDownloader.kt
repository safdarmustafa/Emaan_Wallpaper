package com.squarenova.emaanwallpapers.ui.ringtone.downloader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class AudioDownloader(
    private val context: Context
) {

    private val client = OkHttpClient()

    /**
     * Returns the local target file for a given ringtone name (may not exist yet).
     */
    fun localFile(fileName: String): File {
        val ringtoneDir = File(context.filesDir, "ringtones")
        return File(ringtoneDir, "$fileName.mp3")
    }

    /**
     * Whether the ringtone has already been downloaded to private storage.
     */
    fun isDownloaded(fileName: String): Boolean = localFile(fileName).exists()

    /**
     * Downloads the ringtone if it doesn't already exist.
     * Returns the local file.
     */
    suspend fun download(
        url: String,
        fileName: String
    ): File = withContext(Dispatchers.IO) {

        val ringtoneDir = File(context.filesDir, "ringtones")

        if (!ringtoneDir.exists()) {
            ringtoneDir.mkdirs()
        }

        val audioFile = File(ringtoneDir, "$fileName.mp3")

        // Already downloaded
        if (audioFile.exists()) {
            return@withContext audioFile
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed : ${response.code}")
        }

        val body = response.body
            ?: throw Exception("Empty response body")

        body.byteStream().use { input ->

            audioFile.outputStream().use { output ->

                input.copyTo(output)

            }

        }

        audioFile

    }

}