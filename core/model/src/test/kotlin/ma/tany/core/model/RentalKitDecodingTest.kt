package ma.tany.core.model

import ma.tany.core.model.collect.HandoverBody
import ma.tany.core.model.collect.KitCheckState
import ma.tany.core.model.collect.KitItemType
import ma.tany.core.model.collect.MerchantBookingResponse
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.RentalKit
import ma.tany.core.model.collect.ReturnBody
import ma.tany.core.model.collect.handoverChecks
import ma.tany.core.model.collect.returnChecks
import ma.tany.core.model.collect.returnItems
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rental kit V1 (tany-backend docs/RENTAL-KIT.md). The `real/booking_detail_kit_…` fixtures were produced by the real
 * backend Collect route (`GET /collect/bookings/{id}`, module ON, PGlite): pickup (before the handover), return in
 * progress (one element not handed over at the pickup), return declared (one element damaged).
 */
class RentalKitDecodingTest {
    private fun booking(name: String) = Fixtures.decode<MerchantBookingResponse>("real/$name.json").booking

    @Test
    fun pickupDetailCarriesTheFrozenKitWithTheBagLast() {
        val kit = booking("booking_detail_kit_pickup").kit!!
        assertEquals(3, kit.count)
        assertEquals(KitItemType.TRANSPORT_BAG, kit.items.last().type)
        assertEquals("/images/kit/tany-transport-bag.png", kit.items.last().image)
        assertTrue(kit.items.all { it.handedOver && it.pickupState == null })
        assertNull(kit.pickup)
    }

    @Test
    fun historicalDetailHasNoKit() {
        assertNull(booking("booking_detail").kit)
    }

    @Test
    fun returnExpectsOnlyWhatWasHandedOver() {
        val detail = booking("booking_detail_kit_return")
        assertEquals(MerchantPhase.RETURN_IN_PROGRESS, detail.phase)
        val kit = detail.kit!!
        assertEquals(2, kit.expectedAtReturnCount)
        assertEquals(2, kit.returnItems.size)
        val notHanded = kit.items.single { !it.handedOver }
        assertEquals(KitCheckState.MISSING, notHanded.pickupState)
        // A non-handed-over element is never sent at the return (the server would refuse it).
        val checks = kit.returnChecks(mapOf(notHanded.id to KitCheckState.MISSING, kit.items.last().id to KitCheckState.DAMAGED))
        assertEquals(listOf(kit.items.last().id), checks.checks.map { it.itemId })
        assertTrue(kit.returnChecks(mapOf(kit.items.last().id to KitCheckState.PRESENT)).checks.isEmpty())
    }

    @Test
    fun declaredReturnRecordsTheDifference() {
        val kit = booking("booking_detail_kit_returned").kit!!
        assertFalse(kit.returnCheck!!.allPresent)
        assertEquals(1, kit.returnCheck!!.issueCount)
        assertEquals(KitCheckState.DAMAGED, kit.items.first().returnState)
        assertEquals(KitCheckState.PRESENT, kit.items.last().returnState)
    }

    @Test
    fun bodiesEncodeDifferencesOnly() {
        val kit = booking("booking_detail_kit_pickup").kit!!
        assertEquals(
            """{"collectPointId":"cp","condition":"good","kit":{"checks":[]}}""",
            TanyJson.encodeToString(HandoverBody("cp", AssetCondition.GOOD, kit.handoverChecks(emptySet()))),
        )
        assertEquals("""{"collectPointId":"cp"}""", TanyJson.encodeToString(HandoverBody("cp")))
        val bag = kit.items.last().id
        assertEquals(
            """{"collectPointId":"cp","condition":"issue_reported","missingAccessories":[],"kit":{"checks":[{"itemId":"$bag","state":"MISSING"}]}}""",
            TanyJson.encodeToString(ReturnBody("cp", AssetCondition.ISSUE_REPORTED, kit = kit.returnChecks(mapOf(bag to KitCheckState.MISSING)))),
        )
    }

    @Test
    fun unknownKitValuesNeverCrash() {
        val kit = TanyJson.decodeFromString<RentalKit>("""{"items":[{"id":"x","type":"NEW","name":"X","returnState":"LOST"}]}""")
        assertEquals(KitItemType.UNKNOWN, kit.items.single().type)
        assertEquals(KitCheckState.UNKNOWN, kit.items.single().returnState)
    }
}
