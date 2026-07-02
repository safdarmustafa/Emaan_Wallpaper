package com.squarenova.emaanwallpapers.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ringtone(

    val id: String,

    val title: String,

    val description: String? = null,

    val category: String,

    @SerialName("audio_url")
    val audioUrl: String,

    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,

    @SerialName("duration_seconds")
    val durationSeconds: Int,

    val premium: Boolean,

    val featured: Boolean,

    val downloads: Int,

    @SerialName("play_count")
    val playCount: Int,

    @SerialName("sort_order")
    val sortOrder: Int,

    @SerialName("is_active")
    val isActive: Boolean,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)