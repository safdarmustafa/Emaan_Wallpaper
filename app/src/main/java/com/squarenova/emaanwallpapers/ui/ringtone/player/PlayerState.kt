package com.squarenova.emaanwallpapers.ui.ringtone.player

data class PlayerState(

    val isPlaying: Boolean = false,

    val currentPosition: Long = 0L,

    val duration: Long = 0L,

    val currentUrl: String? = null

)