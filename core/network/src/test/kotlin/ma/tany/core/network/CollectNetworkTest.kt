package ma.tany.core.network

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ma.tany.core.model.collect.CollectPointBody
import ma.tany.core.model.collect.PaymentBody
import ma.tany.core.model.collect.ScanBody
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.MoneyAmount
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectNetworkTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun collectVerifyStoresTheMerchantSession() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemorySessionStore()
        val stack = TestStack(server, TestScope(testScheduler), store)
        server.enqueue(
            json(
                200,
                """{"status":"authenticated","token":"tok_m","user":{"id":"usr_m","firstName":"Ayoub","lastName":"X","phone":"+212600000002",
                |"role":"MERCHANT"},"collectPoints":[{"id":"cp-maarif","name":"TANY Collect Maarif","shortName":"Maarif","address":"a","city":"Casablanca"}]}""".trimMargin(),
            ),
        )
        val result = DefaultCollectAuthRepository(stack.api, stack.session, NoopPushTokenRegistrar).verifyOtp("0600000002", "1234")
        assertEquals("cp-maarif", result.getOrNull()?.collectPoints?.single()?.id)
        assertEquals(StoredSession("tok_m", "usr_m"), store.stored)
        assertEquals("/api/mobile/v1/collect/auth/otp/verify", server.takeRequest().path)
    }

    @Test
    fun customerNumberIsRejectedWithoutSession() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        server.enqueue(json(403, """{"error":{"code":"account_not_allowed","message":"…"}}"""))
        val error = DefaultCollectAuthRepository(stack.api, stack.session, NoopPushTokenRegistrar).verifyOtp("0600000001", "1234").errorOrNull()
        assertEquals(ApiErrorCode.ACCOUNT_NOT_ALLOWED, (error as ApiError.Http).code)
        assertNull(stack.session.currentToken())
    }

    @Test
    fun wrongPointKeepsReasonDetail() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        server.enqueue(json(403, """{"error":{"code":"wrong_collect_point","message":"…","reason":"WRONG_COLLECT_POINT"}}"""))
        val error = apiCall { stack.api.booking("bk_1", "cp-maarif") }.errorOrNull() as ApiError.Http
        assertEquals(ApiErrorCode.WRONG_COLLECT_POINT, error.code)
        assertEquals("WRONG_COLLECT_POINT", error.detailString("reason"))
        assertEquals("/api/mobile/v1/collect/bookings/bk_1?collectPointId=cp-maarif", server.takeRequest().path)
    }

    @Test
    fun paymentIsNeverReplayedAndSendsTheExactAmount() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val result = apiCall { stack.api.confirmPayment("bk_1", PaymentBody("cp-maarif", MoneyAmount.parse("448.5"))) }
        assertTrue(result.errorOrNull() is ApiError.Network)
        assertEquals(1, server.requestCount)
        assertEquals("""{"collectPointId":"cp-maarif","amountReceived":448.5}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun nudgeThrottleIsNotAnError() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        server.enqueue(json(200, """{"nudged":false,"retryAfterSeconds":42}"""))
        val response = apiCall { stack.api.nudge("bk_1", CollectPointBody("cp-maarif")) }.getOrNull()
        assertEquals(false, response?.nudged)
        assertEquals(42, response?.retryAfterSeconds)
    }

    @Test
    fun unauthorizedClearsTheMerchantSession() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemorySessionStore()
        val stack = TestStack(server, TestScope(testScheduler), store)
        stack.session.signIn(StoredSession("tok_m", "usr_m"))
        server.enqueue(json(401, """{"error":{"code":"unauthorized","message":"…"}}"""))
        assertEquals(ApiError.Unauthorized, apiCall { stack.api.me() }.errorOrNull())
        assertEquals(SessionState.SignedOut(expired = true), stack.session.state.value)
        assertEquals("Bearer tok_m", server.takeRequest().getHeader("Authorization"))
        testScheduler.advanceUntilIdle()
        assertNull(store.stored)
    }

    @Test
    fun anUnexpectedRoleNeverGetsAStoredSession() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemorySessionStore()
        val stack = TestStack(server, TestScope(testScheduler), store)
        server.enqueue(
            json(
                200,
                """{"status":"authenticated","token":"tok_x","user":{"id":"usr_x","firstName":"A","lastName":"B","phone":"+212600000009",
                |"role":"COLLECTION_AGENT"},"collectPoints":[]}""".trimMargin(),
            ),
        )
        val error = DefaultCollectAuthRepository(stack.api, stack.session, NoopPushTokenRegistrar).verifyOtp("0600000009", "123456").errorOrNull()
        assertEquals(ApiErrorCode.ACCOUNT_NOT_ALLOWED, (error as ApiError.Http).code)
        assertNull(store.stored)
        assertNull(stack.session.currentToken())
    }

    @Test
    fun depositRefundSendsTheDisplayedAmountAndMapsAmountChanged() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        server.enqueue(json(409, """{"error":{"code":"deposit_amount_changed","message":"…","currentAmount":250}}"""))
        val error = apiCall {
            stack.api.depositRefund("bk_1", ma.tany.core.model.collect.DepositRefundBody("cp-maarif", MoneyAmount.ofMajor(300)))
        }.errorOrNull() as ApiError.Http
        assertEquals(ApiErrorCode.DEPOSIT_AMOUNT_CHANGED, error.code)
        assertEquals(250, error.detailInt("currentAmount"))
        assertEquals("""{"collectPointId":"cp-maarif","expectedAmount":300}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun androidDeviceIsRegisteredOnTheCollectRoute() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        server.enqueue(json(200, """{"ok":true}"""))
        apiCall { stack.api.registerDevice(ma.tany.core.model.common.DeviceRegistrationBody("fcm:token_ABC-123_xyz_0123456789abcdef")) }
        val request = server.takeRequest()
        assertEquals("/api/mobile/v1/collect/devices", request.path)
        assertEquals("""{"token":"fcm:token_ABC-123_xyz_0123456789abcdef","platform":"android"}""", request.body.readUtf8())
    }

    @Test
    fun scanRequiresExactlyOneCode() {
        assertThrows(IllegalArgumentException::class.java) { ScanBody(collectPointId = "cp") }
        assertThrows(IllegalArgumentException::class.java) { ScanBody(collectPointId = "cp", qrPayload = "a.b", shortCode = "123456") }
        ScanBody(collectPointId = "cp", shortCode = "123456")
    }
}
