package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.BusinessDateSerializer
import ma.tany.core.model.common.MoneyAmount
import java.time.Instant
import java.time.LocalDate

/**
 * TANY settlement overview (flag; `{enabled:false}` when OFF). Settlement ≠ revenue ≠ deposits. Money moved is
 * never asserted without real confirmations (agent "received X" + merchant "handed over X").
 * Settlement amounts come with integer `…Cents` twins; confirmation input uses `amountCents`.
 * Collections, statements and visits are typed from real backend output (test-data-lab `collection_*`); `policy` and
 * `upcoming` stay raw JSON (never rendered). `…Label` / `headline` fields are FR prose — render from codes and dates.
 */
@Serializable
data class SettlementOverview(
    val enabled: Boolean,
    val collectPointId: String? = null,
    val currency: String? = null,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
    val configured: Boolean = false,
    val method: SettlementMethodRef? = null,
    val policyLabel: String? = null,
    val policy: JsonElement? = null,
    val summary: SettlementSummary? = null,
    val breakdown: SettlementBreakdown? = null,
    val upcoming: JsonElement? = null,
    val heldDeposits: HeldDeposits? = null,
    val activeCollection: SettlementCollection? = null,
    val statements: List<SettlementStatementRef> = emptyList(),
    val history: List<SettlementCollection> = emptyList(),
    val disclaimer: String? = null,
)

@Serializable
data class SettlementSummary(
    val status: SettlementStatus,
    /** FR prose — use [status]. */
    val headline: String? = null,
    val amountDue: MoneyAmount = MoneyAmount.ZERO,
    val amountDueCents: Long = 0,
    val alreadyCollected: MoneyAmount = MoneyAmount.ZERO,
    val alreadyCollectedCents: Long = 0,
    val overdue: Boolean = false,
    @Serializable(with = InstantSerializer::class) val dueAt: Instant? = null,
    val nextVisit: SettlementVisit? = null,
    val lastCompleted: LastCollection? = null,
    val lastPartialRemaining: MoneyAmount? = null,
)

@Serializable
data class SettlementBreakdown(
    val rentalRevenue: MoneyAmount = MoneyAmount.ZERO,
    val commission: MoneyAmount = MoneyAmount.ZERO,
    val bonus: MoneyAmount = MoneyAmount.ZERO,
    val partnerAdjustments: MoneyAmount = MoneyAmount.ZERO,
    val tanyRentalShare: MoneyAmount = MoneyAmount.ZERO,
    val depositsRetained: MoneyAmount = MoneyAmount.ZERO,
    val settlementAdjustments: MoneyAmount = MoneyAmount.ZERO,
    val total: MoneyAmount = MoneyAmount.ZERO,
    val alreadyCollected: MoneyAmount = MoneyAmount.ZERO,
    val remaining: MoneyAmount = MoneyAmount.ZERO,
    val totalCents: Long = 0,
    val remainingCents: Long = 0,
)

/** Customer deposits still held at the point — NEVER part of the settlement. `label` / `note` are FR prose. */
@Serializable
data class HeldDeposits(val count: Int = 0, val amount: MoneyAmount = MoneyAmount.ZERO, val label: String? = null, val note: String? = null)

/** Server-issued settlement QR (`TCR1…`, distinct HMAC domain): rendered as-is, never parsed. */
@Serializable
data class SettlementQr(
    val qrPayload: String,
    val shortCode: String,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant,
    val expectedAmount: MoneyAmount,
    val expectedAmountCents: Long,
    val collectionId: String,
    val collectionReference: String,
    val purpose: String,
)

@Serializable
data class SettlementConfirmBody(val amountCents: Long, val agentConfirmationId: String)

@Serializable
data class SettlementDisputeBody(val reason: String? = null)

/** `method.code` (e.g. `CASH_AGENT_COLLECTION`) — localized by the app; `label` is FR prose. */
@Serializable
data class SettlementMethodRef(val code: String, val label: String? = null)

/** Planned agent visit. [agentName] = person's display name (data, verbatim). */
@Serializable
data class SettlementVisit(
    @Serializable(with = BusinessDateSerializer::class) val date: LocalDate,
    val label: String? = null,
    val agentName: String? = null,
)

@Serializable
data class LastCollection(
    val reference: String,
    val amount: MoneyAmount,
    val amountCents: Long = 0,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    val status: CollectionStatus,
)

/**
 * One agent collection. The merchant confirms « J'ai remis X » with EXACTLY [agentConfirmedAmountCents] +
 * [agentConfirmationId] while [confirmationRequired]; a newer agent declaration ⇒ 409 `confirmation_stale`.
 */
@Serializable
data class SettlementCollection(
    val id: String,
    val reference: String,
    val status: CollectionStatus,
    val statusLabel: String? = null,
    val method: String? = null,
    @Serializable(with = BusinessDateSerializer::class) val scheduledFor: LocalDate? = null,
    val agentName: String? = null,
    val agentPersonName: String? = null,
    val expectedAmount: MoneyAmount? = null,
    val expectedAmountCents: Long? = null,
    val agentConfirmedAmount: MoneyAmount? = null,
    val agentConfirmedAmountCents: Long? = null,
    val agentConfirmationId: String? = null,
    /** Agent input, verbatim. */
    val discrepancyReason: String? = null,
    val declaredRemainingAmount: MoneyAmount? = null,
    val receivedAmount: MoneyAmount? = null,
    val remainingAmount: MoneyAmount? = null,
    @Serializable(with = InstantSerializer::class) val startedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val agentConfirmedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    val confirmedByTany: Boolean = false,
    val qrAvailable: Boolean = false,
    val confirmationRequired: Boolean = false,
)

/** Closed statement (immutable). `periodLabel` / `statusLabel` are FR prose — use the instants and [status]. */
@Serializable
data class SettlementStatementRef(
    val id: String,
    val reference: String,
    @Serializable(with = InstantSerializer::class) val periodStart: Instant? = null,
    @Serializable(with = InstantSerializer::class) val cutoffAt: Instant? = null,
    val totalDue: MoneyAmount = MoneyAmount.ZERO,
    val collected: MoneyAmount = MoneyAmount.ZERO,
    val outstanding: MoneyAmount = MoneyAmount.ZERO,
    val status: StatementStatus,
    val overdue: Boolean = false,
    @Serializable(with = InstantSerializer::class) val dueAt: Instant? = null,
)

/** `{}` body for POST routes that take no input. */
@Serializable
class EmptyBody
