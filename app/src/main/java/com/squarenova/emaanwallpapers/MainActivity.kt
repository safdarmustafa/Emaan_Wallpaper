package com.squarenova.emaanwallpapers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.squarenova.emaanwallpapers.navigation.AppNavGraph
import com.squarenova.emaanwallpapers.theme.EmaanWallpapersTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EmaanWallpapersTheme {
                AppNavGraph() // ✅ navController is managed inside AppNavGraph
            }
        }
    }
}