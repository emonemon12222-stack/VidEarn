package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.CoinGold
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TikTokPink

private const val TAG = "AdSterraSocialBar"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdSterraSocialBar(
    modifier: Modifier = Modifier,
    socialBarScript: String = "",
    socialBarUrl: String = "https://example.com/adsterra-socialbar-promo",
    fallbackDirectLink: String = "",
    title: String = "🎁 Exclusive Bonus Offer!",
    subtitle: String = "Tap to claim 500 bonus coins on partner portal",
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(true) }

    // Pulsing animation for the notification icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val hasScript = remember(socialBarScript) {
        val trimmed = socialBarScript.trim()
        trimmed.isNotBlank() && (
            trimmed.contains("<script", ignoreCase = true) ||
            trimmed.contains(".js", ignoreCase = true) ||
            trimmed.contains("http", ignoreCase = true) ||
            trimmed.startsWith("//")
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // 1. JavaScript Execution Engine for AdSterra Social Bar Scripts
            if (hasScript) {
                AndroidView(
                    factory = { ctx ->
                        createSocialBarWebView(ctx, socialBarScript, socialBarUrl.ifBlank { fallbackDirectLink })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 2. High-Converting Custom View & Direct Link Trigger
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131728),
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(listOf(TikTokPink, NeonCyan, CoinGold))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            launchUrlSafely(context, socialBarUrl.ifBlank { fallbackDirectLink })
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing Icon
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(TikTokPink, Color(0xFFFF5252)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Alert",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Content Text
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TikTokPink.copy(alpha = 0.25f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "SOCIAL ALERT",
                                    color = TikTokPink,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = subtitle,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Claim Button (Direct Link Trigger)
                    Button(
                        onClick = {
                            launchUrlSafely(context, socialBarUrl.ifBlank { fallbackDirectLink })
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Claim", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Dismiss Button
                    IconButton(
                        onClick = {
                            isVisible = false
                            onDismiss()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Social Bar",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Creates and configures a WebView specifically tailored for AdSterra Social Bar scripts.
 * Enables JavaScript, DOM storage, database, multiple windows, and custom WebChromeClient/WebViewClient
 * to prevent Android WebView blocking policies.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createSocialBarWebView(context: Context, scriptContent: String, fallbackUrl: String): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true

            // Use standard mobile Chrome user agent without "wv" token so ad networks treat as full browser
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val newWebView = WebView(view?.context ?: context)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString()
                        if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                            launchUrlSafely(v?.context ?: context, url)
                            return true
                        }
                        return false
                    }

                    override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                        if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                            launchUrlSafely(v?.context ?: context, url)
                            return true
                        }
                        return false
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                    launchUrlSafely(view?.context ?: context, url)
                    return true
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                    launchUrlSafely(view?.context ?: context, url)
                    return true
                }
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.w(TAG, "AdSterra Social Bar load notice: ${error?.description}")
            }
        }

        val htmlContent = if (scriptContent.contains("<script", ignoreCase = true)) {
            """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body { margin: 0; padding: 0; background: transparent; overflow: hidden; display: flex; justify-content: center; align-items: center; width: 100%; height: 100%; }
                    </style>
                </head>
                <body>
                    $scriptContent
                </body>
                </html>
            """.trimIndent()
        } else {
            val scriptSrc = if (scriptContent.startsWith("//")) "https:$scriptContent" else scriptContent
            """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body { margin: 0; padding: 0; background: transparent; overflow: hidden; display: flex; justify-content: center; align-items: center; width: 100%; height: 100%; }
                    </style>
                    <script type="text/javascript" src="$scriptSrc"></script>
                </head>
                <body>
                </body>
                </html>
            """.trimIndent()
        }

        loadDataWithBaseURL("https://adsterra.com", htmlContent, "text/html", "UTF-8", null)
    }
}

private fun launchUrlSafely(context: Context, url: String) {
    try {
        val target = if (url.isBlank()) "https://adsterra.com" else url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch url: ${e.message}")
    }
}
