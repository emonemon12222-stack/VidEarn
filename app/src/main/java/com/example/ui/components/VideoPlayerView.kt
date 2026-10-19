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
    val isShortVideo = remember(videoUrl, isShort) {
        isShort || YouTubeHelper.isShortsUrl(videoUrl)
    }

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

                    // Enable third-party cookies: Required by YouTube to authenticate embed sessions and prevent error 152-4
                    try {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowContentAccess = true
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // Clean user-agent: remove '; wv' and 'Version/4.0 ' so YouTube server
                        // treats the request as a standard Chrome Mobile browser, bypassing embed restrictions
                        val defaultUa = userAgentString
                        val cleanUa = defaultUa
                            .replace("; wv", "")
                            .replace("Version/4.0 ", "")
                        userAgentString = cleanUa
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun getDefaultVideoPoster(): Bitmap? {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // Keep video playback requests within this WebView
                            return false
                        }
                    }

                    // Load appropriate embed
                    if (videoId != null) {
                        val html = YouTubeHelper.buildEmbedHtml(videoId, isShortVideo)
                        loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", "https://www.youtube.com")
                    } else if (videoUrl.isNotBlank()) {
                        val html = YouTubeHelper.buildDirectVideoHtml(videoUrl)
                        loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
                    }
                }
            },
            update = { webView ->
                val currentTag = webView.tag as? String
                if (currentTag != videoUrl) {
                    webView.tag = videoUrl
                    if (videoId != null) {
                        val html = YouTubeHelper.buildEmbedHtml(videoId, isShortVideo)
                        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", "https://www.youtube.com")
                    } else if (videoUrl.isNotBlank()) {
                        val html = YouTubeHelper.buildDirectVideoHtml(videoUrl)
                        webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
                    }
                }
            },
            onRelease = { webView ->
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
    }
}
