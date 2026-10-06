package ma.tany.collect

import ma.tany.collect.feature.booking.BookingStage
import ma.tany.collect.feature.booking.DetailMode
import ma.tany.collect.feature.booking.ReturnStep
import ma.tany.collect.feature.booking.depositAwaitingTanyDecision
import ma.tany.collect.feature.booking.depositHandBack
import ma.tany.collect.feature.booking.detailMode
import ma.tany.collect.feature.booking.explanationRes
import ma.tany.collect.feature.booking.returnStep
import ma.tany.collect.feature.booking.stage
import ma.tany.collect.feature.today.HomeSection
import ma.tany.collect.feature.today.phaseUi
import ma.tany.collect.feature.today.pickHero
import ma.tany.collect.feature.today.rowMoney
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantBookingResponse
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.DepositLedgerState
import ma.tany.core.model.common.DepositStatus
import ma.tany.core.model.common.LatePenaltyReasonCode
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Late Return Policy V1 + deposit (tany-backend `docs/LATE-RETURNS.md`, API_CONTRACT_V1 § 8): the server is the ONLY
 * authority for lateness, retention, amount to hand back, pending decision, waiver and Admin override. These tests check
 * the PRESENTATION of server values — no amount is ever computed by the app.
 */
class LateReturnDepositTest {
    private fun fixture(name: String) = File("../core/model/src/test/resources/fixtures/real/$name.json").readText()
    private fun res(dir: String) = File("src/main/res/$dir/strings.xml").readText()

    private val today: TodayResponse = TanyJson.decodeFromString(fixture("today"))

    /** Real `deposit_to_refund` capture (RETURNED, HAND_BACK). Its own incident is resolved. */
    private val real: MerchantBookingDetail = TanyJson.decodeFromString<MerchantBookingResponse>(fixture("deposit_refund_booking")).booking

    private val t0 = Instant.parse("2026-10-06T18:24:00Z")
    private fun dh(units: Long) = MoneyAmount.ofMajor(units)

    /** Normal (non-deferred) hand-back with the server's amounts. */
    private fun handBack(toRefund: Long, retained: Long, latePenalty: Long?, state: DepositLedgerState = DepositLedgerState.PARTIAL_REFUND_PENDING) =
        real.copy(
            phase = MerchantPhase.DEPOSIT_TO_REFUND,
            depositRefundAmount = dh(toRefund),
            depositLatePenaltyAmount = latePenalty?.let(::dh),
            incidents = emptyList(),
            deposit = real.deposit!!.copy(
                state = state,
                merchantAction = MerchantDepositAction.HAND_BACK,
                toRefundAmount = dh(toRefund),
                refundableAmount = dh(toRefund),
                retainedAmount = dh(retained),
                latePenaltyAmount = latePenalty?.let(::dh),
                refundPickup = null,
            ),
        )

    // ——— Contract (additive fields) ———

    @Test
    fun additiveLateFieldsDecodeAndOldPayloadsStillDecode() {
        val json = fixture("deposit_refund_booking")
            .replace("\"retainedAmount\": 90,", "\"retainedAmount\": 90, \"latePenaltyAmount\": 45, \"refundableAmount\": 255,")
            .replace("\"depositState\": \"PARTIAL_REFUND_PENDING\",", "\"depositState\": \"PARTIAL_REFUND_PENDING\", \"depositLatePenaltyAmount\": 45,")
        val decoded = TanyJson.decodeFromString<MerchantBookingResponse>(json).booking
        assertEquals(dh(45), decoded.deposit?.latePenaltyAmount)
        assertEquals(dh(255), decoded.deposit?.refundableAmount)
        assertEquals(dh(45), decoded.depositLatePenaltyAmount)
        assertTrue(decoded.deposit!!.hasLatePenalty)
        // Real capture without the additive fields (policy OFF / older backend): historical behaviour.
        assertNull(real.deposit?.latePenaltyAmount)
        assertNull(real.deposit?.refundableAmount)
        assertFalse(real.deposit!!.hasLatePenalty)
        assertEquals(real.deposit!!.toRefundAmount, real.depositHandBack()!!.handBack)
    }

