package ma.tany.collect.feature.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ma.tany.collect.R
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.colors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetTone
import ma.tany.core.model.collect.CollectAssetStatus
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.network.ApiEndpoint
import java.time.Duration
import java.time.Instant

/**
 * Today section of a SERVER phase. The six sections are exactly the groups of the server counters
 * (`counts.late / blocked / toCollect / toReturn / awaitingCustomer / noShow`, tany-backend `getTodayOperations`) — the
 * same sections as TANY Collect iOS. Pure presentation of `phase`: the app never decides what the merchant must do.
 */
enum class OperationSection {
    LATE,

    /** TANY review or disputed deposit (`counts.blocked`). */
    ATTENTION,
    TO_COLLECT,
    TO_RETURN,
    AWAITING_CUSTOMER,
    NO_SHOW,

    /** Closed or unexpected phases (never sent in Today's `operations`). */
    OTHER,
}

/** Presentation of SERVER phases. The merchant's next step is never computed by the app. */
data class PhaseUi(
    /** Long label (booking detail banner). */
    @StringRes val label: Int,
    val tone: TanyTone,
    val section: OperationSection,
    /** One-line explanation of what the phase means (booking detail banner). */
    @StringRes val description: Int,
    /** Short badge of list rows (iOS « ActivityRowContent.status »). */
    @StringRes val short: Int,
)

fun MerchantPhase.ui(): PhaseUi = when (this) {
    MerchantPhase.PICKUP_UPCOMING ->
        PhaseUi(R.string.phase_pickup_upcoming, TanyTone.NEUTRAL, OperationSection.TO_COLLECT, R.string.phase_desc_pickup_upcoming, R.string.phase_short_upcoming)
    MerchantPhase.PICKUP_READY ->
        PhaseUi(R.string.phase_pickup_ready, TanyTone.ACTION, OperationSection.TO_COLLECT, R.string.phase_desc_pickup_ready, R.string.phase_short_ready)
    MerchantPhase.PICKUP_IN_PROGRESS ->
        PhaseUi(R.string.phase_pickup_in_progress, TanyTone.INFO, OperationSection.TO_COLLECT, R.string.phase_desc_pickup_in_progress, R.string.phase_short_in_progress)
    MerchantPhase.PICKUP_AWAITING_CUSTOMER -> PhaseUi(
        R.string.phase_pickup_awaiting_customer, TanyTone.WARNING, OperationSection.AWAITING_CUSTOMER,
        R.string.phase_desc_pickup_awaiting_customer, R.string.phase_short_pending,
    )
    MerchantPhase.NO_SHOW -> PhaseUi(R.string.phase_no_show, TanyTone.NEUTRAL, OperationSection.NO_SHOW, R.string.phase_desc_no_show, R.string.phase_short_no_show)
    MerchantPhase.WITH_CUSTOMER ->
        PhaseUi(R.string.phase_with_customer, TanyTone.INFO, OperationSection.TO_RETURN, R.string.phase_desc_with_customer, R.string.phase_short_rented_out)
    MerchantPhase.RETURN_DUE ->
        PhaseUi(R.string.phase_return_due, TanyTone.ACTION, OperationSection.TO_RETURN, R.string.phase_desc_return_due, R.string.phase_short_expected)
    MerchantPhase.RETURN_LATE -> PhaseUi(R.string.phase_return_late, TanyTone.DANGER, OperationSection.LATE, R.string.phase_desc_return_late, R.string.phase_short_late)
    MerchantPhase.RETURN_IN_PROGRESS ->
        PhaseUi(R.string.phase_return_in_progress, TanyTone.INFO, OperationSection.TO_RETURN, R.string.phase_desc_return_in_progress, R.string.phase_short_in_progress)
    MerchantPhase.RETURN_AWAITING_CUSTOMER -> PhaseUi(
        R.string.phase_return_awaiting_customer, TanyTone.WARNING, OperationSection.AWAITING_CUSTOMER,
        R.string.phase_desc_return_awaiting_customer, R.string.phase_short_pending,
    )
    MerchantPhase.DEPOSIT_TO_REFUND ->
        PhaseUi(R.string.phase_deposit_to_refund, TanyTone.ACTION, OperationSection.TO_RETURN, R.string.phase_desc_deposit_to_refund, R.string.phase_short_deposit)
    MerchantPhase.DEPOSIT_AWAITING_CUSTOMER -> PhaseUi(
        R.string.phase_deposit_awaiting_customer, TanyTone.WARNING, OperationSection.AWAITING_CUSTOMER,
        R.string.phase_desc_deposit_awaiting_customer, R.string.phase_short_pending,
    )
    MerchantPhase.DEPOSIT_DISPUTED ->
        PhaseUi(R.string.phase_deposit_disputed, TanyTone.DANGER, OperationSection.ATTENTION, R.string.phase_desc_deposit_disputed, R.string.phase_short_disputed)
    MerchantPhase.BLOCKED_PENDING_REVIEW -> PhaseUi(
        R.string.phase_blocked_pending_review, TanyTone.DANGER, OperationSection.ATTENTION,
        R.string.phase_desc_blocked_pending_review, R.string.phase_short_incident,
    )
    MerchantPhase.COMPLETED ->
        PhaseUi(R.string.phase_completed, TanyTone.SUCCESS, OperationSection.OTHER, R.string.phase_desc_completed, R.string.phase_short_completed)
    MerchantPhase.CANCELLED ->
        PhaseUi(R.string.phase_cancelled, TanyTone.NEUTRAL, OperationSection.OTHER, R.string.phase_desc_cancelled, R.string.phase_short_cancelled)
    MerchantPhase.UNKNOWN -> PhaseUi(R.string.phase_unknown, TanyTone.NEUTRAL, OperationSection.OTHER, R.string.phase_desc_unknown, R.string.phase_short_unknown)
}

