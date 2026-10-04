package ma.tany.collect

import kotlinx.coroutines.test.runTest
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.feature.scanner.ScanDebouncer
import ma.tany.collect.feature.scanner.ScanInput
import ma.tany.collect.feature.scanner.ScanInputs
import ma.tany.collect.feature.today.TodayViewModel
import ma.tany.collect.feature.today.label
import ma.tany.collect.feature.today.ui
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.CollectAssetStatus
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.QrPurpose
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiEnvironment
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeCollectRepository(var today: ApiResult<TodayResponse>) : CollectRepository {
    val requestedPoints = mutableListOf<String>()

    override suspend fun me(): ApiResult<CollectMe> = ApiResult.Failure(ApiError.Unauthorized)

    override suspend fun today(pointId: String): ApiResult<TodayResponse> {
        requestedPoints += pointId
        return today
    }

    override suspend fun activity(pointId: String, query: String?): ApiResult<ActivityResponse> = ApiResult.Success(ActivityResponse())

    override suspend fun booking(bookingId: String, pointId: String): ApiResult<MerchantBookingDetail> = ApiResult.Failure(ApiError.Unauthorized)

    override suspend fun assets(pointId: String): ApiResult<AssetsResponse> = ApiResult.Success(AssetsResponse(enabled = false))
}

class CollectAppTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val endpoint = ApiEndpoint.of(ApiEnvironment.STAGING, "https://staging.tany.ma")

    @Test
    fun todayIsAlwaysLoadedForTheActivePoint() = runTest {
        val wrongPoint = ApiError.Http(403, ApiErrorCode.WRONG_COLLECT_POINT, "wrong_collect_point", "…")
        val repo = FakeCollectRepository(ApiResult.Failure(wrongPoint))
        val vm = TodayViewModel(repo, endpoint)
        vm.load("cp-maarif")
        assertEquals(LoadState.Failed(wrongPoint), vm.state.value)
        vm.load("cp-gauthier")
        assertEquals(listOf("cp-maarif", "cp-gauthier"), repo.requestedPoints)
    }

    @Test
    fun shortCodeFallbackAcceptsExactlySixDigits() {
        assertEquals(ScanInput.ShortCode("482913"), ScanInputs.shortCodeOrNull("482 913"))
        assertNull(ScanInputs.shortCodeOrNull("48291"))
        assertNull(ScanInputs.shortCodeOrNull("4829131"))
    }

    @Test
    fun qrPayloadIsOpaqueAndBounded() {
        assertEquals(ScanInput.Qr("tok_1.abc"), ScanInputs.fromCamera("  tok_1.abc \n"))
        assertNull(ScanInputs.fromCamera(""))
        assertNull(ScanInputs.fromCamera("x".repeat(513)))
        val body = ScanInputs.toScanBody("cp-maarif", ScanInput.ShortCode("482913"), bookingId = "bk_1", purpose = QrPurpose.PICKUP)
        assertEquals("482913", body.shortCode)
        assertNull(body.qrPayload)
        assertEquals("PSF001", ScanInputs.toAssetBody("cp-maarif", QrPurpose.RETURN, " PSF001 ")?.assetCode)
        assertNull(ScanInputs.toAssetBody("cp-maarif", QrPurpose.RETURN, "x".repeat(65)))
    }

    @Test
    fun cameraDetectionsAreDebounced() {
        var now = 0L
        val debouncer = ScanDebouncer(windowMs = 2_500) { now }
        assertTrue(debouncer.accept("a"))
        assertFalse(debouncer.accept("a"))
        assertTrue(debouncer.accept("b"))
        now = 10_000
        assertTrue(debouncer.accept("b"))
    }

    @Test
    fun everyServerPhaseAndAssetStatusHasAPresentation() {
        MerchantPhase.entries.forEach { it.ui() }
        CollectAssetStatus.entries.forEach { it.label() }
        val labels = MerchantPhase.entries.filter { it != MerchantPhase.UNKNOWN }.map { it.ui().label }
        // completed/cancelled share wording with other phases only if intended; every phase has its own key.
        assertEquals(labels.size, labels.toSet().size)
    }
}
