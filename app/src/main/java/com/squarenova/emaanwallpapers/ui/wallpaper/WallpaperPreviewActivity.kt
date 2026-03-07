package com.squarenova.emaanwallpapers.ui.wallpaper

import android.app.Activity
import android.os.Bundle
import com.squarenova.emaanwallpapers.R

// ✅ Android requires a preview Activity for live wallpapers
// This is shown in the system wallpaper picker before the user confirms
class WallpaperPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Minimal — just shows app name in the system picker
        // You can set a layout here if you want a custom preview UI
        finish() // Close immediately, system picker handles the preview
    }
}