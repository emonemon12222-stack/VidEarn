package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.firebase.FirestoreService
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Single source of truth for VidEarn application state.
 * Implements resilient dual-layer data persistence:
 * 1. Cloud-backed Firebase Firestore with offline PersistentCacheSettings.
 * 2. On-device protected SharedPreferences storage ensuring session, coin balances,
 *    watched video histories, and uploaded videos survive app updates, process deaths,
 *    and background refreshes.
 */
class AppRepository(context: Context) {

    private val TAG = "AppRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("videarn_prefs_v3", Context.MODE_PRIVATE)

    // Legacy fallback prefs for smooth migration from v1/v2
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences("videarn_prefs", Context.MODE_PRIVATE)

    private val firestoreService = FirestoreService(context)

    // Current User State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Videos List
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    // Watched Video History (UserId -> List of WatchedRecord)
    private val _watchedHistory = MutableStateFlow<List<WatchedRecord>>(emptyList())
    val watchedHistory: StateFlow<List<WatchedRecord>> = _watchedHistory.asStateFlow()

    // Withdrawal Requests
    private val _withdrawals = MutableStateFlow<List<WithdrawalRequest>>(emptyList())
    val withdrawals: StateFlow<List<WithdrawalRequest>> = _withdrawals.asStateFlow()

    // Admin Settings
    private val _adminSettings = MutableStateFlow(AdminSettings())
    val adminSettings: StateFlow<AdminSettings> = _adminSettings.asStateFlow()

    // Tasks
    private val _tasks = MutableStateFlow<List<EarningTask>>(emptyList())
    val tasks: StateFlow<List<EarningTask>> = _tasks.asStateFlow()

    // Comments map: VideoId -> List<Comment>
    private val _comments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    val comments: StateFlow<Map<String, List<Comment>>> = _comments.asStateFlow()

    // Transactions map: UserId -> List<CoinTransaction>
    private val _transactions = MutableStateFlow<Map<String, List<CoinTransaction>>>(emptyMap())
    val transactions: StateFlow<Map<String, List<CoinTransaction>>> = _transactions.asStateFlow()

    // All registered users (for admin panel)
    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    init {
        migrateLegacyDataIfNecessary()
        loadSettings()
        loadUsers()
        loadVideos()
        loadWithdrawals()
        loadTasks()
        loadInitialComments()
        loadTransactions()
        restoreSession()
    }

    // ---------------- APP MIGRATION & SMOOTH UPDATES ----------------

    /**
     * Seamlessly migrates existing user data, session, coins, and uploads from
     * older versions (videarn_prefs) into the current persistent store.
     */
    private fun migrateLegacyDataIfNecessary() {
        try {
            if (!prefs.contains("migration_completed")) {
                if (legacyPrefs.contains("logged_in_user_id")) {
                    val legacyUserId = legacyPrefs.getString("logged_in_user_id", null)
                    if (legacyUserId != null) {
                        prefs.edit().putString("logged_in_user_id", legacyUserId).apply()
                    }
                }
                if (legacyPrefs.contains("users_db")) {
                    val legacyUsers = legacyPrefs.getString("users_db", null)
                    if (legacyUsers != null) {
                        prefs.edit().putString("users_db", legacyUsers).apply()
                    }
                }
                if (legacyPrefs.contains("videos_db")) {
                    val legacyVideos = legacyPrefs.getString("videos_db", null)
                    if (legacyVideos != null) {
                        prefs.edit().putString("videos_db", legacyVideos).apply()
                    }
                }
                if (legacyPrefs.contains("withdrawals_db")) {
                    val legacyWithdrawals = legacyPrefs.getString("withdrawals_db", null)
                    if (legacyWithdrawals != null) {
                        prefs.edit().putString("withdrawals_db", legacyWithdrawals).apply()
                    }
                }
                if (legacyPrefs.contains("transactions_db")) {
                    val legacyTx = legacyPrefs.getString("transactions_db", null)
                    if (legacyTx != null) {
                        prefs.edit().putString("transactions_db", legacyTx).apply()
                    }
                }
                prefs.edit().putBoolean("migration_completed", true).apply()
                Log.d(TAG, "Legacy data migration completed successfully.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Migration check warning: ${e.message}")
        }
    }

    // ---------------- AUTHENTICATION & USERS ----------------

    /**
     * Generates a unique, user-branded Referral Code based on user's name & UID
     */
    fun generateReferralCode(name: String, userId: String): String {
        val cleanName = name.trim().filter { it.isLetterOrDigit() }.uppercase()
        val prefix = if (cleanName.length >= 3) cleanName.take(5) else "VID"
        val suffix = if (userId.length >= 4) {
            userId.takeLast(4).uppercase().filter { it.isLetterOrDigit() }.padStart(4, '7')
        } else {
            (1000..9999).random().toString()
        }
        return "$prefix$suffix"
    }

    fun signUp(name: String, email: String, pass: String, inputReferralCode: String = ""): Result<User> {
        val trimmedEmail = email.trim().lowercase()
        val usersJson = prefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)

        if (usersObj.has(trimmedEmail)) {
            return Result.failure(Exception("Email already registered"))
        }

        val trimmedRefCode = inputReferralCode.trim().uppercase()
        var referrerUser: User? = null
        if (trimmedRefCode.isNotBlank()) {
            // Find referrer in loaded users or local database
            referrerUser = _allUsers.value.find { it.referralCode.equals(trimmedRefCode, ignoreCase = true) }
            if (referrerUser == null) {
                val keys = usersObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val uObj = usersObj.getJSONObject(k)
                    if (uObj.optString("referralCode").equals(trimmedRefCode, ignoreCase = true)) {
                        referrerUser = deserializeUser(uObj)
                        break
                    }
                }
            }
            if (referrerUser == null) {
                return Result.failure(Exception("Referral code '$trimmedRefCode' not found. Please check or leave empty."))
            }
        }