/** Sections always listed on Today, even empty (iOS: « À collecter » / « À retourner » with a reassuring line). */
val ALWAYS_SHOWN_SECTIONS = setOf(OperationSection.TO_COLLECT, OperationSection.TO_RETURN)

/**
 * The SERVER instant that matters for a row: when the customer started waiting, else the pickup window start
 * (pickup) or the return deadline (return). Display / ordering only.
 */
fun Operation.referenceTime(): Instant = when (phase.ui().section) {
    OperationSection.AWAITING_CUSTOMER -> waitingSince ?: scheduledAt
    else -> if (kind == OperationKind.RETURN) returnDeadline else pickupWindowStart
}

/**
 * Groups the server's operations by [OperationSection] in the fixed iOS order, each section in chronological order of
 * its server [referenceTime] (stable). Sections in [ALWAYS_SHOWN_SECTIONS] are kept even when empty.
 */
fun groupBySection(operations: List<Operation>): List<Pair<OperationSection, List<Operation>>> =
    OperationSection.entries.mapNotNull { section ->
        val items = operations.filter { it.phase.ui().section == section }.sortedBy { it.referenceTime() }
        if (items.isEmpty() && section !in ALWAYS_SHOWN_SECTIONS) null else section to items
    }

/**
 * Priority tier of an operation for the Home hero, from its SERVER phase only: late return → operation under way →
 * operation open now (pickup window open, return due, deposit to hand back) → upcoming → waiting for the customer.
 * TANY reviews, no-shows and closed rows are never the hero (they stay in their sections). Null = not a hero candidate.
 */
fun Operation.heroTier(): Int? = when (phase) {
    MerchantPhase.RETURN_LATE -> 0
    MerchantPhase.PICKUP_IN_PROGRESS, MerchantPhase.RETURN_IN_PROGRESS -> 1
    MerchantPhase.PICKUP_READY, MerchantPhase.RETURN_DUE, MerchantPhase.DEPOSIT_TO_REFUND -> 2
    MerchantPhase.PICKUP_UPCOMING, MerchantPhase.WITH_CUSTOMER -> 3
    MerchantPhase.PICKUP_AWAITING_CUSTOMER, MerchantPhase.RETURN_AWAITING_CUSTOMER, MerchantPhase.DEPOSIT_AWAITING_CUSTOMER -> 4
    else -> null
}

/** The Home hero: best [heroTier], then the earliest server [referenceTime]. Ordering of server data only. */
fun pickHero(operations: List<Operation>): Operation? =
    operations.filter { it.heroTier() != null }.minWithOrNull(compareBy<Operation>({ it.heroTier() }, { it.referenceTime() }))

