package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.YouTubeHelper

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayerView(
    videoUrl: String,
    isShort: Boolean = false,
    modifier: Modifier = Modifier
) {
    val videoId = remember(videoUrl) { YouTubeHelper.extractVideoId(videoUrl) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowContentAccess = true
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun getDefaultVideoPoster(): Bitmap? {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // Keep navigation inside iframe
                            return false
                        }
                    }

                    // Load appropriate embed
                    if (videoId != null) {
                        val html = YouTubeHelper.buildEmbedHtml(videoId, isShort)
                        loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                    } else if (videoUrl.isNotBlank()) {
                        val html = YouTubeHelper.buildDirectVideoHtml(videoUrl)
                        loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
                    }
                }
            },
            update = { webView ->
                // Update when videoUrl changes
                val currentTag = webView.tag as? String
                if (currentTag != videoUrl) {
                    webView.tag = videoUrl
                    if (videoId != null) {
                        val html = YouTubeHelper.buildEmbedHtml(videoId, isShort)
                        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                    } else if (videoUrl.isNotBlank()) {
                        val html = YouTubeHelper.buildDirectVideoHtml(videoUrl)
                        webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
                    }
                }
            }
        )
    }
}
