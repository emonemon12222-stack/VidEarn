package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.firebase.FirestoreService
import com.example.data.local.AppDatabase
import com.example.data.local.UserEntity
import com.example.data.local.VideoEntity
import com.example.data.local.WatchedRecordEntity
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
 * Implements a resilient 3-Tier Data Persistence Architecture:
 * 1. Cloud-backed Firebase Firestore with unlimited PersistentCacheSettings & real-time sync.
 * 2. On-device Room SQLite Database (videarn_room.db) preventing any data loss across app updates.
 * 3. Multi-Store SharedPreferences with continuous cross-version merging (videarn_master_store,
 *    videarn_prefs_v3, videarn_prefs, etc.), ensuring user login sessions, accumulated reward coins,
 *    wallet balances, and uploaded video lists remain intact across all APK updates and re-installs.
 */
class AppRepository(private val context: Context) {

    private val TAG = "AppRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Master SharedPreferences file
    private val masterPrefs: SharedPreferences =
        context.getSharedPreferences("videarn_master_store", Context.MODE_PRIVATE)

    // Mirror stores to support all legacy versions seamlessly
    private val prefsV3: SharedPreferences =
        context.getSharedPreferences("videarn_prefs_v3", Context.MODE_PRIVATE)

    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences("videarn_prefs", Context.MODE_PRIVATE)

    private val defaultPrefs: SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    // Room Database
    private val roomDb = AppDatabase.getInstance(context)
    private val userDao = roomDb.userDao()
    private val videoDao = roomDb.videoDao()
    private val watchedDao = roomDb.watchedDao()

    private val firestoreService = FirestoreService(context)