/**
 * Home lists, in the iOS order: « À collecter » and « À retourner » first (always shown), then « À traiter maintenant »
 * (late returns + TANY reviews), waiting for the customer, no-shows.
 */
enum class HomeSection(val sections: Set<OperationSection>) {
    TO_COLLECT(setOf(OperationSection.TO_COLLECT)),
    TO_RETURN(setOf(OperationSection.TO_RETURN)),
    NOW(setOf(OperationSection.LATE, OperationSection.ATTENTION)),
    AWAITING_CUSTOMER(setOf(OperationSection.AWAITING_CUSTOMER)),
    NO_SHOW(setOf(OperationSection.NO_SHOW)),
}

fun HomeSection.contains(operation: Operation): Boolean = operation.phase.ui().section in sections

/** Sections the Home always lists, even empty (iOS: a reassuring line under « À collecter » / « À retourner »). */
val ALWAYS_SHOWN_HOME_SECTIONS = setOf(HomeSection.TO_COLLECT, HomeSection.TO_RETURN)

/** [homeSections] plus the always-shown « À collecter » / « À retourner » (possibly empty), in Home order. */
fun homeSectionsWithAnchors(operations: List<Operation>, hero: Operation?): List<Pair<HomeSection, List<Operation>>> {
    val filled = homeSections(operations, hero).toMap()
    return HomeSection.entries.mapNotNull { section ->
        val items = filled[section] ?: emptyList()
        if (items.isEmpty() && section !in ALWAYS_SHOWN_HOME_SECTIONS) null else section to items
    }
}

/** Line under an empty always-shown section; « aucune autre » when the hero above is of that kind. */
@StringRes
fun HomeSection.emptyLine(heroInSection: Boolean): Int = when (this) {
    HomeSection.TO_COLLECT -> if (heroInSection) R.string.home_collect_empty_other else R.string.today_section_to_collect_empty
    HomeSection.TO_RETURN -> if (heroInSection) R.string.home_return_empty_other else R.string.today_section_to_return_empty
    else -> R.string.summary_all_clear
}

/**
 * Index in the Home list where a stat card leads: the hero when it belongs to that group, else the section header.
 * Items: [stale notice] · stats · hero · per section (header + rows or one empty line). Null = nothing to show.
 */
fun homeItemIndex(sections: List<Pair<HomeSection, List<Operation>>>, target: HomeSection, hero: Operation?, hasStale: Boolean): Int? {
    val heroIndex = (if (hasStale) 1 else 0) + 1
    if (hero != null && target.contains(hero)) return heroIndex
    var index = heroIndex + 1
    sections.forEach { (section, ops) ->
        if (section == target) return index
        index += 1 + maxOf(ops.size, 1)
    }
    return null
}

/**
 * Home sections without the hero (never shown twice) and without empty groups; each group in server-time order. Every
 * other operation of the server list appears exactly once.
 */
fun homeSections(operations: List<Operation>, hero: Operation?): List<Pair<HomeSection, List<Operation>>> {
    val rest = operations.filter { it.id != hero?.id }
    return HomeSection.entries.mapNotNull { home ->
        val items = rest.filter { it.phase.ui().section in home.sections }.sortedBy { it.referenceTime() }
        if (items.isEmpty()) null else home to items
    }
}

@StringRes
fun HomeSection.title(): Int = when (this) {
    HomeSection.NOW -> R.string.home_section_now
    HomeSection.TO_COLLECT -> R.string.today_section_to_collect
    HomeSection.TO_RETURN -> R.string.today_section_to_return
    HomeSection.AWAITING_CUSTOMER -> R.string.today_section_awaiting
    HomeSection.NO_SHOW -> R.string.today_section_no_show
}

/** Money worth showing on a card, with its meaning — null when no cash is involved in the current phase. */
data class RowMoney(@StringRes val label: Int, val amount: MoneyAmount)

fun Operation.rowMoney(): RowMoney? = when (phase) {
    MerchantPhase.PICKUP_UPCOMING, MerchantPhase.PICKUP_READY -> RowMoney(R.string.row_money_to_collect, pricing?.totalDueAtPickup ?: rentalAmount)
    MerchantPhase.DEPOSIT_TO_REFUND -> (depositRefundAmount ?: depositAmount)?.let { RowMoney(R.string.row_money_deposit_back, it) }
    else -> null
}

