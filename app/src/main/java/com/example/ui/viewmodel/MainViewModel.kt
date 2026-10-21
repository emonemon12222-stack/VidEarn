package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    SHORTS,
    EARN,
    UPLOAD,
    WALLET,
    PROFILE
}

enum class HomeSubTab {
    LONG_VIDEOS,
    SHORTS
}

enum class AdminTab {
    OVERVIEW,
    SETTINGS,
    WITHDRAWALS,
    VIDEOS,
    USERS,
    ADS
}

data class RewardCelebration(
    val coins: Int,
    val title: String,
    val message: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application.applicationContext)

    // Current Navigation State
    private val _currentTab = MutableStateFlow(MainTab.HOME)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _homeSubTab = MutableStateFlow(HomeSubTab.LONG_VIDEOS)
    val homeSubTab: StateFlow<HomeSubTab> = _homeSubTab.asStateFlow()

    // Currently watched video (for long video watch page)
    private val _watchingVideo = MutableStateFlow<Video?>(null)
    val watchingVideo: StateFlow<Video?> = _watchingVideo.asStateFlow()

    // Active Task (e.g. Website Visit task)
    private val _activeTask = MutableStateFlow<EarningTask?>(null)
    val activeTask: StateFlow<EarningTask?> = _activeTask.asStateFlow()

    private val _taskRemainingSec = MutableStateFlow(0)
    val taskRemainingSec: StateFlow<Int> = _taskRemainingSec.asStateFlow()

    // Watch Video Timer State
    private val _videoWatchSec = MutableStateFlow(0)
    val videoWatchSec: StateFlow<Int> = _videoWatchSec.asStateFlow()

    private val _isVideoPlaying = MutableStateFlow(true)
    val isVideoPlaying: StateFlow<Boolean> = _isVideoPlaying.asStateFlow()

    private val rewardedVideosThisSession = mutableSetOf<String>()

    // Admin State
    private val _isAdminLoggedIn = MutableStateFlow(false)
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()

    private val _showAdminLoginDialog = MutableStateFlow(false)
    val showAdminLoginDialog: StateFlow<Boolean> = _showAdminLoginDialog.asStateFlow()

    private val _adminTab = MutableStateFlow(AdminTab.OVERVIEW)
    val adminTab: StateFlow<AdminTab> = _adminTab.asStateFlow()

    // Celebration Dialog State
    private val _celebration = MutableStateFlow<RewardCelebration?>(null)
    val celebration: StateFlow<RewardCelebration?> = _celebration.asStateFlow()

    // General status message (toast/snackbar)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Shorts Active Page Index
    private val _currentShortIndex = MutableStateFlow(0)
    val currentShortIndex: StateFlow<Int> = _currentShortIndex.asStateFlow()

    // Search query & category filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Timers Coroutine Jobs
    private var videoWatchJob: Job? = null
    private var taskJob: Job? = null

    // Exposed Flows from Repository
    val currentUser = repository.currentUser
    val allVideos = repository.videos
    val withdrawals = repository.withdrawals
    val adminSettings = repository.adminSettings
    val tasks = repository.tasks
    val comments = repository.comments
    val transactions = repository.transactions
    val allUsers = repository.allUsers
    val watchedHistory = repository.watchedHistory

    // Filtered Video Flows
    val longVideos: StateFlow<List<Video>> = combine(
        allVideos,
        _searchQuery,
        _selectedCategory
    ) { vids, query, category ->
        vids.filter { it.type == VideoType.LONG }
            .filter {
                if (query.isBlank()) true
                else it.title.contains(query, ignoreCase = true) || it.channelName.contains(query, ignoreCase = true)
            }
            .filter {
                if (category == "All") true
                else it.category.equals(category, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val shorts: StateFlow<List<Video>> = allVideos.map { list ->
        list.filter { it.type == VideoType.SHORT }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---------------- NAVIGATION ----------------

    fun setTab(tab: MainTab) {
        _currentTab.value = tab
        if (tab != MainTab.HOME) {
            _watchingVideo.value = null
            stopVideoTimer()
        }
    }

    fun setHomeSubTab(subTab: HomeSubTab) {
        _homeSubTab.value = subTab
        if (subTab == HomeSubTab.SHORTS) {
            startShortsWatchTimer()
        } else {
            stopVideoTimer()
        }
    }

    // Popunder Trigger State
    private val _popunderUrlToLaunch = MutableStateFlow<String?>(null)
    val popunderUrlToLaunch: StateFlow<String?> = _popunderUrlToLaunch.asStateFlow()

    fun clearPopunder() {
        _popunderUrlToLaunch.value = null
    }

    fun triggerPopunderIfEnabled() {
        val settings = adminSettings.value
        if (settings.isAdsEnabled && settings.isPopunderEnabled && settings.adSterraPopunderUrl.isNotBlank()) {
            _popunderUrlToLaunch.value = settings.adSterraPopunderUrl
        }
    }

    fun openWatchVideo(video: Video) {
        _watchingVideo.value = video
        repository.incrementViews(video.id)
        startVideoWatchTimer(video)
        triggerPopunderIfEnabled()
    }

    fun closeWatchVideo() {
        _watchingVideo.value = null
        stopVideoTimer()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissCelebration() {
        _celebration.value = null
    }

    // ---------------- WATCH TIMERS & EARNING ----------------

    fun toggleVideoPlayback() {
        _isVideoPlaying.value = !_isVideoPlaying.value
    }

    fun setVideoPlaying(playing: Boolean) {
        _isVideoPlaying.value = playing
    }

    fun checkAndRestoreSession() {
        repository.restoreSession()
    }

    private fun startVideoWatchTimer(video: Video) {
        videoWatchJob?.cancel()
        _videoWatchSec.value = 0
        _isVideoPlaying.value = true

        val requiredSec = adminSettings.value.requiredLongWatchSec

        videoWatchJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_isVideoPlaying.value && _watchingVideo.value?.id == video.id) {
                    _videoWatchSec.value += 1
                    if (_videoWatchSec.value >= requiredSec && !rewardedVideosThisSession.contains(video.id)) {
                        rewardedVideosThisSession.add(video.id)
                        val reward = adminSettings.value.coinsPerLongWatch
                        val user = currentUser.value
                        if (user != null) {
                            repository.addCoins(user.id, reward.toLong(), "Watch Reward: ${video.title}")
                            repository.recordWatchedVideo(user.id, video.id, video.title, reward)
                            _celebration.value = RewardCelebration(
                                coins = reward,
                                title = "Awesome Watcher!",
                                message = "You watched for $requiredSec seconds and earned $reward Coins!"
                            )
                        }
                    }
                }
            }
        }
    }

    fun onShortChanged(index: Int) {
        _currentShortIndex.value = index
        val currentShorts = shorts.value
        if (index in currentShorts.indices) {
            val short = currentShorts[index]
            repository.incrementViews(short.id)
            startShortsWatchTimer()
        }
    }

    private fun startShortsWatchTimer() {
        videoWatchJob?.cancel()
        _videoWatchSec.value = 0
        _isVideoPlaying.value = true

        val requiredSec = adminSettings.value.requiredShortWatchSec
        val currentShorts = shorts.value
        val currentIndex = _currentShortIndex.value
        if (currentIndex !in currentShorts.indices) return
        val currentShort = currentShorts[currentIndex]

        videoWatchJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_isVideoPlaying.value) {
                    _videoWatchSec.value += 1
                    if (_videoWatchSec.value >= requiredSec && !rewardedVideosThisSession.contains(currentShort.id)) {
                        rewardedVideosThisSession.add(currentShort.id)
                        val reward = adminSettings.value.coinsPerShortWatch
                        val user = currentUser.value
                        if (user != null) {
                            repository.addCoins(user.id, reward.toLong(), "Shorts Reward: ${currentShort.title}")
                            repository.recordWatchedVideo(user.id, currentShort.id, currentShort.title, reward)
                            _celebration.value = RewardCelebration(
                                coins = reward,
                                title = "Shorts Bonus Earned! 🔥",
                                message = "You watched this short for $requiredSec seconds and received +$reward Coins!"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopVideoTimer() {
        videoWatchJob?.cancel()
        videoWatchJob = null
        _videoWatchSec.value = 0
    }

    // ---------------- WEBSITE VISIT TASKS ----------------

    fun startTask(task: EarningTask) {
        _activeTask.value = task
        _taskRemainingSec.value = task.timerSeconds
        taskJob?.cancel()

        taskJob = viewModelScope.launch {
            while (_taskRemainingSec.value > 0) {
                delay(1000)
                _taskRemainingSec.value -= 1
            }
            // Completed!
            val user = currentUser.value
            if (user != null) {
                repository.addCoins(user.id, task.rewardCoins.toLong(), "Task Reward: ${task.title}")
                _celebration.value = RewardCelebration(
                    coins = task.rewardCoins,
                    title = "Task Completed! 🚀",
                    message = "You explored the sponsored link and received +${task.rewardCoins} Coins!"
                )
            }
            _activeTask.value = null
        }
    }

    fun cancelActiveTask() {
        taskJob?.cancel()
        taskJob = null
        _activeTask.value = null
        _taskRemainingSec.value = 0
    }

    // ---------------- DAILY BONUS ----------------

    fun claimDailyBonus() {
        val user = currentUser.value ?: return
        repository.addCoins(user.id, 25, "Daily Check-in Bonus Streak")
        _celebration.value = RewardCelebration(
            coins = 25,
            title = "Daily Check-In Claimed! 🎁",
            message = "Come back tomorrow to keep your daily earning streak going!"
        )
    }

    // ---------------- USER AUTHENTICATION ----------------

    fun signUp(
        name: String,
        email: String,
        pass: String,
        referralCode: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        if (name.isBlank() || email.isBlank() || pass.length < 6) {
            onResult(false, "Please provide a valid name, email, and at least 6-character password.")
            return
        }
        repository.signUpWithFirebase(name, email, pass, referralCode) { success, msg, user ->
            if (success && user != null) {
                val bonusMsg = if (referralCode.isNotBlank()) " +150 Coins added with Referral Bonus! 🎁" else " +100 Coins welcome bonus added!"
                _statusMessage.value = "Welcome to VidEarn, ${user.name}!$bonusMsg"
                onResult(true, "Success")
            } else {
                onResult(false, msg)
            }
        }
    }

    /**
     * Safely triggers AdSterra Direct Link in external system browser or custom tab
     * and credits direct sponsor bonus coins to the active user.
     */
    fun triggerDirectLink(context: android.content.Context) {
        val settings = adminSettings.value
        val target = settings.adSterraDirectLink.ifBlank { "https://adsterra.com" }
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val user = currentUser.value
            if (user != null) {
                repository.claimDirectLinkBonus(user.id, 30)
                _celebration.value = RewardCelebration(
                    coins = 30,
                    title = "Direct Sponsor Explored! ⚡",
                    message = "You earned +30 Coins for exploring our verified AdSterra partner offer!"
                )
            }
        } catch (e: Exception) {
            _statusMessage.value = "Could not open direct link: ${e.message}"
        }
    }

    fun signIn(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            onResult(false, "Please enter your email and password.")
            return
        }
        repository.signInWithCloudFallback(email, pass) { success, msg, user ->
            if (success && user != null) {
                _statusMessage.value = "Welcome back, ${user.name}!"
                onResult(true, "Success")
            } else {
                onResult(false, msg)
            }
        }
    }

    fun signInGuest() {
        val guest = repository.signInGuest()
        _statusMessage.value = "Signed in as ${guest.name}"
    }

    fun signOut() {
        repository.signOut()
        _statusMessage.value = "You have been logged out."
    }

    // ---------------- VIDEO ACTIONS ----------------

    fun toggleLike(video: Video) {
        repository.toggleLike(video.id)
        if (_watchingVideo.value?.id == video.id) {
            _watchingVideo.value = _watchingVideo.value?.copy(
                isLiked = !video.isLiked,
                likes = if (!video.isLiked) video.likes + 1 else (video.likes - 1).coerceAtLeast(0)
            )
        }
    }

    fun postComment(videoId: String, content: String) {
        if (content.isBlank()) return
        val user = currentUser.value ?: return
        repository.addComment(videoId, user.name, content.trim())
        _statusMessage.value = "Comment posted!"
    }

    fun uploadVideo(
        title: String,
        description: String,
        url: String,
        type: VideoType,
        thumbnailUrl: String,
        category: String,
        onSuccess: () -> Unit
    ) {
        val user = currentUser.value ?: return
        if (title.isBlank() || url.isBlank()) {
            _statusMessage.value = "Title and Video URL are required."
            return
        }
        val reward = adminSettings.value.coinsPerUpload
        repository.addVideo(
            title = title,
            description = description,
            url = url,
            type = type,
            thumbnailUrl = thumbnailUrl,
            category = category,
            uploaderId = user.id,
            uploaderName = user.name
        )
        repository.refreshPublicVideos()
        _celebration.value = RewardCelebration(
            coins = reward,
            title = "Video Uploaded! 🎬",
            message = "Your video has been published to the feed. You earned +$reward Coins reward!"
        )
        onSuccess()
    }

    // ---------------- WITHDRAWALS ----------------

    fun requestWithdrawal(
        method: PaymentMethod,
        accountAddress: String,
        coins: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(false, "Please sign in to withdraw earnings.")
            return
        }
        if (accountAddress.isBlank()) {
            onResult(false, "Please enter your ${method.displayName} number or address.")
            return
        }
        val result = repository.submitWithdrawal(user, method, accountAddress, coins)
        result.fold(
            onSuccess = {
                _statusMessage.value = "Withdrawal request submitted! Payout: ${it.payoutAmount} ${it.currency}"
                onResult(true, "Request submitted successfully!")
            },
            onFailure = {
                onResult(false, it.message ?: "Withdrawal failed")
            }
        )
    }

    // ---------------- SEARCH & FILTER ----------------

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    // ---------------- ADMIN PANEL ----------------

    fun openAdminLogin() {
        _showAdminLoginDialog.value = true
    }

    fun closeAdminLogin() {
        _showAdminLoginDialog.value = false
    }

    fun adminLogin(user: String, pass: String): Boolean {
        // Strictly verify required credentials: Username: ruhul, Password: ruhulamin1
        if (user.trim() == "ruhul" && pass == "ruhulamin1") {
            _isAdminLoggedIn.value = true
            _showAdminLoginDialog.value = false
            _statusMessage.value = "Welcome Admin Ruhul! Admin Panel Unlocked."
            return true
        }
        return false
    }

    fun adminLogout() {
        _isAdminLoggedIn.value = false
        _statusMessage.value = "Admin logged out."
    }

    fun setAdminTab(tab: AdminTab) {
        _adminTab.value = tab
    }

    fun updateSettings(settings: AdminSettings) {
        repository.updateSettings(settings)
        _statusMessage.value = "Admin settings updated successfully."
    }

    fun approveWithdrawal(requestId: String, note: String) {
        val finalNote = if (note.isBlank()) "Approved & Dispatched by Admin" else note
        repository.updateWithdrawalStatus(requestId, WithdrawalStatus.APPROVED, finalNote)
        _statusMessage.value = "Withdrawal #$requestId Approved!"
    }

    fun rejectWithdrawal(requestId: String, reason: String) {
        val finalReason = if (reason.isBlank()) "Account details invalid or unverified" else reason
        repository.updateWithdrawalStatus(requestId, WithdrawalStatus.REJECTED, finalReason)
        _statusMessage.value = "Withdrawal rejected & coins refunded to user."
    }

    fun deleteVideo(videoId: String) {
        repository.deleteVideo(videoId)
        _statusMessage.value = "Video deleted."
    }

    fun refreshVideosFromCloud() {
        repository.refreshPublicVideos()
    }

    fun toggleUserBan(userId: String) {
        repository.toggleUserBan(userId)
        _statusMessage.value = "User status updated."
    }
}
