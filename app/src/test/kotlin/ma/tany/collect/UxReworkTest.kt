package ma.tany.collect

import ma.tany.collect.core.ui.RelativeDay
import ma.tany.collect.core.ui.relativeDay
import ma.tany.collect.feature.booking.BookingStage
import ma.tany.collect.feature.booking.DetailMode
import ma.tany.collect.feature.booking.PICKUP_STEP_COUNT
import ma.tany.collect.feature.booking.PickupStep
import ma.tany.collect.feature.booking.ReturnStep
import ma.tany.collect.feature.booking.detailMode
import ma.tany.collect.feature.booking.isReturnFlow
import ma.tany.collect.feature.booking.pickupStep
import ma.tany.collect.feature.booking.position
import ma.tany.collect.feature.booking.returnStep
import ma.tany.collect.feature.booking.returnStepCount
import ma.tany.collect.feature.booking.stage
import ma.tany.collect.feature.today.HomeSection
import ma.tany.collect.feature.today.homeSections
import ma.tany.collect.feature.today.pickHero
import ma.tany.collect.feature.today.referenceTime
import ma.tany.collect.feature.today.rowMoney
import ma.tany.collect.feature.today.rowTime
import ma.tany.collect.navigation.ActivityRoute
import ma.tany.collect.navigation.AccountRoute
import ma.tany.collect.navigation.EquipmentRoute
import ma.tany.collect.navigation.TodayRoute
import ma.tany.collect.navigation.bottomBarSlots
import ma.tany.collect.navigation.bottomTabs
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantBookingResponse
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.PaymentStatus
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Premium UX rework: Home hero / sections, state-based booking screen, guided pickup & return steps, navigation. Every
 * rule tested here is a PRESENTATION of server data (phases, timestamps, counters) — no business rule is derived.
 */
class UxReworkTest {
    private fun fixture(name: String) = File("../core/model/src/test/resources/fixtures/real/$name.json").readText()

    private val today: TodayResponse = TanyJson.decodeFromString(fixture("today"))

    /** Real `pickup_upcoming` booking (RESERVED, payment PENDING 467 DH, deposit 200 DH). */
    private val booking: MerchantBookingDetail = TanyJson.decodeFromString<MerchantBookingResponse>(fixture("booking_detail")).booking

    /** Real `deposit_to_refund` booking (RETURNED, deposit HAND_BACK 300 DH). */
    private val depositBooking: MerchantBookingDetail = TanyJson.decodeFromString<MerchantBookingResponse>(fixture("deposit_refund_booking")).booking

    private val t0 = Instant.parse("2026-10-06T09:00:00Z")

    private fun op(phase: MerchantPhase, at: Instant, id: String = phase.wire): Operation =
        today.operations.first().copy(id = id, phase = phase, pickupWindowStart = at, returnDeadline = at, scheduledAt = at, waitingSince = at)

    // ——— HOME ———

    @Test
    fun homeWithoutOperationHasNoHeroAndNoSection() {
        assertNull(pickHero(emptyList()))
        assertTrue(homeSections(emptyList(), null).isEmpty())
    }

    @Test
    fun aSinglePickupIsTheHeroAndIsNotRepeatedBelow() {
        val pickup = op(MerchantPhase.PICKUP_UPCOMING, t0)
        assertEquals(pickup, pickHero(listOf(pickup)))
        assertTrue(homeSections(listOf(pickup), pickup).isEmpty())
        val ret = op(MerchantPhase.RETURN_DUE, t0)
        assertEquals(ret, pickHero(listOf(ret)))
    }

