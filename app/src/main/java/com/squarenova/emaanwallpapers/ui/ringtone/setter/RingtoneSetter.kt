package com.squarenova.emaanwallpapers.ui.ringtone.setter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sets a downloaded MP3 as the device default ringtone using MediaStore + RingtoneManager.
 *
 * - Android 10+ (API 29+): scoped-storage insertion into [MediaStore.Audio.Media] with
 *   RELATIVE_PATH = Ringtones and IS_PENDING handshake. No raw file:// URIs.
 * - Android 7–9 (API 24–28): legacy insertion (WRITE_EXTERNAL_STORAGE, maxSdk 28) but still
 *   resolves a content:// Uri from MediaStore before calling RingtoneManager.
 */
class RingtoneSetter(
    private val context: Context
) {

    fun hasPermission(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun requestPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Opens the system sound settings as a manual fallback when automatic setting fails.
     */
    fun openRingtoneSettings() {
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    /**
     * Publishes [file] to MediaStore (marked as ringtone) and sets it as the default ringtone.
     * Requires WRITE_SETTINGS to already be granted.
     */
    suspend fun setRingtone(file: File, title: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(IllegalStateException("Ringtone file not found"))
            }
            if (!hasPermission()) {
                return@withContext Result.failure(SecurityException("WRITE_SETTINGS not granted"))
            }

            val displayName = sanitizeName(title)
            val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertModern(file, displayName)
            } else {
                insertLegacy(file, displayName)
            } ?: return@withContext Result.failure(IllegalStateException("Could not create ringtone entry"))

            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                contentUri
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun insertModern(file: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        deleteExistingModern(displayName)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp3")
            put(MediaStore.MediaColumns.TITLE, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }

        val publish = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        resolver.update(uri, publish, null, null)

        return uri
    }

    private fun deleteExistingModern(displayName: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$displayName.mp3", "%${Environment.DIRECTORY_RINGTONES}%")
        try {
            resolver.delete(collection, selection, args)
        } catch (_: Exception) {
            // best effort de-dupe
        }
    }

    @Suppress("DEPRECATION")
    private fun insertLegacy(file: File, displayName: String): Uri? {
        val resolver = context.contentResolver

        val ringtonesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_RINGTONES
        )
        if (!ringtonesDir.exists()) ringtonesDir.mkdirs()

        val dest = File(ringtonesDir, "$displayName.mp3")
        file.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }

        val contentUri = MediaStore.Audio.Media.getContentUriForPath(dest.absolutePath)
            ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        resolver.delete(
            contentUri,
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(dest.absolutePath)
        )

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, dest.absolutePath)
            put(MediaStore.MediaColumns.TITLE, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }

        return resolver.insert(contentUri, values)
    }

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
        return cleaned.ifBlank { "ringtone" }
    }
}
