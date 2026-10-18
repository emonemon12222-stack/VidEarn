package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("videarn_prefs", Context.MODE_PRIVATE)

    // Current User State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Videos List
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

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
        loadSettings()
        loadUsers()
        loadVideos()
        loadWithdrawals()
        loadTasks()
        loadInitialComments()
        loadTransactions()
        restoreSession()
    }

    // ---------------- AUTHENTICATION & USERS ----------------

    fun signUp(name: String, email: String, pass: String): Result<User> {
        val trimmedEmail = email.trim().lowercase()
        val usersJson = prefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)

        if (usersObj.has(trimmedEmail)) {
            return Result.failure(Exception("Email already registered"))
        }

        val newUser = User(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            email = trimmedEmail,
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
            coins = 100, // Welcome bonus
            referralCode = "REF" + (1000..9999).random()
        )

        val userRecord = JSONObject().apply {
            put("id", newUser.id)
            put("name", newUser.name)
            put("email", newUser.email)
            put("password", pass)
            put("coins", newUser.coins)
            put("role", newUser.role)
            put("isBanned", newUser.isBanned)
            put("referralCode", newUser.referralCode)
            put("joinedAt", newUser.joinedAt)
        }

        usersObj.put(trimmedEmail, userRecord)
        prefs.edit().putString("users_db", usersObj.toString()).apply()

        // Add welcome transaction
        addTransaction(newUser.id, "Welcome Bonus", 100, true)

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

        val user = User(
            id = userObj.getString("id"),
            name = userObj.getString("name"),
            email = trimmedEmail,
            avatarUrl = userObj.optString("avatarUrl", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150"),
            coins = userObj.optLong("coins", 100),
            role = userObj.optString("role", "USER"),
            isBanned = userObj.optBoolean("isBanned", false),
            referralCode = userObj.optString("referralCode", "REF7721"),
            joinedAt = userObj.optLong("joinedAt", System.currentTimeMillis())
        )

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
            referralCode = "DEMO99"
        )
        saveSession(guest)
        return guest
    }

    fun signOut() {
        prefs.edit().remove("logged_in_user_id").apply()
        _currentUser.value = null
    }

    private fun saveSession(user: User) {
        _currentUser.value = user
        prefs.edit().putString("logged_in_user_id", user.id).apply()
        updateUserInDb(user)
    }

    private fun restoreSession() {
        val userId = prefs.getString("logged_in_user_id", null) ?: return
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

    private fun loadUsers() {
        val usersJson = prefs.getString("users_db", null)
        val list = mutableListOf<User>()
        if (usersJson != null) {
            val obj = JSONObject(usersJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val u = obj.getJSONObject(key)
                list.add(
                    User(
                        id = u.optString("id", UUID.randomUUID().toString()),
                        name = u.optString("name", "User"),
                        email = u.optString("email", key),
                        avatarUrl = u.optString("avatarUrl", ""),
                        coins = u.optLong("coins", 100),
                        role = u.optString("role", "USER"),
                        isBanned = u.optBoolean("isBanned", false),
                        referralCode = u.optString("referralCode", "REF000"),
                        joinedAt = u.optLong("joinedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        if (list.isEmpty()) {
            // Seed a sample user
            val sample = User(
                id = "usr_sample1",
                name = "Rahim Ahmed",
                email = "rahim@gmail.com",
                avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                coins = 480,
                referralCode = "RAHIM12"
            )
            list.add(sample)
        }
        _allUsers.value = list
    }

    private fun updateUserInDb(user: User) {
        val usersJson = prefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)
        val keys = usersObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val u = usersObj.getJSONObject(key)
            if (u.optString("id") == user.id || key == user.email) {
                u.put("coins", user.coins)
                u.put("isBanned", user.isBanned)
                u.put("name", user.name)
                usersObj.put(key, u)
                break
            }
        }
        prefs.edit().putString("users_db", usersObj.toString()).apply()
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

    private fun addTransaction(userId: String, title: String, amount: Long, isCredit: Boolean) {
        val map = _transactions.value.toMutableMap()
        val list = map[userId]?.toMutableList() ?: mutableListOf()
        list.add(0, CoinTransaction(UUID.randomUUID().toString(), title, amount, isCredit))
        map[userId] = list
        _transactions.value = map
        saveTransactions(map)
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
    }

    // ---------------- VIDEOS (FEED, SHORTS, WATCH) ----------------

    private fun loadVideos() {
        val saved = prefs.getString("videos_db", null)
        if (saved != null) {
            try {
                val arr = JSONArray(saved)
                val list = mutableListOf<Video>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Video(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            videoUrl = obj.getString("videoUrl"),
                            type = VideoType.valueOf(obj.optString("type", "LONG")),
                            thumbnailUrl = obj.optString("thumbnailUrl", ""),
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
                    )
                }
                _videos.value = list
                return
            } catch (e: Exception) {
                // fallback to seeds
            }
        }

        // Pre-seeded high quality videos
        val defaultVideos = listOf(
            // --- SHORTS ---
            Video(
                id = "short_1",
                title = "Most Satisfying Kinetic Sand & Neon Slime Cutting ✂️",
                description = "Relaxing ASMR cutting video. Watch full 10s to earn instant coins!",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                type = VideoType.SHORT,
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500",
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
                title = "Amazing 3D Drone Lights Show in Dubai Skyline 🌟",
                description = "Futuristic 1000 drone formation over the city night lights.",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                type = VideoType.SHORT,
                thumbnailUrl = "https://images.unsplash.com/photo-1508614589041-895b88991e3e?w=500",
                channelName = "TechFuturism",
                channelAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100",
                views = 89400,
                likes = 7450,
                commentsCount = 210,
                durationLabel = "00:20",
                rewardCoins = 15,
                category = "Tech"
            ),
            Video(
                id = "short_3",
                title = "Insane Street Basketball Dunk Trickshots 🏀",
                description = "Watch till the end for the 360 between-the-legs windmill dunk!",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                type = VideoType.SHORT,
                thumbnailUrl = "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=500",
                channelName = "HoopDaily",
                channelAvatar = "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=100",
                views = 152000,
                likes = 12900,
                commentsCount = 430,
                durationLabel = "00:18",
                rewardCoins = 15,
                category = "Sports"
            ),
            Video(
                id = "short_4",
                title = "5 Second Life Hack That Saves Hundreds of Dollars 💡",
                description = "Quick finance life hack for online shopping discount codes.",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
                type = VideoType.SHORT,
                thumbnailUrl = "https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?w=500",
                channelName = "MoneyHacks",
                channelAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100",
                views = 68000,
                likes = 5400,
                commentsCount = 142,
                durationLabel = "00:15",
                rewardCoins = 15,
                category = "Earning"
            ),

            // --- LONG VIDEOS ---
            Video(
                id = "long_1",
                title = "How to Make \$50/Day Watching Videos & Doing Online Tasks in 2026",
                description = "Step-by-step complete blueprint for beginners to withdraw earnings directly to bKash, Nagad, or Binance.",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                type = VideoType.LONG,
                thumbnailUrl = "https://images.unsplash.com/photo-1553729459-efe14ef6055d?w=800",
                channelName = "PassiveIncomeBD",
                channelAvatar = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=100",
                views = 284000,
                likes = 18400,
                commentsCount = 760,
                durationLabel = "14:20",
                rewardCoins = 30,
                category = "Earning"
            ),
            Video(
                id = "long_2",
                title = "Top 10 High Paying Remote Skills You Can Learn in 30 Days",
                description = "AI Prompt Engineering, Cloud development, Flutter, and Video editing guide.",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                type = VideoType.LONG,
                thumbnailUrl = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800",
                channelName = "CareerFoundry",
                channelAvatar = "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=100",
                views = 98000,
                likes = 8200,
                commentsCount = 310,
                durationLabel = "18:45",
                rewardCoins = 30,
                category = "Tutorial"
            ),
            Video(
                id = "long_3",
                title = "Binance P2P Trading Guide: How to Safely Buy & Sell USDT to bKash",
                description = "Complete safety checklist for zero-fee crypto peer-to-peer transactions in Bangladesh and South Asia.",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                type = VideoType.LONG,
                thumbnailUrl = "https://images.unsplash.com/photo-1621416894569-0f39ed31d247?w=800",
                channelName = "CryptoBangla",
                channelAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
                views = 142000,
                likes = 11300,
                commentsCount = 520,
                durationLabel = "11:10",
                rewardCoins = 30,
                category = "Crypto"
            ),
            Video(
                id = "long_4",
                title = "Cybersecurity Essentials: Prevent Phone Hacks & Secure Your bKash/Nagad PIN",
                description = "Crucial protection tips against SMS phishing, fake OTP scams, and social engineering.",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                type = VideoType.LONG,
                thumbnailUrl = "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800",
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

        _videos.value = defaultVideos
        saveVideos(defaultVideos)
    }

    private fun saveVideos(list: List<Video>) {
        val arr = JSONArray()
        for (v in list) {
            val obj = JSONObject().apply {
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
            arr.put(obj)
        }
        prefs.edit().putString("videos_db", arr.toString()).apply()
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
        // Auto pick a reliable thumbnail if not provided
        val effectiveThumb = if (thumbnailUrl.isNotBlank()) {
            thumbnailUrl
        } else if (url.contains("youtu", ignoreCase = true)) {
            "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=800"
        } else {
            "https://images.unsplash.com/photo-1536240478700-b869070f9279?w=800"
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
    }

    // ---------------- COMMENTS ----------------

    private fun loadInitialComments() {
        val map = mutableMapOf<String, List<Comment>>()
        map["short_1"] = listOf(
            Comment("c1", "short_1", "Tanvir Hasan", "", "This is super satisfying to watch!", "2h ago", 14),
            Comment("c2", "short_1", "Al Amin", "", "Got my 15 coins instant bonus!", "1h ago", 5)
        )
        map["long_1"] = listOf(
            Comment("c3", "long_1", "Sabbir Hossain", "", "Great explanation brother, just received my bKash payout today!", "5h ago", 48),
            Comment("c4", "long_1", "Nasir Uddin", "", "Trusted platform. Thanks for the guidance.", "3h ago", 21)
        )
        _comments.value = map
    }

    fun addComment(videoId: String, userName: String, content: String) {
        val map = _comments.value.toMutableMap()
        val list = map[videoId]?.toMutableList() ?: mutableListOf()
        val newComment = Comment(
            id = UUID.randomUUID().toString(),
            videoId = videoId,
            userName = userName,
            content = content,
            timestamp = "Just now",
            likes = 0
        )
        list.add(0, newComment)
        map[videoId] = list
        _comments.value = map

        // Increment count on video
        val videos = _videos.value.map {
            if (it.id == videoId) it.copy(commentsCount = it.commentsCount + 1) else it
        }
        _videos.value = videos
        saveVideos(videos)
    }

    // ---------------- WITHDRAWALS ----------------

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
                // empty
            }
        }
        if (list.isEmpty()) {
            // Seed a sample past approved withdrawal
            list.add(
                WithdrawalRequest(
                    id = "w_demo_101",
                    userId = "usr_sample1",
                    userName = "Rahim Ahmed",
                    userEmail = "rahim@gmail.com",
                    method = PaymentMethod.BKASH,
                    accountAddress = "01712345678",
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
            // e.g. 1 BDT ~ 0.0085 USDT
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
        return Result.success(request)
    }

    fun updateWithdrawalStatus(requestId: String, newStatus: WithdrawalStatus, adminNote: String) {
        val updated = _withdrawals.value.map { w ->
            if (w.id == requestId) {
                // If rejected, refund coins back to user
                if (w.status != WithdrawalStatus.REJECTED && newStatus == WithdrawalStatus.REJECTED) {
                    addCoins(w.userId, w.coinAmount, "Refund: Withdrawal #${w.id} Rejected ($adminNote)")
                }
                w.copy(status = newStatus, adminNote = adminNote)
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
        val adScript = prefs.getString("cfg_adsterra_script", "//adsterra-banner-code-zone-918231.js") ?: ""
        val adLink = prefs.getString("cfg_adsterra_link", "https://example.com/adsterra-direct-earn") ?: ""
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
            adSterraBannerScript = adScript,
            adSterraDirectLink = adLink,
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
            .putString("cfg_adsterra_script", newSettings.adSterraBannerScript)
            .putString("cfg_adsterra_link", newSettings.adSterraDirectLink)
            .putBoolean("cfg_ads_enabled", newSettings.isAdsEnabled)
            .apply()
    }
}