/** Pickup phases (the pickup itself is not done yet). */
private val PICKUP_PHASES = setOf(
    MerchantPhase.PICKUP_UPCOMING, MerchantPhase.PICKUP_READY, MerchantPhase.PICKUP_IN_PROGRESS, MerchantPhase.PICKUP_AWAITING_CUSTOMER, MerchantPhase.NO_SHOW,
)

/**
 * `completedAt` belongs to the operation of this row's kind only once it is over: a pickup past the pickup phases, or a
 * closed booking. (A `return_due` row may still carry the earlier pickup's `completedAt` — real backend capture.)
 */
fun Operation.isDoneForItsKind(): Boolean = phase.ui().section == OperationSection.OTHER ||
    (kind == OperationKind.PICKUP && phase !in PICKUP_PHASES)

/**
 * The time a card puts forward (server instants): the pickup window, the return deadline, the waiting start, or the
 * completion time. [end] null = a single time.
 */
data class RowTime(val start: Instant, val end: Instant?, @StringRes val prefix: Int?)

fun Operation.rowTime(): RowTime = when {
    completedAt != null && isDoneForItsKind() -> RowTime(completedAt!!, null, null)
    phase.ui().section == OperationSection.AWAITING_CUSTOMER -> RowTime(waitingSince ?: scheduledAt, null, R.string.row_time_since)
    kind == OperationKind.RETURN -> RowTime(returnDeadline, null, R.string.row_time_before)
    else -> RowTime(pickupWindowStart, pickupWindowEnd, null)
}

@StringRes
fun OperationSection.title(): Int = when (this) {
    OperationSection.LATE -> R.string.today_section_late
    OperationSection.ATTENTION -> R.string.today_section_attention
    OperationSection.TO_COLLECT -> R.string.today_section_to_collect
    OperationSection.TO_RETURN -> R.string.today_section_to_return
    OperationSection.AWAITING_CUSTOMER -> R.string.today_section_awaiting
    OperationSection.NO_SHOW -> R.string.today_section_no_show
    OperationSection.OTHER -> R.string.today_section_other
}

/** Line shown under an empty always-visible section. */
@StringRes
fun OperationSection.emptyLine(): Int? = when (this) {
    OperationSection.TO_COLLECT -> R.string.today_section_to_collect_empty
    OperationSection.TO_RETURN -> R.string.today_section_to_return_empty
    else -> null
}

@StringRes
fun MerchantDepositAction.label(): Int? = when (this) {
    MerchantDepositAction.COLLECT -> R.string.deposit_action_collect
    MerchantDepositAction.HAND_BACK -> R.string.deposit_action_hand_back
    MerchantDepositAction.NONE, MerchantDepositAction.UNKNOWN -> null
}

fun AssetTone.tone(): TanyTone = when (this) {
    AssetTone.SUCCESS -> TanyTone.SUCCESS
    AssetTone.INFO -> TanyTone.INFO
    AssetTone.ACTION -> TanyTone.ACTION
    AssetTone.WARNING -> TanyTone.WARNING
    AssetTone.DANGER -> TanyTone.DANGER
    AssetTone.NEUTRAL, AssetTone.UNKNOWN -> TanyTone.NEUTRAL
}

@StringRes
fun CollectAssetStatus.label(): Int = when (this) {
    CollectAssetStatus.AVAILABLE -> R.string.asset_status_available
    CollectAssetStatus.RESERVED -> R.string.asset_status_reserved
    CollectAssetStatus.TO_HAND_OVER -> R.string.asset_status_to_hand_over
    CollectAssetStatus.WITH_CUSTOMER -> R.string.asset_status_with_customer
    CollectAssetStatus.RETURN_DUE -> R.string.asset_status_return_due
    CollectAssetStatus.RETURN_LATE -> R.string.asset_status_return_late
    CollectAssetStatus.INSPECTION -> R.string.asset_status_inspection
    CollectAssetStatus.OUT_OF_SERVICE -> R.string.asset_status_out_of_service
    CollectAssetStatus.LOST -> R.string.asset_status_lost
    CollectAssetStatus.LOCATION_TO_CONFIRM -> R.string.asset_status_location_to_confirm
    CollectAssetStatus.UNKNOWN -> R.string.asset_status_unknown
}