    @Test
    fun heroFollowsServerUrgencyThenServerTime() {
        val upcoming = op(MerchantPhase.PICKUP_UPCOMING, t0, "upcoming")
        val ready = op(MerchantPhase.PICKUP_READY, t0.plusSeconds(3_600), "ready")
        val inProgress = op(MerchantPhase.RETURN_IN_PROGRESS, t0.plusSeconds(7_200), "in-progress")
        val late = op(MerchantPhase.RETURN_LATE, t0.plusSeconds(9_000), "late")
        val waiting = op(MerchantPhase.PICKUP_AWAITING_CUSTOMER, t0.minusSeconds(600), "waiting")
        assertEquals("late", pickHero(listOf(upcoming, ready, inProgress, late, waiting))?.id)
        assertEquals("in-progress", pickHero(listOf(upcoming, ready, inProgress, waiting))?.id)
        assertEquals("ready", pickHero(listOf(upcoming, ready, waiting))?.id)
        assertEquals("upcoming", pickHero(listOf(upcoming, waiting))?.id)
        assertEquals("waiting", pickHero(listOf(waiting))?.id)
        // Same tier: the earliest server time first.
        val earlier = op(MerchantPhase.PICKUP_READY, t0.plusSeconds(60), "earlier")
        assertEquals("earlier", pickHero(listOf(ready, earlier))?.id)
    }

    @Test
    fun tanyReviewsAndNoShowsAreNeverTheHero() {
        val blocked = op(MerchantPhase.BLOCKED_PENDING_REVIEW, t0, "blocked")
        val noShow = op(MerchantPhase.NO_SHOW, t0, "no-show")
        assertNull(pickHero(listOf(blocked, noShow)))
        val sections = homeSections(listOf(blocked, noShow), null)
        assertEquals(listOf(HomeSection.NOW, HomeSection.NO_SHOW), sections.map { it.first })
    }

    @Test
    fun manyOperationsAppearExactlyOnceInPriorityOrder() {
        val operations = today.operations
        val hero = pickHero(operations)
        assertTrue("real fixture has a hero", hero != null)
        val sections = homeSections(operations, hero)
        val listed = sections.flatMap { it.second } + listOfNotNull(hero)
        assertEquals(operations.map { it.id }.sorted(), listed.map { it.id }.sorted())
        assertTrue(sections.none { it.second.isEmpty() })
        assertEquals(sections.map { it.first }, sections.map { it.first }.sortedBy { it.ordinal })
        sections.forEach { (_, ops) -> assertEquals(ops.sortedBy { it.referenceTime() }, ops) }
        // Real fixture: the deposit waiting for the customer is never preferred to the return due / pickup ready.
        assertNotEquals(MerchantPhase.DEPOSIT_AWAITING_CUSTOMER, hero?.phase)
    }

    @Test
    fun lateOperationsGoToTheNowSection() {
        val late = op(MerchantPhase.RETURN_LATE, t0, "late")
        val late2 = op(MerchantPhase.RETURN_LATE, t0.plusSeconds(60), "late-2")
        val hero = pickHero(listOf(late, late2))
        assertEquals("late", hero?.id)
        assertEquals(listOf(HomeSection.NOW to listOf(late2)), homeSections(listOf(late, late2), hero))
    }

    @Test
    fun cardsPutTheServerTimeAndOnlyRelevantMoneyForward() {
        val pickup = today.operations.first { it.phase == MerchantPhase.PICKUP_READY }
        val time = pickup.rowTime()
        assertEquals(pickup.pickupWindowStart, time.start)
        assertEquals(pickup.pickupWindowEnd, time.end)
        assertEquals(pickup.pricing?.totalDueAtPickup ?: pickup.rentalAmount, pickup.rowMoney()?.amount)
        val ret = today.operations.first { it.phase == MerchantPhase.RETURN_DUE }
        assertEquals(ret.returnDeadline, ret.rowTime().start)
        assertNull(ret.rowTime().end)
        assertNull("no cash at a plain return", ret.rowMoney())
        val deposit = op(MerchantPhase.DEPOSIT_TO_REFUND, t0).copy(depositRefundAmount = MoneyAmount.ofMajor(300))
        assertEquals(MoneyAmount.ofMajor(300), deposit.rowMoney()?.amount)
        // The full product name is kept in the model (the card wraps it, never truncates the data).
        val longName = pickup.copy(product = pickup.product.copy(name = "Nettoyeur Pro des canapés et matelas — kit complet"))
        assertEquals("Nettoyeur Pro des canapés et matelas — kit complet", pickHero(listOf(longName))?.product?.name)
    }

