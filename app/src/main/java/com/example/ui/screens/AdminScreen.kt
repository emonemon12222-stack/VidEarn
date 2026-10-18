package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.AdminSettings
import com.example.data.model.WithdrawalStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminTab
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AdminLoginDialog(
    viewModel: MainViewModel
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { viewModel.closeAdminLogin() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.5.dp, NeonCyan),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin",
                        tint = NeonCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Admin Access Portal",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )

                Text(
                    text = "Authorized Personnel Only",
                    color = DarkTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = null
                    },
                    placeholder = { Text("Enter Username", color = DarkTextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = DarkTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = { Text("Enter Password", color = DarkTextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = DarkTextMuted) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = DarkTextMuted
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = StatusRejected, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.closeAdminLogin() },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val success = viewModel.adminLogin(username, password)
                            if (!success) {
                                error = "Invalid credentials. Access Denied."
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Login", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val adminTab by viewModel.adminTab.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()

    var noteText by remember { mutableStateOf("") }
    var selectedWithdrawalId by remember { mutableStateOf<String?>(null) }
    var showActionDialog by remember { mutableStateOf(false) }
    var actionType by remember { mutableStateOf("APPROVE") } // APPROVE or REJECT

    // Editable settings state
    var shortCoins by remember(adminSettings) { mutableStateOf(adminSettings.coinsPerShortWatch.toString()) }
    var shortSec by remember(adminSettings) { mutableStateOf(adminSettings.requiredShortWatchSec.toString()) }
    var longCoins by remember(adminSettings) { mutableStateOf(adminSettings.coinsPerLongWatch.toString()) }
    var longSec by remember(adminSettings) { mutableStateOf(adminSettings.requiredLongWatchSec.toString()) }
    var webCoins by remember(adminSettings) { mutableStateOf(adminSettings.coinsPerWebsiteVisit.toString()) }
    var uploadCoins by remember(adminSettings) { mutableStateOf(adminSettings.coinsPerUpload.toString()) }
    var rateText by remember(adminSettings) { mutableStateOf(adminSettings.coinToBdtRate.toString()) }
    var minCoinsText by remember(adminSettings) { mutableStateOf(adminSettings.minWithdrawalCoins.toString()) }
    // 1. Banner Ad State
    var bannerEnabled by remember(adminSettings) { mutableStateOf(adminSettings.isBannerAdEnabled) }
    var adScriptText by remember(adminSettings) { mutableStateOf(adminSettings.adSterraBannerScript) }

    // 2. Direct Link State
    var directLinkEnabled by remember(adminSettings) { mutableStateOf(adminSettings.isDirectLinkEnabled) }
    var adLinkText by remember(adminSettings) { mutableStateOf(adminSettings.adSterraDirectLink) }

    // 3. Social Bar Ad State
    var socialBarEnabled by remember(adminSettings) { mutableStateOf(adminSettings.isSocialBarEnabled) }
    var socialBarScript by remember(adminSettings) { mutableStateOf(adminSettings.adSterraSocialBarScript) }
    var socialBarUrl by remember(adminSettings) { mutableStateOf(adminSettings.adSterraSocialBarUrl) }

    // 4. Popunder Ad State
    var popunderEnabled by remember(adminSettings) { mutableStateOf(adminSettings.isPopunderEnabled) }
    var popunderScript by remember(adminSettings) { mutableStateOf(adminSettings.adSterraPopunderScript) }
    var popunderUrl by remember(adminSettings) { mutableStateOf(adminSettings.adSterraPopunderUrl) }

    // Master Switch State
    var adsEnabled by remember(adminSettings) { mutableStateOf(adminSettings.isAdsEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Admin Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NeonCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Admin Control Center",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "VidEarn Control Center",
                        color = NeonCyan,
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = { viewModel.adminLogout() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkCard,
                    contentColor = StatusRejected
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Exit Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Admin Navigation Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                AdminTab.OVERVIEW to "📊 Stats",
                AdminTab.SETTINGS to "⚙️ Reward Rates",
                AdminTab.WITHDRAWALS to "💰 Cashouts (${withdrawals.count { it.status == WithdrawalStatus.PENDING }})",
                AdminTab.VIDEOS to "🎬 Videos (${allVideos.size})",
                AdminTab.USERS to "👥 Users (${allUsers.size})",
                AdminTab.ADS to "📢 AdSterra Ads"
            )

            items(tabs) { (tab, title) ->
                val isSelected = adminTab == tab
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) NeonCyan else DarkSurface,
                    border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.clickable { viewModel.setAdminTab(tab) }
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Body Content by AdminTab
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (adminTab) {
                AdminTab.OVERVIEW -> {
                    item {
                        Text(
                            text = "Platform Live Overview",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Stat Cards Grid
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AdminStatCard(
                                title = "Total Users",
                                value = "${allUsers.size + 142}",
                                subtitle = "128 Online Now",
                                icon = Icons.Default.People,
                                color = NeonCyan,
                                modifier = Modifier.weight(1f)
                            )
                            AdminStatCard(
                                title = "Total Videos",
                                value = "${allVideos.size}",
                                subtitle = "${allVideos.count { it.type.name == "SHORT" }} Shorts",
                                icon = Icons.Default.VideoLibrary,
                                color = BrandRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AdminStatCard(
                                title = "Pending Cashouts",
                                value = "${withdrawals.count { it.status == WithdrawalStatus.PENDING }}",
                                subtitle = "Needs Approval",
                                icon = Icons.Default.PendingActions,
                                color = StatusPending,
                                modifier = Modifier.weight(1f)
                            )
                            AdminStatCard(
                                title = "Total Paid",
                                value = "৳ ${String.format("%.2f", withdrawals.filter { it.status == WithdrawalStatus.APPROVED }.sumOf { it.payoutAmount })}",
                                subtitle = "Dispatched",
                                icon = Icons.Default.CheckCircle,
                                color = StatusSuccess,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "System Operational Status",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "• Database: Local Persistent & Cloud Ready\n• Video Stream Engine: Active (MP4 & YouTube)\n• Ad Partner: AdSterra Network Script Injection Enabled\n• Payment Gateways: bKash, Nagad, Rocket, Binance",
                                    color = DarkTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                AdminTab.SETTINGS -> {
                    item {
                        Text(
                            text = "Configure Dynamic Earning & Reward Rates",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Changes take effect immediately across all user screens",
                            color = DarkTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Shorts Settings
                                Text(text = "Shorts Rewards", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = shortCoins,
                                        onValueChange = { shortCoins = it },
                                        label = { Text("Coins per Short") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = shortSec,
                                        onValueChange = { shortSec = it },
                                        label = { Text("Required Sec") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }

                                // Long Videos Settings
                                Text(text = "Long Videos Rewards", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = longCoins,
                                        onValueChange = { longCoins = it },
                                        label = { Text("Coins per Long") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = longSec,
                                        onValueChange = { longSec = it },
                                        label = { Text("Required Sec") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }

                                // Upload and Web Visit
                                Text(text = "Tasks & Uploads", color = CoinGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = webCoins,
                                        onValueChange = { webCoins = it },
                                        label = { Text("Website Visit Coins") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = uploadCoins,
                                        onValueChange = { uploadCoins = it },
                                        label = { Text("Upload Video Coins") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }

                                // Conversion & Minimum
                                Text(text = "Conversion & Payout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = rateText,
                                        onValueChange = { rateText = it },
                                        label = { Text("Rate (1 Coin in BDT)") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                    OutlinedTextField(
                                        value = minCoinsText,
                                        onValueChange = { minCoinsText = it },
                                        label = { Text("Min Withdrawal Coins") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        val updated = adminSettings.copy(
                                            coinsPerShortWatch = shortCoins.toIntOrNull() ?: 15,
                                            requiredShortWatchSec = shortSec.toIntOrNull() ?: 10,
                                            coinsPerLongWatch = longCoins.toIntOrNull() ?: 30,
                                            requiredLongWatchSec = longSec.toIntOrNull() ?: 20,
                                            coinsPerWebsiteVisit = webCoins.toIntOrNull() ?: 20,
                                            coinsPerUpload = uploadCoins.toIntOrNull() ?: 50,
                                            coinToBdtRate = rateText.toDoubleOrNull() ?: 0.01,
                                            minWithdrawalCoins = minCoinsText.toLongOrNull() ?: 500L
                                        )
                                        viewModel.updateSettings(updated)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Earning Rates", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AdminTab.WITHDRAWALS -> {
                    item {
                        Text(
                            text = "User Withdrawal Requests (${withdrawals.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (withdrawals.isEmpty()) {
                        item {
                            Text(text = "No withdrawal requests found.", color = DarkTextSecondary)
                        }
                    } else {
                        items(withdrawals) { req ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${req.userName} (${req.userEmail})",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = when (req.status) {
                                                WithdrawalStatus.PENDING -> StatusPending.copy(alpha = 0.2f)
                                                WithdrawalStatus.APPROVED -> StatusSuccess.copy(alpha = 0.2f)
                                                WithdrawalStatus.REJECTED -> StatusRejected.copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                text = req.status.name,
                                                color = when (req.status) {
                                                    WithdrawalStatus.PENDING -> StatusPending
                                                    WithdrawalStatus.APPROVED -> StatusSuccess
                                                    WithdrawalStatus.REJECTED -> StatusRejected
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Method: ${req.method.displayName} • Account: ${req.accountAddress}",
                                        color = NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Text(
                                        text = "Amount: ${req.coinAmount} Coins -> ${String.format("%.2f", req.payoutAmount)} ${req.currency}",
                                        color = CoinGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (req.adminNote.isNotBlank()) {
                                        Text(
                                            text = "Note: ${req.adminNote}",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (req.status == WithdrawalStatus.PENDING) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    selectedWithdrawalId = req.id
                                                    actionType = "APPROVE"
                                                    noteText = "TrxID: TX" + (100000..999999).random()
                                                    showActionDialog = true
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = StatusSuccess,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("Approve Payout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    selectedWithdrawalId = req.id
                                                    actionType = "REJECT"
                                                    noteText = "Invalid account number or name mismatch"
                                                    showActionDialog = true
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = StatusRejected,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("Reject & Refund", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AdminTab.VIDEOS -> {
                    item {
                        Text(
                            text = "Content Management (${allVideos.size} Videos)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(allVideos) { video ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = video.thumbnailUrl,
                                        contentDescription = video.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${video.type.name} • ${video.views} views • Creator: ${video.channelName}",
                                        color = DarkTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(onClick = { viewModel.deleteVideo(video.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = StatusRejected
                                    )
                                }
                            }
                        }
                    }
                }

                AdminTab.USERS -> {
                    item {
                        Text(
                            text = "User Accounts Management",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(allUsers) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.name.take(1).uppercase(),
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${user.name} (${user.coins} Coins)",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${user.email} • Code: ${user.referralCode}",
                                        color = DarkTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Button(
                                    onClick = { viewModel.toggleUserBan(user.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (user.isBanned) StatusSuccess else StatusRejected,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(
                                        text = if (user.isBanned) "Unban" else "Ban",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                AdminTab.ADS -> {
                    item {
                        Column {
                            Text(
                                text = "AdSterra Advertising Management",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Individually configure and toggle all 4 AdSterra ad formats across the application",
                                color = DarkTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Global Master Ad Switch Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (adsEnabled) NeonCyan.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = if (adsEnabled) NeonCyan else Color.Gray,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Global Ads Master Switch",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (adsEnabled) "Ads Active across all screens" else "All ads globally paused",
                                            color = if (adsEnabled) NeonCyan else DarkTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = adsEnabled,
                                    onCheckedChange = { adsEnabled = it }
                                )
                            }
                        }
                    }

                    // 1. Banner Ad (Script Tag / Zone ID / HTML)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(NeonCyan.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ViewAgenda,
                                                contentDescription = null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "1. Banner Ad",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Home, Watch Video & Earn slots",
                                                color = DarkTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = bannerEnabled,
                                        onCheckedChange = { bannerEnabled = it }
                                    )
                                }

                                OutlinedTextField(
                                    value = adScriptText,
                                    onValueChange = { adScriptText = it },
                                    label = { Text("Banner Script Tag / Zone ID / HTML") },
                                    placeholder = { Text("//adsterra-banner-code-zone-918231.js or <script>...</script>") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkCard,
                                        unfocusedContainerColor = DarkCard,
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Text(
                                    text = "Supports JavaScript <script> tags, Zone IDs, or raw HTML banner snippets.",
                                    color = DarkTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // 2. Direct Link / Smartlink URL
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CoinGold.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                tint = CoinGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "2. Direct Link / Smartlink URL",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "High-converting partner landing",
                                                color = DarkTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = directLinkEnabled,
                                        onCheckedChange = { directLinkEnabled = it }
                                    )
                                }

                                OutlinedTextField(
                                    value = adLinkText,
                                    onValueChange = { adLinkText = it },
                                    label = { Text("AdSterra Direct Link / Smartlink URL") },
                                    placeholder = { Text("https://example.com/adsterra-direct-earn") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkCard,
                                        unfocusedContainerColor = DarkCard,
                                        focusedBorderColor = CoinGold,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Text(
                                    text = "Smartlink triggered upon clicking partner banners and sponsored earning tasks.",
                                    color = DarkTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // 3. Social Bar Ad (Script Tag / URL)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TikTokPink.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = TikTokPink,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "3. Social Bar Ad",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Floating notification engaging bar",
                                                color = DarkTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = socialBarEnabled,
                                        onCheckedChange = { socialBarEnabled = it }
                                    )
                                }

                                OutlinedTextField(
                                    value = socialBarScript,
                                    onValueChange = { socialBarScript = it },
                                    label = { Text("Social Bar Script Tag / Zone") },
                                    placeholder = { Text("//adsterra-socialbar-zone-839201.js") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkCard,
                                        unfocusedContainerColor = DarkCard,
                                        focusedBorderColor = TikTokPink,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = socialBarUrl,
                                    onValueChange = { socialBarUrl = it },
                                    label = { Text("Social Bar Target / Landing URL") },
                                    placeholder = { Text("https://example.com/adsterra-socialbar-promo") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkCard,
                                        unfocusedContainerColor = DarkCard,
                                        focusedBorderColor = TikTokPink,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Text(
                                    text = "Renders an eye-catching floating push notification bar with pulsing animation.",
                                    color = DarkTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // 4. Popunder Ad (Script Tag / URL)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(BrandRed.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WebAsset,
                                                contentDescription = null,
                                                tint = BrandRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "4. Popunder Ad",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Triggered on video and user interactions",
                                                color = DarkTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = popunderEnabled,
                                        onCheckedChange = { popunderEnabled = it }
                                    )
                                }

                                OutlinedTextField(
                                    value = popunderScript,
                                    onValueChange = { popunderScript = it },
                                    label = { Text("Popunder Script Tag / Zone") },
                                    placeholder = { Text("//adsterra-popunder-zone-492012.js") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkCard,
                                        unfocusedContainerColor = DarkCard,
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = popunderUrl,
                                    onValueChange = { popunderUrl = it },
                                    label = { Text("Popunder Target / Landing URL") },
                                    placeholder = { Text("https://example.com/adsterra-popunder-landing") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkCard,
                                        unfocusedContainerColor = DarkCard,
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Text(
                                    text = "Opens partner landing page on video selections or high-intent actions.",
                                    color = DarkTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Save All AdSterra Configurations Button
                    item {
                        Button(
                            onClick = {
                                viewModel.updateSettings(
                                    adminSettings.copy(
                                        isAdsEnabled = adsEnabled,
                                        isBannerAdEnabled = bannerEnabled,
                                        adSterraBannerScript = adScriptText,
                                        isDirectLinkEnabled = directLinkEnabled,
                                        adSterraDirectLink = adLinkText,
                                        isSocialBarEnabled = socialBarEnabled,
                                        adSterraSocialBarScript = socialBarScript,
                                        adSterraSocialBarUrl = socialBarUrl,
                                        isPopunderEnabled = popunderEnabled,
                                        adSterraPopunderScript = popunderScript,
                                        adSterraPopunderUrl = popunderUrl
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save All AdSterra Configurations", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Action dialog for Approve / Reject with note
    if (showActionDialog && selectedWithdrawalId != null) {
        val reqId = selectedWithdrawalId!!
        Dialog(onDismissRequest = { showActionDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (actionType == "APPROVE") "Approve Withdrawal #$reqId" else "Reject Withdrawal #$reqId",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(if (actionType == "APPROVE") "Transaction ID (TrxID) / Note" else "Rejection Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showActionDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (actionType == "APPROVE") {
                                    viewModel.approveWithdrawal(reqId, noteText)
                                } else {
                                    viewModel.rejectWithdrawal(reqId, noteText)
                                }
                                showActionDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (actionType == "APPROVE") StatusSuccess else StatusRejected,
                                contentColor = Color.White
                            )
                        ) {
                            Text(if (actionType == "APPROVE") "Confirm" else "Reject", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = DarkTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
