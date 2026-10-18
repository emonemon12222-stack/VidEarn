package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PaymentMethod
import com.example.data.model.WithdrawalStatus
import com.example.data.repository.AppRepository
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
}
