package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.ui.components.ShortsItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeSubTab
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val homeSubTab by viewModel.homeSubTab.collectAsState()
    val longVideos by viewModel.longVideos.collectAsState()
    val shorts by viewModel.shorts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val commentsMap by viewModel.comments.collectAsState()
    val isVideoPlaying by viewModel.isVideoPlaying.collectAsState()
    val videoWatchSec by viewModel.videoWatchSec.collectAsState()

    val categories = listOf("All", "Trending", "Earning", "Tech", "Tutorial", "Crypto", "Sports")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { viewModel.openAdminLogin() }
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(BrandRed, BrandRedDark))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Vid",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Earn",
                            color = BrandRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                    Text(
                        text = "Watch & Earn Cash",
                        color = DarkTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right header actions: Balance Pill & Admin Padlock
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Coins Balance Pill (Tapping goes to Wallet)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkCard,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.clickable { viewModel.setTab(MainTab.WALLET) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Coins",
                            tint = CoinGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${currentUser?.coins ?: 0}",
                            color = CoinGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Discreet Admin Portal Button
                IconButton(
                    onClick = { viewModel.openAdminLogin() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Admin Login",
                        tint = DarkTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Sub-Tab Pill Switcher: [Long Videos] vs [Shorts 🔥]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (homeSubTab == HomeSubTab.LONG_VIDEOS) BrandRed else DarkCard,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clickable { viewModel.setHomeSubTab(HomeSubTab.LONG_VIDEOS) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Long Videos",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (homeSubTab == HomeSubTab.SHORTS) TikTokPink else DarkCard,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clickable { viewModel.setHomeSubTab(HomeSubTab.SHORTS) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shorts 🔥",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Content Area based on Sub-Tab
        if (homeSubTab == HomeSubTab.LONG_VIDEOS) {
            // Long Videos Feed
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Search Input Field
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search videos or creators...", fontSize = 13.sp, color = DarkTextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = DarkTextMuted)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = DarkTextMuted)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
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
                }

                // Category Chips
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == selectedCategory
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color.White else DarkCard,
                                border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.clickable { viewModel.setCategory(cat) }
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.Black else DarkTextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // AdSterra Top Banner Ad Slot
                if (adminSettings.isAdsEnabled && adminSettings.isBannerAdEnabled) {
                    item {
                        AdSterraBanner(
                            adScriptTag = adminSettings.adSterraBannerScript,
                            directUrl = adminSettings.adSterraDirectLink
                        )
                    }
                }

                // Video Items List
                items(longVideos) { video ->
                    VideoCard(
                        video = video,
                        onWatchClick = { viewModel.openWatchVideo(video) }
                    )
                }

                if (longVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = DarkTextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No videos found in this category",
                                    color = DarkTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Shorts Vertical Feed (TikTok-Style Swiping)
            if (shorts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No Shorts available yet. Upload one!", color = DarkTextSecondary)
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { shorts.size })

                LaunchedEffect(pagerState.currentPage) {
                    viewModel.onShortChanged(pagerState.currentPage)
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val shortVideo = shorts[page]
                    val commentsForShort = commentsMap[shortVideo.id] ?: emptyList()

                    ShortsItem(
                        video = shortVideo,
                        isPlaying = isVideoPlaying,
                        watchSeconds = if (pagerState.currentPage == page) videoWatchSec else 0,
                        requiredWatchSeconds = adminSettings.requiredShortWatchSec,
                        onTogglePlay = { viewModel.toggleVideoPlayback() },
                        onLikeClick = { viewModel.toggleLike(shortVideo) },
                        onCommentClick = { /* Handled inside ShortsItem */ },
                        commentsList = commentsForShort,
                        onAddComment = { text -> viewModel.postComment(shortVideo.id, text) },
                        isActivePage = (pagerState.currentPage == page)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    video: Video,
    onWatchClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(onClick = onWatchClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column {
            // Video Thumbnail Container strictly following 16:9 aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF1E2130))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play Button Overlay Icon
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Duration badge bottom right
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = video.durationLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Coin reward badge top left
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E1E2E).copy(alpha = 0.9f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = CoinGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+${video.rewardCoins} Coins",
                            color = CoinGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Video Meta & Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = video.channelAvatar.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100" },
                    contentDescription = video.channelName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${video.channelName} • ${video.views} views • ${video.category}",
                        color = DarkTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Watch Button
                Button(
                    onClick = onWatchClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Watch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
