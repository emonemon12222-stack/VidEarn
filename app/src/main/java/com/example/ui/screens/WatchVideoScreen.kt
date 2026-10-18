package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Video
import com.example.ui.components.AdSterraBanner
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun WatchVideoScreen(
    video: Video,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVideoPlaying by viewModel.isVideoPlaying.collectAsState()
    val videoWatchSec by viewModel.videoWatchSec.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val commentsMap by viewModel.comments.collectAsState()

    var isSubscribed by remember { mutableStateOf(false) }
    var isDescExpanded by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }

    val requiredSec = adminSettings.requiredLongWatchSec
    val progress = (videoWatchSec.toFloat() / requiredSec.toFloat()).coerceIn(0f, 1f)
    val remainingSec = (requiredSec - videoWatchSec).coerceAtLeast(0)

    val currentComments = commentsMap[video.id] ?: emptyList()
    val relatedVideos = allVideos.filter { it.id != video.id && it.type == video.type }

    BackHandler {
        viewModel.closeWatchVideo()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Video Player Container (YouTube & Direct Video Player)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black)
        ) {
            // Embedded YouTube & Direct Video Player
            VideoPlayerView(
                videoUrl = video.videoUrl,
                isShort = false,
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar over Player: Back button & Video Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.closeWatchVideo() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Bottom Progress Bar for coin earning
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = BrandRed,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }

        // Live Coin Earning Status Bar below player
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkCard,
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Coin Reward",
                        tint = CoinGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (remainingSec > 0) {
                        Text(
                            text = "Watch ${remainingSec}s to claim reward",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "🎉 +${video.rewardCoins} Coins Claimed!",
                            color = CoinGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CoinGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "+${video.rewardCoins} Coins",
                        color = CoinGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Scrollable Video Details & Comments & Recommendations
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
        ) {
            // Video Title & Category
            item {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${video.views} views • Uploaded on VidEarn • #${video.category}",
                    color = DarkTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Interactive Actions Bar (Like, Dislike, Share, Save)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Like button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkCard,
                        modifier = Modifier.clickable { viewModel.toggleLike(video) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (video.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (video.isLiked) BrandRed else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${video.likes}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Share button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkCard,
                        modifier = Modifier.clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, video.title)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Watch '${video.title}' and earn instant coins on VidEarn! ${video.videoUrl}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Download / Offline
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkCard
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkCardBorder)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Channel Info & Subscribe Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = video.channelAvatar.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100" },
                        contentDescription = video.channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = video.channelName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "24.5K subscribers",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = { isSubscribed = !isSubscribed },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) DarkCard else Color.White,
                            contentColor = if (isSubscribed) Color.White else Color.Black
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Video Description Box
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDescExpanded = !isDescExpanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = video.description.ifBlank { "No detailed description provided for this video. Watch and earn your reward coins!" },
                            color = DarkTextSecondary,
                            fontSize = 12.sp,
                            maxLines = if (isDescExpanded) 20 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDescExpanded) "Show less" else "...more",
                            color = BrandRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // AdSterra Banner Slot inside watch page
            if (adminSettings.isAdsEnabled && adminSettings.isBannerAdEnabled) {
                item {
                    AdSterraBanner(
                        adScriptTag = adminSettings.adSterraBannerScript,
                        directUrl = adminSettings.adSterraDirectLink,
                        title = "Exclusive Offer: Get Cash Rewards for Playing Games!",
                        description = "Partner with AdSterra • Instant Withdrawals Available"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Comments Section Header
            item {
                Text(
                    text = "Comments (${currentComments.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Add comment input box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("Write a comment...", fontSize = 12.sp, color = DarkTextMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                viewModel.postComment(video.id, commentInput)
                                commentInput = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = BrandRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Post Comment",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Comments List
            items(currentComments) { comment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = comment.userName.take(1).uppercase(),
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.userName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = comment.timestamp,
                                color = DarkTextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = comment.content,
                            color = DarkTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkCardBorder)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Up Next / Recommended",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Up next related videos
            items(relatedVideos) { related ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { viewModel.openWatchVideo(related) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = related.thumbnailUrl,
                                contentDescription = related.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(3.dp),
                                color = Color.Black.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = related.durationLabel,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = related.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${related.channelName} • +${related.rewardCoins} Coins",
                                color = CoinGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