    // ——— Return never blocked ———

    @Test
    fun aLateReturnNeverBlocksTheGuidedReturn() {
        val late = real.copy(
            phase = MerchantPhase.RETURN_LATE, status = BookingStatus.COLLECTED, lateMinutes = 600,
            returnInfo = real.returnInfo!!.copy(clientVerifiedAt = null, assetVerifiedAt = null, photoCount = 0, merchantConfirmedAt = null),
        )
        assertEquals(BookingStage.RETURN_EXPECTED, late.stage())
        assertEquals(ReturnStep.CUSTOMER, late.returnStep())
        assertEquals(DetailMode.GUIDED, detailMode(late.stage(), choice = true, completed = false))
    }

    // ——— Money hierarchy (server amounts only) ———

    @Test
    fun onTimeAndGraceReturnsHandBackTheFullDeposit() {
        val onTime = handBack(toRefund = 300, retained = 0, latePenalty = null, state = DepositLedgerState.REFUND_PENDING)
        val money = onTime.depositHandBack()!!
        assertEquals(dh(300), money.received)
        assertEquals(MoneyAmount.ZERO, money.retention)
        assertEquals(dh(300), money.handBack)
        assertFalse(money.isLatePenalty)
        assertEquals(ReturnStep.DEPOSIT, onTime.returnStep())
    }

    @Test
    fun partialLateDeductionMakesTheHandBackAmountDominant() {
        val late = handBack(toRefund = 255, retained = 45, latePenalty = 45)
        val money = late.depositHandBack()!!
        assertEquals(dh(300), money.received)
        assertEquals(dh(45), money.retention)
        assertEquals(dh(255), money.handBack)
        assertTrue(money.isLatePenalty)
        assertEquals(ReturnStep.DEPOSIT, late.returnStep())
    }

    @Test
    fun fullDailyPriceDeductionIsShownAsReturnedByTheServer() {
        val capped = handBack(toRefund = 150, retained = 150, latePenalty = 150)
        assertEquals(dh(150), capped.depositHandBack()!!.retention)
        assertEquals(dh(150), capped.depositHandBack()!!.handBack)
    }

    @Test
    fun waiverAndAdminOverrideAreDisplayedVerbatim() {
        val waived = handBack(toRefund = 300, retained = 0, latePenalty = null, state = DepositLedgerState.REFUND_PENDING)
        assertFalse(waived.depositHandBack()!!.isLatePenalty)
        assertEquals(dh(300), waived.depositHandBack()!!.handBack)
        val overridden = handBack(toRefund = 280, retained = 20, latePenalty = 20)
        assertEquals(dh(20), overridden.depositHandBack()!!.retention)
        assertEquals(dh(280), overridden.depositHandBack()!!.handBack)
    }

    @Test
    fun anIncidentDecisionIsNeverPresentedAsALateRetention() {
        // Real capture: partial refund decided after a DAMAGED incident (not a late return).
        val money = real.depositHandBack()!!
        assertFalse(money.isLatePenalty)
        assertEquals(dh(90), money.retention)
    }

    // ——— Pending TANY decision ———

    @Test
    fun pendingTanyDecisionOffersNoAmountAndKeepsTheReturnRecorded() {
        val pending = real.copy(
            phase = MerchantPhase.BLOCKED_PENDING_REVIEW,
            depositRefundAmount = null,
            incidents = emptyList(),
            returnInfo = real.returnInfo!!.copy(merchantConfirmedAt = t0, returnedAt = t0),
            deposit = real.deposit!!.copy(
                state = DepositLedgerState.PENDING_DECISION,
                merchantAction = MerchantDepositAction.NONE,
                toRefundAmount = MoneyAmount.ZERO,
                refundPickup = null,
            ),
        )
        assertTrue(pending.depositAwaitingTanyDecision())
        assertEquals(BookingStage.ATTENTION, pending.stage())
        assertEquals(ReturnStep.DONE, pending.returnStep())
        assertNotNull(pending.returnInfo?.returnedAt)
        // With an open incident the regular TANY-intervention card stays.
        val withIncident = pending.copy(incidents = real.incidents.map { it.copy(resolvedAt = null) })
        assertFalse(withIncident.depositAwaitingTanyDecision())
    }