@DrawableRes
fun OperationKind.icon(): Int = if (this == OperationKind.RETURN) DsR.drawable.ic_tany_return else DsR.drawable.ic_tany_pickup

@StringRes
fun OperationKind.label(): Int = if (this == OperationKind.RETURN) R.string.kind_return else R.string.kind_pickup

/** Amount of a row: the deposit to hand back in deposit phases, otherwise the rental total — server values only. */
fun Operation.rowAmount(): MoneyAmount = when (phase) {
    MerchantPhase.DEPOSIT_TO_REFUND, MerchantPhase.DEPOSIT_AWAITING_CUSTOMER -> depositRefundAmount ?: depositAmount ?: rentalAmount
    else -> pricing?.rentalTotal ?: rentalAmount
}

/** « 25 min » / « 2 h » / « 1 h 05 min » from a SERVER count of minutes. */
@Composable
fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.duration_hours, minutes / 60)
    else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

/** Phases with a live return countdown (to the SERVER `returnDeadline`). Lateness itself always comes from `phase`. */
private val COUNTDOWN_PHASES = setOf(MerchantPhase.WITH_CUSTOMER, MerchantPhase.RETURN_DUE, MerchantPhase.RETURN_IN_PROGRESS)

/**
 * The single exception / countdown line of a row, from server fields only (`phase`, `lateMinutes`, `waitingMinutes`,
 * `customerConfirmationOverdue`, `returnDeadline`). Null = nothing to add.
 */
@Composable
fun Operation.exceptionLine(): Pair<String, TanyTone>? = when (phase) {
    MerchantPhase.NO_SHOW -> stringResource(R.string.row_exception_no_show) to TanyTone.NEUTRAL
    MerchantPhase.RETURN_LATE -> {
        val late = lateMinutes?.takeIf { it > 0 }
        (if (late != null) stringResource(R.string.row_exception_late_for, durationText(late)) else stringResource(R.string.row_exception_late)) to TanyTone.DANGER
    }
    MerchantPhase.PICKUP_AWAITING_CUSTOMER, MerchantPhase.RETURN_AWAITING_CUSTOMER, MerchantPhase.DEPOSIT_AWAITING_CUSTOMER -> {
        val waiting = waitingMinutes
        val text = when {
            waiting == null -> stringResource(R.string.row_exception_waiting)
            waiting < 1 -> stringResource(R.string.row_exception_waiting_now)
            else -> stringResource(R.string.row_exception_waiting_for, durationText(waiting))
        }
        text to (if (customerConfirmationOverdue) TanyTone.DANGER else TanyTone.WARNING)
    }
    MerchantPhase.DEPOSIT_TO_REFUND -> stringResource(R.string.row_exception_deposit) to TanyTone.WARNING
    MerchantPhase.BLOCKED_PENDING_REVIEW -> stringResource(R.string.row_exception_review) to TanyTone.DANGER
    MerchantPhase.DEPOSIT_DISPUTED -> stringResource(R.string.row_exception_disputed) to TanyTone.DANGER
    in COUNTDOWN_PHASES -> {
        // Ticks every 15 s; no network call. Below one minute the line stays « less than 1 min » until the server phase
        // says the return is late.
        val now by produceState(Instant.now(), returnDeadline) {
            while (true) {
                value = Instant.now()
                delay(15_000)
            }
        }
        val minutes = Duration.between(now, returnDeadline).toMinutes().toInt()
        (if (minutes < 1) stringResource(R.string.row_countdown_soon) else stringResource(R.string.row_countdown, durationText(minutes))) to TanyTone.NEUTRAL
    }
    else -> null
}

/**
 * Operation card content, ordered for a 2-second read: TIME (large) + server status, operation type, the object (image
 * large enough to recognise it, FULL product name, unit code), customer / reference, cash only when it matters, then
 * one exception or countdown line. The whole card opens the operation (no inline action: the server state drives it).
 */
