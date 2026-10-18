package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.theme.CoinGold
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TikTokPink

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

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
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
                        try {
                            val target = socialBarUrl.ifBlank { fallbackDirectLink }.ifBlank { "https://adsterra.com" }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
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

                // Claim Button
                Button(
                    onClick = {
                        try {
                            val target = socialBarUrl.ifBlank { fallbackDirectLink }.ifBlank { "https://adsterra.com" }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
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