        val newUserId = UUID.randomUUID().toString()
        val generatedCode = generateReferralCode(name, newUserId)
        val refBonusNewUser = if (referrerUser != null) _adminSettings.value.referralBonusNewUser else 0L
        val initialCoins = 100L + refBonusNewUser // 100 Base Welcome Bonus + Referral Bonus if referred

        val newUser = User(
            id = newUserId,
            name = name.trim(),
            email = trimmedEmail,
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
            coins = initialCoins,
            referralCode = generatedCode,
            referredBy = if (referrerUser != null) trimmedRefCode else "",
            totalReferredCount = 0,
            totalReferralBonusEarned = 0L,
            joinedAt = System.currentTimeMillis()
        )

        val userRecord = serializeUser(newUser).apply {
            put("password", pass)
        }

        usersObj.put(trimmedEmail, userRecord)
        prefs.edit().putString("users_db", usersObj.toString()).apply()

        // Base Welcome transaction
        addTransaction(newUser.id, "Welcome Bonus", 100, true)

        // Referral reward execution for both parties
        if (referrerUser != null) {
            val refBonusReferrer = _adminSettings.value.referralBonusReferrer
            addTransaction(newUser.id, "Referral Bonus (Code: $trimmedRefCode)", refBonusNewUser, true)

            // Reward Referrer directly & update referral stats
            val updatedReferrer = referrerUser.copy(
                coins = referrerUser.coins + refBonusReferrer,
                totalReferredCount = referrerUser.totalReferredCount + 1,
                totalReferralBonusEarned = referrerUser.totalReferralBonusEarned + refBonusReferrer
            )
            updateUserInDb(updatedReferrer)
            addTransaction(referrerUser.id, "Referral Reward (${newUser.name} joined)", refBonusReferrer, true)
        }