    // ——— Home / Activity rows ———

    private fun op(phase: MerchantPhase, id: String = phase.wire, state: DepositLedgerState? = null, penalty: Long? = null): Operation {
        val base = today.operations.first()
        return base.copy(
            id = id, phase = phase,
            depositState = state ?: base.depositState,
            depositLatePenaltyAmount = penalty?.let(::dh),
            depositRefundAmount = if (phase == MerchantPhase.DEPOSIT_TO_REFUND) dh(255) else base.depositRefundAmount,
        )
    }

    @Test
    fun lateReturnsLeadTheHomeAndPendingDepositsReadAsPending() {
        val late = op(MerchantPhase.RETURN_LATE, "late").copy(lateMinutes = 84)
        val refund = op(MerchantPhase.DEPOSIT_TO_REFUND, "refund", penalty = 45)
        val ready = op(MerchantPhase.PICKUP_READY, "ready")
        assertEquals("late", pickHero(listOf(ready, refund, late))?.id)
        assertTrue(HomeSection.NOW.sections.contains(late.phaseUi().section))
        // The row amount is the server's amount to hand back (retention already subtracted).
        assertEquals(dh(255), refund.rowMoney()?.amount)
        val pending = op(MerchantPhase.BLOCKED_PENDING_REVIEW, "pending", state = DepositLedgerState.PENDING_DECISION)
        assertTrue(pending.depositAwaitingTanyDecision())
        assertEquals(R.string.phase_short_pending, pending.phaseUi().short)
        val review = op(MerchantPhase.BLOCKED_PENDING_REVIEW, "review", state = DepositLedgerState.UNDER_REVIEW)
        assertFalse(review.depositAwaitingTanyDecision())
        assertEquals(R.string.phase_short_incident, review.phaseUi().short)
        assertNull("never the hero", pickHero(listOf(pending)))
    }

    // ——— Amount changed / completion ———

    @Test
    fun depositAmountChangedIsACanonicalCode() {
        assertEquals(ApiErrorCode.DEPOSIT_AMOUNT_CHANGED, ApiErrorCode.Serializer.fromWire("deposit_amount_changed"))
    }

    @Test
    fun finalHandBackThenCustomerConfirmationCompletes() {
        val late = handBack(toRefund = 255, retained = 45, latePenalty = 45)
        val handedBack = late.copy(
            phase = MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
            deposit = late.deposit!!.copy(merchantRefundConfirmedAt = t0, merchantAction = MerchantDepositAction.NONE),
        )
        assertEquals(ReturnStep.DEPOSIT_CONFIRMATION, handedBack.returnStep())
        val done = handedBack.copy(
            phase = MerchantPhase.COMPLETED, status = BookingStatus.COMPLETED,
            deposit = handedBack.deposit!!.copy(customerRefundConfirmedAt = t0, refundedAmount = dh(255), status = DepositStatus.RETURNED),
        )
        assertEquals(ReturnStep.DONE, done.returnStep())
        assertEquals(dh(255), done.deposit?.refundedAmount)
    }

    // ——— Merchant-safe reason + refunded amount (contract § 9) ———

