package ma.tany.collect

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ma.tany.collect.core.media.OperationImageMath
import ma.tany.collect.core.media.OperationPhotos
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.feature.booking.BookingDetailViewModel
import ma.tany.collect.feature.operations.OperationScanViewModel
import ma.tany.collect.feature.scanner.ScanInput
import ma.tany.collect.feature.scanner.ScanTarget
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetScanBody
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.KitCheckState
import ma.tany.core.model.collect.KitChecks
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantBookingResponse
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.collect.ReturnBody
import ma.tany.core.model.collect.ReturnIncident
import ma.tany.core.model.common.IncidentType
import ma.tany.core.model.collect.ScanBody
import ma.tany.core.model.collect.ScanResponse
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.QrPurpose
import ma.tany.core.model.common.TanyJson
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiEnvironment
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectOperationError
import ma.tany.core.network.CollectOperationsRepository
import ma.tany.core.network.CollectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PickupFlowTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    /** Real backend capture (core:model fixtures). */
    private val booking: MerchantBookingDetail = TanyJson.decodeFromString<MerchantBookingResponse>(
        File("../core/model/src/test/resources/fixtures/real/booking_detail.json").readText(),
    ).booking

    private val endpoint = ApiEndpoint.of(ApiEnvironment.STAGING, "https://staging.tany.ma")

    private class FakeCollect(var detail: ApiResult<MerchantBookingDetail>) : CollectRepository {
        val reads = mutableListOf<String>()

        override suspend fun me(): ApiResult<CollectMe> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun today(pointId: String): ApiResult<TodayResponse> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun activity(pointId: String, query: String?): ApiResult<ActivityResponse> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun booking(bookingId: String, pointId: String): ApiResult<MerchantBookingDetail> {
            reads += pointId
            return detail
        }

        override suspend fun assets(pointId: String, query: String?, filter: ma.tany.core.model.collect.AssetFilter?): ApiResult<AssetsResponse> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun asset(pointId: String, assetId: String): ApiResult<ma.tany.core.model.collect.AssetDetail> =
            ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun assetLookup(pointId: String, code: String): ApiResult<ma.tany.core.model.collect.AssetDetail> =
            ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun incidents(pointId: String): ApiResult<ma.tany.core.model.collect.IncidentsResponse> =
            ApiResult.Failure(ApiError.Unauthorized)
    }

    private class FakeOps : CollectOperationsRepository {
        val calls = mutableListOf<String>()
        var result: ApiResult<MerchantBookingDetail> = ApiResult.Failure(ApiError.Unauthorized)
        var scanResult: ApiResult<ScanResponse> = ApiResult.Failure(ApiError.Unauthorized)
        var lastScan: ScanBody? = null
        var lastAsset: AssetScanBody? = null
        var lastAmount: MoneyAmount? = null
        var lastPhoto: Triple<String, AssetCondition, ByteArray>? = null
        var lastPhotoPurpose: QrPurpose? = null
        var lastReturn: ReturnBody? = null
        var lastRefund: MoneyAmount? = null

        override suspend fun scan(body: ScanBody): ApiResult<ScanResponse> {
            calls += "scan"; lastScan = body
            return scanResult
        }

        override suspend fun verifyAsset(bookingId: String, body: AssetScanBody): ApiResult<MerchantBookingDetail> {
            calls += "asset"; lastAsset = body
            return result
        }

        override suspend fun uploadPhoto(bookingId: String, collectPointId: String, purpose: QrPurpose, condition: AssetCondition, jpeg: ByteArray): ApiResult<MerchantBookingDetail> {
            calls += "photo"; lastPhoto = Triple(collectPointId, condition, jpeg); lastPhotoPurpose = purpose
            return result
        }

        override suspend fun confirmPayment(bookingId: String, collectPointId: String, amount: MoneyAmount): ApiResult<MerchantBookingDetail> {
            calls += "payment"; lastAmount = amount
            return result
        }

        override suspend fun handover(bookingId: String, collectPointId: String, condition: AssetCondition?, kit: KitChecks?): ApiResult<MerchantBookingDetail> {
            calls += "handover"; lastHandoverCondition = condition; lastHandoverKit = kit
            return result
        }

        override suspend fun declareReturn(bookingId: String, body: ReturnBody): ApiResult<MerchantBookingDetail> {
            calls += "return"; lastReturn = body
            return result
        }

        override suspend fun depositRefund(bookingId: String, collectPointId: String, expectedAmount: MoneyAmount): ApiResult<MerchantBookingDetail> {
            calls += "deposit"; lastRefund = expectedAmount
            return result
        }

        var lastHandoverCondition: AssetCondition? = null
        var lastHandoverKit: KitChecks? = null
        var lastIncident: ma.tany.core.model.collect.IncidentBody? = null
        var nudgeResult: ApiResult<ma.tany.core.model.collect.NudgeResponse> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun reportIncident(bookingId: String, body: ma.tany.core.model.collect.IncidentBody): ApiResult<MerchantBookingDetail> {
            calls += "incident"; lastIncident = body
            return result
        }

        override suspend fun nudge(bookingId: String, collectPointId: String): ApiResult<ma.tany.core.model.collect.NudgeResponse> {
            calls += "nudge"
            return nudgeResult
        }
    }

    private inner class FakePhotos(var failEncode: Boolean = false) : OperationPhotos {
        val discarded = mutableListOf<File>()

        override fun newCaptureFile(): File = tmp.newFile()

        override fun uriFor(file: File): Uri = error("not used in JVM tests")

        override suspend fun encode(file: File): ByteArray {
            if (failEncode) throw IOException("not an image")
            return file.readBytes()
        }

        override fun discard(file: File) {
            discarded += file
            file.delete()
        }
    }

    private fun detailVm(collect: FakeCollect, ops: FakeOps, photos: FakePhotos = FakePhotos()) =
        BookingDetailViewModel(collect, ops, photos, endpoint, SavedStateHandle(mapOf("bookingId" to booking.id))).apply { load("cp1") }

    @Test
    fun photoIsUploadedWithTheDeclaredConditionThenDeleted() = runTest {
        val ops = FakeOps().apply { result = ApiResult.Success(booking) }
        val photos = FakePhotos()
        val vm = detailVm(FakeCollect(ApiResult.Success(booking)), ops, photos)
        vm.onPhotoCondition(AssetCondition.ISSUE_REPORTED)
        val file = vm.preparePhoto().apply { writeBytes(byteArrayOf(7)) }
        vm.onPhotoCaptured("cp1", success = true)
        assertEquals(listOf("photo"), ops.calls)
        assertEquals("cp1", ops.lastPhoto!!.first)
        assertEquals(AssetCondition.ISSUE_REPORTED, ops.lastPhoto!!.second)
        assertTrue(file in photos.discarded) // never kept on the device
        assertNull(vm.pickup.value.busy)
    }

    @Test
    fun cancelledOrUnreadablePhotoSendsNothing() = runTest {
        val ops = FakeOps()
        val photos = FakePhotos(failEncode = true)
        val vm = detailVm(FakeCollect(ApiResult.Success(booking)), ops, photos)
        vm.preparePhoto()
        vm.onPhotoCaptured("cp1", success = false)
        assertTrue(ops.calls.isEmpty())

        vm.preparePhoto().writeBytes(byteArrayOf(1))
        vm.onPhotoCaptured("cp1", success = true)
        assertTrue(ops.calls.isEmpty())
        assertEquals(CollectOperationError.Photo, vm.pickup.value.error)
    }

    @Test
    fun paymentSendsTheServerAmountAndFailuresRereadWithoutRetry() = runTest {
        val collect = FakeCollect(ApiResult.Success(booking))
        val ops = FakeOps().apply { result = ApiResult.Failure(ApiError.Http(409, ApiErrorCode.INVALID_STATE, "invalid_state", "…")) }
        val vm = detailVm(collect, ops)
        var done = 0
        vm.confirmPayment("cp1", MoneyAmount(34950)) { done++ }
        assertEquals(listOf("payment"), ops.calls)
        assertEquals(MoneyAmount(34950), ops.lastAmount)
        assertEquals(1, done)
        assertEquals(CollectOperationError.NotAllowedNow, vm.pickup.value.error)
        assertEquals(2, collect.reads.size) // initial load + re-read after the refusal
    }

    @Test
    fun handoverAppliesTheServerBooking() = runTest {
        val handedOver = booking.copy(phase = ma.tany.core.model.collect.MerchantPhase.PICKUP_AWAITING_CUSTOMER)
        val ops = FakeOps().apply { result = ApiResult.Success(handedOver) }
        val vm = detailVm(FakeCollect(ApiResult.Success(booking)), ops)
        vm.handover("cp1") {}
        assertEquals(handedOver, (vm.state.value as LoadState.Loaded).value)
    }

    @Test
    fun scannerTabResolvesTheBookingAndLabelScanTargetsTheBooking() = runTest {
        val ops = FakeOps().apply {
            scanResult = ApiResult.Success(ScanResponse(QrPurpose.PICKUP, booking))
            result = ApiResult.Success(booking)
        }
        val tab = OperationScanViewModel(ops, SavedStateHandle())
        tab.submit("cp1", ScanInput.ShortCode("123456"))
        assertEquals(booking.id, tab.done.first())
        assertEquals(ScanBody(collectPointId = "cp1", shortCode = "123456"), ops.lastScan)

        val label = OperationScanViewModel(
            ops,
            SavedStateHandle(mapOf("bookingId" to "bk1", "target" to ScanTarget.ASSET_LABEL.name, "purpose" to "PICKUP")),
        )
        label.submit("cp1", ScanInput.Qr(" PRC-001 "))
        assertEquals(AssetScanBody("cp1", QrPurpose.PICKUP, "PRC-001"), ops.lastAsset)
    }

    @Test
    fun qrRefusalIsTypedAndShown() = runTest {
        val ops = FakeOps().apply {
            scanResult = ApiResult.Failure(ApiError.Http(409, ApiErrorCode.QR_EXPIRED, "qr_expired", "…"))
        }
        val vm = OperationScanViewModel(ops, SavedStateHandle(mapOf("bookingId" to "bk1", "target" to "BOOKING_QR", "purpose" to "PICKUP")))
        vm.submit("cp1", ScanInput.Qr("tok.sig"))
        assertEquals(CollectOperationError.Qr(ApiErrorCode.QR_EXPIRED), vm.state.value.error)
        assertEquals("bk1", ops.lastScan!!.bookingId)
        assertEquals(QrPurpose.PICKUP, ops.lastScan!!.purpose)
    }

    @Test
    fun photoSizingRules() {
        assertEquals(2, OperationImageMath.inSampleSize(4096, 3072, 2048))
        assertEquals(2048 to 1536, OperationImageMath.fit(4000, 3000, 2048))
        assertEquals(90, OperationImageMath.rotationDegrees(6))
    }

    @Test
    fun returnPhotoUsesTheReturnPurpose() = runTest {
        val returning = booking.copy(kind = OperationKind.RETURN)
        val ops = FakeOps().apply { result = ApiResult.Success(returning) }
        val vm = detailVm(FakeCollect(ApiResult.Success(returning)), ops)
        vm.preparePhoto().writeBytes(byteArrayOf(1))
        vm.onPhotoCaptured("cp1", success = true)
        assertEquals(QrPurpose.RETURN, ops.lastPhotoPurpose)
    }

    @Test
    fun returnStatementSendsMissingAccessoriesAndIncidentOnce() = runTest {
        val returning = booking.copy(kind = OperationKind.RETURN)
        val ops = FakeOps().apply { result = ApiResult.Success(returning) }
        val vm = detailVm(FakeCollect(ApiResult.Success(returning)), ops)
        vm.updateReturnForm { it.copy(missingAccessories = setOf("Chargeur")) }
        vm.updateReturnForm { it.copy(incidentType = IncidentType.DAMAGED, incidentDescription = "  Carter fissuré  ") }
        var done = 0
        vm.declareReturn("cp1") { done++ }
        assertEquals(listOf("return"), ops.calls)
        assertEquals(
            ReturnBody(
                collectPointId = "cp1",
                // A missing accessory or an incident is an issue: never declared "good".
                condition = AssetCondition.ISSUE_REPORTED,
                missingAccessories = listOf("Chargeur"),
                incident = ReturnIncident(IncidentType.DAMAGED, "Carter fissuré"),
            ),
            ops.lastReturn,
        )
        assertEquals(1, done)
        assertTrue(vm.pickup.value.returnForm.missingAccessories.isEmpty()) // reset after success

        vm.declareReturn("cp1") {}
        assertEquals(
            ReturnBody(collectPointId = "cp1", condition = AssetCondition.GOOD, missingAccessories = emptyList(), incident = null),
            ops.lastReturn,
        )
    }

    @Test
    fun depositHandBackSendsTheShownAmountAndAChangedAmountIsRereadNeverResent() = runTest {
        val collect = FakeCollect(ApiResult.Success(booking))
        val ops = FakeOps().apply {
            result = ApiResult.Failure(
                ApiError.Http(
                    409, ApiErrorCode.DEPOSIT_AMOUNT_CHANGED, "deposit_amount_changed", "…",
                    kotlinx.serialization.json.buildJsonObject { put("currentAmount", kotlinx.serialization.json.JsonPrimitive(210)) },
                ),
            )
        }
        val vm = detailVm(collect, ops)
        var done = 0
        vm.handBackDeposit("cp1", MoneyAmount.ofMajor(300)) { done++ }
        assertEquals(listOf("deposit"), ops.calls) // sent once, never retried
        assertEquals(MoneyAmount.ofMajor(300), ops.lastRefund)
        assertEquals(1, done)
        assertEquals(CollectOperationError.DepositAmountChanged(MoneyAmount.ofMajor(210)), vm.pickup.value.error)
        assertEquals(ma.tany.collect.feature.booking.PickupGesture.DEPOSIT_REFUND, vm.pickup.value.failed)
        assertEquals(2, collect.reads.size) // re-read: the merchant sees the new amount before any cash moves
    }

    @Test
    fun handoverSendsTheConditionRecordedWithThePickupPhotos() = runTest {
        val withIssue = booking.copy(pickup = booking.pickup!!.copy(condition = AssetCondition.ISSUE_REPORTED, photoCount = 1))
        val ops = FakeOps().apply { result = ApiResult.Success(withIssue) }
        val vm = detailVm(FakeCollect(ApiResult.Success(withIssue)), ops)
        vm.handover("cp1") {}
        assertEquals(AssetCondition.ISSUE_REPORTED, ops.lastHandoverCondition)

        // No condition recorded by the server ⇒ none invented.
        val bare = FakeOps().apply { result = ApiResult.Success(booking) }
        detailVm(FakeCollect(ApiResult.Success(booking)), bare).handover("cp1") {}
        assertEquals(listOf("handover"), bare.calls)
        assertNull(bare.lastHandoverCondition)
    }

    @Test
    fun nudgeIsOneRequestPerTapAndHonoursTheServerDelay() = runTest {
        val ops = FakeOps().apply {
            nudgeResult = ApiResult.Success(ma.tany.core.model.collect.NudgeResponse(nudged = true, retryAfterSeconds = 60))
        }
        val vm = detailVm(FakeCollect(ApiResult.Success(booking)), ops)
        var clock = 1_000L
        vm.nudge("cp1") { clock }
        assertEquals(ma.tany.collect.feature.booking.NudgeOutcome.SENT, vm.pickup.value.nudgeOutcome)
        assertEquals(61_000L, vm.pickup.value.nudgeAvailableAt)
        clock = 30_000L
        vm.nudge("cp1") { clock } // still inside the server delay: nothing sent
        assertEquals(listOf("nudge"), ops.calls)

        clock = 61_000L
        ops.nudgeResult = ApiResult.Success(ma.tany.core.model.collect.NudgeResponse(nudged = false, retryAfterSeconds = 20))
        vm.nudge("cp1") { clock }
        assertEquals(listOf("nudge", "nudge"), ops.calls)
        assertEquals(ma.tany.collect.feature.booking.NudgeOutcome.ALREADY_SENT, vm.pickup.value.nudgeOutcome)
    }

    @Test
    fun incidentIsReportedWithThePointAndATrimmedDescription() = runTest {
        val ops = FakeOps().apply { result = ApiResult.Success(booking) }
        val vm = detailVm(FakeCollect(ApiResult.Success(booking)), ops)
        var done = 0
        vm.reportIncident("cp1", IncidentType.DAMAGED, "   ", onDone = { done++ })
        assertEquals(ma.tany.core.model.collect.IncidentBody("cp1", IncidentType.DAMAGED, null), ops.lastIncident)
        assertTrue(vm.pickup.value.incidentReported)
        assertEquals(1, done)
    }

    @Test
    fun successCardOnlyWhenTheServerRecordsTheCustomerConfirmation() = runTest {
        val collect = FakeCollect(ApiResult.Success(booking))
        val vm = detailVm(collect, FakeOps())
        vm.poll("cp1")
        assertNull(vm.pickup.value.completed) // same server state: nothing to celebrate

        val confirmed = booking.copy(pickup = booking.pickup!!.copy(customerConfirmedAt = java.time.Instant.parse("2026-10-05T09:30:00Z")))
        collect.detail = ApiResult.Success(confirmed)
        vm.poll("cp1")
        assertEquals(ma.tany.collect.feature.booking.CompletedStep.PICKUP, vm.pickup.value.completed)
        vm.dismissCompleted()
        assertNull(vm.pickup.value.completed)

        // A failed poll keeps the booking on screen.
        collect.detail = ApiResult.Failure(ApiError.Network(IOException("offline")))
        vm.poll("cp1")
        assertEquals(confirmed, (vm.state.value as LoadState.Loaded).value)
    }

    @Test
    fun photoReviewIsAUiAcknowledgementResetByEachNewPhoto() = runTest {
        val ops = FakeOps().apply { result = ApiResult.Success(booking) }
        val vm = detailVm(FakeCollect(ApiResult.Success(booking)), ops)
        vm.acceptPhotos()
        assertTrue(vm.pickup.value.photosAccepted)
        assertTrue(ops.calls.isEmpty()) // no server call
        vm.preparePhoto().writeBytes(byteArrayOf(3))
        vm.onPhotoCaptured("cp1", success = true)
        assertEquals(listOf("photo"), ops.calls)
        org.junit.Assert.assertFalse(vm.pickup.value.photosAccepted) // the new photo is reviewed again
    }

    @Test
    fun returnStatementSendsTheConditionRecordedWithTheReturnPhotos() = runTest {
        val returning = booking.copy(
            kind = OperationKind.RETURN,
            returnInfo = booking.returnInfo!!.copy(condition = AssetCondition.ISSUE_REPORTED, photoCount = 1),
        )
        val ops = FakeOps().apply { result = ApiResult.Success(returning) }
        val vm = detailVm(FakeCollect(ApiResult.Success(returning)), ops)
        vm.declareReturn("cp1") {}
        assertEquals(AssetCondition.ISSUE_REPORTED, ops.lastReturn?.condition)
    }

    // ——— Rental kit V1: what physically goes out and comes back (differences only, never blocking) ———

    private fun kitBooking(name: String): MerchantBookingDetail = TanyJson.decodeFromString<MerchantBookingResponse>(
        File("../core/model/src/test/resources/fixtures/real/$name.json").readText(),
    ).booking

    @Test
    fun handoverSendsTheKitDifferencesAndResetsTheChecklist() = runTest {
        val pickup = kitBooking("booking_detail_kit_pickup")
        val pouch = pickup.kit!!.items[1].id
        val ops = FakeOps().apply { result = ApiResult.Success(pickup) }
        val vm = detailVm(FakeCollect(ApiResult.Success(pickup)), ops)
        vm.toggleHandoverKitItem(pouch)
        vm.toggleHandoverKitItem("x")
        vm.toggleHandoverKitItem("x") // second tap restores it
        assertEquals(setOf(pouch), vm.pickup.value.kitNotHandedOver)
        vm.handover("cp1") {}
        assertEquals(listOf(pouch), ops.lastHandoverKit!!.checks.map { it.itemId })
        assertEquals(KitCheckState.MISSING, ops.lastHandoverKit!!.checks.single().state)
        assertTrue(vm.pickup.value.kitNotHandedOver.isEmpty())

        // Everything handed over ⇒ `checks: []`; a booking without kit keeps the historical body.
        vm.handover("cp1") {}
        assertTrue(ops.lastHandoverKit!!.checks.isEmpty())
        val bare = FakeOps().apply { result = ApiResult.Success(booking) }
        detailVm(FakeCollect(ApiResult.Success(booking)), bare).handover("cp1") {}
        assertNull(bare.lastHandoverKit)
    }

    @Test
    fun returnStatementSendsTheKitInsteadOfAccessoryNames() = runTest {
        val returning = kitBooking("booking_detail_kit_return")
        val kit = returning.kit!!
        val notHanded = kit.items.single { !it.handedOver }.id
        val bag = kit.items.last().id
        val ops = FakeOps().apply { result = ApiResult.Success(returning) }
        val vm = detailVm(FakeCollect(ApiResult.Success(returning)), ops)
        vm.updateReturnForm { it.copy(kitIssues = mapOf(bag to KitCheckState.DAMAGED, notHanded to KitCheckState.MISSING), missingAccessories = setOf("Câbles")) }
        vm.declareReturn("cp1") {}
        val body = ops.lastReturn!!
        assertEquals(AssetCondition.ISSUE_REPORTED, body.condition) // a kit difference is never declared « good »
        assertTrue(body.missingAccessories.isEmpty())
        assertEquals(listOf(bag), body.kit!!.checks.map { it.itemId }) // never an element that was not handed over
        assertEquals(KitCheckState.DAMAGED, body.kit!!.checks.single().state)
        assertTrue(vm.pickup.value.returnForm.kitIssues.isEmpty()) // reset after success

        vm.declareReturn("cp1") {}
        assertTrue(ops.lastReturn!!.kit!!.checks.isEmpty()) // « Tout est présent »
    }
}