        saveSession(newUser)
        loadUsers()
        return Result.success(newUser)
    }

    fun signIn(email: String, pass: String): Result<User> {
        val trimmedEmail = email.trim().lowercase()
        val usersJson = prefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)

        if (!usersObj.has(trimmedEmail)) {
            return Result.failure(Exception("Account not found. Please sign up."))
        }

        val userObj = usersObj.getJSONObject(trimmedEmail)
        val savedPass = userObj.optString("password", "")
        if (savedPass != pass) {
            return Result.failure(Exception("Invalid password. Please check again."))
        }

        if (userObj.optBoolean("isBanned", false)) {
            return Result.failure(Exception("This account has been suspended by admin."))
        }

        val user = deserializeUser(userObj)
        saveSession(user)
        return Result.success(user)
    }

    fun signInGuest(): User {
        val guest = User(
            id = "guest_user",
            name = "Demo User",
            email = "demo@videarn.com",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            coins = 250,
            referralCode = "DEMO99",
            referredBy = "",
            totalReferredCount = 5,
            totalReferralBonusEarned = 250L
        )
        saveSession(guest)
        return guest
    }

    fun signOut() {
        prefs.edit()
            .remove("logged_in_user_id")
            .remove("active_user_session_json")
            .apply()
        _currentUser.value = null
        _watchedHistory.value = emptyList()
    }

    private fun saveSession(user: User) {
        _currentUser.value = user
        prefs.edit()
            .putString("logged_in_user_id", user.id)
            .putString("active_user_session_json", serializeUser(user).toString())
            .apply()

        updateUserInDb(user)

        // Sync directly to Firestore
        firestoreService.syncUser(user)
        loadWatchedHistoryForUser(user.id)
    }

    private fun restoreSession() {
        val userId = prefs.getString("logged_in_user_id", null) ?: return

        // 1. Direct restore from serialized active session JSON
        val savedSession = prefs.getString("active_user_session_json", null)
        if (savedSession != null) {
            try {
                val parsed = deserializeUser(JSONObject(savedSession))
                if (!parsed.isBanned) {
                    _currentUser.value = parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error restoring saved session JSON: ${e.message}")
            }
        }

        // 2. Fallback to allUsers search
        if (_currentUser.value == null) {
            val user = _allUsers.value.find { it.id == userId }
            if (user != null && !user.isBanned) {
                _currentUser.value = user
            } else if (userId == "guest_user") {
                _currentUser.value = User(
                    id = "guest_user",
                    name = "Demo User",
                    email = "demo@videarn.com",
                    avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                    coins = 250,
                    referralCode = "DEMO99"
                )
            }
        }

        // 3. Sync and reconcile with Firestore cloud state
        val activeUser = _currentUser.value
        if (activeUser != null) {
            loadWatchedHistoryForUser(activeUser.id)
            firestoreService.fetchUser(activeUser.id) { cloudUser ->
                if (cloudUser != null && !cloudUser.isBanned) {
                    // Retain the higher coin balance to guarantee zero coin loss
                    val mergedCoins = maxOf(activeUser.coins, cloudUser.coins)
                    val merged = cloudUser.copy(coins = mergedCoins)
                    _currentUser.value = merged
                    updateUserInDb(merged)
                }
            }

            // Real-time listener
            firestoreService.observeUser(activeUser.id) { updatedFromCloud ->
                if (!updatedFromCloud.isBanned) {
                    val current = _currentUser.value
                    val bestCoins = if (current != null) maxOf(current.coins, updatedFromCloud.coins) else updatedFromCloud.coins
                    val finalUser = updatedFromCloud.copy(coins = bestCoins)
                    _currentUser.value = finalUser
                    updateUserInDb(finalUser)
                }
            }
        }
    }

    private fun loadUsers() {
        val usersJson = prefs.getString("users_db", null)
        val list = mutableListOf<User>()
        if (usersJson != null) {
            try {
                val obj = JSONObject(usersJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val u = obj.getJSONObject(key)
                    list.add(deserializeUser(u))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading users_db: ${e.message}")
            }
        }
        if (list.isEmpty()) {
            // Seed sample users with referral codes
            val sample = User(
                id = "usr_sample1",
                name = "Rahim Ahmed",
                email = "rahim@gmail.com",
                avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                coins = 480,
                referralCode = "RAHIM12",
                referredBy = "",
                totalReferredCount = 3,
                totalReferralBonusEarned = 150L
            )
            list.add(sample)
        }
        _allUsers.value = list
    }

    private fun updateUserInDb(user: User) {
        val usersJson = prefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)
        val keys = usersObj.keys()
        var found = false
        while (keys.hasNext()) {
            val key = keys.next()
            val u = usersObj.getJSONObject(key)
            if (u.optString("id") == user.id || key == user.email) {
                u.put("coins", user.coins)
                u.put("isBanned", user.isBanned)
                u.put("name", user.name)
                u.put("referralCode", user.referralCode)
                u.put("referredBy", user.referredBy)
                u.put("totalReferredCount", user.totalReferredCount)
                u.put("totalReferralBonusEarned", user.totalReferralBonusEarned)
                usersObj.put(key, u)
                found = true
                break
            }
        }
        if (!found && user.email.isNotBlank()) {
            usersObj.put(user.email, serializeUser(user))
        }
        prefs.edit()
            .putString("users_db", usersObj.toString())
            .putString("active_user_session_json", serializeUser(user).toString())
            .apply()

        // Also sync to Firestore
        firestoreService.syncUser(user)
        loadUsers()
    }

    fun toggleUserBan(userId: String) {
        val users = _allUsers.value.map {
            if (it.id == userId) it.copy(isBanned = !it.isBanned) else it
        }
        _allUsers.value = users
        val found = users.find { it.id == userId }
        if (found != null) {
            updateUserInDb(found)
        }
    }

    private fun serializeUser(user: User): JSONObject {
        return JSONObject().apply {
            put("id", user.id)
            put("name", user.name)
            put("email", user.email)
            put("avatarUrl", user.avatarUrl)
            put("coins", user.coins)
            put("role", user.role)
            put("isBanned", user.isBanned)
            put("referralCode", user.referralCode)
            put("referredBy", user.referredBy)
            put("totalReferredCount", user.totalReferredCount)
            put("totalReferralBonusEarned", user.totalReferralBonusEarned)
            put("joinedAt", user.joinedAt)
        }
    }

    private fun deserializeUser(obj: JSONObject): User {
        return User(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "User"),
            email = obj.optString("email", ""),
            avatarUrl = obj.optString("avatarUrl", ""),
            coins = obj.optLong("coins", 100),
            role = obj.optString("role", "USER"),
            isBanned = obj.optBoolean("isBanned", false),
            referralCode = obj.optString("referralCode", "REF7721"),
            referredBy = obj.optString("referredBy", ""),
            totalReferredCount = obj.optInt("totalReferredCount", 0),
            totalReferralBonusEarned = obj.optLong("totalReferralBonusEarned", 0L),
            joinedAt = obj.optLong("joinedAt", System.currentTimeMillis())
        )
    }

    // ---------------- COIN REWARDS & BALANCE ----------------

    fun addCoins(userId: String, amount: Long, reason: String) {
        val current = _currentUser.value
        if (current != null && current.id == userId) {
            val updated = current.copy(coins = current.coins + amount)
            _currentUser.value = updated
            updateUserInDb(updated)
            addTransaction(userId, reason, amount, true)
        } else {
            val user = _allUsers.value.find { it.id == userId }
            if (user != null) {
                val updated = user.copy(coins = user.coins + amount)
                updateUserInDb(updated)
                addTransaction(userId, reason, amount, true)
            }
        }
    }

    fun deductCoins(userId: String, amount: Long, reason: String): Boolean {
        val current = _currentUser.value
        if (current != null && current.id == userId) {
            if (current.coins < amount) return false
            val updated = current.copy(coins = current.coins - amount)
            _currentUser.value = updated
            updateUserInDb(updated)
            addTransaction(userId, reason, amount, false)
            return true
        }
        return false
    }

    /**
     * Awards direct bonus coins when user opens and explores AdSterra Direct Link sponsor offer
     */
    fun claimDirectLinkBonus(userId: String, bonusCoins: Long = 30): Boolean {
        addCoins(userId, bonusCoins, "AdSterra Partner Direct Link Reward")
        return true
    }

    private fun addTransaction(userId: String, title: String, amount: Long, isCredit: Boolean) {
        val tx = CoinTransaction(UUID.randomUUID().toString(), title, amount, isCredit)
        val map = _transactions.value.toMutableMap()
        val list = map[userId]?.toMutableList() ?: mutableListOf()
        list.add(0, tx)
        map[userId] = list
        _transactions.value = map
        saveTransactions(map)

        // Sync transaction to Firestore linked to user ID
        firestoreService.syncTransaction(userId, tx)
    }

    private fun saveTransactions(map: Map<String, List<CoinTransaction>>) {
        val obj = JSONObject()
        for ((uid, txs) in map) {
            val arr = JSONArray()
            for (tx in txs.take(50)) {
                val item = JSONObject().apply {
                    put("id", tx.id)
                    put("title", tx.title)
                    put("amount", tx.amount)
                    put("isCredit", tx.isCredit)
                    put("timestamp", tx.timestamp)
                }
                arr.put(item)
            }
            obj.put(uid, arr)
        }
        prefs.edit().putString("transactions_db", obj.toString()).apply()
    }

    private fun loadTransactions() {
        val str = prefs.getString("transactions_db", null) ?: return
        try {
            val obj = JSONObject(str)
            val map = mutableMapOf<String, List<CoinTransaction>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.getJSONArray(key)
                val list = mutableListOf<CoinTransaction>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        CoinTransaction(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            amount = item.getLong("amount"),
                            isCredit = item.getBoolean("isCredit"),
                            timestamp = item.getLong("timestamp")
                        )
                    )
                }
                map[key] = list
            }
            _transactions.value = map
        } catch (e: Exception) {
            Log.w(TAG, "Error loading transactions: ${e.message}")
        }
    }

    // ---------------- WATCHED VIDEO HISTORY (STATE RETENTION) ----------------

    /**
     * Records that the user watched a video and earned coins.
     * Saved to local SharedPreferences and synced directly to Firestore /users/{userId}/watched_history
     */
    fun recordWatchedVideo(userId: String, videoId: String, videoTitle: String, earnedCoins: Int) {
        val record = WatchedRecord(
            id = UUID.randomUUID().toString(),
            videoId = videoId,
            userId = userId,
            videoTitle = videoTitle,
            timestamp = System.currentTimeMillis(),
            earnedCoins = earnedCoins
        )

        val updated = listOf(record) + _watchedHistory.value.filterNot { it.videoId == videoId }
        _watchedHistory.value = updated
        saveWatchedHistoryForUser(userId, updated)

        // Sync to cloud Firestore
        firestoreService.recordWatchedVideo(record)
    }

    private fun saveWatchedHistoryForUser(userId: String, list: List<WatchedRecord>) {
        val arr = JSONArray()
        for (r in list.take(100)) {
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("videoId", r.videoId)
                    put("userId", r.userId)
                    put("videoTitle", r.videoTitle)
                    put("timestamp", r.timestamp)
                    put("earnedCoins", r.earnedCoins)
                }
            )
        }
        prefs.edit().putString("watched_history_$userId", arr.toString()).apply()
    }

    private fun loadWatchedHistoryForUser(userId: String) {
        val saved = prefs.getString("watched_history_$userId", null)
        val list = mutableListOf<WatchedRecord>()
        if (saved != null) {
            try {
                val arr = JSONArray(saved)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        WatchedRecord(
                            id = obj.getString("id"),
                            videoId = obj.getString("videoId"),
                            userId = obj.getString("userId"),
                            videoTitle = obj.optString("videoTitle", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            earnedCoins = obj.optInt("earnedCoins", 0)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing watched history: ${e.message}")
            }
        }
        _watchedHistory.value = list

        // Fetch cloud watched history to merge
        firestoreService.fetchWatchedHistory(userId) { cloudList ->
            if (cloudList.isNotEmpty()) {
                val combined = (cloudList + _watchedHistory.value)
                    .distinctBy { it.videoId }
                    .sortedByDescending { it.timestamp }
                _watchedHistory.value = combined
                saveWatchedHistoryForUser(userId, combined)
            }
        }
    }

    // ---------------- VIDEOS (PROTECTED USER UPLOADS & SEEDS) ----------------

    private fun loadVideos() {
        val defaultVideos = getPreSeededVideos()
        val userUploadedVideos = loadUserUploadedVideosFromPrefs()

        val savedAll = prefs.getString("videos_db", null)
        val allSavedList = mutableListOf<Video>()
        if (savedAll != null) {
            try {
                val arr = JSONArray(savedAll)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    allSavedList.add(deserializeVideo(obj))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing videos_db: ${e.message}")
            }
        }

        // Merge user uploads + saved + defaults without duplicates, prioritizing user uploads
        val combinedMap = linkedMapOf<String, Video>()
        // 1. User uploaded videos have the highest priority
        for (v in userUploadedVideos) {
            combinedMap[v.id] = v
        }
        // 2. Saved videos from database
        for (v in allSavedList) {
            if (!combinedMap.containsKey(v.id)) {
                combinedMap[v.id] = v
            }
        }
        // 3. Pre-seeded fallback videos
        for (v in defaultVideos) {
            if (!combinedMap.containsKey(v.id)) {
                combinedMap[v.id] = v
            }
        }

        val finalVideos = combinedMap.values.toList()
        _videos.value = finalVideos
        saveVideos(finalVideos)

        // Fetch any new videos from Firestore cloud
        firestoreService.fetchVideos { cloudVideos ->
            if (cloudVideos.isNotEmpty()) {
                val merged = (userUploadedVideos + cloudVideos + _videos.value)
                    .distinctBy { it.id }
                _videos.value = merged
                saveVideos(merged)
            }
        }
    }

    /**
     * User-uploaded videos are stored in a dedicated key that is NEVER touched or overwritten
     * by default seed updates or app migrations.
     */
    private fun loadUserUploadedVideosFromPrefs(): List<Video> {
        val saved = prefs.getString("user_uploaded_videos_db", null) ?: return emptyList()
        val list = mutableListOf<Video>()
        try {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(deserializeVideo(obj))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading user_uploaded_videos_db: ${e.message}")
        }
        return list
    }

    private fun saveUserUploadedVideosToPrefs(list: List<Video>) {
        val userUploads = list.filter { it.uploaderId != "official" }
        val arr = JSONArray()
        for (v in userUploads) {
            arr.put(serializeVideo(v))
        }
        prefs.edit().putString("user_uploaded_videos_db", arr.toString()).apply()
    }

    private fun saveVideos(list: List<Video>) {
        val arr = JSONArray()
        for (v in list) {
            arr.put(serializeVideo(v))
        }
        prefs.edit().putString("videos_db", arr.toString()).apply()
        saveUserUploadedVideosToPrefs(list)
    }

    fun addVideo(
        title: String,
        description: String,
        url: String,
        type: VideoType,
        thumbnailUrl: String,
        category: String,
        uploaderId: String,
        uploaderName: String
    ): Video {
        val ytThumb = com.example.util.YouTubeHelper.getThumbnailUrl(url)
        val effectiveThumb = when {
            thumbnailUrl.isNotBlank() -> thumbnailUrl.trim()
            ytThumb.isNotBlank() -> ytThumb
            else -> "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=800"
        }

        val newVideo = Video(
            id = "vid_" + UUID.randomUUID().toString().take(8),
            title = title.trim(),
            description = description.trim(),
            videoUrl = url.trim(),
            type = type,
            thumbnailUrl = effectiveThumb,
            channelName = uploaderName,
            channelAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100",
            views = 1,
            likes = 0,
            durationLabel = if (type == VideoType.SHORT) "00:30" else "05:00",
            uploaderId = uploaderId,
            category = category,
            rewardCoins = if (type == VideoType.SHORT) _adminSettings.value.coinsPerShortWatch else _adminSettings.value.coinsPerLongWatch
        )

        val updated = listOf(newVideo) + _videos.value
        _videos.value = updated
        saveVideos(updated)

        // Sync directly to Firestore
        firestoreService.syncVideo(newVideo)

        // Give upload reward
        addCoins(uploaderId, _adminSettings.value.coinsPerUpload.toLong(), "Reward for Uploading Video: '$title'")

        return newVideo
    }

    fun toggleLike(videoId: String) {
        val updated = _videos.value.map { v ->
            if (v.id == videoId) {
                val newLiked = !v.isLiked
                v.copy(
                    isLiked = newLiked,
                    likes = if (newLiked) v.likes + 1 else (v.likes - 1).coerceAtLeast(0)
                )
            } else v
        }
        _videos.value = updated
        saveVideos(updated)
    }

    fun incrementViews(videoId: String) {
        val updated = _videos.value.map { v ->
            if (v.id == videoId) v.copy(views = v.views + 1) else v
        }
        _videos.value = updated
        saveVideos(updated)
    }

    fun deleteVideo(videoId: String) {
        val updated = _videos.value.filterNot { it.id == videoId }
        _videos.value = updated
        saveVideos(updated)
        firestoreService.deleteVideo(videoId)
    }

    private fun serializeVideo(v: Video): JSONObject {
        return JSONObject().apply {
            put("id", v.id)
            put("title", v.title)
            put("description", v.description)
            put("videoUrl", v.videoUrl)
            put("type", v.type.name)
            put("thumbnailUrl", v.thumbnailUrl)
            put("channelName", v.channelName)
            put("channelAvatar", v.channelAvatar)
            put("views", v.views)
            put("likes", v.likes)
            put("isLiked", v.isLiked)
            put("commentsCount", v.commentsCount)
            put("durationLabel", v.durationLabel)
            put("uploaderId", v.uploaderId)
            put("category", v.category)
            put("rewardCoins", v.rewardCoins)
            put("createdAt", v.createdAt)
        }
    }

    private fun deserializeVideo(obj: JSONObject): Video {
        val videoType = try {
            VideoType.valueOf(obj.optString("type", "LONG"))
        } catch (e: Exception) {
            VideoType.LONG
        }
        return Video(
            id = obj.getString("id"),
            title = obj.getString("title"),
            description = obj.optString("description", ""),
            videoUrl = obj.getString("videoUrl"),
            type = videoType,
            thumbnailUrl = obj.optString("thumbnailUrl", "").ifBlank {
                com.example.util.YouTubeHelper.getThumbnailUrl(obj.getString("videoUrl"))
            },
            channelName = obj.optString("channelName", "Creator"),
            channelAvatar = obj.optString("channelAvatar", ""),
            views = obj.optLong("views", 100),
            likes = obj.optLong("likes", 10),
            isLiked = obj.optBoolean("isLiked", false),
            commentsCount = obj.optInt("commentsCount", 0),
            durationLabel = obj.optString("durationLabel", "02:15"),
            uploaderId = obj.optString("uploaderId", "official"),
            category = obj.optString("category", "Trending"),
            rewardCoins = obj.optInt("rewardCoins", 25),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun getPreSeededVideos(): List<Video> {
        return listOf(
            Video(
                id = "short_1",
                title = "Most Satisfying Kinetic Sand & Neon Art ✂️ #shorts",
                description = "Ultra satisfying neon art cutting. Watch 10s to earn instant coins!",
                videoUrl = "https://www.youtube.com/shorts/50_4e0K9_Y8",
                type = VideoType.SHORT,
                thumbnailUrl = "https://img.youtube.com/vi/50_4e0K9_Y8/hqdefault.jpg",
                channelName = "SatisfyingVibes",
                channelAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100",
                views = 42800,
                likes = 3120,
                commentsCount = 84,
                durationLabel = "00:15",
                rewardCoins = 15,
                category = "Shorts"
            ),
            Video(
                id = "short_2",
                title = "Insane 3D Drone Formation Light Show 🌟 #shorts",
                description = "Futuristic 1000 drone formation over the city night lights.",
                videoUrl = "https://www.youtube.com/shorts/k9GjF8eP210",
                type = VideoType.SHORT,
                thumbnailUrl = "https://img.youtube.com/vi/k9GjF8eP210/hqdefault.jpg",
                channelName = "TechPulse",
                channelAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100",
                views = 28400,
                likes = 1890,
                commentsCount = 210,
                durationLabel = "00:20",
                rewardCoins = 15,
                category = "Tech"
            ),
            Video(
                id = "short_3",
                title = "Amazing Basketball Trickshot Dunks 🏀 #shorts",
                description = "Watch till the end for the 360 between-the-legs windmill dunk!",
                videoUrl = "https://www.youtube.com/shorts/4V90Aky6_w0",
                type = VideoType.SHORT,
                thumbnailUrl = "https://img.youtube.com/vi/4V90Aky6_w0/hqdefault.jpg",
                channelName = "HoopMasters",
                channelAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100",
                views = 61200,
                likes = 5410,
                commentsCount = 430,
                durationLabel = "00:18",
                rewardCoins = 15,
                category = "Sports"
            ),
            Video(
                id = "short_4",
                title = "5 Second Life Hack That Saves Hundreds of Dollars 💡 #shorts",
                description = "Quick finance life hack for online shopping discount codes.",
                videoUrl = "https://www.youtube.com/shorts/L_LUpnjgPso",
                type = VideoType.SHORT,
                thumbnailUrl = "https://img.youtube.com/vi/L_LUpnjgPso/hqdefault.jpg",
                channelName = "MoneyMindset",
                channelAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
                views = 98100,
                likes = 7200,
                commentsCount = 890,
                durationLabel = "00:12",
                rewardCoins = 15,
                category = "Earning"
            ),
            Video(
                id = "long_1",
                title = "How to Make Online Income Watching Videos & Doing Tasks in 2026",
                description = "Step-by-step complete blueprint for beginners to withdraw earnings directly to bKash, Nagad, or Binance.",
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                type = VideoType.LONG,
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "ProEarn Tips",
                channelAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100",
                views = 124000,
                likes = 9400,
                commentsCount = 760,
                durationLabel = "14:20",
                rewardCoins = 30,
                category = "Earning"
            ),
            Video(
                id = "long_2",
                title = "The First Video Ever on YouTube - Me at the zoo (Historical)",
                description = "Historic YouTube milestone video recorded at the San Diego Zoo.",
                videoUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                type = VideoType.LONG,
                thumbnailUrl = "https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg",
                channelName = "jawed",
                channelAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100",
                views = 310000000,
                likes = 15000000,
                commentsCount = 11200000,
                durationLabel = "00:19",
                rewardCoins = 30,
                category = "Tutorial"
            ),
            Video(
                id = "long_3",
                title = "Top Tech Innovations & Futuristic Gadgets That Blew My Mind",
                description = "Futuristic gadgets, artificial intelligence, and smart robotics breakdown.",
                videoUrl = "https://www.youtube.com/watch?v=fJ9rUzIMcZQ",
                type = VideoType.LONG,
                thumbnailUrl = "https://img.youtube.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
                channelName = "FutureTech Lab",
                channelAvatar = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=100",
                views = 85000,
                likes = 4900,
                commentsCount = 520,
                durationLabel = "11:10",
                rewardCoins = 30,
                category = "Tech"
            ),
            Video(
                id = "long_4",
                title = "Cybersecurity Essentials: Prevent Phone Hacks & Secure Your bKash/Nagad PIN",
                description = "Crucial protection tips against SMS phishing, fake OTP scams, and social engineering.",
                videoUrl = "https://www.youtube.com/watch?v=kJQP7kiw5Fk",
                type = VideoType.LONG,
                thumbnailUrl = "https://img.youtube.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
                channelName = "CyberShield",
                channelAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100",
                views = 73000,
                likes = 6400,
                commentsCount = 290,
                durationLabel = "09:30",
                rewardCoins = 30,
                category = "Tech"
            )
        )
    }

    // ---------------- COMMENTS ----------------

    private fun loadInitialComments() {
        val map = mutableMapOf<String, List<Comment>>()
        map["long_1"] = listOf(
            Comment(
                id = "c1",
                videoId = "long_1",
                userName = "Tamim Iqbal",
                content = "Great earning app! Got my first 50 BDT payout instantly via bKash 🚀",
                timestamp = "2 hours ago",
                likes = 14
            ),
            Comment(
                id = "c2",
                videoId = "long_1",
                userName = "Sadia Islam",
                content = "Watching shorts daily gives really good bonus coins. Loved the UI!",
                timestamp = "5 hours ago",
                likes = 9
            )
        )
        map["short_1"] = listOf(
            Comment(
                id = "c3",
                videoId = "short_1",
                userName = "Mehedi Hasan",
                content = "So satisfying to watch! +15 coins claimed instantly 🔥",
                timestamp = "1 hour ago",
                likes = 22
            )
        )
        _comments.value = map
    }

    fun addComment(videoId: String, userName: String, content: String) {
        val newComment = Comment(
            id = UUID.randomUUID().toString(),
            videoId = videoId,
            userName = userName,
            userAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100",
            content = content.trim(),
            timestamp = "Just now",
            likes = 0
        )
        val map = _comments.value.toMutableMap()
        val list = map[videoId]?.toMutableList() ?: mutableListOf()
        list.add(0, newComment)
        _comments.value = map

        val videos = _videos.value.map { v ->
            if (v.id == videoId) v.copy(commentsCount = v.commentsCount + 1) else v
        }
        _videos.value = videos
        saveVideos(videos)
    }

    fun addComment(videoId: String, content: String, user: User) {
        val newComment = Comment(
            id = UUID.randomUUID().toString(),
            videoId = videoId,
            userName = user.name,
            userAvatar = user.avatarUrl,
            content = content.trim(),
            timestamp = "Just now",
            likes = 0
        )
        val map = _comments.value.toMutableMap()
        val list = map[videoId]?.toMutableList() ?: mutableListOf()
        list.add(0, newComment)
        map[videoId] = list
        _comments.value = map

        // Increment commentsCount on video
        val videos = _videos.value.map { v ->
            if (v.id == videoId) v.copy(commentsCount = v.commentsCount + 1) else v
        }
        _videos.value = videos
        saveVideos(videos)
    }

    // ---------------- WITHDRAWALS (WALLET) ----------------

    private fun loadWithdrawals() {
        val saved = prefs.getString("withdrawals_db", null)
        val list = mutableListOf<WithdrawalRequest>()
        if (saved != null) {
            try {
                val arr = JSONArray(saved)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        WithdrawalRequest(
                            id = obj.getString("id"),
                            userId = obj.getString("userId"),
                            userName = obj.getString("userName"),
                            userEmail = obj.getString("userEmail"),
                            method = PaymentMethod.valueOf(obj.getString("method")),
                            accountAddress = obj.getString("accountAddress"),
                            coinAmount = obj.getLong("coinAmount"),
                            payoutAmount = obj.getDouble("payoutAmount"),
                            currency = obj.optString("currency", "BDT"),
                            status = WithdrawalStatus.valueOf(obj.getString("status")),
                            adminNote = obj.optString("adminNote", ""),
                            requestDate = obj.getLong("requestDate")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing withdrawals: ${e.message}")
            }
        }
        if (list.isEmpty()) {
            // Seed a sample approved withdrawal for demonstration
            list.add(
                WithdrawalRequest(
                    id = "w_sample99",
                    userId = "usr_sample1",
                    userName = "Rahim Ahmed",
                    userEmail = "rahim@gmail.com",
                    method = PaymentMethod.BKASH,
                    accountAddress = "01712-345678",
                    coinAmount = 1000,
                    payoutAmount = 10.0,
                    currency = "BDT",
                    status = WithdrawalStatus.APPROVED,
                    adminNote = "TrxID: 9X71KB029 - Sent to bKash",
                    requestDate = System.currentTimeMillis() - 86400000L
                )
            )
        }
        _withdrawals.value = list
    }

    private fun saveWithdrawals(list: List<WithdrawalRequest>) {
        val arr = JSONArray()
        for (w in list) {
            val obj = JSONObject().apply {
                put("id", w.id)
                put("userId", w.userId)
                put("userName", w.userName)
                put("userEmail", w.userEmail)
                put("method", w.method.name)
                put("accountAddress", w.accountAddress)
                put("coinAmount", w.coinAmount)
                put("payoutAmount", w.payoutAmount)
                put("currency", w.currency)
                put("status", w.status.name)
                put("adminNote", w.adminNote)
                put("requestDate", w.requestDate)
            }
            arr.put(obj)
        }
        prefs.edit().putString("withdrawals_db", arr.toString()).apply()
    }

    fun submitWithdrawal(
        user: User,
        method: PaymentMethod,
        accountAddress: String,
        coinAmount: Long
    ): Result<WithdrawalRequest> {
        if (coinAmount < _adminSettings.value.minWithdrawalCoins) {
            return Result.failure(Exception("Minimum withdrawal is ${_adminSettings.value.minWithdrawalCoins} coins"))
        }

        if (user.coins < coinAmount) {
            return Result.failure(Exception("Insufficient coin balance"))
        }

        val payout = coinAmount * _adminSettings.value.coinToBdtRate
        val currency = if (method == PaymentMethod.BINANCE) "USDT" else "BDT"
        val effectivePayout = if (method == PaymentMethod.BINANCE) {
            (payout / 120.0)
        } else {
            payout
        }

        // Deduct coins
        val deducted = deductCoins(user.id, coinAmount, "Withdrawal Request via ${method.displayName}")
        if (!deducted) {
            return Result.failure(Exception("Failed to deduct coins"))
        }

        val request = WithdrawalRequest(
            id = "w_" + UUID.randomUUID().toString().take(8),
            userId = user.id,
            userName = user.name,
            userEmail = user.email,
            method = method,
            accountAddress = accountAddress.trim(),
            coinAmount = coinAmount,
            payoutAmount = effectivePayout,
            currency = currency,
            status = WithdrawalStatus.PENDING,
            requestDate = System.currentTimeMillis()
        )

        val updated = listOf(request) + _withdrawals.value
        _withdrawals.value = updated
        saveWithdrawals(updated)

        // Sync withdrawal to Firestore
        firestoreService.syncWithdrawal(request)

        return Result.success(request)
    }

    fun updateWithdrawalStatus(requestId: String, newStatus: WithdrawalStatus, adminNote: String) {
        val updated = _withdrawals.value.map { w ->
            if (w.id == requestId) {
                // If rejected, refund coins back to user
                if (w.status != WithdrawalStatus.REJECTED && newStatus == WithdrawalStatus.REJECTED) {
                    addCoins(w.userId, w.coinAmount, "Refund: Withdrawal #${w.id} Rejected ($adminNote)")
                }
                val mod = w.copy(status = newStatus, adminNote = adminNote)
                firestoreService.syncWithdrawal(mod)
                mod
            } else w
        }
        _withdrawals.value = updated
        saveWithdrawals(updated)
    }

    // ---------------- TASKS (VISIT & EARN) ----------------

    private fun loadTasks() {
        _tasks.value = listOf(
            EarningTask(
                id = "task_web_1",
                title = "Visit Sponsored Gadget Portal",
                description = "Browse trending smartphones & tech accessories for 15 seconds.",
                rewardCoins = 20,
                timerSeconds = 15,
                targetUrl = "https://www.google.com"
            ),
            EarningTask(
                id = "task_web_2",
                title = "Check Daily Crypto Market Rates",
                description = "View real-time Bitcoin, Ethereum, and USDT exchange rates.",
                rewardCoins = 25,
                timerSeconds = 20,
                targetUrl = "https://www.binance.com"
            ),
            EarningTask(
                id = "task_web_3",
                title = "Explore Online Work & Freelance Hub",
                description = "Read 3 tips on remote earning & freelance client pitching.",
                rewardCoins = 20,
                timerSeconds = 15,
                targetUrl = "https://www.wikipedia.org"
            )
        )
    }

    // ---------------- ADMIN SETTINGS ----------------

    private fun loadSettings() {
        val coinsShort = prefs.getInt("cfg_short_coins", 15)
        val secShort = prefs.getInt("cfg_short_sec", 10)
        val coinsLong = prefs.getInt("cfg_long_coins", 30)
        val secLong = prefs.getInt("cfg_long_sec", 20)
        val coinsWeb = prefs.getInt("cfg_web_coins", 20)
        val secWeb = prefs.getInt("cfg_web_sec", 15)
        val coinsUpload = prefs.getInt("cfg_upload_coins", 50)
        val rate = prefs.getFloat("cfg_bdt_rate", 0.01f).toDouble()
        val minCoins = prefs.getLong("cfg_min_coins", 500L)
        val bannerEnabled = prefs.getBoolean("cfg_banner_enabled", true)
        val adScript = prefs.getString("cfg_adsterra_script", "//adsterra-banner-code-zone-918231.js") ?: ""
        val directLinkEnabled = prefs.getBoolean("cfg_direct_link_enabled", true)
        val adLink = prefs.getString("cfg_adsterra_link", "https://example.com/adsterra-direct-earn") ?: ""
        val socialBarEnabled = prefs.getBoolean("cfg_socialbar_enabled", true)
        val socialBarScript = prefs.getString("cfg_socialbar_script", "//adsterra-socialbar-zone-839201.js") ?: ""
        val socialBarUrl = prefs.getString("cfg_socialbar_url", "https://example.com/adsterra-socialbar-promo") ?: ""
        val popunderEnabled = prefs.getBoolean("cfg_popunder_enabled", false)
        val popunderScript = prefs.getString("cfg_popunder_script", "//adsterra-popunder-zone-492012.js") ?: ""
        val popunderUrl = prefs.getString("cfg_popunder_url", "https://example.com/adsterra-popunder-landing") ?: ""
        val adsEnabled = prefs.getBoolean("cfg_ads_enabled", true)

        _adminSettings.value = AdminSettings(
            coinsPerShortWatch = coinsShort,
            requiredShortWatchSec = secShort,
            coinsPerLongWatch = coinsLong,
            requiredLongWatchSec = secLong,
            coinsPerWebsiteVisit = coinsWeb,
            websiteVisitSec = secWeb,
            coinsPerUpload = coinsUpload,
            coinToBdtRate = rate,
            minWithdrawalCoins = minCoins,
            isBannerAdEnabled = bannerEnabled,
            adSterraBannerScript = adScript,
            isDirectLinkEnabled = directLinkEnabled,
            adSterraDirectLink = adLink,
            isSocialBarEnabled = socialBarEnabled,
            adSterraSocialBarScript = socialBarScript,
            adSterraSocialBarUrl = socialBarUrl,
            isPopunderEnabled = popunderEnabled,
            adSterraPopunderScript = popunderScript,
            adSterraPopunderUrl = popunderUrl,
            isAdsEnabled = adsEnabled
        )
    }

    fun updateSettings(newSettings: AdminSettings) {
        _adminSettings.value = newSettings
        prefs.edit()
            .putInt("cfg_short_coins", newSettings.coinsPerShortWatch)
            .putInt("cfg_short_sec", newSettings.requiredShortWatchSec)
            .putInt("cfg_long_coins", newSettings.coinsPerLongWatch)
            .putInt("cfg_long_sec", newSettings.requiredLongWatchSec)
            .putInt("cfg_web_coins", newSettings.coinsPerWebsiteVisit)
            .putInt("cfg_web_sec", newSettings.websiteVisitSec)
            .putInt("cfg_upload_coins", newSettings.coinsPerUpload)
            .putFloat("cfg_bdt_rate", newSettings.coinToBdtRate.toFloat())
            .putLong("cfg_min_coins", newSettings.minWithdrawalCoins)
            .putBoolean("cfg_banner_enabled", newSettings.isBannerAdEnabled)
            .putString("cfg_adsterra_script", newSettings.adSterraBannerScript)
            .putBoolean("cfg_direct_link_enabled", newSettings.isDirectLinkEnabled)
            .putString("cfg_adsterra_link", newSettings.adSterraDirectLink)
            .putBoolean("cfg_socialbar_enabled", newSettings.isSocialBarEnabled)
            .putString("cfg_socialbar_script", newSettings.adSterraSocialBarScript)
            .putString("cfg_socialbar_url", newSettings.adSterraSocialBarUrl)
            .putBoolean("cfg_popunder_enabled", newSettings.isPopunderEnabled)
            .putString("cfg_popunder_script", newSettings.adSterraPopunderScript)
            .putString("cfg_popunder_url", newSettings.adSterraPopunderUrl)
            .putBoolean("cfg_ads_enabled", newSettings.isAdsEnabled)
            .apply()
    }
}
