package com.squarenova.emaanwallpapers.ui.ringtone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squarenova.emaanwallpapers.data.model.Ringtone
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.ui.ringtone.components.DownloadSuccessDialog
import com.squarenova.emaanwallpapers.ui.ringtone.components.RingtoneFailureDialog
import com.squarenova.emaanwallpapers.ui.ringtone.components.RingtoneProcessingDialog
import com.squarenova.emaanwallpapers.ui.ringtone.components.RingtoneSuccessDialog
import com.squarenova.emaanwallpapers.ui.ringtone.downloader.AudioDownloader
import com.squarenova.emaanwallpapers.ui.ringtone.player.AudioDurationResolver
import com.squarenova.emaanwallpapers.ui.ringtone.player.AudioPlayerManager
import com.squarenova.emaanwallpapers.ui.ringtone.setter.RingtoneSetter
import kotlinx.coroutines.launch

private val ScreenBackground = Color(0xFF0B0B0B)
private val HeaderTitle = Color(0xFFF4F5F4)
private val HeaderSubtitle = Color(0xFF8B948D)
private val SearchSurface = Color(0xFF181818)

@Composable
fun RingtoneScreen(
    viewModel: RingtoneViewModel = viewModel()
) {

    val ringtones by viewModel.ringtones.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val audioPlayerManager = remember { AudioPlayerManager(context) }
    val audioDownloader = remember { AudioDownloader(context) }
    val ringtoneSetter = remember { RingtoneSetter(context) }

    var downloadVersion by remember { mutableIntStateOf(0) }

    // Real durations resolved from audio metadata (keyed by audioUrl), since backend values vary.
    val resolvedDurations = remember { mutableStateMapOf<String, Long>() }

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var processingMessage by remember { mutableStateOf<String?>(null) }
    var downloadSuccess by remember { mutableStateOf(false) }
    var successTitle by remember { mutableStateOf<String?>(null) }
    var showFailure by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf("Couldn't set automatically.") }

    var pendingRingtone by remember { mutableStateOf<Ringtone?>(null) }

    val playerState by audioPlayerManager.playerState.collectAsState()

    fun applyRingtone(ringtone: Ringtone) {
        scope.launch {
            processingMessage = "Setting ringtone…"
            val result = runCatching {
                val file = audioDownloader.download(ringtone.audioUrl, ringtone.title)
                downloadVersion++
                ringtoneSetter.setRingtone(file, ringtone.title)
            }.getOrElse { Result.failure(it) }

            processingMessage = null
            if (result.isSuccess) {
                successTitle = ringtone.title
            } else {
                failureMessage = "Couldn't set automatically."
                showFailure = true
            }
        }
    }

    fun onSetRingtone(ringtone: Ringtone) {
        scope.launch {
            processingMessage = "Preparing ringtone…"
            val prepared = runCatching {
                audioDownloader.download(ringtone.audioUrl, ringtone.title)
            }
            processingMessage = null

            if (prepared.isFailure) {
                failureMessage = "Download failed."
                showFailure = true
                return@launch
            }
            downloadVersion++

            if (ringtoneSetter.hasPermission()) {
                applyRingtone(ringtone)
            } else {
                pendingRingtone = ringtone
                ringtoneSetter.requestPermission()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = pendingRingtone
                if (pending != null && ringtoneSetter.hasPermission()) {
                    pendingRingtone = null
                    applyRingtone(pending)
                } else if (pending != null && !ringtoneSetter.hasPermission()) {
                    pendingRingtone = null
                    failureMessage = "Permission not granted."
                    showFailure = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { audioPlayerManager.release() }
    }

    val filtered = remember(ringtones, searchQuery) {
        if (searchQuery.isBlank()) ringtones
        else ringtones.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            RingtoneHeader(
                searchActive = searchActive,
                onToggleSearch = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                }
            )

            AnimatedVisibility(
                visible = searchActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    placeholder = { Text("Search adhans…", color = HeaderSubtitle) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = HeaderSubtitle)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SearchSurface,
                        unfocusedContainerColor = SearchSurface,
                        focusedBorderColor = BrandGreen,
                        unfocusedBorderColor = Color(0xFF2A2A2A),
                        focusedTextColor = HeaderTitle,
                        unfocusedTextColor = HeaderTitle,
                        cursorColor = BrandGreen
                    )
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandGreen)
                    }
                }

                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No ringtones available"
                            else "No results found",
                            color = HeaderSubtitle
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(filtered) { index, ringtone ->
                            val isDownloaded = remember(downloadVersion, ringtone.title) {
                                audioDownloader.isDownloaded(ringtone.title)
                            }

                            val isActive = playerState.currentUrl == ringtone.audioUrl

                            LaunchedEffect(ringtone.audioUrl, downloadVersion) {
                                if (resolvedDurations[ringtone.audioUrl] == null) {
                                    AudioDurationResolver.resolveMs(
                                        url = ringtone.audioUrl,
                                        localFile = audioDownloader.localFile(ringtone.title)
                                    )?.let { resolvedDurations[ringtone.audioUrl] = it }
                                }
                            }

                            val resolvedMs = resolvedDurations[ringtone.audioUrl]
                            val trackDurationMs = resolvedMs
                                ?: (ringtone.durationSeconds * 1000L)

                            RingtoneCard(
                                ringtone = ringtone,
                                isPlaying = isActive && playerState.isPlaying,
                                currentPosition = if (isActive) playerState.currentPosition else 0L,
                                duration = if (isActive && playerState.duration > 0)
                                    playerState.duration else trackDurationMs,
                                gradientIndex = index,
                                isDownloaded = isDownloaded,
                                onSeek = { position -> audioPlayerManager.seekTo(position) },
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
                                },
                                onDownloadClick = {
                                    scope.launch {
                                        processingMessage = "Downloading…"
                                        val result = runCatching {
                                            audioDownloader.download(
                                                url = ringtone.audioUrl,
                                                fileName = ringtone.title
                                            )
                                        }
                                        processingMessage = null
                                        if (result.isSuccess) {
                                            downloadVersion++
                                            downloadSuccess = true
                                        } else {
                                            failureMessage = "Download failed."
                                            showFailure = true
                                        }
                                    }
                                },
                                onSetRingtoneClick = { onSetRingtone(ringtone) }
                            )
                        }
                    }
                }
            }
        }

        processingMessage?.let { message ->
            RingtoneProcessingDialog(message = message)
        }

        if (downloadSuccess) {
            DownloadSuccessDialog(onDone = { downloadSuccess = false })
        }

        successTitle?.let { title ->
            RingtoneSuccessDialog(
                title = title,
                onDone = { successTitle = null }
            )
        }

        if (showFailure) {
            RingtoneFailureDialog(
                message = failureMessage,
                onOpenSettings = {
                    showFailure = false
                    ringtoneSetter.openRingtoneSettings()
                },
                onDismiss = { showFailure = false }
            )
        }
    }
}

@Composable
private fun RingtoneHeader(
    searchActive: Boolean,
    onToggleSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ringtones",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderTitle
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Premium Islamic ringtones for your device",
                fontSize = 14.sp,
                color = HeaderSubtitle
            )
        }

        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier
                .size(46.dp)
                .background(SearchSurface, CircleShape)
        ) {
            Icon(
                imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (searchActive) "Close search" else "Search",
                tint = HeaderTitle,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
