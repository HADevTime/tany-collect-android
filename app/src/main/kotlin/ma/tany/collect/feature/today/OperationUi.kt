package ma.tany.collect.feature.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.tany.collect.R
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyToneIcon
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetTone
import ma.tany.core.model.collect.CollectAssetStatus
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.network.ApiEndpoint

/**
 * Where a SERVER phase is listed on Today. Pure presentation of `phase` (same nature as its tone): the app never
 * decides what the merchant must do next — it only groups the server's phases so the counter work stands out.
 */
enum class OperationSection {
    /** The merchant is the one expected at the counter (handover, reception, deposit hand-back). */
    TO_HANDLE,

    /** Waiting for the customer's confirmation in TANY or for a TANY decision. */
    WAITING,

    /** Later today / with the customer / closed. */
    LATER,
}

/** Presentation of SERVER phases. The merchant's next step is never computed by the app. */
data class PhaseUi(
    @StringRes val label: Int,
    val tone: TanyTone,
    val section: OperationSection,
    /** One-line explanation of what the phase means (booking detail banner). */
    @StringRes val description: Int,
)

fun MerchantPhase.ui(): PhaseUi = when (this) {
    MerchantPhase.PICKUP_UPCOMING -> PhaseUi(R.string.phase_pickup_upcoming, TanyTone.NEUTRAL, OperationSection.LATER, R.string.phase_desc_pickup_upcoming)
    MerchantPhase.PICKUP_READY -> PhaseUi(R.string.phase_pickup_ready, TanyTone.ACTION, OperationSection.TO_HANDLE, R.string.phase_desc_pickup_ready)
    MerchantPhase.PICKUP_IN_PROGRESS -> PhaseUi(R.string.phase_pickup_in_progress, TanyTone.INFO, OperationSection.TO_HANDLE, R.string.phase_desc_pickup_in_progress)
    MerchantPhase.PICKUP_AWAITING_CUSTOMER ->
        PhaseUi(R.string.phase_pickup_awaiting_customer, TanyTone.WARNING, OperationSection.WAITING, R.string.phase_desc_pickup_awaiting_customer)
    MerchantPhase.NO_SHOW -> PhaseUi(R.string.phase_no_show, TanyTone.NEUTRAL, OperationSection.LATER, R.string.phase_desc_no_show)
    MerchantPhase.WITH_CUSTOMER -> PhaseUi(R.string.phase_with_customer, TanyTone.INFO, OperationSection.LATER, R.string.phase_desc_with_customer)
    MerchantPhase.RETURN_DUE -> PhaseUi(R.string.phase_return_due, TanyTone.ACTION, OperationSection.TO_HANDLE, R.string.phase_desc_return_due)
    MerchantPhase.RETURN_LATE -> PhaseUi(R.string.phase_return_late, TanyTone.DANGER, OperationSection.TO_HANDLE, R.string.phase_desc_return_late)
    MerchantPhase.RETURN_IN_PROGRESS -> PhaseUi(R.string.phase_return_in_progress, TanyTone.INFO, OperationSection.TO_HANDLE, R.string.phase_desc_return_in_progress)
    MerchantPhase.RETURN_AWAITING_CUSTOMER ->
        PhaseUi(R.string.phase_return_awaiting_customer, TanyTone.WARNING, OperationSection.WAITING, R.string.phase_desc_return_awaiting_customer)
    MerchantPhase.DEPOSIT_TO_REFUND -> PhaseUi(R.string.phase_deposit_to_refund, TanyTone.ACTION, OperationSection.TO_HANDLE, R.string.phase_desc_deposit_to_refund)
    MerchantPhase.DEPOSIT_AWAITING_CUSTOMER ->
        PhaseUi(R.string.phase_deposit_awaiting_customer, TanyTone.WARNING, OperationSection.WAITING, R.string.phase_desc_deposit_awaiting_customer)
    MerchantPhase.DEPOSIT_DISPUTED -> PhaseUi(R.string.phase_deposit_disputed, TanyTone.DANGER, OperationSection.WAITING, R.string.phase_desc_deposit_disputed)
    MerchantPhase.BLOCKED_PENDING_REVIEW ->
        PhaseUi(R.string.phase_blocked_pending_review, TanyTone.WARNING, OperationSection.WAITING, R.string.phase_desc_blocked_pending_review)
    MerchantPhase.COMPLETED -> PhaseUi(R.string.phase_completed, TanyTone.SUCCESS, OperationSection.LATER, R.string.phase_desc_completed)
    MerchantPhase.CANCELLED -> PhaseUi(R.string.phase_cancelled, TanyTone.NEUTRAL, OperationSection.LATER, R.string.phase_desc_cancelled)
    MerchantPhase.UNKNOWN -> PhaseUi(R.string.phase_unknown, TanyTone.NEUTRAL, OperationSection.LATER, R.string.phase_desc_unknown)
}

