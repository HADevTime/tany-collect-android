package ma.tany.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.ActorRole
import ma.tany.core.model.collect.AssetDetailResponse
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.BookingEventType
import ma.tany.core.model.collect.CollectAuthResponse
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.CollectRole
import ma.tany.core.model.collect.EarningsStatus
import ma.tany.core.model.collect.IncidentsResponse
import ma.tany.core.model.collect.MerchantBookingResponse
import ma.tany.core.model.collect.MerchantCustomer
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.RevenueOverview
import ma.tany.core.model.collect.SettlementOverview
import ma.tany.core.model.collect.SettlementStatus
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.DepositLedgerState
import ma.tany.core.model.common.NotificationsPage
import ma.tany.core.model.common.OtpRequestResponse
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Responses captured from the REAL tany-backend Collect API (`scripts/mobile-e2e-server.ts`, demo merchant of
 * TANY Collect Maarif, module flags ON, tokens replaced). Backend implementation is authoritative.
 */
class RealCollectFixturesTest {
    private fun real(name: String) = "real/$name.json"

    @Test
    fun authAndMe() {
        assertEquals(6, Fixtures.decode<OtpRequestResponse>(real("otp_request")).codeLength)
        val auth = Fixtures.decode<CollectAuthResponse>(real("otp_verify"))
        assertEquals(CollectRole.MERCHANT, auth.user.role)
        assertEquals("Maarif", auth.collectPoints.single().shortName)
        val me = Fixtures.decode<CollectMe>(real("me"))
        assertTrue(me.features.assets)
    }

    @Test
    fun todayActivityIncidents() {
        val today = Fixtures.decode<TodayResponse>(real("today"))
        assertTrue(today.operations.isNotEmpty())
        today.operations.forEach {
            assertTrue("phase ${it.phase}", it.phase != MerchantPhase.UNKNOWN)
            assertTrue(it.depositState != DepositLedgerState.UNKNOWN)
            assertTrue(it.depositAction != MerchantDepositAction.UNKNOWN)
        }
        assertTrue(Fixtures.decode<ActivityResponse>(real("activity")).items.isNotEmpty())
        Fixtures.decode<IncidentsResponse>(real("incidents"))
    }

    @Test
    fun merchantBookingDetail() {
        val booking = Fixtures.decode<MerchantBookingResponse>(real("booking_detail")).booking
        assertEquals(3, booking.effectiveUsagePeriod().dayCount)
        assertNotNull(booking.deposit)
        assertEquals(BookingEventType.BOOKING_CREATED, booking.history.first().type)
        assertEquals(ActorRole.CUSTOMER, booking.history.first().actorRole)
    }

    @Test
    fun deferredDepositRefund() {
        val before = Fixtures.decode<MerchantBookingResponse>(real("deposit_refund_booking")).booking
        val deposit = requireNotNull(before.deposit)
        assertEquals(ma.tany.core.model.collect.MerchantDepositAction.HAND_BACK, deposit.merchantAction)
        assertEquals(ma.tany.core.model.common.MoneyAmount.ofMajor(210), deposit.toRefundAmount)
        assertTrue(deposit.refundPickup!!.qrRequired && deposit.refundPickup!!.partial)
        val after = Fixtures.decode<MerchantBookingResponse>(real("deposit_refund_handed_back")).booking.deposit!!
        assertNotNull(after.merchantRefundConfirmedAt)
        assertEquals(ma.tany.core.model.collect.MerchantDepositAction.NONE, after.merchantAction)
    }

    @Test
    fun trustedIsNeverModeledForMerchants() {
        // The backend may send `customer.trusted` (null when the cancellation module is ON, an object when OFF);
        // the Android model deliberately has no such property, so it can never be displayed.
        assertFalse(MerchantCustomer::class.java.declaredFields.any { it.name.contains("trusted", ignoreCase = true) })
        val withTrusted = """{"shortName":"Amina B.","phoneLast4":"0001","trusted":{"score":70,"completedBookings":2,"incidents":0}}"""
        assertEquals("Amina B.", TanyJson.decodeFromString<MerchantCustomer>(withTrusted).shortName)
    }

    @Test
    fun assets() {
        val list = Fixtures.decode<AssetsResponse>(real("assets"))
        assertTrue(list.enabled && list.assets.isNotEmpty())
        Fixtures.decode<AssetDetailResponse>(real("asset_detail"))
        assertFalse(TanyJson.decodeFromString<AssetsResponse>("""{"enabled":false,"collectPointId":"cp","serverTime":"2026-10-04T09:00:00.000Z"}""").enabled)
    }

