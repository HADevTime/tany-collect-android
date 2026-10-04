package ma.tany.core.model.collect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.BookingPayment
import ma.tany.core.model.common.BookingPricing
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.BusinessDateSerializer
import ma.tany.core.model.common.DepositDecision
import ma.tany.core.model.common.DepositLedgerState
import ma.tany.core.model.common.DepositStatus
import ma.tany.core.model.common.IncidentStatus
import ma.tany.core.model.common.IncidentType
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.PhotoType
import ma.tany.core.model.common.UsagePeriod
import java.time.Instant
import java.time.LocalDate

/** Minimal customer data for the merchant: first name + short name + 4 last phone digits. NEVER Trusted / identity documents. */
@Serializable
data class OperationCustomer(
    /** « Amina B. » — verbatim proper noun. */
    val shortName: String,
    val phoneLast4: String,
)

@Serializable
data class OperationProduct(
    val id: String,
    val name: String,
    val image: String,
    val catalogImageUrl: String? = null,
    /** Booking detail only. */
    val includedAccessories: List<String> = emptyList(),
) {
    val displayImage: String get() = catalogImageUrl ?: image
}

/**
 * One operation (pickup or return) of Today / Activity (`serializeOperation`). [phase] and [depositAction] are
 * computed by the server; the app renders them and never infers the next step itself.
 */
@Serializable
data class Operation(
    val id: String,
    val reference: String,
    val kind: OperationKind,
    val phase: MerchantPhase,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant,
    @Serializable(with = InstantSerializer::class) val windowEnd: Instant? = null,
    @Serializable(with = InstantSerializer::class) val returnDeadline: Instant,
    @Serializable(with = BusinessDateSerializer::class) val usageDate: LocalDate,
    @Serializable(with = BusinessDateSerializer::class) val usageEndDate: LocalDate? = null,
    val dayCount: Int = 1,
    @Serializable(with = InstantSerializer::class) val returnWindowStart: Instant? = null,
    val dailyPrice: MoneyAmount,
    val usagePeriod: UsagePeriod? = null,
    val pricing: BookingPricing? = null,
    @Serializable(with = InstantSerializer::class) val pickupWindowStart: Instant,
    @Serializable(with = InstantSerializer::class) val pickupWindowEnd: Instant,
    val customer: OperationCustomer,
    val product: OperationProduct,
    val assetCode: String? = null,
    val rentalAmount: MoneyAmount,
    val depositAmount: MoneyAmount? = null,
    val depositRefundAmount: MoneyAmount? = null,
    val depositAction: MerchantDepositAction = MerchantDepositAction.NONE,
    /** FR prose — use [depositState] + [depositAction] + amounts. */
    val depositHeadline: String? = null,
    val depositState: DepositLedgerState,
    val currency: String,
    val lateMinutes: Int? = null,
    @Serializable(with = InstantSerializer::class) val nextBookingAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val waitingSince: Instant? = null,
    val waitingMinutes: Int? = null,
    /** > 10 min waiting for the customer's confirmation (server-computed). */
    val customerConfirmationOverdue: Boolean = false,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
) {
    /** Contract rule 6 — legacy one-day rows. */
    fun effectiveUsagePeriod(): UsagePeriod = usagePeriod ?: UsagePeriod(usageDate, usageEndDate ?: usageDate, dayCount)
}

@Serializable
data class TodayCounts(
    val toCollect: Int = 0,
    val toReturn: Int = 0,
    val awaitingCustomer: Int = 0,
    val late: Int = 0,
    val blocked: Int = 0,
    val noShow: Int = 0,
    val completedToday: Int = 0,
)

