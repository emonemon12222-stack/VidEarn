package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PaymentMethod
import com.example.data.model.WithdrawalStatus
import com.example.data.repository.AppRepository
import com.example.util.YouTubeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VidEarn", appName)
  }

  @Test
  fun `test youtube url parser for various formats`() {
    // Standard watch URL
    val id1 = YouTubeHelper.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    assertEquals("dQw4w9WgXcQ", id1)

    // Shortened youtu.be URL
    val id2 = YouTubeHelper.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=testing123")
    assertEquals("dQw4w9WgXcQ", id2)

    // YouTube Shorts URL
    val id3 = YouTubeHelper.extractVideoId("https://www.youtube.com/shorts/50_4e0K9_Y8")
    assertEquals("50_4e0K9_Y8", id3)

    // Shorts with query parameters
    val id4 = YouTubeHelper.extractVideoId("https://youtube.com/shorts/k9GjF8eP210?feature=share")
    assertEquals("k9GjF8eP210", id4)

    // Embed URL
    val id5 = YouTubeHelper.extractVideoId("https://www.youtube.com/embed/jNQXAC9IVRw")
    assertEquals("jNQXAC9IVRw", id5)

    // Raw 11-char ID
    val id6 = YouTubeHelper.extractVideoId("dQw4w9WgXcQ")
    assertEquals("dQw4w9WgXcQ", id6)

    // Check thumbnail URL
    val thumb = YouTubeHelper.getThumbnailUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", thumb)

    // Check embed HTML contains video ID and iframe
    val html = YouTubeHelper.buildEmbedHtml("dQw4w9WgXcQ", false)
    assertTrue(html.contains("dQw4w9WgXcQ"))
    assertTrue(html.contains("iframe"))
  }

  @Test
  fun `test user signup and withdrawal flow`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AppRepository(context)

    // Sign up new user
    val signupResult = repo.signUp("Test User", "test@example.com", "secret123")
    assertTrue(signupResult.isSuccess)
    val user = signupResult.getOrThrow()
    assertEquals(100L, user.coins) // Welcome bonus

    // Add watch reward coins
    repo.addCoins(user.id, 500L, "Watched 10 videos")
    val updatedUser = repo.currentUser.value
    assertNotNull(updatedUser)
    assertEquals(600L, updatedUser!!.coins)

    // Submit withdrawal request via bKash
    val withdrawalResult = repo.submitWithdrawal(
      user = updatedUser,
      method = PaymentMethod.BKASH,
      accountAddress = "01700000000",
      coinAmount = 500L
    )
    assertTrue(withdrawalResult.isSuccess)
    val req = withdrawalResult.getOrThrow()
    assertEquals(WithdrawalStatus.PENDING, req.status)
    assertEquals(500L, req.coinAmount)

    // Admin approves withdrawal
    repo.updateWithdrawalStatus(req.id, WithdrawalStatus.APPROVED, "TrxID: TX123456")
    val approvedReq = repo.withdrawals.value.find { it.id == req.id }
    assertNotNull(approvedReq)
    assertEquals(WithdrawalStatus.APPROVED, approvedReq!!.status)
  }

  @Test
  fun `test referral program generation, signup reward and stats persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AppRepository(context)

    // 1. Test unique code generator based on username & ID
    val code = repo.generateReferralCode("Emon Ahmed", "uid987654")
    assertTrue(code.startsWith("EMON"))
    assertTrue(code.length >= 7)

    // 2. Sign up Referrer user
    val user1Result = repo.signUp("Referrer User", "referrer@videarn.com", "pass123")
    assertTrue(user1Result.isSuccess)
    val referrer = user1Result.getOrThrow()
    assertEquals(100L, referrer.coins)
    val referrerCode = referrer.referralCode
    assertTrue(referrerCode.isNotBlank())

    // 3. New user signs up using Referrer's code
    val user2Result = repo.signUp("Friend User", "friend@videarn.com", "pass456", referrerCode)
    assertTrue(user2Result.isSuccess)
    val friend = user2Result.getOrThrow()
    // Friend receives 100 base welcome + 50 referral bonus = 150 coins
    assertEquals(150L, friend.coins)
    assertEquals(referrerCode, friend.referredBy)

    // 4. Verify Referrer received bonus and stats incremented
    val loadedReferrer = repo.allUsers.value.find { it.id == referrer.id }
    assertNotNull(loadedReferrer)
    assertEquals(150L, loadedReferrer!!.coins) // 100 base + 50 referral bonus
    assertEquals(1, loadedReferrer.totalReferredCount)
    assertEquals(50L, loadedReferralBonusEarned(loadedReferrer))

    // 5. Test invalid referral code
    val invalidResult = repo.signUp("Invalid Test", "invalid@videarn.com", "pass789", "NONEXISTENT999")
    assertTrue(invalidResult.isFailure)

    // 6. Test Direct link bonus claim
    val directLinkClaim = repo.claimDirectLinkBonus(friend.id, 30L)
    assertTrue(directLinkClaim)
    val friendAfterBonus = repo.allUsers.value.find { it.id == friend.id }
    assertNotNull(friendAfterBonus)
    assertEquals(180L, friendAfterBonus!!.coins)
  }

  private fun loadedReferralBonusEarned(user: com.example.data.model.User): Long {
    return user.totalReferralBonusEarned
  }
}
