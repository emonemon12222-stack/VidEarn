package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PaymentMethod
import com.example.data.model.WithdrawalStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun WalletScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()
    val allWithdrawals by viewModel.withdrawals.collectAsState()
    val transactionsMap by viewModel.transactions.collectAsState()

    var selectedMethod by remember { mutableStateOf(PaymentMethod.BKASH) }
    var accountAddress by remember { mutableStateOf("") }
    var coinAmountText by remember { mutableStateOf("500") }
    var selectedWalletSubTab by remember { mutableStateOf("WITHDRAW") } // WITHDRAW, HISTORY, TRANSACTIONS

    val userCoins = currentUser?.coins ?: 0
    val coinAmount = coinAmountText.toLongOrNull() ?: 0L
    val payout = coinAmount * adminSettings.coinToBdtRate
    val effectivePayout = if (selectedMethod == PaymentMethod.BINANCE) {
        String.format("%.3f USDT", payout / 120.0)
    } else {
        String.format("%.2f BDT", payout)
    }

    val userWithdrawals = allWithdrawals.filter { it.userId == currentUser?.id }
    val userTransactions = transactionsMap[currentUser?.id] ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Cashout & Wallet",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Convert coins to instant mobile money or crypto",
                    color = DarkTextSecondary,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkCard,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Text(
                    text = "Min: ${adminSettings.minWithdrawalCoins} Coins",
                    color = CoinGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1E1B4B),
                                        Color(0xFF311042),
                                        Color(0xFF181824)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TOTAL WALLET BALANCE",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = CoinGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "$userCoins COINS",
                                color = CoinGold,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Equivalent to: ৳ ${String.format("%.2f", userCoins * adminSettings.coinToBdtRate)} BDT  /  $ ${String.format("%.3f", (userCoins * adminSettings.coinToBdtRate) / 120.0)} USD",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Sub-Navigation Tabs: [Withdraw Cash] [My Requests] [Coin Log]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "WITHDRAW" to "Withdraw",
                        "HISTORY" to "Requests (${userWithdrawals.size})",
                        "TRANSACTIONS" to "Coin Log"
                    )

                    tabs.forEach { (key, label) ->
                        val isSelected = selectedWalletSubTab == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) BrandRed else DarkSurface,
                            border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clickable { selectedWalletSubTab = key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (selectedWalletSubTab == "WITHDRAW") {
                // Withdrawal Form
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "1. Select Payment Method",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Payment Methods Grid: bKash, Nagad, Rocket, Binance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PaymentMethodItem(
                                    method = PaymentMethod.BKASH,
                                    color = BKashPink,
                                    isSelected = selectedMethod == PaymentMethod.BKASH,
                                    onClick = { selectedMethod = PaymentMethod.BKASH },
                                    modifier = Modifier.weight(1f)
                                )
                                PaymentMethodItem(
                                    method = PaymentMethod.NAGAD,
                                    color = NagadOrange,
                                    isSelected = selectedMethod == PaymentMethod.NAGAD,
                                    onClick = { selectedMethod = PaymentMethod.NAGAD },
                                    modifier = Modifier.weight(1f)
                                )
                                PaymentMethodItem(
                                    method = PaymentMethod.ROCKET,
                                    color = RocketPurple,
                                    isSelected = selectedMethod == PaymentMethod.ROCKET,
                                    onClick = { selectedMethod = PaymentMethod.ROCKET },
                                    modifier = Modifier.weight(1f)
                                )
                                PaymentMethodItem(
                                    method = PaymentMethod.BINANCE,
                                    color = BinanceYellow,
                                    isSelected = selectedMethod == PaymentMethod.BINANCE,
                                    onClick = { selectedMethod = PaymentMethod.BINANCE },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Account Number / Address Field
                            Text(
                                text = if (selectedMethod == PaymentMethod.BINANCE) "2. Binance Pay ID / USDT (BEP-20) Address" else "2. ${selectedMethod.displayName} Personal Number",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = accountAddress,
                                onValueChange = { accountAddress = it },
                                placeholder = {
                                    Text(
                                        if (selectedMethod == PaymentMethod.BINANCE) "Enter Binance Pay ID or USDT Address" else "017XXXXXXXX / 018XXXXXXXX",
                                        color = DarkTextMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (selectedMethod == PaymentMethod.BINANCE) Icons.Default.QrCode else Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = DarkTextMuted
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkCard,
                                    unfocusedContainerColor = DarkCard,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (selectedMethod == PaymentMethod.BINANCE) KeyboardType.Text else KeyboardType.Phone
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Amount in Coins
                            Text(
                                text = "3. Amount (Coins)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = coinAmountText,
                                onValueChange = { coinAmountText = it.filter { ch -> ch.isDigit() } },
                                placeholder = { Text("Min ${adminSettings.minWithdrawalCoins}", color = DarkTextMuted, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = CoinGold)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkCard,
                                    unfocusedContainerColor = DarkCard,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick preset chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf(500L, 1000L, 2500L, 5000L)
                                presets.forEach { p ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (coinAmount == p) CoinGold.copy(alpha = 0.2f) else DarkCard,
                                        border = if (coinAmount == p) BorderStroke(1.dp, CoinGold) else CardDefaults.outlinedCardBorder(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { coinAmountText = p.toString() }
                                    ) {
                                        Text(
                                            text = "$p",
                                            color = if (coinAmount == p) CoinGold else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkCard,
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { coinAmountText = userCoins.toString() }
                                ) {
                                    Text(
                                        text = "MAX",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Payout Conversion Summary Box
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkBg,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Estimated Payout Amount:",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = effectivePayout,
                                            color = CoinGold,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "0% Fee",
                                            color = Color(0xFF10B981),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Submit Button
                            Button(
                                onClick = {
                                    viewModel.requestWithdrawal(
                                        method = selectedMethod,
                                        accountAddress = accountAddress,
                                        coins = coinAmount,
                                        onResult = { success, msg ->
                                            if (success) {
                                                accountAddress = ""
                                                selectedWalletSubTab = "HISTORY"
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Submit Cashout Request",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else if (selectedWalletSubTab == "HISTORY") {
                // User's Withdrawals List
                if (userWithdrawals.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No withdrawal requests yet.",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(userWithdrawals) { req ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = req.method.displayName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = req.accountAddress,
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Status Pill
                                    val (chipBg, chipFg) = when (req.status) {
                                        WithdrawalStatus.PENDING -> StatusPending.copy(alpha = 0.2f) to StatusPending
                                        WithdrawalStatus.APPROVED -> StatusSuccess.copy(alpha = 0.2f) to StatusSuccess
                                        WithdrawalStatus.REJECTED -> StatusRejected.copy(alpha = 0.2f) to StatusRejected
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = chipBg
                                    ) {
                                        Text(
                                            text = req.status.name,
                                            color = chipFg,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${req.coinAmount} Coins",
                                        color = CoinGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${String.format("%.2f", req.payoutAmount)} ${req.currency}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                if (req.adminNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Admin Note: ${req.adminNote}",
                                        color = NeonCyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Transactions Log
                if (userTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions recorded yet.",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(userTransactions) { tx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Text(
                                    text = if (tx.isCredit) "+${tx.amount}" else "-${tx.amount}",
                                    color = if (tx.isCredit) StatusSuccess else StatusRejected,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodItem(
    method: PaymentMethod,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) color.copy(alpha = 0.25f) else DarkCard,
        border = if (isSelected) BorderStroke(1.5.dp, color) else CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = method.displayName,
                color = if (isSelected) color else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