    @Test
    fun revenueAndSettlement() {
        val revenue = Fixtures.decode<RevenueOverview>(real("revenue"))
        assertEquals(EarningsStatus.ESTIMATED, revenue.earnings?.status)
        assertEquals("2026-10", revenue.period?.key)
        val settlement = Fixtures.decode<SettlementOverview>(real("settlement"))
        assertEquals(SettlementStatus.NOT_CONFIGURED, settlement.summary?.status)
        assertEquals(3, settlement.heldDeposits?.count)
        assertFalse(TanyJson.decodeFromString<RevenueOverview>("""{"enabled":false,"collectPointId":"cp"}""").enabled)
    }

    @Test
    fun revenueSubObjectsAreTyped() {
        val revenue = Fixtures.decode<RevenueOverview>(real("revenue_full"))
        val bonus = requireNotNull(revenue.bonus)
        assertEquals(3, bonus.unlockedCount)
        assertTrue(bonus.tiers.any { it.metric == ma.tany.core.model.collect.RevenueMetric.RENTAL_REVENUE && it.threshold == ma.tany.core.model.common.MoneyAmount.ofMajor(1000) })
        assertEquals(ma.tany.core.model.collect.RevenueActionKind.HAND_BACK_DEPOSIT, revenue.actions.first().kind)
        assertEquals(20.0, revenue.agreement!!.commissionRatePercent!!, 0.0)
        assertTrue(revenue.activity.isNotEmpty())
    }

    @Test
    fun settlementCollectionsAreTyped() {
        val scheduled = Fixtures.decode<SettlementOverview>(real("settlement_full"))
        assertTrue(scheduled.activeCollection!!.qrAvailable)
        assertEquals("CASH_AGENT_COLLECTION", scheduled.method!!.code)
        assertTrue(scheduled.statements.isNotEmpty())
        val awaiting = Fixtures.decode<SettlementOverview>(real("settlement_awaiting")).activeCollection!!
        assertTrue(awaiting.confirmationRequired)
        assertEquals(18620L, awaiting.agentConfirmedAmountCents)
        assertNotNull(awaiting.agentConfirmationId)
        val done = Fixtures.decode<SettlementOverview>(real("settlement_confirmed"))
        assertEquals(ma.tany.core.model.collect.CollectionStatus.PARTIALLY_COLLECTED, done.history.first().status)
        assertEquals(ma.tany.core.model.common.MoneyAmount.ofMajor(5), done.summary!!.lastPartialRemaining)
        val qr = Fixtures.decode<ma.tany.core.model.collect.SettlementQr>(real("settlement_qr"))
        assertTrue(qr.qrPayload.startsWith("TCR1."))
    }

    @Test
    fun notificationsAndAssetDetailFromScenarios() {
        val page = Fixtures.decode<NotificationsPage>(real("notifications_full"))
        assertTrue(page.notifications.all { it.collectPointId == "cp-maarif" })
        assertTrue(page.notifications.any { it.deepLink == "tanycollect://revenue/settlement" })
        Fixtures.decode<AssetDetailResponse>(real("asset_detail_full"))
    }

    @Test
    fun merchantNotificationsCarryThePoint() {
        val page = Fixtures.decode<NotificationsPage>(real("notifications"))
        assertTrue(page.notifications.all { it.collectPointId == "cp-maarif" })
    }

    @Test
    fun scanErrorsUseCanonicalCodesAndReasons() {
        val scan = TanyJson.parseToJsonElement(Fixtures.read(real("err_scan"))).jsonObject["error"] as JsonObject
        assertEquals(ApiErrorCode.QR_NOT_FOUND, ApiErrorCode.Serializer.fromWire(scan["code"]?.jsonPrimitive?.content))
        assertEquals("NOT_FOUND", scan["reason"]?.jsonPrimitive?.content)
        val asset = TanyJson.parseToJsonElement(Fixtures.read(real("err_asset"))).jsonObject["error"] as JsonObject
        assertEquals(ApiErrorCode.PICKUP_TOO_EARLY, ApiErrorCode.Serializer.fromWire(asset["code"]?.jsonPrimitive?.content))
    }

    @Test
    fun assetLifecycleKeepsTheServerFloatAndInstant() {
        // Backend `assetLifecycle`: percentage = max(usage %, age %) unrounded, replacement date = toISOString().
        val lifecycle = TanyJson.decodeFromString<ma.tany.core.model.collect.AssetLifecycle>(
            """{"status":"watch","label":"À surveiller","percentage":33.33,"estimatedReplacementDate":"2027-03-01T00:00:00.000Z","estimatedRemainingUses":12}""",
        )
        assertEquals(ma.tany.core.model.collect.AssetLifecycleStatus.WATCH, lifecycle.status)
        assertEquals(33.33, lifecycle.percentage!!, 0.0)
        assertEquals(java.time.Instant.parse("2027-03-01T00:00:00Z"), lifecycle.estimatedReplacementDate)
        // The real « not configured » capture still decodes.
        val full = Fixtures.decode<AssetDetailResponse>(real("asset_detail_full"))
        assertEquals(ma.tany.core.model.collect.AssetLifecycleStatus.NOT_CONFIGURED, full.asset.lifecycle?.status)
    }
}
