package com.squarenova.emaanwallpapers.model

data class Wallpaper(
    val url: String,
    val category: String,
    val type: String // "static" or "live"
)