/** `GET /collect/points/{id}/today`. */
@Serializable
data class TodayResponse(
    val collectPoint: MerchantPoint,
    val operations: List<Operation> = emptyList(),
    val completedToday: List<Operation> = emptyList(),
    val counts: TodayCounts = TodayCounts(),
    val gracePeriodMinutes: Int = 0,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

/** `GET /collect/points/{id}/activity` (unpaginated, limit ≤ 200, CANCELLED excluded). */
@Serializable
data class ActivityResponse(
    val items: List<Operation> = emptyList(),
    val completedToday: Int = 0,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

@Serializable
data class IncidentItem(
    val id: String,
    val bookingId: String? = null,
    val reference: String? = null,
    val assetCode: String? = null,
    val productName: String? = null,
    val type: IncidentType,
    /** User-generated text, shown verbatim. */
    val description: String? = null,
    val status: IncidentStatus,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val createdByMe: Boolean = false,
    @Serializable(with = InstantSerializer::class) val resolvedAt: Instant? = null,
    val depositDecision: DepositDecision? = null,
)

@Serializable
data class IncidentsResponse(val incidents: List<IncidentItem> = emptyList())

/**
 * Merchant booking detail = Operation + detail. Customer data is minimal by contract; Trusted is deliberately
 * NOT modeled (the merchant never sees a score or history — the backend field is ignored when present).
 */
@Serializable
data class MerchantBookingDetail(
    val id: String,
    val reference: String,
    val kind: OperationKind,
    val phase: MerchantPhase,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant,
    @Serializable(with = InstantSerializer::class) val windowEnd: Instant? = null,
    @Serializable(with = InstantSerializer::class) val returnDeadline: Instant,
    @Serializable(with = BusinessDateSerializer::class) val usageDate: LocalDate,
    @Serializable(with = BusinessDateSerializer::class) val usageEndDate: LocalDate? = null,
    val dayCount: Int = 1,
    @Serializable(with = InstantSerializer::class) val returnWindowStart: Instant? = null,
    val dailyPrice: MoneyAmount,
    val usagePeriod: UsagePeriod? = null,
    val pricing: BookingPricing? = null,
    @Serializable(with = InstantSerializer::class) val pickupWindowStart: Instant,
    @Serializable(with = InstantSerializer::class) val pickupWindowEnd: Instant,
    val customer: MerchantCustomer,
    val product: OperationProduct,
    val assetCode: String? = null,
    val rentalAmount: MoneyAmount,
    val depositAmount: MoneyAmount? = null,
    val depositRefundAmount: MoneyAmount? = null,
    val depositAction: MerchantDepositAction = MerchantDepositAction.NONE,
    val depositHeadline: String? = null,
    val depositState: DepositLedgerState,
    val currency: String,
    val lateMinutes: Int? = null,
    @Serializable(with = InstantSerializer::class) val nextBookingAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val waitingSince: Instant? = null,
    val waitingMinutes: Int? = null,
    val customerConfirmationOverdue: Boolean = false,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    // — detail —
    val status: BookingStatus,
    val asset: MerchantAsset? = null,
    val rental: Rental? = null,
    val pickup: MerchantPickup? = null,
    @SerialName("return") val returnInfo: MerchantReturn? = null,
    val payment: BookingPayment? = null,
    val deposit: MerchantDeposit? = null,
    val incidents: List<IncidentItem> = emptyList(),
    val photos: List<MerchantPhoto> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val requiresTanyIntervention: Boolean = false,
) {
    fun effectiveUsagePeriod(): UsagePeriod = usagePeriod ?: UsagePeriod(usageDate, usageEndDate ?: usageDate, dayCount)
}

@Serializable
data class MerchantCustomer(
    val shortName: String,
    val phoneLast4: String,
    val firstName: String? = null,
    val identity: MerchantCustomerIdentity? = null,
)

/** `profilePhotoUrl` is only present during the pickup (signed ~5 min). `label` is FR prose — use [verified]. */
@Serializable
data class MerchantCustomerIdentity(val verified: Boolean = false, val label: String? = null, val profilePhotoUrl: String? = null)

@Serializable
data class MerchantAsset(
    val id: String,
    val code: String,
    val status: AssetStatus,
    val condition: AssetCondition,
    @Serializable(with = InstantSerializer::class) val nextBookingAt: Instant? = null,
    val nextBookingReference: String? = null,
    @Serializable(with = InstantSerializer::class) val nextBookingWindowEnd: Instant? = null,
    val nextBookingUsagePeriod: UsagePeriod? = null,
    val turnaroundMinutes: Int = 0,
)

@Serializable
data class Rental(
    @Serializable(with = BusinessDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = BusinessDateSerializer::class) val endDate: LocalDate,
    val dayCount: Int,
    val dailyPrice: MoneyAmount,
    val total: MoneyAmount,
    val currency: String,
)

@Serializable
data class MerchantPickup(
    @Serializable(with = InstantSerializer::class) val windowStart: Instant,
    @Serializable(with = InstantSerializer::class) val windowEnd: Instant,
    @Serializable(with = InstantSerializer::class) val startedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val completionDeadline: Instant? = null,
    @Serializable(with = InstantSerializer::class) val clientVerifiedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val assetVerifiedAt: Instant? = null,
    val photoCount: Int = 0,
    val condition: AssetCondition? = null,
    @Serializable(with = InstantSerializer::class) val merchantConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val customerConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val collectedAt: Instant? = null,
)

@Serializable
data class MerchantReturn(
    @Serializable(with = InstantSerializer::class) val deadline: Instant,
    @Serializable(with = InstantSerializer::class) val windowStart: Instant? = null,
    @Serializable(with = InstantSerializer::class) val clientVerifiedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val assetVerifiedAt: Instant? = null,
    val photoCount: Int = 0,
    val condition: AssetCondition? = null,
    val incidentType: IncidentType? = null,
    @Serializable(with = InstantSerializer::class) val merchantConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val customerConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val returnedAt: Instant? = null,
)

/**
 * Merchant deposit view. Three distinct dimensions: [status] (stored projection), [state] (ledger), and
 * [merchantAction] (the only physical gesture expected). Financial decisions are never the merchant's.
 */
@Serializable
data class MerchantDeposit(
    val status: DepositStatus,
    val amount: MoneyAmount? = null,
    val expectedRefundAmount: MoneyAmount? = null,
    @Serializable(with = InstantSerializer::class) val merchantRefundConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val customerRefundConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val customerPaidConfirmedAt: Instant? = null,
    val state: DepositLedgerState,
    val merchantAction: MerchantDepositAction = MerchantDepositAction.NONE,
    val headline: String? = null,
    val heldAmount: MoneyAmount = MoneyAmount.ZERO,
    val toRefundAmount: MoneyAmount = MoneyAmount.ZERO,
    val refundedAmount: MoneyAmount = MoneyAmount.ZERO,
    val retainedAmount: MoneyAmount = MoneyAmount.ZERO,
    val currency: String,
    /** Deferred refund (flag): handing back requires a verified `DEPOSIT_REFUND` QR scan ≤ 15 min. */
    val refundPickup: MerchantRefundPickup? = null,
)

@Serializable
data class MerchantRefundPickup(
    val qrRequired: Boolean = true,
    val amount: MoneyAmount,
    val decision: DepositDecision,
    val partial: Boolean = false,
    @Serializable(with = InstantSerializer::class) val verifiedAt: Instant? = null,
)

@Serializable
data class MerchantPhoto(
    val id: String,
    val type: PhotoType,
    val condition: AssetCondition? = null,
    /** Relative, signed 1 h. */
    val url: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)

/** `label` / `actor` are FR prose — render from [type] + [actorRole]. */
@Serializable
data class HistoryEntry(
    val id: String,
    val type: BookingEventType,
    val label: String? = null,
    val actor: String? = null,
    val actorRole: ActorRole,
    @Serializable(with = InstantSerializer::class) val at: Instant,
)

@Serializable
data class MerchantBookingResponse(val booking: MerchantBookingDetail)

/** `POST bookings/{id}/photos` — the stored photo (signed relative url) and the updated booking. */
@Serializable
data class UploadedPhoto(val id: String, val url: String? = null)

@Serializable
data class PhotoUploadResponse(val photo: UploadedPhoto? = null, val booking: MerchantBookingDetail)