    @Test
    fun merchantSafeReasonMapsToControlledCopyWithGenericFallback() {
        val late = handBack(toRefund = 255, retained = 45, latePenalty = 45)
        fun reasoned(code: LatePenaltyReasonCode?) = late.copy(deposit = late.deposit!!.copy(latePenaltyReasonCode = code)).depositHandBack()!!.reasonCode
        assertEquals(R.string.deposit_late_retention_note, reasoned(LatePenaltyReasonCode.LATE_RETURN).explanationRes())
        assertEquals(R.string.deposit_late_reason_next_delayed, reasoned(LatePenaltyReasonCode.NEXT_BOOKING_DELAYED).explanationRes())
        assertEquals(R.string.deposit_late_reason_next_lost, reasoned(LatePenaltyReasonCode.NEXT_BOOKING_LOST).explanationRes())
        assertEquals(R.string.deposit_late_retention_note, reasoned(null).explanationRes())
        assertEquals(R.string.deposit_late_retention_note, reasoned(LatePenaltyReasonCode.UNKNOWN).explanationRes())
        // Wire decoding: known, unknown and absent values.
        val json = fixture("deposit_refund_booking")
        fun decode(extra: String) = TanyJson.decodeFromString<MerchantBookingResponse>(
            json.replace("\"retainedAmount\": 90,", "\"retainedAmount\": 90, $extra"),
        ).booking.deposit?.latePenaltyReasonCode
        assertEquals(LatePenaltyReasonCode.NEXT_BOOKING_LOST, decode("\"latePenaltyReasonCode\": \"NEXT_BOOKING_LOST\","))
        assertEquals(LatePenaltyReasonCode.UNKNOWN, decode("\"latePenaltyReasonCode\": \"SOMETHING_NEW\","))
        assertNull(decode("\"latePenaltyReasonCode\": null,"))
        assertNull(real.deposit?.latePenaltyReasonCode)
    }

    @Test
    fun completedOperationsCarryTheAuthoritativeRefundedAmount() {
        val json = fixture("today").replaceFirst("\"depositRefundAmount\":", "\"depositRefundedAmount\": 255, \"depositLatePenaltyReasonCode\": \"LATE_RETURN\", \"depositRefundAmount\":")
        val first = TanyJson.decodeFromString<TodayResponse>(json).operations.first()
        assertEquals(dh(255), first.depositRefundedAmount)
        assertEquals(LatePenaltyReasonCode.LATE_RETURN, first.depositLatePenaltyReasonCode)
        // Real capture without the additive fields: nothing is invented.
        assertNull(today.operations.first().depositRefundedAmount)
    }

    // ——— Localization FR / EN / AR ———

    @Test
    fun lateReturnStringsExistInTheThreeLanguages() {
        val keys = listOf(
            "deposit_amount_to_hand_back_customer", "deposit_late_retention_note", "deposit_received_label", "deposit_retention_label",
            "deposit_pending_title", "deposit_pending_message", "deposit_pending_note", "deposit_pending_return_recorded",
            "row_exception_deposit_retention", "hero_return_was_due", "flow_deposit_cta",
            "deposit_late_reason_next_delayed", "deposit_late_reason_next_lost", "row_exception_deposit_returned",
        )
        val fr = res("values")
        val en = res("values-en")
        val ar = res("values-ar")
        keys.forEach { key ->
            listOf("fr" to fr, "en" to en, "ar" to ar).forEach { (lang, xml) ->
                assertTrue("[$lang] missing $key", xml.contains("name=\"$key\""))
            }
            val arValue = Regex("<string name=\"$key\">([^<]*)</string>").find(ar)!!.groupValues[1]
            assertTrue("[ar] $key must be Arabic", arValue.any { it in '؀'..'ۿ' })
        }
        assertTrue(fr.contains("<string name=\"deposit_retention_label\">Retenue TANY</string>"))
        assertTrue(fr.contains("<string name=\"deposit_amount_to_hand_back_customer\">À remettre au client</string>"))
        assertFalse("calm copy, never « amende »", fr.contains("amende", ignoreCase = true))
    }

    // ——— Privacy: no usage zone, saved place or Trusted data in Collect ———

    @Test
    fun merchantModelsCarryNoUsageZoneOrTrustedData() {
        val forbidden = listOf("usageZone", "savedPlace", "zoneConfidence", "trusted", "returnTiming", "lateReturn")
        val sources = File("../core/model/src/main/kotlin/ma/tany/core/model/collect").walk().filter { it.isFile }.map { it.readText() }.toList()
        forbidden.forEach { word ->
            sources.forEach { src ->
                assertFalse("forbidden field in Collect models: $word", Regex("val\\s+$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(src))
            }
        }
    }
}
