package com.squarenova.emaanwallpapers.ui.ringtone.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayerManager(
    context: Context
) {

    private val player = ExoPlayer.Builder(context).build()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState

    init {
        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {

                _playerState.value = _playerState.value.copy(
                    isPlaying = isPlaying
                )

            }

            override fun onPlaybackStateChanged(playbackState: Int) {

                if (playbackState == Player.STATE_ENDED) {

                    _playerState.value = PlayerState()

                }

            }

        })
    }

    /**
     * Play audio
     */
    fun play(url: String) {

        if (_playerState.value.currentUrl == url) {
            resume()
            return
        }

        player.stop()
        player.clearMediaItems()

        val mediaItem = MediaItem.fromUri(url)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        _playerState.value = _playerState.value.copy(
            currentUrl = url
        )
    }

    /**
     * Pause
     */
    fun pause() {
        player.pause()
    }

    /**
     * Resume
     */
    fun resume() {
        player.play()
    }

    /**
     * Stop
     */
    fun stop() {
        player.stop()
        _playerState.value = PlayerState()
    }

    /**
     * Current Position
     */
    fun getCurrentPosition(): Long {
        return player.currentPosition
    }

    /**
     * Duration
     */
    fun getDuration(): Long {
        return player.duration
    }

    /**
     * Seek
     */
    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    /**
     * Release
     */
    fun release() {
        player.release()
    }
}