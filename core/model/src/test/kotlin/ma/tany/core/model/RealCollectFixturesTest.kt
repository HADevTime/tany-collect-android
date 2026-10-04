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
}
