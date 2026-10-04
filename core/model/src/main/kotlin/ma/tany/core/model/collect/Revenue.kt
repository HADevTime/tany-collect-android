package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.MoneyAmount
import java.time.Instant

/**
 * Partner revenue (flag; `{enabled:false}` when OFF). Earnings are ESTIMATED — never display "paid / settled /
 * balance". Deposits are NOT revenue and are shown separately. Sub-objects typed from real backend output
 * (test-data-lab `revenue_*` scenarios). `…label` / `title` / `subtitle` fields are FR prose — render from codes.
 */
@Serializable
data class RevenueOverview(
    val enabled: Boolean,
    val collectPointId: String? = null,
    val currency: String? = null,
    val period: RevenuePeriod? = null,
    val periods: List<RevenuePeriodRef> = emptyList(),
    val earnings: RevenueEarnings? = null,
    val performance: RevenuePerformance? = null,
    val bonus: RevenueBonus? = null,
    val deposits: RevenueDeposits? = null,
    val actions: List<RevenueAction> = emptyList(),
    val activity: List<RevenueActivity> = emptyList(),
    val agreement: RevenueAgreement? = null,
    val upcomingAgreement: RevenueAgreement? = null,
    /** FR prose — the app shows its own localized disclaimer. */
    val disclaimer: String? = null,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

@Serializable
data class RevenuePeriod(
    /** `YYYY-MM` — format the month locally, never use `label`. */
    val key: String,
    val label: String? = null,
    val rangeLabel: String? = null,
    @Serializable(with = InstantSerializer::class) val start: Instant,
    @Serializable(with = InstantSerializer::class) val end: Instant,
    val isCurrent: Boolean = false,
    val previousKey: String? = null,
    val nextKey: String? = null,
)

@Serializable
data class RevenuePeriodRef(val key: String, val label: String? = null, val isCurrent: Boolean = false)

@Serializable
data class RevenueEarnings(
    val status: EarningsStatus,
    val title: String? = null,
    val amount: MoneyAmount,
    val commissionAmount: MoneyAmount = MoneyAmount.ZERO,
    val bonusAmount: MoneyAmount = MoneyAmount.ZERO,
    val adjustmentAmount: MoneyAmount = MoneyAmount.ZERO,
    val previousAmount: MoneyAmount = MoneyAmount.ZERO,
    val hasAgreement: Boolean = false,
)

@Serializable
data class RevenuePerformance(
    val rentals: Int = 0,
    val rentalRevenue: MoneyAmount = MoneyAmount.ZERO,
    val previousRentals: Int = 0,
    val previousRentalRevenue: MoneyAmount = MoneyAmount.ZERO,
)

@Serializable
data class CountAmount(val count: Int = 0, val amount: MoneyAmount = MoneyAmount.ZERO)

@Serializable
data class RevenueDeposits(
    val count: Int = 0,
    val amount: MoneyAmount = MoneyAmount.ZERO,
    val toHandBack: CountAmount = CountAmount(),
    val awaitingCustomer: CountAmount = CountAmount(),
    val underReview: CountAmount = CountAmount(),
)

@Serializable
data class RevenueActivity(
    val id: String,
    val kind: RevenueActivityKind,
    val title: String? = null,
    val subtitle: String? = null,
    val amount: MoneyAmount,
    val amountKind: RevenueAmountKind,
    @Serializable(with = InstantSerializer::class) val at: Instant,
    val bookingId: String? = null,
    val productName: String? = null,
    val bookingReference: String? = null,
    val bonusMetric: RevenueMetric? = null,
    val bonusThreshold: Int? = null,
    /** Adjustment note — Admin input, verbatim. */
    val note: String? = null,
)

/**
 * Bonus tiers of the period. `threshold` / `current` / `remaining` are a COUNT for [RevenueMetric.RENTAL_COUNT] and an
 * amount in DH for [RevenueMetric.RENTAL_REVENUE]: both decoded exactly as [MoneyAmount] (never a float), the screen
 * renders a count from [MoneyAmount.major].
 */
@Serializable
data class RevenueBonus(
    val unlockedCount: Int = 0,
    val unlockedAmount: MoneyAmount = MoneyAmount.ZERO,
    val next: BonusTier? = null,
    val tiers: List<BonusTier> = emptyList(),
)

@Serializable
data class BonusTier(
    val metric: RevenueMetric,
    val threshold: MoneyAmount,
    val reward: MoneyAmount,
    val achieved: Boolean = false,
    @Serializable(with = InstantSerializer::class) val achievedAt: Instant? = null,
    val current: MoneyAmount? = null,
    val remaining: MoneyAmount? = null,
    /** 0…1, display only. */
    val progress: Double? = null,
)

/** Something the point must do now (today: a deposit to hand back); opens the booking, never acts by itself. */
@Serializable
data class RevenueAction(
    val kind: RevenueActionKind,
    val bookingId: String? = null,
    val bookingReference: String? = null,
    val productName: String? = null,
    val customerShortName: String? = null,
    val amount: MoneyAmount? = null,
    val deepLink: String? = null,
)

/** Partner agreement in force for the period (versioned server-side). [note] = Admin input, verbatim. */
@Serializable
data class RevenueAgreement(
    /** Rate in percent (not money). */
    val commissionRatePercent: Double? = null,
    @Serializable(with = InstantSerializer::class) val effectiveFrom: Instant? = null,
    val bonuses: List<AgreementBonus> = emptyList(),
    val note: String? = null,
)

@Serializable
data class AgreementBonus(val metric: RevenueMetric, val threshold: MoneyAmount, val reward: MoneyAmount)
