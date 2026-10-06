package ma.tany.collect.feature.booking

import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.PaymentStatus

/**
 * Which experience the booking screen shows, from the SERVER phase only (tany-backend `deriveMerchantPhase`): the app
 * never decides whether a pickup or a return may start — `pickup_upcoming` (before the window) vs `pickup_ready`
 * (window open) etc. are the server's verdicts. Presentation only.
 */
enum class BookingStage {
    /** `pickup_upcoming`: preparation view, no execution control. */
    PICKUP_PLANNED,

    /** `pickup_ready`: « Commencer la collecte ». */
    PICKUP_READY,

    /** `pickup_in_progress` / `pickup_awaiting_customer`: the guided pickup. */
    PICKUP_ACTIVE,

    /** `with_customer`: the rental runs; a return can still be received (early return). */
    RENTAL_ACTIVE,

    /** `return_due` / `return_late`: the return is expected (late = information, never a blocker). */
    RETURN_EXPECTED,

    /** `return_in_progress` / `return_awaiting_customer` / deposit hand-back: the guided return. */
    RETURN_ACTIVE,

    /** `deposit_disputed` / `blocked_pending_review`: TANY decides. */
    ATTENTION,

    /** `completed` / `cancelled` / `no_show` / unknown: read-only summary. */
    CLOSED,
}

fun MerchantBookingDetail.stage(): BookingStage = when (phase) {
    MerchantPhase.PICKUP_UPCOMING -> BookingStage.PICKUP_PLANNED
    MerchantPhase.PICKUP_READY -> BookingStage.PICKUP_READY
    MerchantPhase.PICKUP_IN_PROGRESS, MerchantPhase.PICKUP_AWAITING_CUSTOMER -> BookingStage.PICKUP_ACTIVE
    MerchantPhase.WITH_CUSTOMER -> BookingStage.RENTAL_ACTIVE
    MerchantPhase.RETURN_DUE, MerchantPhase.RETURN_LATE -> BookingStage.RETURN_EXPECTED
    MerchantPhase.RETURN_IN_PROGRESS, MerchantPhase.RETURN_AWAITING_CUSTOMER,
    MerchantPhase.DEPOSIT_TO_REFUND, MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
    -> BookingStage.RETURN_ACTIVE
    MerchantPhase.DEPOSIT_DISPUTED, MerchantPhase.BLOCKED_PENDING_REVIEW -> BookingStage.ATTENTION
    MerchantPhase.COMPLETED, MerchantPhase.CANCELLED, MerchantPhase.NO_SHOW, MerchantPhase.UNKNOWN -> BookingStage.CLOSED
}

/** Stages where the screen opens directly on the guided flow (an operation is under way at the counter). */
val GUIDED_STAGES = setOf(BookingStage.PICKUP_ACTIVE, BookingStage.RETURN_ACTIVE)

/** Stages where the merchant may open the guided flow (the server validates every gesture anyway). */
val STARTABLE_STAGES = setOf(BookingStage.PICKUP_READY, BookingStage.RENTAL_ACTIVE, BookingStage.RETURN_EXPECTED) + GUIDED_STAGES

/**
 * Guided pickup steps. Their ORDER is the order the backend enforces (`pickup-return.ts`, `booking-photos.ts`): the
 * object scan needs the customer's QR, the photo needs both, the handover needs QR + object + photo + payment. The
 * cash payment has no prerequisite server-side; it is placed right before the handover, like TANY Collect iOS.
 */
enum class PickupStep { CUSTOMER, ASSET, PHOTO, PAYMENT, HANDOVER, CUSTOMER_CONFIRMATION, DONE }

/** Number of numbered pickup steps (« Étape n sur 6 »). */
val PICKUP_STEP_COUNT = PickupStep.entries.size - 1

