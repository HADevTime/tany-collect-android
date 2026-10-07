package ma.tany.collect

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ma.tany.collect.core.push.CollectPushRouting
import ma.tany.collect.core.push.PushChannel
import ma.tany.collect.core.push.PushEvents
import ma.tany.core.model.common.NotificationType
import ma.tany.core.model.common.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Push tap routing parity with TANY Collect iOS, plus the active-point guard of the merchant app. */
class PushRoutingTest {
    private fun payload(link: String?, point: String? = "cp1", category: String = "BOOKING", id: String = "ntf_1") = PushPayload.fromData(
        mapOf("deeplink" to link, "notificationId" to id, "type" to "MERCHANT_NEW_BOOKING", "badge" to "1", "category" to category, "collectPointId" to point),
    )

    @Test
    fun everyBackendMerchantLinkOpensItsScreenOnTheActivePoint() {
        listOf(
            "tanycollect://booking/bkg_1", "tanycollect://return/bkg_1", "tanycollect://revenue", "tanycollect://revenue/settlement",
            "tanycollect://today", "tanycollect://scan", "tanycollect://activity", "tanycollect://notifications",
        ).forEach { assertEquals(it, CollectPushRouting.destination(payload(it), "cp1")) }
    }

    @Test
    fun unknownOrClientLinksFallBackToTheCentre() {
        listOf(null, "", "tany://booking/bkg_1", "tanycollect://admin", "https://evil.example").forEach {
            assertEquals(CollectPushRouting.NOTIFICATION_CENTRE, CollectPushRouting.destination(payload(it), "cp1"))
        }
    }

    @Test
    fun aNotificationOfAnotherPointNeverOpensItsOperationFromTheActivePoint() {
        assertNull(CollectPushRouting.destination(payload("tanycollect://booking/bkg_1", point = "cp2"), "cp1"))
        // Read on ITS point (the backend scopes read state per point); older payloads without the point ⇒ active one.
        assertEquals("cp2", CollectPushRouting.readPoint(payload("tanycollect://booking/bkg_1", point = "cp2"), "cp1"))
        assertEquals("cp1", CollectPushRouting.readPoint(payload("tanycollect://booking/bkg_1", point = null), "cp1"))
        assertEquals("tanycollect://booking/bkg_1", CollectPushRouting.destination(payload("tanycollect://booking/bkg_1", point = null), "cp1"))
    }

    @Test
    fun payloadToleratesUnknownTypes() {
        assertEquals(NotificationType.UNKNOWN, PushPayload.fromData(mapOf("type" to "MERCHANT_SOMETHING_NEW")).type)
        assertEquals(NotificationType.MERCHANT_DEPOSIT_TO_HAND_BACK, PushPayload.fromData(mapOf("type" to "MERCHANT_DEPOSIT_TO_HAND_BACK")).type)
    }

    @Test
    fun foregroundChannelsMirrorTheBackendSemanticChannels() {
        assertEquals(PushChannel.OPERATIONS, CollectPushRouting.channelFor(payload("tanycollect://booking/b", category = "BOOKING")))
        assertEquals(PushChannel.RETURNS_DEPOSITS, CollectPushRouting.channelFor(payload("tanycollect://booking/b", category = "DEPOSIT")))
        assertEquals(PushChannel.ACCOUNT, CollectPushRouting.channelFor(payload("tanycollect://revenue/settlement", category = "OPERATIONS")))
        assertEquals(listOf("operations", "returns_deposits", "account"), PushChannel.entries.map { it.id })
    }

    @Test
    fun theSameNotificationDeliveredTwiceHasItsInAppEffectsOnce() = runTest(UnconfinedTestDispatcher()) {
        val events = PushEvents()
        val received = mutableListOf<PushPayload>()
        val job = launch { events.received.toList(received) }
        assertTrue(events.publish(payload("tanycollect://booking/b1", id = "n1")))
        assertFalse(events.publish(payload("tanycollect://booking/b1", id = "n1")))
        assertEquals(1, received.size)
        job.cancel()
    }
}
