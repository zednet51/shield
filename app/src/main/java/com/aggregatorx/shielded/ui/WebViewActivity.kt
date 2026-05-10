package com.aggregatorx.shielded.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.viewinterop.AndroidView
import com.aggregatorx.shielded.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url   = intent.getStringExtra("url")   ?: ""
        val title = intent.getStringExtra("title") ?: "Browser"
        setContent { ShieldTheme { InAppBrowserScreen(url = url, title = title, onBack = { finish() }) } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppBrowserScreen(url: String, title: String, onBack: () -> Unit) {
    var pageTitle by remember { mutableStateOf(title) }
    var progress  by remember { mutableStateOf(0) }
    var webViewRef: WebView? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
        TopAppBar(
            title = { Text(pageTitle, color = NeonGreen, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                }
            },
            actions = {
                IconButton(onClick = { webViewRef?.goBack() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = NeonGreen)
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = NeonGreen)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
        )
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = NeonGreen,
                trackColor = BorderGreen
            )
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            pageTitle = view?.title ?: title
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        override fun onReceivedTitle(view: WebView?, t: String?) { pageTitle = t ?: title }
                    }
                    if (url.isNotBlank()) loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
