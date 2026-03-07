package com.squarenova.emaanwallpapers.service

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.util.Log

class GifWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var videoUrl: String? = null
        private var isPlayerReady = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // ✅ Load saved video URL from SharedPreferences
            val prefs = getSharedPreferences("live_wallpaper_prefs", MODE_PRIVATE)
            videoUrl = prefs.getString("gif_url", null)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            setupMediaPlayer(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (!isPlayerReady) {
                setupMediaPlayer(holder)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                mediaPlayer?.start()
            } else {
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayer()
        }

        private fun setupMediaPlayer(holder: SurfaceHolder) {
            val url = videoUrl ?: return
            releasePlayer()

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)           // ✅ Stream MP4 directly from URL
                    setSurface(holder.surface)   // ✅ Render onto wallpaper surface
                    isLooping = true             // ✅ Loop forever
                    setVolume(0f, 0f)            // ✅ Mute — wallpapers should be silent

                    setOnPreparedListener { player ->
                        isPlayerReady = true
                        player.start()
                        Log.d("VIDEO_WALLPAPER", "MP4 started successfully")
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e("VIDEO_WALLPAPER_ERROR", "Error: what=$what extra=$extra")
                        isPlayerReady = false
                        false
                    }

                    prepareAsync() // ✅ Non-blocking prepare — streams from URL
                }
            } catch (e: Exception) {
                Log.e("VIDEO_WALLPAPER_ERROR", e.message ?: "Unknown error")
            }
        }

        private fun releasePlayer() {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                Log.e("VIDEO_WALLPAPER", "Release error: ${e.message}")
            } finally {
                mediaPlayer = null
                isPlayerReady = false
            }
        }
    }
}