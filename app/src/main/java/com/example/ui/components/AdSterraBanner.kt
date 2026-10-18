package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdSterraBanner(
    modifier: Modifier = Modifier,
    adScriptTag: String = "//adsterra-banner-code-zone-918231.js",
    directUrl: String = "https://example.com/adsterra-sponsor",
    title: String = "Sponsored: Claim 200% Bonus On Trading & Earning!",
    description: String = "Official AdSterra Network Partner • Fast Verification & Instant Cashout",
    isDismissible: Boolean = true
) {
    var isVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val isHtmlScript = remember(adScriptTag) {
        val trimmed = adScriptTag.trim()
        trimmed.contains("<script", ignoreCase = true) ||
            trimmed.contains("<iframe", ignoreCase = true) ||
            trimmed.contains("<div", ignoreCase = true)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E1E2E),
                            Color(0xFF28283E)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            NeonCyan.copy(alpha = 0.5f),
                            CoinGold.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isHtmlScript) {
                    // Embedded WebView executing AdSterra Banner script
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean {
                                        if (!url.isNullOrBlank()) {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                return true
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                        return false
                                    }
                                }
                                val htmlWrapper = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                      <style>
                                        body { margin: 0; padding: 4px; background: transparent; display: flex; justify-content: center; align-items: center; overflow: hidden; }
                                      </style>
                                    </head>
                                    <body>
                                      $adScriptTag
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://adsterra.com", htmlWrapper, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                    )
                }

                // Native Responsive Banner Card with Direct Link trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val target = directUrl.ifBlank { "https://adsterra.com" }
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Ad Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFE2C55)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Ad",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CoinGold.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "ADSTERRA",
                                    color = CoinGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
                            text = description,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Visit Sponsor",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )

                    if (isDismissible) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { isVisible = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Ad",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