/** Amount due at the counter, from the server (`payment.totalDueAtPickup`, else `pricing`). Null = nothing to collect. */
fun MerchantBookingDetail.amountDueAtPickup(): MoneyAmount? = payment?.totalDueAtPickup ?: pricing?.totalDueAtPickup

/** The server recorded the cash payment (or there is nothing to collect). */
fun MerchantBookingDetail.pickupPaid(): Boolean = payment?.status == PaymentStatus.PAID || amountDueAtPickup()?.isZero != false

/**
 * Current pickup step = the first step whose SERVER fact is missing (`pickup.clientVerifiedAt`, `assetVerifiedAt`,
 * `photoCount`, `payment.status`, `merchantConfirmedAt`, `customerConfirmedAt`). [photosAccepted] is a UI-only
 * acknowledgement: the merchant reviews the uploaded photo before moving on (it never skips a server requirement).
 */
fun MerchantBookingDetail.pickupStep(photosAccepted: Boolean = true): PickupStep {
    val facts = pickup
    return when {
        facts?.customerConfirmedAt != null || (status != BookingStatus.RESERVED && status != BookingStatus.UNKNOWN) -> PickupStep.DONE
        facts?.merchantConfirmedAt != null -> PickupStep.CUSTOMER_CONFIRMATION
        facts?.clientVerifiedAt == null -> PickupStep.CUSTOMER
        facts.assetVerifiedAt == null -> PickupStep.ASSET
        facts.photoCount == 0 || !photosAccepted -> PickupStep.PHOTO
        !pickupPaid() -> PickupStep.PAYMENT
        else -> PickupStep.HANDOVER
    }
}

/**
 * Guided return steps — same principle (backend order: customer QR → object → photo → statement; the customer alone
 * finalises), then the deposit hand-back when the SERVER expects it (`deposit.merchantAction == HAND_BACK`).
 */
enum class ReturnStep { CUSTOMER, ASSET, PHOTO, STATEMENT, CUSTOMER_CONFIRMATION, DEPOSIT, DEPOSIT_CONFIRMATION, DONE }

fun MerchantBookingDetail.returnStep(photosAccepted: Boolean = true): ReturnStep {
    val facts = returnInfo
    val deposit = deposit
    return when {
        status == BookingStatus.COLLECTED && facts?.merchantConfirmedAt == null -> when {
            facts?.clientVerifiedAt == null -> ReturnStep.CUSTOMER
            facts.assetVerifiedAt == null -> ReturnStep.ASSET
            facts.photoCount == 0 || !photosAccepted -> ReturnStep.PHOTO
            else -> ReturnStep.STATEMENT
        }
        status == BookingStatus.COLLECTED -> ReturnStep.CUSTOMER_CONFIRMATION
        deposit?.merchantAction == MerchantDepositAction.HAND_BACK && deposit.merchantRefundConfirmedAt == null -> ReturnStep.DEPOSIT
        deposit?.merchantRefundConfirmedAt != null && deposit.customerRefundConfirmedAt == null -> ReturnStep.DEPOSIT_CONFIRMATION
        else -> ReturnStep.DONE
    }
}

/**
 * Numbered position of a return step (1-based) and the total shown in « Étape n sur N ». The deposit step only counts
 * when the server announced a deposit on the booking.
 */
fun MerchantBookingDetail.returnStepCount(): Int = if ((depositAmount ?: deposit?.amount)?.isZero == false) 6 else 5

fun ReturnStep.position(): Int = when (this) {
    ReturnStep.CUSTOMER -> 1
    ReturnStep.ASSET -> 2
    ReturnStep.PHOTO -> 3
    ReturnStep.STATEMENT -> 4
    ReturnStep.CUSTOMER_CONFIRMATION -> 5
    ReturnStep.DEPOSIT, ReturnStep.DEPOSIT_CONFIRMATION -> 6
    ReturnStep.DONE -> 6
}

fun PickupStep.position(): Int = (ordinal + 1).coerceAtMost(PICKUP_STEP_COUNT)