    @Test
    fun relativeDaysUseTheBusinessCalendar() {
        val day = LocalDate.of(2026, 10, 6)
        assertEquals(RelativeDay.TODAY, relativeDay(day, day))
        assertEquals(RelativeDay.TOMORROW, relativeDay(day.plusDays(1), day))
        assertEquals(RelativeDay.YESTERDAY, relativeDay(day.minusDays(1), day))
        assertEquals(RelativeDay.OTHER, relativeDay(day.plusDays(3), day))
    }

    // ——— BOOKING DETAIL ———

    @Test
    fun everyServerPhaseMapsToOneStage() {
        val expected = mapOf(
            MerchantPhase.PICKUP_UPCOMING to BookingStage.PICKUP_PLANNED,
            MerchantPhase.PICKUP_READY to BookingStage.PICKUP_READY,
            MerchantPhase.PICKUP_IN_PROGRESS to BookingStage.PICKUP_ACTIVE,
            MerchantPhase.PICKUP_AWAITING_CUSTOMER to BookingStage.PICKUP_ACTIVE,
            MerchantPhase.WITH_CUSTOMER to BookingStage.RENTAL_ACTIVE,
            MerchantPhase.RETURN_DUE to BookingStage.RETURN_EXPECTED,
            MerchantPhase.RETURN_LATE to BookingStage.RETURN_EXPECTED,
            MerchantPhase.RETURN_IN_PROGRESS to BookingStage.RETURN_ACTIVE,
            MerchantPhase.RETURN_AWAITING_CUSTOMER to BookingStage.RETURN_ACTIVE,
            MerchantPhase.DEPOSIT_TO_REFUND to BookingStage.RETURN_ACTIVE,
            MerchantPhase.DEPOSIT_AWAITING_CUSTOMER to BookingStage.RETURN_ACTIVE,
            MerchantPhase.DEPOSIT_DISPUTED to BookingStage.ATTENTION,
            MerchantPhase.BLOCKED_PENDING_REVIEW to BookingStage.ATTENTION,
            MerchantPhase.COMPLETED to BookingStage.CLOSED,
            MerchantPhase.CANCELLED to BookingStage.CLOSED,
            MerchantPhase.NO_SHOW to BookingStage.CLOSED,
            MerchantPhase.UNKNOWN to BookingStage.CLOSED,
        )
        assertEquals(MerchantPhase.entries.toSet(), expected.keys)
        expected.forEach { (phase, stage) -> assertEquals(phase.name, stage, booking.copy(phase = phase).stage()) }
        assertEquals(BookingStage.PICKUP_PLANNED, booking.stage()) // real capture: before the window
    }

    @Test
    fun beforeTheWindowTheGuidedFlowCanNeverOpen() {
        // Pre-window = preparation view, whatever the merchant taps.
        assertEquals(DetailMode.OVERVIEW, detailMode(BookingStage.PICKUP_PLANNED, choice = null, completed = false))
        assertEquals(DetailMode.OVERVIEW, detailMode(BookingStage.PICKUP_PLANNED, choice = true, completed = false))
        // Window open: overview first, guided flow on « Commencer la collecte ».
        assertEquals(DetailMode.OVERVIEW, detailMode(BookingStage.PICKUP_READY, choice = null, completed = false))
        assertEquals(DetailMode.GUIDED, detailMode(BookingStage.PICKUP_READY, choice = true, completed = false))
        // Under way (in progress / waiting for the customer): straight into the flow, unless the merchant left it.
        assertEquals(DetailMode.GUIDED, detailMode(BookingStage.PICKUP_ACTIVE, choice = null, completed = false))
        assertEquals(DetailMode.OVERVIEW, detailMode(BookingStage.PICKUP_ACTIVE, choice = false, completed = false))
        // Active rental / return due: overview; the return can be started (early returns are possible).
        assertEquals(DetailMode.OVERVIEW, detailMode(BookingStage.RENTAL_ACTIVE, choice = null, completed = false))
        assertEquals(DetailMode.GUIDED, detailMode(BookingStage.RETURN_EXPECTED, choice = true, completed = false))
        // Closed / TANY review: never an execution flow.
        listOf(BookingStage.CLOSED, BookingStage.ATTENTION).forEach {
            assertEquals(DetailMode.OVERVIEW, detailMode(it, choice = true, completed = false))
        }
        // A customer confirmation seen on screen: success.
        assertEquals(DetailMode.SUCCESS, detailMode(BookingStage.RENTAL_ACTIVE, choice = null, completed = true))
    }

