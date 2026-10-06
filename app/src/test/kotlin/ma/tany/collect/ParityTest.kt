package ma.tany.collect

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ma.tany.collect.feature.account.PRIVACY_PATH
import ma.tany.collect.feature.account.SUPPORT_PATH
import ma.tany.collect.feature.account.webUrl
import ma.tany.collect.feature.auth.remaining
import ma.tany.collect.feature.auth.retryAfterSeconds
import ma.tany.collect.feature.notifications.NotificationBucket
import ma.tany.collect.feature.notifications.bucketNotifications
import ma.tany.collect.feature.revenue.REVENUE_ACTIVITY_ROWS
import ma.tany.collect.feature.revenue.signPrefix
import ma.tany.collect.feature.scanner.ScanGate
import ma.tany.core.model.collect.RevenueAmountKind
import ma.tany.core.model.collect.RevenueOverview
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.NotificationsPage
import ma.tany.core.model.common.TanyJson
import ma.tany.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** iOS-parity presentation rules: they only format server data, never decide a business outcome. */
class ParityTest {
    private fun fixture(name: String) = File("../core/model/src/test/resources/fixtures/real/$name.json").readText()

    @Test
    fun scanGateAcceptsOneCodePerArmingAndIgnoresTheSameCodeRightAfterRearming() {
        var now = 0L
        val gate = ScanGate(cooldownMs = 1_500) { now }
        assertTrue(gate.accept("A"))
        assertFalse(gate.accept("B")) // one code per arming: the camera waits for the result
        gate.setActive(false)
        assertFalse(gate.accept("B")) // paused (refusal card / sheet shown)
        gate.setActive(true)
        assertFalse(gate.accept("A")) // the same QR still in front of the lens is not re-sent at once
        assertTrue(gate.accept("B"))

        gate.setActive(false)
        gate.setActive(true)
        now += 1_600
        assertTrue(gate.accept("B")) // after the cooldown the same code may be scanned again (« Scanner à nouveau »)
    }

    @Test
    fun notificationsAreBucketedOnTheBusinessDayInServerOrder() {
        val page: NotificationsPage = TanyJson.decodeFromString(fixture("notifications_full"))
        val items = page.notifications
        assertTrue(items.isNotEmpty())
        // Captured 2026-10-04 ~21:24 Africa/Casablanca.
        assertEquals(listOf(NotificationBucket.TODAY), bucketNotifications(items, LocalDate.of(2026, 10, 4)).map { it.first })
        assertEquals(listOf(NotificationBucket.YESTERDAY), bucketNotifications(items, LocalDate.of(2026, 10, 5)).map { it.first })
        assertEquals(listOf(NotificationBucket.OLDER), bucketNotifications(items, LocalDate.of(2026, 10, 7)).map { it.first })

        // 23:30 UTC on Oct 4 is already Oct 5 in Casablanca (UTC+1): the business day, never the device zone.
        val lateEvening = items.first().copy(createdAt = Instant.parse("2026-10-04T23:30:00Z"))
        val groups = bucketNotifications(listOf(lateEvening) + items, LocalDate.of(2026, 10, 5))
        assertEquals(listOf(NotificationBucket.TODAY, NotificationBucket.YESTERDAY), groups.map { it.first })
        assertEquals(items.size + 1, groups.sumOf { it.second.size })
    }

    @Test
    fun revenueActivitySignsEarningsAndKeepsDepositsNeutral() {
        val revenue: RevenueOverview = TanyJson.decodeFromString(fixture("revenue_full"))
        val activity = revenue.activity
        assertTrue(activity.any { it.amountKind == RevenueAmountKind.DEPOSIT })
        activity.forEach {
            when (it.amountKind) {
                RevenueAmountKind.EARNING -> assertEquals("+ ", it.signPrefix())
                else -> assertEquals("", it.signPrefix()) // a deposit is never revenue: no « + »
            }
        }
        val refund = activity.first().copy(amount = MoneyAmount(-1500), amountKind = RevenueAmountKind.EARNING)
        assertEquals("− ", refund.signPrefix())
        assertEquals(8, REVENUE_ACTIVITY_ROWS)
    }

    @Test
    fun otpLockAndResendDelaysComeFromTheServer() {
        val tooMany = ApiError.Http(
            429, ApiErrorCode.OTP_TOO_MANY_ATTEMPTS, "otp_too_many_attempts", "…",
            buildJsonObject { put("retryAfterSeconds", JsonPrimitive(42)) },
        )
        assertEquals(42, tooMany.retryAfterSeconds())
        assertNull(ApiError.Http(400, ApiErrorCode.OTP_INVALID, "otp_invalid", "…").retryAfterSeconds())
        assertNull(ApiError.Network(java.io.IOException()).retryAfterSeconds())
        // Countdown seconds are rounded up and never negative.
        assertEquals(0, remaining(until = 1_000, now = 5_000))
        assertEquals(1, remaining(until = 5_001, now = 5_000))
        assertEquals(30, remaining(until = 35_000, now = 5_000))
    }

    @Test
    fun supportAndPrivacyPagesStayOnTheBuildEnvironment() {
        val base = ma.tany.collect.core.AppEnvironment.endpoint.baseUrl.trimEnd('/')
        assertEquals("$base/support", webUrl(SUPPORT_PATH))
        assertEquals("$base/confidentialite", webUrl(PRIVACY_PATH))
    }
}
