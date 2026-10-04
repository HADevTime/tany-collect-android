package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.MoneyAmount
import java.time.Instant

/**
 * TANY settlement overview (flag; `{enabled:false}` when OFF). Settlement ≠ revenue ≠ deposits. Money moved is
 * never asserted without real confirmations (agent "received X" + merchant "handed over X").
 * Settlement amounts come with integer `…Cents` twins; confirmation input uses `amountCents`.
 * Sub-objects whose shapes are not frozen yet (contract gap A-4: statements `groups[]` / `payments[]`, collections,
 * policy, method, visits) stay raw JSON until the settlement slice types them from real backend output.
 */
@Serializable
data class SettlementOverview(
    val enabled: Boolean,
    val collectPointId: String? = null,
    val currency: String? = null,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
    val configured: Boolean = false,
    val method: JsonElement? = null,
    val policyLabel: String? = null,
    val policy: JsonElement? = null,
    val summary: SettlementSummary? = null,
    val breakdown: SettlementBreakdown? = null,
    val upcoming: JsonElement? = null,
    val heldDeposits: HeldDeposits? = null,
    val activeCollection: JsonElement? = null,
    val statements: List<JsonElement> = emptyList(),
    val history: List<JsonElement> = emptyList(),
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
    val nextVisit: JsonElement? = null,
    val lastCompleted: JsonElement? = null,
    val lastPartialRemaining: JsonElement? = null,
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
