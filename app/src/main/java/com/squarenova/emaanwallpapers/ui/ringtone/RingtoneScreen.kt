package com.squarenova.emaanwallpapers.ui.ringtone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squarenova.emaanwallpapers.ui.ringtone.player.AudioPlayerManager

@Composable
fun RingtoneScreen(
    viewModel: RingtoneViewModel = viewModel()
) {

    val ringtones by viewModel.ringtones.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val context = LocalContext.current

    val audioPlayerManager = remember {
        AudioPlayerManager(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayerManager.release()
        }
    }

    val playerState by audioPlayerManager.playerState.collectAsState()

    when {

        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        ringtones.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No ringtones available")
            }
        }

        else -> {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 120.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                items(ringtones) { ringtone ->

                    RingtoneCard(

                        ringtone = ringtone,

                        isPlaying =
                            playerState.currentUrl == ringtone.audioUrl &&
                                    playerState.isPlaying,

                        onPlayClick = {

                            if (playerState.currentUrl == ringtone.audioUrl) {

                                if (playerState.isPlaying) {

                                    audioPlayerManager.pause()

                                } else {

                                    audioPlayerManager.resume()

                                }

                            } else {

                                audioPlayerManager.play(ringtone.audioUrl)

                            }

                        }

                    )

                }

            }

        }

    }

}