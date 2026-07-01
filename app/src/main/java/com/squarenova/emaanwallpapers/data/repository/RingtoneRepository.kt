package com.squarenova.emaanwallpapers.data.repository

import android.util.Log
import com.squarenova.emaanwallpapers.data.model.Ringtone
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object RingtoneRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getRingtones(): List<Ringtone> {

        return try {

            val response = SupabaseClient.client
                .postgrest["ringtones"]
                .select()

            Log.d("RINGTONE", response.data)

            val result = json.decodeFromString<List<Ringtone>>(response.data)

            Log.d("RINGTONE", "Decoded ${result.size} ringtones")

            result

        } catch (e: Exception) {

            Log.e("RINGTONE", "Repository Error", e)

            emptyList()
        }
    }
}