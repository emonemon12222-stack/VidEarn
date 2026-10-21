package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.User
import com.example.data.model.Video
import com.example.data.model.VideoType
import com.example.data.model.WatchedRecord

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String = "",
    val coins: Long = 100,
    val role: String = "USER",
    val isBanned: Boolean = false,
    val referralCode: String = "",
    val referredBy: String = "",
    val totalReferredCount: Int = 0,
    val totalReferralBonusEarned: Long = 0L,
    val password: String = "",
    val joinedAt: Long = System.currentTimeMillis()
) {
    fun toUser(): User = User(
        id = id,
        name = name,
        email = email,
        avatarUrl = avatarUrl,
        coins = coins,
        role = role,
        isBanned = isBanned,
        referralCode = referralCode,
        referredBy = referredBy,
        totalReferredCount = totalReferredCount,
        totalReferralBonusEarned = totalReferralBonusEarned,
        joinedAt = joinedAt
    )

    companion object {
        fun fromUser(user: User, password: String = ""): UserEntity = UserEntity(
            id = user.id,
            name = user.name,
            email = user.email,
            avatarUrl = user.avatarUrl,
            coins = user.coins,
            role = user.role,
            isBanned = user.isBanned,
            referralCode = user.referralCode,
            referredBy = user.referredBy,
            totalReferredCount = user.totalReferredCount,
            totalReferralBonusEarned = user.totalReferralBonusEarned,
            password = password,
            joinedAt = user.joinedAt
        )
    }
}

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val type: String, // "SHORT" or "LONG"
    val thumbnailUrl: String,
    val channelName: String,
    val channelAvatar: String,
    val views: Long,
    val likes: Long,
    val isLiked: Boolean,
    val commentsCount: Int,
    val durationLabel: String,
    val uploaderId: String,
    val category: String,
    val rewardCoins: Int,
    val createdAt: Long
) {
    fun toVideo(): Video = Video(
        id = id,
        title = title,
        description = description,
        videoUrl = videoUrl,
        type = try { VideoType.valueOf(type) } catch (e: Exception) { VideoType.LONG },
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        channelAvatar = channelAvatar,
        views = views,
        likes = likes,
        isLiked = isLiked,
        commentsCount = commentsCount,
        durationLabel = durationLabel,
        uploaderId = uploaderId,
        category = category,
        rewardCoins = rewardCoins,
        createdAt = createdAt
    )

    companion object {
        fun fromVideo(video: Video): VideoEntity = VideoEntity(
            id = video.id,
            title = video.title,
            description = video.description,
            videoUrl = video.videoUrl,
            type = video.type.name,
            thumbnailUrl = video.thumbnailUrl,
            channelName = video.channelName,
            channelAvatar = video.channelAvatar,
            views = video.views,
            likes = video.likes,
            isLiked = video.isLiked,
            commentsCount = video.commentsCount,
            durationLabel = video.durationLabel,
            uploaderId = video.uploaderId,
            category = video.category,
            rewardCoins = video.rewardCoins,
            createdAt = video.createdAt
        )
    }
}

@Entity(tableName = "watched_records")
data class WatchedRecordEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val userId: String,
    val videoTitle: String,
    val timestamp: Long,
    val earnedCoins: Int
) {
    fun toRecord(): WatchedRecord = WatchedRecord(
        id = id,
        videoId = videoId,
        userId = userId,
        videoTitle = videoTitle,
        timestamp = timestamp,
        earnedCoins = earnedCoins
    )

    companion object {
        fun fromRecord(record: WatchedRecord): WatchedRecordEntity = WatchedRecordEntity(
            id = record.id,
            videoId = record.videoId,
            userId = record.userId,
            videoTitle = record.videoTitle,
            timestamp = record.timestamp,
            earnedCoins = record.earnedCoins
        )
    }
}
