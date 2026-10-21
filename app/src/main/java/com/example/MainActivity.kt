package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdSterraPopunderDialog
import com.example.ui.components.AdSterraSocialBar
import com.example.ui.components.CoinRewardDialog
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeSubTab
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VidEarnTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val watchingVideo by viewModel.watchingVideo.collectAsState()
    val isAdminLoggedIn by viewModel.isAdminLoggedIn.collectAsState()
    val showAdminLoginDialog by viewModel.showAdminLoginDialog.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()
    val popunderUrlToLaunch by viewModel.popunderUrlToLaunch.collectAsState()
    val celebration by viewModel.celebration.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Handle back button for admin & watch video
    BackHandler(enabled = isAdminLoggedIn || watchingVideo != null) {
        if (isAdminLoggedIn) {
            viewModel.adminLogout()
        } else if (watchingVideo != null) {
            viewModel.closeWatchVideo()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkCard,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        bottomBar = {
            // Show bottom navigation bar only when authenticated and not watching full video or inside admin
            if (currentUser != null && !isAdminLoggedIn && watchingVideo == null) {
                NavigationBar(
                    containerColor = Color(0xFF12141D),
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(64.dp)
                ) {
                    // Home
                    NavigationBarItem(
                        selected = currentTab == MainTab.HOME && viewModel.homeSubTab.collectAsState().value == HomeSubTab.LONG_VIDEOS,
                        onClick = {
                            viewModel.setTab(MainTab.HOME)
                            viewModel.setHomeSubTab(HomeSubTab.LONG_VIDEOS)
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandRed,
                            selectedTextColor = BrandRed,
                            unselectedIconColor = DarkTextSecondary,
                            unselectedTextColor = DarkTextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Shorts
                    NavigationBarItem(
                        selected = currentTab == MainTab.HOME && viewModel.homeSubTab.collectAsState().value == HomeSubTab.SHORTS,
                        onClick = {
                            viewModel.setTab(MainTab.HOME)
                            viewModel.setHomeSubTab(HomeSubTab.SHORTS)
                        },
                        icon = { Icon(Icons.Default.FlashOn, contentDescription = "Shorts") },
                        label = { Text("Shorts", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TikTokPink,
                            selectedTextColor = TikTokPink,
                            unselectedIconColor = DarkTextSecondary,
                            unselectedTextColor = DarkTextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Upload Center Icon
                    NavigationBarItem(
                        selected = currentTab == MainTab.UPLOAD,
                        onClick = { viewModel.setTab(MainTab.UPLOAD) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Upload",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = { Text("Upload", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.White,
                            unselectedTextColor = DarkTextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Earn
                    NavigationBarItem(
                        selected = currentTab == MainTab.EARN,
                        onClick = { viewModel.setTab(MainTab.EARN) },
                        icon = { Icon(Icons.Default.MonetizationOn, contentDescription = "Earn") },
                        label = { Text("Earn", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CoinGold,
                            selectedTextColor = CoinGold,
                            unselectedIconColor = DarkTextSecondary,
                            unselectedTextColor = DarkTextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Wallet
                    NavigationBarItem(
                        selected = currentTab == MainTab.WALLET,
                        onClick = { viewModel.setTab(MainTab.WALLET) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
                        label = { Text("Wallet", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = DarkTextSecondary,
                            unselectedTextColor = DarkTextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Profile
                    NavigationBarItem(
                        selected = currentTab == MainTab.PROFILE,
                        onClick = { viewModel.setTab(MainTab.PROFILE) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = DarkTextSecondary,
                            unselectedTextColor = DarkTextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val hasFirebaseUser = remember {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
                } catch (e: Exception) {
                    false
                }
            }

            when {
                // If user is restoring session from persistent Firebase Auth
                currentUser == null && hasFirebaseUser -> {
                    LaunchedEffect(Unit) {
                        viewModel.checkAndRestoreSession()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.linearGradient(listOf(BrandRed, BrandRedDark))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = BrandRed,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Restoring your account...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // If user is not logged in, show AuthScreen
                currentUser == null -> {
                    AuthScreen(viewModel = viewModel)
                }

                // If Admin is logged in, show dedicated Admin Panel
                isAdminLoggedIn -> {
                    AdminScreen(viewModel = viewModel)
                }

                // If user opened a video detail watch screen
                watchingVideo != null -> {
                    WatchVideoScreen(
                        video = watchingVideo!!,
                        viewModel = viewModel
                    )
                }

                // Main navigation tabs
                else -> {
                    when (currentTab) {
                        MainTab.HOME -> HomeScreen(viewModel = viewModel)
                        MainTab.SHORTS -> {
                            // Sync with HomeScreen shorts sub-tab
                            LaunchedEffect(Unit) {
                                viewModel.setHomeSubTab(HomeSubTab.SHORTS)
                            }
                            HomeScreen(viewModel = viewModel)
                        }
                        MainTab.EARN -> EarnScreen(viewModel = viewModel)
                        MainTab.UPLOAD -> UploadScreen(viewModel = viewModel)
                        MainTab.WALLET -> WalletScreen(viewModel = viewModel)
                        MainTab.PROFILE -> ProfileScreen(viewModel = viewModel)
                    }
                }
            }

            // Admin Login Modal Dialog
            if (showAdminLoginDialog) {
                AdminLoginDialog(viewModel = viewModel)
            }

            // Coin Reward Celebration Dialog
            celebration?.let { cel ->
                CoinRewardDialog(
                    coins = cel.coins,
                    title = cel.title,
                    message = cel.message,
                    onDismiss = { viewModel.dismissCelebration() }
                )
            }

            // Floating AdSterra Social Bar
            if (adminSettings.isAdsEnabled && adminSettings.isSocialBarEnabled && currentUser != null && !isAdminLoggedIn && watchingVideo == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    AdSterraSocialBar(
                        socialBarScript = adminSettings.adSterraSocialBarScript,
                        socialBarUrl = adminSettings.adSterraSocialBarUrl,
                        fallbackDirectLink = adminSettings.adSterraDirectLink
                    )
                }
            }

            // AdSterra Popunder Dialog
            popunderUrlToLaunch?.let { popUrl ->
                AdSterraPopunderDialog(
                    popunderUrl = popUrl,
                    popunderScript = adminSettings.adSterraPopunderScript,
                    fallbackUrl = adminSettings.adSterraDirectLink,
                    onDismiss = { viewModel.clearPopunder() }
                )
            }
        }
    }
}