    // Current User State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Videos List
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    // Watched Video History
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
        migrateAndMergeAllPreferences()
        loadSettings()
        loadUsers()
        loadVideos()
        loadWithdrawals()
        loadTasks()
        loadInitialComments()
        loadTransactions()
        restoreSession()
        startPublicVideosRealtimeSync()
    }

    // ---------------- APP MIGRATION & SMOOTH UPDATES ----------------

    /**
     * Broadcasts writes to all local SharedPreferences stores so that whether the system
     * or an older component reads from master, v3, or legacy, the state is 100% synchronized.
     */
    private fun writeToAllPrefs(block: (SharedPreferences.Editor) -> Unit) {
        listOf(masterPrefs, prefsV3, legacyPrefs).forEach { p ->
            try {
                val editor = p.edit()
                block(editor)
                editor.apply()
            } catch (e: Exception) {
                Log.w(TAG, "Error writing to prefs store: ${e.message}")
            }
        }
    }

    /**
     * Inspects ALL known preference files and consolidates all user profiles, sessions,
     * video uploads, and transactions into the master persistent store.
     * Always retains the MAXIMUM coin count found for each user so coins are never lost.
     */
    private fun migrateAndMergeAllPreferences() {
        try {
            val allStores = listOf(masterPrefs, prefsV3, legacyPrefs, defaultPrefs)

            // 1. Recover logged_in_user_id from whichever store has it
            val activeUserId = allStores.mapNotNull { it.getString("logged_in_user_id", null) }
                .firstOrNull { it.isNotBlank() }
            if (activeUserId != null) {
                writeToAllPrefs { it.putString("logged_in_user_id", activeUserId) }
            }

            // 2. Recover and merge users_db (keeping highest coin balance per user)
            val mergedUsersMap = mutableMapOf<String, JSONObject>()
            for (store in allStores) {
                val usersJson = store.getString("users_db", null) ?: continue
                try {
                    val obj = JSONObject(usersJson)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val uObj = obj.getJSONObject(key)
                        val id = uObj.optString("id", key)
                        val existing = mergedUsersMap[id] ?: mergedUsersMap[key]
                        if (existing == null) {
                            mergedUsersMap[key] = uObj
                        } else {
                            // Retain highest coins
                            val oldCoins = existing.optLong("coins", 0L)
                            val newCoins = uObj.optLong("coins", 0L)
                            if (newCoins > oldCoins) {
                                existing.put("coins", newCoins)
                            }
                            mergedUsersMap[key] = existing
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing users_db from store: ${e.message}")
                }
            }

            if (mergedUsersMap.isNotEmpty()) {
                val consolidatedObj = JSONObject()
                mergedUsersMap.forEach { (k, v) -> consolidatedObj.put(k, v) }
                writeToAllPrefs { it.putString("users_db", consolidatedObj.toString()) }
            }

            // 3. Recover user_uploaded_videos_db
            val recoveredUploads = allStores.mapNotNull { it.getString("user_uploaded_videos_db", null) }
                .firstOrNull { it.isNotBlank() && it != "[]" }
            if (recoveredUploads != null) {
                writeToAllPrefs { it.putString("user_uploaded_videos_db", recoveredUploads) }
            }

            // 4. Recover withdrawals_db
            val recoveredWithdrawals = allStores.mapNotNull { it.getString("withdrawals_db", null) }
                .firstOrNull { it.isNotBlank() && it != "[]" }
            if (recoveredWithdrawals != null) {
                writeToAllPrefs { it.putString("withdrawals_db", recoveredWithdrawals) }
            }

            // 5. Recover transactions_db
            val recoveredTx = allStores.mapNotNull { it.getString("transactions_db", null) }
                .firstOrNull { it.isNotBlank() && it != "{}" }
            if (recoveredTx != null) {
                writeToAllPrefs { it.putString("transactions_db", recoveredTx) }
            }

            writeToAllPrefs { it.putBoolean("migration_completed", true) }
            Log.d(TAG, "Multi-store preferences migration and state verification completed.")
        } catch (e: Exception) {
            Log.w(TAG, "Preferences migration warning: ${e.message}")
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
        val usersJson = masterPrefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)

        if (usersObj.has(trimmedEmail)) {
            return Result.failure(Exception("Email already registered"))
        }

        val trimmedRefCode = inputReferralCode.trim().uppercase()
        var referrerUser: User? = null
        if (trimmedRefCode.isNotBlank()) {
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

        val refBonusNewUser = if (referrerUser != null) _adminSettings.value.referralBonusNewUser else 0L
        val startingCoins = 100L + refBonusNewUser
        val newUserId = "usr_" + UUID.randomUUID().toString().take(8)
        val generatedRefCode = generateReferralCode(name, newUserId)

        val newUser = User(
            id = newUserId,
            name = name.trim(),
            email = trimmedEmail,
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
            coins = startingCoins,
            referralCode = generatedRefCode,
            referredBy = if (referrerUser != null) trimmedRefCode else "",
            totalReferredCount = 0,
            totalReferralBonusEarned = 0L,
            joinedAt = System.currentTimeMillis()
        )

        val userRecord = serializeUser(newUser).apply {
            put("password", pass)
        }

        usersObj.put(trimmedEmail, userRecord)
        writeToAllPrefs { it.putString("users_db", usersObj.toString()) }

        // Save to Room SQLite database
        scope.launch {
            try {
                userDao.insertUser(UserEntity.fromUser(newUser, pass))
            } catch (e: Exception) {
                Log.w(TAG, "Room insertUser error: ${e.message}")
            }
        }

        // Base Welcome transaction
        addTransaction(newUser.id, "Welcome Bonus", 100, true)

        if (referrerUser != null) {
            val refBonusReferrer = _adminSettings.value.referralBonusReferrer
            addTransaction(newUser.id, "Referral Bonus (Code: $trimmedRefCode)", refBonusNewUser, true)

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

    /**
     * Local synchronous sign in check
     */
    fun signIn(email: String, pass: String): Result<User> {
        val trimmedEmail = email.trim().lowercase()
        val usersJson = masterPrefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)

        if (!usersObj.has(trimmedEmail)) {
            // Check in loaded allUsers
            val cachedUser = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            if (cachedUser != null) {
                saveSession(cachedUser)
                return Result.success(cachedUser)
            }
            return Result.failure(Exception("Account not found. Please sign up or restore from cloud."))
        }

        val userObj = usersObj.getJSONObject(trimmedEmail)
        val savedPass = userObj.optString("password", "")
        if (savedPass.isNotBlank() && savedPass != pass) {
            return Result.failure(Exception("Invalid password. Please check again."))
        }

        if (userObj.optBoolean("isBanned", false)) {
            return Result.failure(Exception("This account has been suspended by admin."))
        }

        val user = deserializeUser(userObj)
        saveSession(user)
        return Result.success(user)
    }

    /**
     * Sign In with asynchronous Firestore Cloud Fallback.
     * If the app was updated or reinstalled and local database was empty, it queries
     * Firestore cloud to restore the user's permanent account, coin balance, and history.
     */
    fun signInWithCloudFallback(
        email: String,
        pass: String,
        onResult: (Boolean, String, User?) -> Unit
    ) {
        val localResult = signIn(email, pass)
        localResult.fold(
            onSuccess = { user ->
                onResult(true, "Success", user)
            },
            onFailure = { localError ->
                // Attempt cloud restoration from Firestore using permanent email/auth
                firestoreService.fetchUserByEmail(email) { cloudUser ->
                    if (cloudUser != null) {
                        if (cloudUser.isBanned) {
                            onResult(false, "This account has been suspended by admin.", null)
                            return@fetchUserByEmail
                        }
                        // Restored from Firestore!
                        saveSession(cloudUser)
                        updateUserInDb(cloudUser)
                        onResult(true, "Account restored successfully from cloud!", cloudUser)
                    } else {
                        onResult(false, localError.message ?: "Sign in failed", null)
                    }
                }
            }
        )
    }

    /**
     * Instant Demo Login:
     * Never resets accumulated coins back to 250! Always retains previous earnings.
     */
    fun signInGuest(): User {
        val existingGuest = _allUsers.value.find { it.id == "guest_user" }
            ?: run {
                val usersJson = masterPrefs.getString("users_db", "{}") ?: "{}"
                try {
                    val obj = JSONObject(usersJson)
                    if (obj.has("demo@videarn.com")) {
                        deserializeUser(obj.getJSONObject("demo@videarn.com"))
                    } else null
                } catch (e: Exception) { null }
            }

        val bestCoins = if (existingGuest != null) maxOf(existingGuest.coins, 250L) else 250L

        val guest = existingGuest?.copy(coins = bestCoins) ?: User(
            id = "guest_user",
            name = "Demo User",
            email = "demo@videarn.com",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            coins = bestCoins,
            referralCode = "DEMO99",
            referredBy = "",
            totalReferredCount = 5,
            totalReferralBonusEarned = 250L
        )
        saveSession(guest)
        return guest
    }

    fun signOut() {
        writeToAllPrefs {
            it.remove("logged_in_user_id")
            it.remove("active_user_session_json")
        }
        _currentUser.value = null
        _watchedHistory.value = emptyList()
    }

    private fun saveSession(user: User) {
        _currentUser.value = user
        val serialized = serializeUser(user).toString()
        writeToAllPrefs {
            it.putString("logged_in_user_id", user.id)
            it.putString("active_user_session_json", serialized)
        }

        // Save to Room SQLite database
        scope.launch {
            try {
                userDao.insertUser(UserEntity.fromUser(user))
            } catch (e: Exception) {
                Log.w(TAG, "Room saveSession error: ${e.message}")
            }
        }

        updateUserInDb(user)

        // Sync directly to Firestore cloud
        firestoreService.syncUser(user)
        loadWatchedHistoryForUser(user.id)
        syncUserCloudData(user.id)
    }

    private fun restoreSession() {
        val userId = masterPrefs.getString("logged_in_user_id", null)
            ?: prefsV3.getString("logged_in_user_id", null)
            ?: legacyPrefs.getString("logged_in_user_id", null)

        if (userId == null) {
            // Check Room Database for any existing user to restore
            scope.launch {
                try {
                    val roomUsers = userDao.getAllUsers()
                    if (roomUsers.isNotEmpty()) {
                        val mostRecent = roomUsers.first().toUser()
                        _currentUser.value = mostRecent
                        syncUserCloudData(mostRecent.id)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Room restoreSession error: ${e.message}")
                }
            }
            return
        }

        // 1. Direct restore from serialized active session JSON
        val savedSession = masterPrefs.getString("active_user_session_json", null)
            ?: prefsV3.getString("active_user_session_json", null)

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

        val activeUser = _currentUser.value
        if (activeUser != null) {
            loadWatchedHistoryForUser(activeUser.id)
            syncUserCloudData(activeUser.id)
        }
    }

    /**
     * Seamlessly reconciles local state with Firestore Cloud.
     * Guarantees that coin balances, watch history, and uploaded videos are synced instantly.
     */
    private fun syncUserCloudData(userId: String) {
        val activeUser = _currentUser.value ?: return

        // 1. User profile and coin balance reconciliation
        firestoreService.fetchUser(userId) { cloudUser ->
            if (cloudUser != null && !cloudUser.isBanned) {
                val bestCoins = maxOf(activeUser.coins, cloudUser.coins)
                val merged = cloudUser.copy(coins = bestCoins)
                _currentUser.value = merged
                updateUserInDb(merged)
            } else if (cloudUser == null) {
                // If cloud does not have this user yet, push active local profile to cloud
                firestoreService.syncUser(activeUser)
            }
        }

        // 2. Real-time Firestore snapshot listener
        firestoreService.observeUser(userId) { updatedFromCloud ->
            if (!updatedFromCloud.isBanned) {
                val current = _currentUser.value
                val bestCoins = if (current != null) maxOf(current.coins, updatedFromCloud.coins) else updatedFromCloud.coins
                val finalUser = updatedFromCloud.copy(coins = bestCoins)
                _currentUser.value = finalUser
                updateUserInDb(finalUser)
            }
        }

        // 3. User-uploaded videos recovery from cloud
        firestoreService.fetchVideosByUploader(userId) { cloudUserVideos ->
            if (cloudUserVideos.isNotEmpty()) {
                val merged = (cloudUserVideos + _videos.value).distinctBy { it.id }
                _videos.value = merged
                saveVideos(merged)
            }
        }

        // 4. Watched video history recovery from cloud
        firestoreService.fetchWatchedHistory(userId) { cloudWatched ->
            if (cloudWatched.isNotEmpty()) {
                val merged = (cloudWatched + _watchedHistory.value)
                    .distinctBy { it.videoId }
                    .sortedByDescending { it.timestamp }
                _watchedHistory.value = merged
                saveWatchedHistoryForUser(userId, merged)
            }
        }

        // 5. Withdrawal requests recovery from cloud
        firestoreService.fetchWithdrawals(userId) { cloudWithdrawals ->
            if (cloudWithdrawals.isNotEmpty()) {
                val merged = (cloudWithdrawals + _withdrawals.value).distinctBy { it.id }
                _withdrawals.value = merged
                saveWithdrawals(merged)
            }
        }

        // 6. Transactions recovery from cloud
        firestoreService.fetchTransactions(userId) { cloudTx ->
            if (cloudTx.isNotEmpty()) {
                val currentMap = _transactions.value.toMutableMap()
                val existingList = currentMap[userId] ?: emptyList()
                val mergedList = (cloudTx + existingList).distinctBy { it.id }.sortedByDescending { it.timestamp }
                currentMap[userId] = mergedList
                _transactions.value = currentMap
                saveTransactions(currentMap)
            }
        }
    }

    private fun loadUsers() {
        val usersJson = masterPrefs.getString("users_db", null)
            ?: prefsV3.getString("users_db", null)
            ?: legacyPrefs.getString("users_db", null)

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

        // Also check Room Database
        scope.launch {
            try {
                val roomUsers = userDao.getAllUsers()
                if (roomUsers.isNotEmpty()) {
                    val roomList = roomUsers.map { it.toUser() }
                    val combined = (roomList + _allUsers.value).distinctBy { it.id }
                    _allUsers.value = combined
                }
            } catch (e: Exception) {
                Log.w(TAG, "Room loadUsers error: ${e.message}")
            }
        }

        if (list.isEmpty()) {
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
        val usersJson = masterPrefs.getString("users_db", "{}") ?: "{}"
        val usersObj = JSONObject(usersJson)
        val keys = usersObj.keys()
        var found = false
        while (keys.hasNext()) {
            val key = keys.next()
            val u = usersObj.getJSONObject(key)
            if (u.optString("id") == user.id || key == user.email) {
                val oldCoins = u.optLong("coins", 0L)
                val bestCoins = maxOf(oldCoins, user.coins)
                u.put("coins", bestCoins)
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

        val serialized = serializeUser(user).toString()
        writeToAllPrefs {
            it.putString("users_db", usersObj.toString())
            if (_currentUser.value?.id == user.id) {
                it.putString("active_user_session_json", serialized)
            }
        }

        // Room SQLite update
        scope.launch {
            try {
                userDao.insertUser(UserEntity.fromUser(user))
            } catch (e: Exception) {
                Log.w(TAG, "Room updateUser error: ${e.message}")
            }
        }

        // Firestore Cloud update
        firestoreService.syncUser(user)
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
        writeToAllPrefs { it.putString("transactions_db", obj.toString()) }
    }

    private fun loadTransactions() {
        val str = masterPrefs.getString("transactions_db", null)
            ?: prefsV3.getString("transactions_db", null)
            ?: legacyPrefs.getString("transactions_db", null)
            ?: return

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
     * Saved to Room Database, SharedPreferences, and synced directly to Firestore.
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

        // Save to Room SQLite database
        scope.launch {
            try {
                watchedDao.insertRecord(WatchedRecordEntity.fromRecord(record))
            } catch (e: Exception) {
                Log.w(TAG, "Room recordWatchedVideo error: ${e.message}")
            }
        }

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
        writeToAllPrefs { it.putString("watched_history_$userId", arr.toString()) }
    }

    private fun loadWatchedHistoryForUser(userId: String) {
        val saved = masterPrefs.getString("watched_history_$userId", null)
            ?: prefsV3.getString("watched_history_$userId", null)
            ?: legacyPrefs.getString("watched_history_$userId", null)

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

        // Also check Room SQLite Database
        scope.launch {
            try {
                val roomRecords = watchedDao.getRecordsByUser(userId).map { it.toRecord() }
                if (roomRecords.isNotEmpty()) {
                    val combined = (roomRecords + _watchedHistory.value).distinctBy { it.videoId }
                    _watchedHistory.value = combined
                }
            } catch (e: Exception) {
                Log.w(TAG, "Room loadWatchedHistory error: ${e.message}")
            }
        }

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

        val savedAll = masterPrefs.getString("videos_db", null)
            ?: prefsV3.getString("videos_db", null)
            ?: legacyPrefs.getString("videos_db", null)

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
        for (v in userUploadedVideos) {
            combinedMap[v.id] = v
        }
        for (v in allSavedList) {
            if (!combinedMap.containsKey(v.id)) {
                combinedMap[v.id] = v
            }
        }
        for (v in defaultVideos) {
            if (!combinedMap.containsKey(v.id)) {
                combinedMap[v.id] = v
            }
        }

        val finalVideos = combinedMap.values.toList()
        _videos.value = finalVideos
        saveVideos(finalVideos)

        // Also load from Room Database
        scope.launch {
            try {
                val roomVideos = videoDao.getAllVideos().map { it.toVideo() }
                if (roomVideos.isNotEmpty()) {
                    val mergedWithRoom = (roomVideos + _videos.value).distinctBy { it.id }
                    _videos.value = mergedWithRoom
                }
            } catch (e: Exception) {
                Log.w(TAG, "Room loadVideos error: ${e.message}")
            }
        }

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

    private var publicVideosListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Attaches a real-time listener to the global public `videos` collection in Firestore.
     * Ensures newly uploaded videos by any user immediately appear on HomeScreen in real-time.
     */
    fun startPublicVideosRealtimeSync() {
        publicVideosListener?.remove()
        publicVideosListener = firestoreService.observePublicVideos { cloudVideos ->
            if (cloudVideos.isNotEmpty()) {
                val userUploads = loadUserUploadedVideosFromPrefs()
                val defaultVideos = getPreSeededVideos()
                val combinedMap = linkedMapOf<String, Video>()
                for (v in userUploads) combinedMap[v.id] = v
                for (v in cloudVideos) if (!combinedMap.containsKey(v.id)) combinedMap[v.id] = v
                for (v in _videos.value) if (!combinedMap.containsKey(v.id)) combinedMap[v.id] = v
                for (v in defaultVideos) if (!combinedMap.containsKey(v.id)) combinedMap[v.id] = v
                val merged = combinedMap.values.toList()
                _videos.value = merged
                saveVideos(merged)
            }
        }
    }

    /**
     * Refreshes the global public video feed directly from Firestore
     */
    fun refreshPublicVideos() {
        firestoreService.fetchVideos { cloudVideos ->
            if (cloudVideos.isNotEmpty()) {
                val userUploads = loadUserUploadedVideosFromPrefs()
                val defaultVideos = getPreSeededVideos()
                val combinedMap = linkedMapOf<String, Video>()
                for (v in userUploads) combinedMap[v.id] = v
                for (v in cloudVideos) if (!combinedMap.containsKey(v.id)) combinedMap[v.id] = v
                for (v in _videos.value) if (!combinedMap.containsKey(v.id)) combinedMap[v.id] = v
                for (v in defaultVideos) if (!combinedMap.containsKey(v.id)) combinedMap[v.id] = v
                val merged = combinedMap.values.toList()
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
        val saved = masterPrefs.getString("user_uploaded_videos_db", null)
            ?: prefsV3.getString("user_uploaded_videos_db", null)
            ?: legacyPrefs.getString("user_uploaded_videos_db", null)
            ?: return emptyList()

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
        writeToAllPrefs { it.putString("user_uploaded_videos_db", arr.toString()) }

        // Also persist user uploads into Room SQLite
        scope.launch {
            try {
                videoDao.insertVideos(userUploads.map { VideoEntity.fromVideo(it) })
            } catch (e: Exception) {
                Log.w(TAG, "Room saveUserUploadedVideos error: ${e.message}")
            }
        }
    }

    private fun saveVideos(list: List<Video>) {
        val arr = JSONArray()
        for (v in list) {
            arr.put(serializeVideo(v))
        }
        writeToAllPrefs { it.putString("videos_db", arr.toString()) }
        saveUserUploadedVideosToPrefs(list)

        // Save all videos into Room database
        scope.launch {
            try {
                videoDao.insertVideos(list.map { VideoEntity.fromVideo(it) })
            } catch (e: Exception) {
                Log.w(TAG, "Room saveVideos error: ${e.message}")
            }
        }
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

        // Sync directly to Firestore (both public and personal sub-collection)
        firestoreService.syncVideo(newVideo)

        // Give upload reward
        addCoins(uploaderId, _adminSettings.value.coinsPerUpload.toLong(), "Reward for Uploading Video: '$title'")

        return newVideo
    }

    fun incrementVideoViews(videoId: String) {
        val updated = _videos.value.map {
            if (it.id == videoId) it.copy(views = it.views + 1) else it
        }
        _videos.value = updated
        saveVideos(updated)
    }

    fun toggleVideoLike(videoId: String) {
        val updated = _videos.value.map {
            if (it.id == videoId) {
                val newLiked = !it.isLiked
                val newLikes = if (newLiked) it.likes + 1 else maxOf(0, it.likes - 1)
                it.copy(isLiked = newLiked, likes = newLikes)
            } else it
        }
        _videos.value = updated
        saveVideos(updated)
    }

    fun deleteVideo(videoId: String) {
        val target = _videos.value.find { it.id == videoId }
        val uploaderId = target?.uploaderId
        val updated = _videos.value.filterNot { it.id == videoId }
        _videos.value = updated
        saveVideos(updated)
        firestoreService.deleteVideo(videoId, uploaderId)

        scope.launch {
            try {
                videoDao.deleteVideo(videoId)
            } catch (e: Exception) {
                Log.w(TAG, "Room deleteVideo error: ${e.message}")
            }
        }
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
                views = 312000,
                likes = 14500,
                commentsCount = 4210,
                durationLabel = "00:19",
                rewardCoins = 30,
                category = "Tech"
            ),
            Video(
                id = "long_3",
                title = "Top 10 Most Beautiful Travel Places to Visit in 2026 (4K UHD)",
                description = "Breathtaking landscapes across Switzerland, Norway, Japan, and New Zealand.",
                videoUrl = "https://www.youtube.com/watch?v=M7FIvfx5J10",
                type = VideoType.LONG,
                thumbnailUrl = "https://img.youtube.com/vi/M7FIvfx5J10/hqdefault.jpg",
                channelName = "Wanderlust 4K",
                channelAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100",
                views = 89000,
                likes = 6200,
                commentsCount = 380,
                durationLabel = "11:45",
                rewardCoins = 30,
                category = "Music"
            ),
            Video(
                id = "long_4",
                title = "Complete Android App Development with Jetpack Compose & Firebase",
                description = "Master Android modern architecture, Kotlin Coroutines, and cloud data persistence in 2026.",
                videoUrl = "https://www.youtube.com/watch?v=k38Xg72X-tY",
                type = VideoType.LONG,
                thumbnailUrl = "https://img.youtube.com/vi/k38Xg72X-tY/hqdefault.jpg",
                channelName = "CodeMaster Studio",
                channelAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100",
                views = 45200,
                likes = 4100,
                commentsCount = 290,
                durationLabel = "22:10",
                rewardCoins = 30,
                category = "Tech"
            )
        )
    }

    // ---------------- COMMENTS ----------------

    private fun loadInitialComments() {
        val map = mutableMapOf<String, List<Comment>>()
        map["long_1"] = listOf(
            Comment("c1", "long_1", "Rahim Ahmed", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100", "Received my bKash withdrawal in 10 minutes! Legit platform 👍", "2 hours ago", 5),
            Comment("c2", "long_1", "Tanvir Hasan", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100", "Watching shorts gives easy coins, keep uploading!", "1 hour ago", 2)
        )
        map["short_1"] = listOf(
            Comment("c3", "short_1", "Nusrat Jahan", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100", "So relaxing to watch after work ❤️", "30 mins ago", 8)
        )
        _comments.value = map
    }

    fun addComment(videoId: String, userName: String, content: String): Comment {
        val newComment = Comment(
            id = "cmt_" + UUID.randomUUID().toString().take(8),
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
        map[videoId] = list
        _comments.value = map

        val updatedVideos = _videos.value.map {
            if (it.id == videoId) it.copy(commentsCount = it.commentsCount + 1) else it
        }
        _videos.value = updatedVideos
        saveVideos(updatedVideos)

        return newComment
    }

    fun incrementViews(videoId: String) {
        incrementVideoViews(videoId)
    }

    fun toggleLike(videoId: String) {
        toggleVideoLike(videoId)
    }

    // ---------------- WITHDRAWALS ----------------

    private fun loadWithdrawals() {
        val str = masterPrefs.getString("withdrawals_db", null)
            ?: prefsV3.getString("withdrawals_db", null)
            ?: legacyPrefs.getString("withdrawals_db", null)
            ?: return

        try {
            val arr = JSONArray(str)
            val list = mutableListOf<WithdrawalRequest>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val methodStr = obj.getString("method")
                val statusStr = obj.getString("status")
                list.add(
                    WithdrawalRequest(
                        id = obj.getString("id"),
                        userId = obj.getString("userId"),
                        userName = obj.getString("userName"),
                        userEmail = obj.getString("userEmail"),
                        method = try { PaymentMethod.valueOf(methodStr) } catch (e: Exception) { PaymentMethod.BKASH },
                        accountAddress = obj.getString("accountAddress"),
                        coinAmount = obj.getLong("coinAmount"),
                        payoutAmount = obj.getDouble("payoutAmount"),
                        currency = obj.getString("currency"),
                        status = try { WithdrawalStatus.valueOf(statusStr) } catch (e: Exception) { WithdrawalStatus.PENDING },
                        adminNote = obj.optString("adminNote", ""),
                        requestDate = obj.getLong("requestDate")
                    )
                )
            }
            _withdrawals.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Error loading withdrawals: ${e.message}")
        }
    }

    private fun saveWithdrawals(list: List<WithdrawalRequest>) {
        val arr = JSONArray()
        for (w in list) {
            arr.put(
                JSONObject().apply {
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
            )
        }
        writeToAllPrefs { it.putString("withdrawals_db", arr.toString()) }
    }

    fun submitWithdrawal(
        user: User,
        method: PaymentMethod,
        accountAddress: String,
        coinAmount: Long
    ): Result<WithdrawalRequest> = requestWithdrawal(user, method, accountAddress, coinAmount)

    fun requestWithdrawal(
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

        val success = deductCoins(user.id, coinAmount, "Withdrawal Request (${method.name})")
        if (!success) {
            return Result.failure(Exception("Failed to deduct coins for withdrawal"))
        }

        val rate = _adminSettings.value.coinToBdtRate
        val bdtValue = coinAmount * rate
        val (currency, effectivePayout) = if (method == PaymentMethod.BINANCE) {
            val usdt = bdtValue / 125.0
            Pair("USDT", usdt)
        } else {
            Pair("BDT", bdtValue)
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
        val coinsShort = masterPrefs.getInt("cfg_short_coins", 15)
        val secShort = masterPrefs.getInt("cfg_short_sec", 10)
        val coinsLong = masterPrefs.getInt("cfg_long_coins", 30)
        val secLong = masterPrefs.getInt("cfg_long_sec", 20)
        val coinsWeb = masterPrefs.getInt("cfg_web_coins", 20)
        val secWeb = masterPrefs.getInt("cfg_web_sec", 15)
        val coinsUpload = masterPrefs.getInt("cfg_upload_coins", 50)
        val rate = masterPrefs.getFloat("cfg_bdt_rate", 0.01f).toDouble()
        val minCoins = masterPrefs.getLong("cfg_min_coins", 500L)
        val refBonusReferrer = masterPrefs.getLong("cfg_ref_referrer", 50L)
        val refBonusNewUser = masterPrefs.getLong("cfg_ref_new_user", 50L)
        val bannerEnabled = masterPrefs.getBoolean("cfg_banner_enabled", true)
        val adScript = masterPrefs.getString("cfg_adsterra_script", "//adsterra-banner-code-zone-918231.js") ?: ""
        val directLinkEnabled = masterPrefs.getBoolean("cfg_direct_link_enabled", true)
        val adLink = masterPrefs.getString("cfg_adsterra_link", "https://example.com/adsterra-direct-earn") ?: ""
        val socialBarEnabled = masterPrefs.getBoolean("cfg_socialbar_enabled", true)
        val socialBarScript = masterPrefs.getString("cfg_socialbar_script", "//adsterra-socialbar-zone-839201.js") ?: ""
        val socialBarUrl = masterPrefs.getString("cfg_socialbar_url", "https://example.com/adsterra-socialbar-promo") ?: ""
        val popunderEnabled = masterPrefs.getBoolean("cfg_popunder_enabled", false)
        val popunderScript = masterPrefs.getString("cfg_popunder_script", "//adsterra-popunder-zone-492012.js") ?: ""
        val popunderUrl = masterPrefs.getString("cfg_popunder_url", "https://example.com/adsterra-popunder-landing") ?: ""
        val adsEnabled = masterPrefs.getBoolean("cfg_ads_enabled", true)

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
            referralBonusReferrer = refBonusReferrer,
            referralBonusNewUser = refBonusNewUser,
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
        writeToAllPrefs {
            it.putInt("cfg_short_coins", newSettings.coinsPerShortWatch)
            it.putInt("cfg_short_sec", newSettings.requiredShortWatchSec)
            it.putInt("cfg_long_coins", newSettings.coinsPerLongWatch)
            it.putInt("cfg_long_sec", newSettings.requiredLongWatchSec)
            it.putInt("cfg_web_coins", newSettings.coinsPerWebsiteVisit)
            it.putInt("cfg_web_sec", newSettings.websiteVisitSec)
            it.putInt("cfg_upload_coins", newSettings.coinsPerUpload)
            it.putFloat("cfg_bdt_rate", newSettings.coinToBdtRate.toFloat())
            it.putLong("cfg_min_coins", newSettings.minWithdrawalCoins)
            it.putLong("cfg_ref_referrer", newSettings.referralBonusReferrer)
            it.putLong("cfg_ref_new_user", newSettings.referralBonusNewUser)
            it.putBoolean("cfg_banner_enabled", newSettings.isBannerAdEnabled)
            it.putString("cfg_adsterra_script", newSettings.adSterraBannerScript)
            it.putBoolean("cfg_direct_link_enabled", newSettings.isDirectLinkEnabled)
            it.putString("cfg_adsterra_link", newSettings.adSterraDirectLink)
            it.putBoolean("cfg_socialbar_enabled", newSettings.isSocialBarEnabled)
            it.putString("cfg_socialbar_script", newSettings.adSterraSocialBarScript)
            it.putString("cfg_socialbar_url", newSettings.adSterraSocialBarUrl)
            it.putBoolean("cfg_popunder_enabled", newSettings.isPopunderEnabled)
            it.putString("cfg_popunder_script", newSettings.adSterraPopunderScript)
            it.putString("cfg_popunder_url", newSettings.adSterraPopunderUrl)
            it.putBoolean("cfg_ads_enabled", newSettings.isAdsEnabled)
        }
    }
}
