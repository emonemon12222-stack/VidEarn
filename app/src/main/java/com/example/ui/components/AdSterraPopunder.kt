package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CoinGold
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkTextMuted
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.NeonCyan

private const val TAG = "AdSterraPopunder"

@Composable
fun AdSterraPopunderDialog(
    popunderUrl: String,
    popunderScript: String = "",
    fallbackUrl: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveUrl = popunderUrl.ifBlank { fallbackUrl }.ifBlank { "https://adsterra.com" }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(CoinGold, NeonCyan))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Glowing Emblem
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(CoinGold.copy(alpha = 0.25f), NeonCyan.copy(alpha = 0.2f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = "Partner Deal",
                        tint = CoinGold,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CoinGold.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "ADSTERRA SPONSOR DEAL",
                        color = CoinGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Special Partner Announcement!",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Earn up to 200% extra rewards and unlock bonus cashout channels by visiting our verified partner portal.",
                    color = DarkTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // Invisible / compact WebView executing the Popunder JavaScript tag
                if (popunderScript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AndroidView(
                        factory = { ctx ->
                            createPopunderWebView(ctx, popunderScript, effectiveUrl)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Trigger Button (Direct Link to Partner Offer)
                Button(
                    onClick = {
                        launchUrlSafely(context, effectiveUrl)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Visit Partner Offer", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to VidEarn", color = DarkTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Creates and configures a WebView specifically for AdSterra Popunder scripts.
 * Configured with javaScriptEnabled, domStorageEnabled, databaseEnabled, supportMultipleWindows,
 * and a WebChromeClient that captures onCreateWindow so popup/popunder windows open safely
 * in the system browser without being blocked.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createPopunderWebView(context: Context, scriptContent: String, fallbackUrl: String): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(1, 1)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false

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
                Log.w(TAG, "AdSterra Popunder notice: ${error?.description}")
            }
        }

        val html = if (scriptContent.contains("<script", ignoreCase = true)) {
            """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body>
                    $scriptContent
                    <script>
                        // Trigger synthetic click after DOM is ready to activate popunder script
                        window.addEventListener('load', function() {
                            setTimeout(function() {
                                document.body.click();
                            }, 500);
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()
        } else {
            val src = if (scriptContent.startsWith("//")) "https:$scriptContent" else scriptContent
            """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <script type="text/javascript" src="$src"></script>
                </head>
                <body>
                    <script>
                        window.addEventListener('load', function() {
                            setTimeout(function() {
                                document.body.click();
                            }, 500);
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()
        }

        loadDataWithBaseURL("https://adsterra.com", html, "text/html", "UTF-8", null)
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
        Log.e(TAG, "Failed to launch popunder url: ${e.message}")
    }
}
