package com.example.data.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String = "",
    val coins: Long = 100, // Welcome bonus!
    val role: String = "USER", // "USER" or "ADMIN"
    val isBanned: Boolean = false,
    val referralCode: String = "",
    val referredBy: String = "",
    val totalReferredCount: Int = 0,
    val totalReferralBonusEarned: Long = 0,
    val joinedAt: Long = System.currentTimeMillis()
)

enum class VideoType {
    SHORT,
    LONG
}

data class Video(
    val id: String,
    val title: String,
    val description: String = "",
    val videoUrl: String,
    val type: VideoType = VideoType.LONG,
    val thumbnailUrl: String = "",
    val channelName: String,
    val channelAvatar: String = "",
    val views: Long = 1200,
    val likes: Long = 85,
    val isLiked: Boolean = false,
    val commentsCount: Int = 12,
    val durationLabel: String = "03:45",
    val uploaderId: String = "official",
    val category: String = "Trending",
    val rewardCoins: Int = 25,
    val createdAt: Long = System.currentTimeMillis()
)

data class Comment(
    val id: String,
    val videoId: String,
    val userName: String,
    val userAvatar: String = "",
    val content: String,
    val timestamp: String = "Just now",
    val likes: Int = 0
)

enum class WithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class PaymentMethod(val displayName: String) {
    BKASH("bKash"),
    NAGAD("Nagad"),
    ROCKET("Rocket"),
    BINANCE("Binance (USDT)")
}

data class WithdrawalRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val method: PaymentMethod,
    val accountAddress: String,
    val coinAmount: Long,
    val payoutAmount: Double,
    val currency: String = "BDT",
    val status: WithdrawalStatus = WithdrawalStatus.PENDING,
    val adminNote: String = "",
    val requestDate: Long = System.currentTimeMillis()
)

data class AdminSettings(
    val coinsPerShortWatch: Int = 15,
    val requiredShortWatchSec: Int = 10,
    val coinsPerLongWatch: Int = 30,
    val requiredLongWatchSec: Int = 20,
    val coinsPerWebsiteVisit: Int = 20,
    val websiteVisitSec: Int = 15,
    val coinsPerUpload: Int = 50,
    val coinToBdtRate: Double = 0.01, // 100 coins = 1 BDT
    val minWithdrawalCoins: Long = 500, // 5 BDT
    val referralBonusReferrer: Long = 50, // Reward for referrer
    val referralBonusNewUser: Long = 50, // Extra welcome coins for new user
    // 1. Banner Ad
    val isBannerAdEnabled: Boolean = true,
    val adSterraBannerScript: String = "//adsterra-banner-code-zone-918231.js",
    // 2. Direct Link / Smartlink
    val isDirectLinkEnabled: Boolean = true,
    val adSterraDirectLink: String = "https://example.com/adsterra-direct-earn",
    // 3. Social Bar Ad
    val isSocialBarEnabled: Boolean = true,
    val adSterraSocialBarScript: String = "//adsterra-socialbar-zone-839201.js",
    val adSterraSocialBarUrl: String = "https://example.com/adsterra-socialbar-promo",
    // 4. Popunder Ad
    val isPopunderEnabled: Boolean = false,
    val adSterraPopunderScript: String = "//adsterra-popunder-zone-492012.js",
    val adSterraPopunderUrl: String = "https://example.com/adsterra-popunder-landing",
    // Master Switch
    val isAdsEnabled: Boolean = true
)

data class EarningTask(
    val id: String,
    val title: String,
    val description: String,
    val rewardCoins: Int,
    val timerSeconds: Int,
    val targetUrl: String,
    val category: String = "Website Visit"
)

data class CoinTransaction(
    val id: String,
    val title: String,
    val amount: Long,
    val isCredit: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class WatchedRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val videoId: String,
    val userId: String,
    val videoTitle: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val earnedCoins: Int = 0
)
