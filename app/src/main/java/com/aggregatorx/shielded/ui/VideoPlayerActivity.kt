package com.aggregatorx.shielded.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aggregatorx.shielded.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url   = intent.getStringExtra("url")   ?: ""
        val title = intent.getStringExtra("title") ?: "Player"
        setContent {
            ShieldTheme {
                VideoPlayerScreen(url = url, title = title, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            if (url.isNotBlank()) {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
        TopAppBar(
            title = { Text(title, color = NeonGreen, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
        )
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
        if (url.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No stream URL available", color = AccentRed,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