@Composable
fun OperationRowContent(operation: Operation, endpoint: ApiEndpoint, modifier: Modifier = Modifier, showDay: Boolean = false) {
    val formatters = LocalTanyFormatters.current
    val colors = TanyTheme.colors
    val phase = operation.phase.ui()
    val period = operation.effectiveUsagePeriod()
    val time = operation.rowTime()
    val timeText = buildString {
        if (showDay) append(formatters.businessDay(ma.tany.core.model.common.BusinessTime.businessDate(time.start))).append(" · ")
        append(time.end?.let { formatters.businessTimeRange(time.start, it) } ?: formatters.businessTime(time.start))
    }
    val exception = operation.exceptionLine()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // « 09:30 · Collecte » + server status: when and what, on one line (iOS row).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(operation.kind.icon()), contentDescription = null, tint = phase.tone.colors().accent, modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    withStyle(TanyTheme.typography.title.copy(fontFeatureSettings = "tnum").toSpanStyle()) {
                        append((time.prefix?.let { stringResource(it) + " " } ?: "") + ltrIsolated(timeText))
                    }
                    withStyle(TanyTheme.typography.body.toSpanStyle().copy(color = colors.textMuted)) {
                        append(" · ")
                        append(stringResource(operation.kind.label()))
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TanyStatusChip(stringResource(phase.short), phase.tone, size = TanyChipSize.SMALL)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(operation.product.displayImage),
                contentDescription = null,
                modifier = Modifier.width(80.dp),
                padding = 6.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Full name: wraps instead of « Coll… ».
                Text(operation.product.name, style = TanyTheme.typography.headline)
                operation.assetCode?.let { TanyCodePill(ltrIsolated(it)) }
                Text(
                    "${operation.customer.shortName} · ${ltrIsolated(operation.reference)}",
                    style = TanyTheme.typography.label,
                    color = colors.textMuted,
                )
                if (period.dayCount > 1) {
                    Text(
                        "${pluralStringResource(R.plurals.booking_days, period.dayCount, period.dayCount)} · ${formatters.usagePeriod(period.startDate, period.endDate)}",
                        style = TanyTheme.typography.caption,
                        color = colors.textMuted,
                    )
                }
            }
        }
        operation.rowMoney()?.let { money ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(money.label), style = TanyTheme.typography.label, color = colors.textMuted, modifier = Modifier.weight(1f))
                MoneyText(money.amount)
            }
        }
        exception?.let { (text, tone) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painterResource(if (tone == TanyTone.DANGER) DsR.drawable.ic_tany_warning else DsR.drawable.ic_tany_clock),
                    contentDescription = null,
                    tint = if (tone == TanyTone.NEUTRAL) colors.textMuted else tone.colors().accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text,
                    style = TanyTheme.typography.label,
                    color = if (tone == TanyTone.NEUTRAL) colors.textMuted else tone.colors().accent,
                )
            }
        }
    }
}

/** One sentence for TalkBack (type, reference, time, product, item, amount, status, exception). */
@Composable
fun Operation.spokenSummary(): String {
    val formatters = LocalTanyFormatters.current
    val phase = phase.ui()
    val parts = listOfNotNull(
        stringResource(kind.label()),
        reference,
        formatters.businessTime(rowTime().start),
        product.name,
        assetCode?.let { stringResource(R.string.row_item_code, it) },
        rowMoney()?.let { formatters.money(it.amount) },
        stringResource(phase.short),
        exceptionLine()?.first,
    )
    return parts.joinToString(", ")
}

/** Card wrapper of [OperationRowContent] (Today). Danger sections get the leading status bar. */
@Composable
fun OperationCard(operation: Operation, endpoint: ApiEndpoint, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val phase = operation.phase.ui()
    val summary = operation.spokenSummary()
    val openLabel = stringResource(R.string.operation_open)
    TanyCard(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = summary
            role = Role.Button
            onClick(label = openLabel) {
                onClick()
                true
            }
        },
        onClick = onClick,
        accent = phase.tone.takeIf { it == TanyTone.DANGER },
        contentPadding = 16.dp,
    ) {
        OperationRowContent(operation, endpoint)
    }
}

/** Row wrapper of [OperationRowContent] for grouped lists (Activity). */
@Composable
fun OperationListRow(operation: Operation, endpoint: ApiEndpoint, onClick: () -> Unit, showDay: Boolean) {
    val summary = operation.spokenSummary()
    val openLabel = stringResource(R.string.operation_open)
    OperationRowContent(
        operation = operation,
        endpoint = endpoint,
        showDay = showDay,
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = summary
                role = Role.Button
                onClick(label = openLabel) {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