/**
 * Groups the server's operations by [OperationSection], keeping the SERVER order inside each group (the backend
 * already sorts by urgency). Empty sections are dropped.
 */
fun groupBySection(operations: List<Operation>): List<Pair<OperationSection, List<Operation>>> =
    OperationSection.entries.mapNotNull { section ->
        operations.filter { it.phase.ui().section == section }.takeIf { it.isNotEmpty() }?.let { section to it }
    }

@StringRes
fun OperationSection.title(): Int = when (this) {
    OperationSection.TO_HANDLE -> R.string.today_section_to_handle
    OperationSection.WAITING -> R.string.today_section_waiting
    OperationSection.LATER -> R.string.today_section_later
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

/** « Collecte · 09:00–11:00 » / « Retour · avant lun. 5 oct. · 18:00 » — times always in Africa/Casablanca. */
@Composable
fun Operation.scheduleLine(): String {
    val formatters = LocalTanyFormatters.current
    return if (kind == OperationKind.RETURN) {
        stringResource(R.string.operation_return_before, stringResource(kind.label()), formatters.businessDayTime(returnDeadline))
    } else {
        "${stringResource(kind.label())} · ${formatters.businessTimeRange(pickupWindowStart, pickupWindowEnd)}"
    }
}

/**
 * Operation card: kind tile + schedule, product (white studio), server phase / deposit gesture / lateness chips, then
 * customer short name, usage period and reference. The leading accent bar marks cards the merchant handles now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationCard(operation: Operation, endpoint: ApiEndpoint, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatters = LocalTanyFormatters.current
    val phase = operation.phase.ui()
    val period = operation.effectiveUsagePeriod()
    val colors = TanyTheme.colors
    TanyCard(
        modifier = modifier,
        onClick = onClick,
        accent = phase.tone.takeIf { phase.section == OperationSection.TO_HANDLE && (it == TanyTone.ACTION || it == TanyTone.DANGER) },
        onClickLabel = stringResource(R.string.operation_open),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TanyToneIcon(operation.kind.icon(), phase.tone, size = 28.dp)
                    Text(
                        operation.scheduleLine(),
                        style = TanyTheme.typography.label,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(operation.product.name, style = TanyTheme.typography.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            ProductImageSurface(
                url = endpoint.resolveMedia(operation.product.displayImage),
                contentDescription = operation.product.name,
                modifier = Modifier.width(64.dp),
                padding = 6.dp,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TanyStatusChip(stringResource(phase.label), phase.tone, size = TanyChipSize.SMALL)
            operation.depositAction.label()?.let { TanyStatusChip(stringResource(it), TanyTone.WARNING, size = TanyChipSize.SMALL) }
            operation.lateMinutes?.takeIf { it > 0 }?.let {
                TanyStatusChip(stringResource(R.string.operation_late_minutes, it), TanyTone.DANGER, size = TanyChipSize.SMALL)
            }
            if (operation.customerConfirmationOverdue) {
                val waiting = operation.waitingMinutes
                TanyStatusChip(
                    if (waiting != null) stringResource(R.string.operation_waiting_minutes, waiting) else stringResource(R.string.operation_waiting),
                    TanyTone.WARNING,
                    size = TanyChipSize.SMALL,
                )
            }
        }
        TanyDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Minimal customer data only: short name (verbatim proper noun).
                Text(operation.customer.shortName, style = TanyTheme.typography.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatters.usagePeriod(period.startDate, period.endDate)} · " +
                        pluralStringResource(R.plurals.booking_days, period.dayCount, period.dayCount),
                    style = TanyTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            TanyCodePill(ltrIsolated(operation.reference))
        }
    }
}
