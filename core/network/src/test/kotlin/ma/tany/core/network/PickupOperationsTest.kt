package ma.tany.core.network

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ma.tany.core.model.collect.ScanBody
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.QrPurpose
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PickupOperationsTest {
    private lateinit var server: MockWebServer

    /** Real backend captures (core:model fixtures). */
    private fun fixture(name: String) = File("../model/src/test/resources/fixtures/real/$name.json").readText()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private suspend fun repo(scope: TestScope): DefaultCollectOperationsRepository {
        val stack = TestStack(server, scope)
        stack.session.signIn(StoredSession("tok_m", "usr_m"))
        return DefaultCollectOperationsRepository(stack.api)
    }

    @Test
    fun photoIsUploadedAsContractMultipartWithTheActivePoint() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(TestScope(testScheduler))
        server.enqueue(json(201, """{"photo":{"id":"ph1","url":"/api/photos/ph1/1/sig"},${fixture("booking_detail").trim().removePrefix("{").removeSuffix("}")}}"""))
        val result = repo.uploadPhoto("bk1", "cp1", QrPurpose.PICKUP, AssetCondition.GOOD, byteArrayOf(1, 2, 3))
        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("/api/mobile/v1/collect/bookings/bk1/photos", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"collectPointId\""))
        assertTrue(body.contains("cp1"))
        assertTrue(body.contains("name=\"purpose\""))
        assertTrue(body.contains("PICKUP"))
        assertTrue(body.contains("name=\"condition\""))
        assertTrue(body.contains("good"))
        assertTrue(body.contains("name=\"photo\"; filename=\"photo.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
    }

    @Test
    fun paymentSendsTheServerAmountExactlyAndHandoverItsCondition() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(TestScope(testScheduler))
        server.enqueue(json(200, fixture("booking_detail")))
        repo.confirmPayment("bk1", "cp1", MoneyAmount(34950))
        assertEquals("""{"collectPointId":"cp1","amountReceived":349.5}""", server.takeRequest().body.readUtf8())

        server.enqueue(json(200, fixture("booking_detail")))
        repo.handover("bk1", "cp1", AssetCondition.ISSUE_REPORTED)
        assertEquals("""{"collectPointId":"cp1","condition":"issue_reported"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun operationErrorsAreTypedFromRealBackendBodies() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(TestScope(testScheduler))
        server.enqueue(json(409, fixture("err_scan")))
        val scan = repo.scan(ScanBody(collectPointId = "cp1", shortCode = "123456"))
        assertEquals(CollectOperationError.Qr(ApiErrorCode.QR_NOT_FOUND), CollectOperationError.from((scan as ApiResult.Failure).error))

        server.enqueue(json(409, fixture("err_asset")))
        val early = repo.handover("bk1", "cp1", null)
        assertEquals(
            CollectOperationError.TooEarly("2026-10-05T09:00:00.000Z", "2026-10-05T11:00:00.000Z"),
            CollectOperationError.from((early as ApiResult.Failure).error),
        )

        server.enqueue(json(409, """{"error":{"code":"asset_mismatch","message":"…","expectedAssetCode":"PRC-001","scannedAssetCode":"PRC-002"}}"""))
        val mismatch = repo.verifyAsset("bk1", ma.tany.core.model.collect.AssetScanBody("cp1", QrPurpose.PICKUP, "PRC-002"))
        assertEquals(CollectOperationError.AssetMismatch("PRC-001", "PRC-002"), CollectOperationError.from((mismatch as ApiResult.Failure).error))
        assertEquals(3, server.requestCount) // never retried
    }

    @Test
    fun returnStatementCarriesConditionAccessoriesAndIncident() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(TestScope(testScheduler))
        server.enqueue(json(200, fixture("booking_detail")))
        repo.declareReturn(
            "bk1",
            ma.tany.core.model.collect.ReturnBody(
                collectPointId = "cp1",
                condition = AssetCondition.ISSUE_REPORTED,
                missingAccessories = listOf("Chargeur"),
                incident = ma.tany.core.model.collect.ReturnIncident(ma.tany.core.model.common.IncidentType.DAMAGED, "Carter fissuré"),
            ),
        )
        val request = server.takeRequest()
        assertEquals("/api/mobile/v1/collect/bookings/bk1/return", request.path)
        assertEquals(
            """{"collectPointId":"cp1","condition":"issue_reported","missingAccessories":["Chargeur"],"incident":{"type":"DAMAGED","description":"Carter fissuré"}}""",
            request.body.readUtf8(),
        )

        server.enqueue(json(200, fixture("booking_detail")))
        repo.declareReturn("bk1", ma.tany.core.model.collect.ReturnBody(collectPointId = "cp1", condition = AssetCondition.GOOD))
        // missingAccessories is always sent (contract), even empty.
        assertEquals("""{"collectPointId":"cp1","condition":"good","missingAccessories":[]}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun depositRefundSendsTheShownAmountAndTypesRefusals() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(TestScope(testScheduler))
        server.enqueue(json(409, fixture("err_deposit_amount_changed")))
        val changed = repo.depositRefund("bk1", "cp1", MoneyAmount.ofMajor(1))
        val request = server.takeRequest()
        assertEquals("/api/mobile/v1/collect/bookings/bk1/deposit-refund", request.path)
        assertEquals("""{"collectPointId":"cp1","expectedAmount":1}""", request.body.readUtf8())
        assertEquals(
            CollectOperationError.DepositAmountChanged(MoneyAmount.ofMajor(210)),
            CollectOperationError.from((changed as ApiResult.Failure).error),
        )

        server.enqueue(json(409, fixture("err_deposit_refund_qr_required")))
        val qr = repo.depositRefund("bk1", "cp1", MoneyAmount.ofMajor(210))
        assertEquals(CollectOperationError.DepositRefundQrRequired, CollectOperationError.from((qr as ApiResult.Failure).error))

        server.enqueue(json(200, fixture("deposit_refund_handed_back")))
        assertTrue(repo.depositRefund("bk1", "cp1", MoneyAmount.ofMajor(210)) is ApiResult.Success)
    }
}
