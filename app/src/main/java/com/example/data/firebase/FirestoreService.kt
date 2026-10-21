package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions

/**
 * Manages Firebase Firestore cloud synchronization with offline persistence.
 * All operations are linked to the user account ID (userId) and gracefully handle
 * offline mode via Firestore's PersistentCache.
 */
class FirestoreService(private val context: Context) {

    companion object {
        private const val TAG = "FirestoreService"
        private const val USERS_COLLECTION = "users"
        private const val VIDEOS_COLLECTION = "videos"
        private const val WITHDRAWALS_COLLECTION = "withdrawals"
        private const val TRANSACTIONS_COLLECTION = "transactions"
        private const val WATCHED_COLLECTION = "watched_history"
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val db = FirebaseFirestore.getInstance()
            try {
                // Configure persistent disk cache with unlimited capacity for resilient offline sync
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                db.firestoreSettings = settings
                Log.d(TAG, "Firebase Firestore initialized with PersistentCacheSettings (unlimited)")
            } catch (settingsError: Exception) {
                // If firestoreSettings was already set during a previous launch in the same process,
                // catch gracefully without discarding the active db instance
                Log.d(TAG, "Firestore settings could not be altered (already initialized): ${settingsError.message}")
            }
            db
        } catch (e: Throwable) {
            Log.e(TAG, "Firestore initialization warning: ${e.message}", e)
            try {
                FirebaseFirestore.getInstance()
            } catch (fallbackError: Throwable) {
                null
            }
        }
    }

    // ---------------- USER DATA SYNC ----------------

    /**
     * Persists or updates user profile in Firestore under /users/{userId}
     */
    fun syncUser(user: User, onComplete: ((Boolean) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false)
            return
        }

        val data = hashMapOf(
            "id" to user.id,
            "name" to user.name,
            "email" to user.email,
            "avatarUrl" to user.avatarUrl,
            "coins" to user.coins,
            "role" to user.role,
            "isBanned" to user.isBanned,
            "referralCode" to user.referralCode,
            "referredBy" to user.referredBy,
            "totalReferredCount" to user.totalReferredCount,
            "totalReferralBonusEarned" to user.totalReferralBonusEarned,
            "joinedAt" to user.joinedAt,
            "lastActiveAt" to System.currentTimeMillis()
        )

        db.collection(USERS_COLLECTION).document(user.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "User ${user.id} synced to Firestore cloud")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore syncUser failed (cached locally if offline): ${e.message}")
                onComplete?.invoke(false)
            }
    }

    /**
     * Fetches cloud user data for the given userId
     */
    fun fetchUser(userId: String, onUserLoaded: (User?) -> Unit) {
        val db = firestore ?: run {
            onUserLoaded(null)
            return
        }

        db.collection(USERS_COLLECTION).document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val user = User(
                        id = doc.getString("id") ?: userId,
                        name = doc.getString("name") ?: "User",
                        email = doc.getString("email") ?: "",
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        coins = doc.getLong("coins") ?: 100L,
                        role = doc.getString("role") ?: "USER",
                        isBanned = doc.getBoolean("isBanned") ?: false,
                        referralCode = doc.getString("referralCode") ?: "",
                        referredBy = doc.getString("referredBy") ?: "",
                        totalReferredCount = (doc.getLong("totalReferredCount") ?: 0L).toInt(),
                        totalReferralBonusEarned = doc.getLong("totalReferralBonusEarned") ?: 0L,
                        joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis()
                    )
                    onUserLoaded(user)
                } else {
                    onUserLoaded(null)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore fetchUser failed: ${e.message}")
                onUserLoaded(null)
            }
    }

    /**
     * Query a user by email to restore account even after clean install or update
     */
    fun fetchUserByEmail(email: String, onUserLoaded: (User?) -> Unit) {
        val db = firestore ?: run {
            onUserLoaded(null)
            return
        }
        val trimmed = email.trim().lowercase()
        if (trimmed.isBlank()) {
            onUserLoaded(null)
            return
        }

        db.collection(USERS_COLLECTION)
            .whereEqualTo("email", trimmed)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null && doc.exists()) {
                    val user = User(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "User",
                        email = doc.getString("email") ?: trimmed,
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        coins = doc.getLong("coins") ?: 100L,
                        role = doc.getString("role") ?: "USER",
                        isBanned = doc.getBoolean("isBanned") ?: false,
                        referralCode = doc.getString("referralCode") ?: "",
                        referredBy = doc.getString("referredBy") ?: "",
                        totalReferredCount = (doc.getLong("totalReferredCount") ?: 0L).toInt(),
                        totalReferralBonusEarned = doc.getLong("totalReferralBonusEarned") ?: 0L,
                        joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis()
                    )
                    onUserLoaded(user)
                } else {
                    onUserLoaded(null)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore fetchUserByEmail failed: ${e.message}")
                onUserLoaded(null)
            }
    }

    /**
     * Listens for real-time changes to the current user's profile in Firestore
     */
    fun observeUser(userId: String, onUserChanged: (User) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null

        return db.collection(USERS_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeUser listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = User(
                        id = snapshot.getString("id") ?: userId,
                        name = snapshot.getString("name") ?: "User",
                        email = snapshot.getString("email") ?: "",
                        avatarUrl = snapshot.getString("avatarUrl") ?: "",
                        coins = snapshot.getLong("coins") ?: 100L,
                        role = snapshot.getString("role") ?: "USER",
                        isBanned = snapshot.getBoolean("isBanned") ?: false,
                        referralCode = snapshot.getString("referralCode") ?: "",
                        referredBy = snapshot.getString("referredBy") ?: "",
                        totalReferredCount = (snapshot.getLong("totalReferredCount") ?: 0L).toInt(),
                        totalReferralBonusEarned = snapshot.getLong("totalReferralBonusEarned") ?: 0L,
                        joinedAt = snapshot.getLong("joinedAt") ?: System.currentTimeMillis()
                    )
                    onUserChanged(user)
                }
            }
    }

    /**
     * Query a user by referral code in Firestore
     */
    fun findUserByReferralCode(referralCode: String, onResult: (User?) -> Unit) {
        val db = firestore ?: run {
            onResult(null)
            return
        }
        val trimmed = referralCode.trim().uppercase()
        if (trimmed.isBlank()) {
            onResult(null)
            return
        }

        db.collection(USERS_COLLECTION)
            .whereEqualTo("referralCode", trimmed)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                val doc = snapshots.documents.firstOrNull()
                if (doc != null && doc.exists()) {
                    val user = User(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "User",
                        email = doc.getString("email") ?: "",
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        coins = doc.getLong("coins") ?: 100L,
                        role = doc.getString("role") ?: "USER",
                        isBanned = doc.getBoolean("isBanned") ?: false,
                        referralCode = doc.getString("referralCode") ?: trimmed,
                        referredBy = doc.getString("referredBy") ?: "",
                        totalReferredCount = (doc.getLong("totalReferredCount") ?: 0L).toInt(),
                        totalReferralBonusEarned = doc.getLong("totalReferralBonusEarned") ?: 0L,
                        joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis()
                    )
                    onResult(user)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // ---------------- VIDEOS SYNC ----------------

    /**
     * Persists uploaded video directly to Firestore linked to the uploaderId
     */
    fun syncVideo(video: Video, onComplete: ((Boolean) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false)
            return
        }

        val data = hashMapOf(
            "id" to video.id,
            "title" to video.title,
            "description" to video.description,
            "videoUrl" to video.videoUrl,
            "type" to video.type.name,
            "thumbnailUrl" to video.thumbnailUrl,
            "channelName" to video.channelName,
            "channelAvatar" to video.channelAvatar,
            "views" to video.views,
            "likes" to video.likes,
            "isLiked" to video.isLiked,
            "commentsCount" to video.commentsCount,
            "durationLabel" to video.durationLabel,
            "uploaderId" to video.uploaderId,
            "category" to video.category,
            "rewardCoins" to video.rewardCoins,
            "createdAt" to video.createdAt
        )

        db.collection(VIDEOS_COLLECTION).document(video.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Video ${video.id} synced to Firestore (uploader: ${video.uploaderId})")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore syncVideo failed: ${e.message}")
                onComplete?.invoke(false)
            }
    }

    /**
     * Fetches all cloud videos from Firestore
     */
    fun fetchVideos(onVideosLoaded: (List<Video>) -> Unit) {
        val db = firestore ?: run {
            onVideosLoaded(emptyList())
            return
        }

        db.collection(VIDEOS_COLLECTION)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val list = mutableListOf<Video>()
                for (doc in querySnapshot.documents) {
                    try {
                        val videoTypeStr = doc.getString("type") ?: "LONG"
                        val videoType = try {
                            VideoType.valueOf(videoTypeStr)
                        } catch (e: Exception) {
                            VideoType.LONG
                        }
                        list.add(
                            Video(
                                id = doc.getString("id") ?: doc.id,
                                title = doc.getString("title") ?: "Video",
                                description = doc.getString("description") ?: "",
                                videoUrl = doc.getString("videoUrl") ?: "",
                                type = videoType,
                                thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                                channelName = doc.getString("channelName") ?: "Creator",
                                channelAvatar = doc.getString("channelAvatar") ?: "",
                                views = doc.getLong("views") ?: 100L,
                                likes = doc.getLong("likes") ?: 10L,
                                isLiked = doc.getBoolean("isLiked") ?: false,
                                commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                                durationLabel = doc.getString("durationLabel") ?: "02:30",
                                uploaderId = doc.getString("uploaderId") ?: "official",
                                category = doc.getString("category") ?: "Trending",
                                rewardCoins = (doc.getLong("rewardCoins") ?: 25L).toInt(),
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing video doc: ${e.message}")
                    }
                }
                onVideosLoaded(list)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore fetchVideos failed: ${e.message}")
                onVideosLoaded(emptyList())
            }
    }

    /**
     * Fetches videos specifically uploaded by a user (guarantees restore on app update)
     */
    fun fetchVideosByUploader(uploaderId: String, onVideosLoaded: (List<Video>) -> Unit) {
        val db = firestore ?: run {
            onVideosLoaded(emptyList())
            return
        }
        if (uploaderId.isBlank()) {
            onVideosLoaded(emptyList())
            return
        }

        db.collection(VIDEOS_COLLECTION)
            .whereEqualTo("uploaderId", uploaderId)
            .get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents.mapNotNull { doc ->
                    try {
                        val videoTypeStr = doc.getString("type") ?: "LONG"
                        val videoType = try {
                            VideoType.valueOf(videoTypeStr)
                        } catch (e: Exception) {
                            VideoType.LONG
                        }
                        Video(
                            id = doc.getString("id") ?: doc.id,
                            title = doc.getString("title") ?: "Video",
                            description = doc.getString("description") ?: "",
                            videoUrl = doc.getString("videoUrl") ?: "",
                            type = videoType,
                            thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                            channelName = doc.getString("channelName") ?: "Creator",
                            channelAvatar = doc.getString("channelAvatar") ?: "",
                            views = doc.getLong("views") ?: 100L,
                            likes = doc.getLong("likes") ?: 10L,
                            isLiked = doc.getBoolean("isLiked") ?: false,
                            commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                            durationLabel = doc.getString("durationLabel") ?: "02:30",
                            uploaderId = doc.getString("uploaderId") ?: uploaderId,
                            category = doc.getString("category") ?: "Trending",
                            rewardCoins = (doc.getLong("rewardCoins") ?: 25L).toInt(),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onVideosLoaded(list)
            }
            .addOnFailureListener {
                onVideosLoaded(emptyList())
            }
    }

    /**
     * Deletes video from Firestore
     */
    fun deleteVideo(videoId: String) {
        val db = firestore ?: return
        db.collection(VIDEOS_COLLECTION).document(videoId)
            .delete()
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore deleteVideo failed: ${e.message}")
            }
    }

    // ---------------- WATCHED VIDEO HISTORY SYNC ----------------

    /**
     * Records watched video in cloud linked directly to user's account ID
     */
    fun recordWatchedVideo(record: WatchedRecord) {
        val db = firestore ?: return

        val data = hashMapOf(
            "id" to record.id,
            "videoId" to record.videoId,
            "userId" to record.userId,
            "videoTitle" to record.videoTitle,
            "timestamp" to record.timestamp,
            "earnedCoins" to record.earnedCoins
        )

        // Stored both in user's subcollection and top-level watched history
        db.collection(USERS_COLLECTION).document(record.userId)
            .collection(WATCHED_COLLECTION).document(record.videoId)
            .set(data, SetOptions.merge())

        db.collection(WATCHED_COLLECTION).document("${record.userId}_${record.videoId}")
            .set(data, SetOptions.merge())
    }

    /**
     * Fetches watched history records for a given user from Firestore
     */
    fun fetchWatchedHistory(userId: String, onLoaded: (List<WatchedRecord>) -> Unit) {
        val db = firestore ?: run {
            onLoaded(emptyList())
            return
        }

        db.collection(USERS_COLLECTION).document(userId)
            .collection(WATCHED_COLLECTION)
            .get()
            .addOnSuccessListener { query ->
                val list = query.documents.mapNotNull { doc ->
                    try {
                        WatchedRecord(
                            id = doc.getString("id") ?: doc.id,
                            videoId = doc.getString("videoId") ?: "",
                            userId = doc.getString("userId") ?: userId,
                            videoTitle = doc.getString("videoTitle") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            earnedCoins = (doc.getLong("earnedCoins") ?: 0L).toInt()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onLoaded(list)
            }
            .addOnFailureListener {
                onLoaded(emptyList())
            }
    }

    // ---------------- WITHDRAWALS SYNC ----------------

    /**
     * Persists withdrawal request linked to userId
     */
    fun syncWithdrawal(req: WithdrawalRequest) {
        val db = firestore ?: return

        val data = hashMapOf(
            "id" to req.id,
            "userId" to req.userId,
            "userName" to req.userName,
            "userEmail" to req.userEmail,
            "method" to req.method.name,
            "accountAddress" to req.accountAddress,
            "coinAmount" to req.coinAmount,
            "payoutAmount" to req.payoutAmount,
            "currency" to req.currency,
            "status" to req.status.name,
            "adminNote" to req.adminNote,
            "requestDate" to req.requestDate
        )

        db.collection(WITHDRAWALS_COLLECTION).document(req.id)
            .set(data, SetOptions.merge())

        db.collection(USERS_COLLECTION).document(req.userId)
            .collection(WITHDRAWALS_COLLECTION).document(req.id)
            .set(data, SetOptions.merge())
    }

    /**
     * Restores withdrawal history for a user from Firestore
     */
    fun fetchWithdrawals(userId: String, onLoaded: (List<WithdrawalRequest>) -> Unit) {
        val db = firestore ?: run {
            onLoaded(emptyList())
            return
        }

        db.collection(USERS_COLLECTION).document(userId)
            .collection(WITHDRAWALS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents.mapNotNull { doc ->
                    try {
                        val methodStr = doc.getString("method") ?: "BKASH"
                        val statusStr = doc.getString("status") ?: "PENDING"
                        WithdrawalRequest(
                            id = doc.getString("id") ?: doc.id,
                            userId = doc.getString("userId") ?: userId,
                            userName = doc.getString("userName") ?: "User",
                            userEmail = doc.getString("userEmail") ?: "",
                            method = try { PaymentMethod.valueOf(methodStr) } catch (e: Exception) { PaymentMethod.BKASH },
                            accountAddress = doc.getString("accountAddress") ?: "",
                            coinAmount = doc.getLong("coinAmount") ?: 500L,
                            payoutAmount = doc.getDouble("payoutAmount") ?: 5.0,
                            currency = doc.getString("currency") ?: "BDT",
                            status = try { WithdrawalStatus.valueOf(statusStr) } catch (e: Exception) { WithdrawalStatus.PENDING },
                            adminNote = doc.getString("adminNote") ?: "",
                            requestDate = doc.getLong("requestDate") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onLoaded(list)
            }
            .addOnFailureListener {
                onLoaded(emptyList())
            }
    }

    // ---------------- TRANSACTIONS SYNC ----------------

    /**
     * Persists coin transaction linked to userId
     */
    fun syncTransaction(userId: String, tx: CoinTransaction) {
        val db = firestore ?: return

        val data = hashMapOf(
            "id" to tx.id,
            "userId" to userId,
            "title" to tx.title,
            "amount" to tx.amount,
            "isCredit" to tx.isCredit,
            "timestamp" to tx.timestamp
        )

        db.collection(USERS_COLLECTION).document(userId)
            .collection(TRANSACTIONS_COLLECTION).document(tx.id)
            .set(data, SetOptions.merge())
    }

    /**
     * Restores transactions for a user from Firestore
     */
    fun fetchTransactions(userId: String, onLoaded: (List<CoinTransaction>) -> Unit) {
        val db = firestore ?: run {
            onLoaded(emptyList())
            return
        }

        db.collection(USERS_COLLECTION).document(userId)
            .collection(TRANSACTIONS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents.mapNotNull { doc ->
                    try {
                        CoinTransaction(
                            id = doc.getString("id") ?: doc.id,
                            title = doc.getString("title") ?: "Coin Activity",
                            amount = doc.getLong("amount") ?: 0L,
                            isCredit = doc.getBoolean("isCredit") ?: true,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onLoaded(list)
            }
            .addOnFailureListener {
                onLoaded(emptyList())
            }
    }
}