    @Test
    fun cancelledAndExpiredBookingsAreReadOnly() {
        val cancelled = booking.copy(phase = MerchantPhase.CANCELLED, status = BookingStatus.CANCELLED)
        val expired = booking.copy(phase = MerchantPhase.NO_SHOW, status = BookingStatus.EXPIRED)
        listOf(cancelled, expired).forEach {
            assertEquals(BookingStage.CLOSED, it.stage())
            assertEquals(DetailMode.OVERVIEW, detailMode(it.stage(), choice = true, completed = false))
            assertFalse(it.isReturnFlow())
        }
    }

    // ——— GUIDED PICKUP ———

    @Test
    fun guidedPickupFollowsTheServerFactsOneStepAtATime() {
        val ready = booking.copy(phase = MerchantPhase.PICKUP_READY)
        val p = ready.pickup!!
        val at = Instant.parse("2026-10-05T09:30:00Z")
        assertEquals(PickupStep.CUSTOMER, ready.pickupStep())
        val clientOk = ready.copy(phase = MerchantPhase.PICKUP_IN_PROGRESS, pickup = p.copy(clientVerifiedAt = at))
        assertEquals(PickupStep.ASSET, clientOk.pickupStep())
        val assetOk = clientOk.copy(pickup = clientOk.pickup!!.copy(assetVerifiedAt = at))
        assertEquals(PickupStep.PHOTO, assetOk.pickupStep())
        val photo = assetOk.copy(pickup = assetOk.pickup!!.copy(photoCount = 1))
        // The merchant reviews the photo before moving on (UI acknowledgement only).
        assertEquals(PickupStep.PHOTO, photo.pickupStep(photosAccepted = false))
        assertEquals(PickupStep.PAYMENT, photo.pickupStep(photosAccepted = true))
        val paid = photo.copy(payment = photo.payment!!.copy(status = PaymentStatus.PAID))
        assertEquals(PickupStep.HANDOVER, paid.pickupStep())
        val handedOver = paid.copy(phase = MerchantPhase.PICKUP_AWAITING_CUSTOMER, pickup = paid.pickup!!.copy(merchantConfirmedAt = at))
        assertEquals(PickupStep.CUSTOMER_CONFIRMATION, handedOver.pickupStep())
        val collected = handedOver.copy(
            phase = MerchantPhase.WITH_CUSTOMER,
            status = BookingStatus.COLLECTED,
            pickup = handedOver.pickup!!.copy(customerConfirmedAt = at),
        )
        assertEquals(PickupStep.DONE, collected.pickupStep())
        // Progress « Étape n sur 6 » follows the same order.
        assertEquals(6, PICKUP_STEP_COUNT)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), PickupStep.entries.dropLast(1).map { it.position() })
    }

    @Test
    fun nothingToCollectGoesStraightToTheHandover() {
        val free = booking.copy(
            pickup = booking.pickup!!.copy(clientVerifiedAt = t0, assetVerifiedAt = t0, photoCount = 1),
            payment = booking.payment!!.copy(totalDueAtPickup = MoneyAmount.ZERO),
            pricing = booking.pricing!!.copy(totalDueAtPickup = MoneyAmount.ZERO),
        )
        assertEquals(PickupStep.HANDOVER, free.pickupStep())
        // Real capture: 467 DH pending ⇒ the payment step is required.
        val unpaid = free.copy(payment = booking.payment, pricing = booking.pricing)
        assertEquals(PickupStep.PAYMENT, unpaid.pickupStep())
    }

    // ——— GUIDED RETURN ———

    @Test
    fun guidedReturnStepsAndALateReturnIsNeverBlocked() {
        val collected = booking.copy(phase = MerchantPhase.RETURN_DUE, status = BookingStatus.COLLECTED)
        val r = collected.returnInfo!!
        assertTrue(collected.isReturnFlow())
        assertEquals(ReturnStep.CUSTOMER, collected.returnStep())
        // Late: same steps, same order — lateness is information only.
        val late = collected.copy(phase = MerchantPhase.RETURN_LATE, lateMinutes = 95)
        assertEquals(ReturnStep.CUSTOMER, late.returnStep())
        assertEquals(DetailMode.GUIDED, detailMode(late.stage(), choice = true, completed = false))
        val client = late.copy(phase = MerchantPhase.RETURN_IN_PROGRESS, returnInfo = r.copy(clientVerifiedAt = t0))
        assertEquals(ReturnStep.ASSET, client.returnStep())
        val asset = client.copy(returnInfo = client.returnInfo!!.copy(assetVerifiedAt = t0))
        assertEquals(ReturnStep.PHOTO, asset.returnStep())
        val photo = asset.copy(returnInfo = asset.returnInfo!!.copy(photoCount = 1))
        assertEquals(ReturnStep.PHOTO, photo.returnStep(photosAccepted = false))
        assertEquals(ReturnStep.STATEMENT, photo.returnStep(photosAccepted = true))
        val declared = photo.copy(phase = MerchantPhase.RETURN_AWAITING_CUSTOMER, returnInfo = photo.returnInfo!!.copy(merchantConfirmedAt = t0))
        assertEquals(ReturnStep.CUSTOMER_CONFIRMATION, declared.returnStep())
    }

    @Test
    fun depositHandBackIsTheLastReturnStepWhenTheServerAsksForIt() {
        // Real capture: RETURNED, deposit HAND_BACK.
        assertEquals(BookingStage.RETURN_ACTIVE, depositBooking.stage())
        assertTrue(depositBooking.isReturnFlow())
        assertEquals(MerchantDepositAction.HAND_BACK, depositBooking.deposit?.merchantAction)
        assertEquals(ReturnStep.DEPOSIT, depositBooking.returnStep())
        assertEquals(6, depositBooking.returnStepCount())
        val handedBack = depositBooking.copy(
            phase = MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
            deposit = depositBooking.deposit!!.copy(merchantRefundConfirmedAt = t0, merchantAction = MerchantDepositAction.NONE),
        )
        assertEquals(ReturnStep.DEPOSIT_CONFIRMATION, handedBack.returnStep())
        val closed = handedBack.copy(phase = MerchantPhase.COMPLETED, deposit = handedBack.deposit!!.copy(customerRefundConfirmedAt = t0))
        assertEquals(ReturnStep.DONE, closed.returnStep())
        // Without a deposit the return has five steps.
        val noDeposit = depositBooking.copy(depositAmount = null, deposit = depositBooking.deposit!!.copy(amount = null))
        assertEquals(5, noDeposit.returnStepCount())
    }

    // ——— NAVIGATION ———

    @Test
    fun scannerIsAnActionInTheMiddleOfTheBarNotATab() {
        val withAssets = bottomTabs(assetsEnabled = true)
        assertEquals(listOf(TodayRoute, ActivityRoute, EquipmentRoute, AccountRoute), withAssets.map { it.route })
        val slots = bottomBarSlots(withAssets)
        assertEquals(5, slots.size)
        assertNull("scanner action in the centre", slots[2])
        assertEquals(1, slots.count { it == null })

        // Matériel only when the server module is ON; the scanner stays after Today + Activity.
        val withoutAssets = bottomBarSlots(bottomTabs(assetsEnabled = false))
        assertEquals(listOf(TodayRoute, ActivityRoute, null, AccountRoute), withoutAssets.map { it?.route })
    }
}
