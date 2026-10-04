package ma.tany.collect.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import ma.tany.collect.R
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetTone
import ma.tany.core.model.collect.CollectAssetStatus
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.network.ApiEndpoint

/** Presentation of SERVER phases. The merchant's next step is never computed by the app. */
data class PhaseUi(@StringRes val label: Int, val tone: TanyTone)

fun MerchantPhase.ui(): PhaseUi = when (this) {
    MerchantPhase.PICKUP_UPCOMING -> PhaseUi(R.string.phase_pickup_upcoming, TanyTone.NEUTRAL)
    MerchantPhase.PICKUP_READY -> PhaseUi(R.string.phase_pickup_ready, TanyTone.ACTION)
    MerchantPhase.PICKUP_IN_PROGRESS -> PhaseUi(R.string.phase_pickup_in_progress, TanyTone.INFO)
    MerchantPhase.PICKUP_AWAITING_CUSTOMER -> PhaseUi(R.string.phase_pickup_awaiting_customer, TanyTone.WARNING)
    MerchantPhase.NO_SHOW -> PhaseUi(R.string.phase_no_show, TanyTone.NEUTRAL)
    MerchantPhase.WITH_CUSTOMER -> PhaseUi(R.string.phase_with_customer, TanyTone.INFO)
    MerchantPhase.RETURN_DUE -> PhaseUi(R.string.phase_return_due, TanyTone.ACTION)
    MerchantPhase.RETURN_LATE -> PhaseUi(R.string.phase_return_late, TanyTone.DANGER)
    MerchantPhase.RETURN_IN_PROGRESS -> PhaseUi(R.string.phase_return_in_progress, TanyTone.INFO)
    MerchantPhase.RETURN_AWAITING_CUSTOMER -> PhaseUi(R.string.phase_return_awaiting_customer, TanyTone.WARNING)
    MerchantPhase.DEPOSIT_TO_REFUND -> PhaseUi(R.string.phase_deposit_to_refund, TanyTone.ACTION)
    MerchantPhase.DEPOSIT_AWAITING_CUSTOMER -> PhaseUi(R.string.phase_deposit_awaiting_customer, TanyTone.WARNING)
    MerchantPhase.DEPOSIT_DISPUTED -> PhaseUi(R.string.phase_deposit_disputed, TanyTone.DANGER)
    MerchantPhase.BLOCKED_PENDING_REVIEW -> PhaseUi(R.string.phase_blocked_pending_review, TanyTone.WARNING)
    MerchantPhase.COMPLETED -> PhaseUi(R.string.phase_completed, TanyTone.SUCCESS)
    MerchantPhase.CANCELLED -> PhaseUi(R.string.phase_cancelled, TanyTone.NEUTRAL)
    MerchantPhase.UNKNOWN -> PhaseUi(R.string.phase_unknown, TanyTone.NEUTRAL)
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

/** Operation card: phase chip, kind, product (white studio), customer short name, period, deposit gesture. */
@Composable
fun OperationCard(operation: Operation, endpoint: ApiEndpoint, onClick: () -> Unit) {
    val formatters = LocalTanyFormatters.current
    val phase = operation.phase.ui()
    val period = operation.effectiveUsagePeriod()
    TanyCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(operation.product.displayImage),
                contentDescription = operation.product.name,
                modifier = Modifier.width(64.dp),
                padding = 6.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TanyStatusChip(stringResource(phase.label), phase.tone)
                Text(
                    "${stringResource(if (operation.kind == OperationKind.RETURN) R.string.kind_return else R.string.kind_pickup)} · ${operation.product.name}",
                    style = TanyTheme.typography.headline,
                )
                Text(
                    "${operation.customer.shortName} · ${formatters.usagePeriod(period.startDate, period.endDate)} · " +
                        pluralStringResource(R.plurals.booking_days, period.dayCount, period.dayCount),
                    style = TanyTheme.typography.caption,
                    color = TanyTheme.colors.textMuted,
                )
                Text(ltrIsolated(operation.reference), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                operation.depositAction.label()?.let { TanyStatusChip(stringResource(it), TanyTone.WARNING) }
            }
        }
    }
}
