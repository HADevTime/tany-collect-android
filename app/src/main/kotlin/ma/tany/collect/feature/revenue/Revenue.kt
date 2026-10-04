package ma.tany.collect.feature.revenue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.BonusTier
import ma.tany.core.model.collect.RevenueActionKind
import ma.tany.core.model.collect.RevenueActivity
import ma.tany.core.model.collect.RevenueActivityKind
import ma.tany.core.model.collect.RevenueAgreement
import ma.tany.core.model.collect.RevenueMetric
import ma.tany.core.model.collect.RevenueOverview
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.network.CollectBusinessRepository
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Revenue of the active point — ESTIMATED earnings only (never "paid / settled / balance"), deposits shown apart
 * (a deposit is never revenue). Everything is the server's computation; the app only navigates between its periods.
 */
@HiltViewModel
class RevenueViewModel @Inject constructor(private val repository: CollectBusinessRepository) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<RevenueOverview>>(LoadState.Loading)
    val state: StateFlow<LoadState<RevenueOverview>> = _state.asStateFlow()

    /** [period] = a key given by the server (`previousKey` / `nextKey`), null = current month. */
    fun load(pointId: String, period: String? = null) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.revenue(pointId, period).toLoadState() }
    }
}

@Composable
fun RevenueScreen(
    pointId: String,
    onBack: () -> Unit,
    onOpenBooking: (String) -> Unit,
    onOpenSettlement: () -> Unit,
    viewModel: RevenueViewModel = hiltViewModel(),
) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.revenue_title), onBack = onBack, chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled) {
                TanyEmptyState(title = stringResource(R.string.revenue_disabled))
            } else {
                Content(s.value, onPeriod = { viewModel.load(pointId, it) }, onOpenBooking = onOpenBooking, onOpenSettlement = onOpenSettlement)
            }
        }
    }
}

@Composable
private fun Content(revenue: RevenueOverview, onPeriod: (String) -> Unit, onOpenBooking: (String) -> Unit, onOpenSettlement: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        revenue.period?.let { period ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val previous = period.previousKey
                val next = period.nextKey
                TanyButton("‹", { previous?.let(onPeriod) }, style = TanyButtonStyle.TEXT, enabled = previous != null, modifier = Modifier.weight(1f))
                Text(
                    monthLabel(period.key),
                    style = TanyTheme.typography.headline,
                    modifier = Modifier
                        .weight(3f)
                        .semantics { heading() },
                )
                TanyButton("›", { next?.let(onPeriod) }, style = TanyButtonStyle.TEXT, enabled = next != null, modifier = Modifier.weight(1f))
            }
        }
        revenue.earnings?.let { earnings ->
            TanyCard {
                TanyStatusChip(stringResource(R.string.revenue_estimated), TanyTone.INFO)
                Text(stringResource(R.string.revenue_earnings), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
                MoneyText(earnings.amount, style = TanyTheme.typography.display)
                TanyInfoRow(stringResource(R.string.revenue_commission)) { MoneyText(earnings.commissionAmount) }
                TanyInfoRow(stringResource(R.string.revenue_bonus)) { MoneyText(earnings.bonusAmount) }
                if (!earnings.adjustmentAmount.isZero) TanyInfoRow(stringResource(R.string.revenue_adjustments)) { MoneyText(earnings.adjustmentAmount) }
                TanyInfoRow(stringResource(R.string.revenue_previous)) { MoneyText(earnings.previousAmount) }
                if (!earnings.hasAgreement) {
                    Text(stringResource(R.string.revenue_no_agreement), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                }
                Text(stringResource(R.string.revenue_disclaimer), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
            }
        }
        revenue.performance?.let { p ->
            TanyCard {
                TanyInfoRow(stringResource(R.string.revenue_rentals)) { Count(p.rentals) }
                TanyInfoRow(stringResource(R.string.revenue_rental_revenue)) { MoneyText(p.rentalRevenue) }
            }
        }
        if (revenue.actions.isNotEmpty()) {
            Section(stringResource(R.string.revenue_actions))
            revenue.actions.forEach { action ->
                val bookingId = action.bookingId
                TanyCard(onClick = bookingId?.let { { onOpenBooking(it) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TanyStatusChip(
                            stringResource(if (action.kind == RevenueActionKind.HAND_BACK_DEPOSIT) R.string.deposit_action_hand_back else R.string.revenue_action_other),
                            TanyTone.WARNING,
                        )
                        action.amount?.let { MoneyText(it) }
                    }
                    Text(
                        listOfNotNull(action.productName, action.bookingReference?.let(::ltrIsolated), action.customerShortName).joinToString(" · "),
                        style = TanyTheme.typography.body,
                    )
                }
            }
        }
        revenue.bonus?.takeIf { it.tiers.isNotEmpty() }?.let { bonus ->
            Section(stringResource(R.string.revenue_bonus_title))
            TanyCard {
                TanyInfoRow(stringResource(R.string.revenue_bonus_unlocked, bonus.unlockedCount)) { MoneyText(bonus.unlockedAmount) }
                bonus.tiers.forEach { BonusRow(it) }
            }
        }
        revenue.deposits?.let { d ->
            Section(stringResource(R.string.revenue_deposits_title))
            TanyCard {
                Text(stringResource(R.string.revenue_deposits_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                TanyInfoRow(stringResource(R.string.revenue_deposits_held, d.count)) { MoneyText(d.amount) }
                TanyInfoRow(stringResource(R.string.revenue_deposits_to_hand_back, d.toHandBack.count)) { MoneyText(d.toHandBack.amount) }
                TanyInfoRow(stringResource(R.string.revenue_deposits_awaiting, d.awaitingCustomer.count)) { MoneyText(d.awaitingCustomer.amount) }
                TanyInfoRow(stringResource(R.string.revenue_deposits_review, d.underReview.count)) { MoneyText(d.underReview.amount) }
            }
        }
        TanyButton(stringResource(R.string.settlement_title), onOpenSettlement, style = TanyButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        if (revenue.activity.isNotEmpty()) {
            Section(stringResource(R.string.revenue_activity))
            TanyCard { revenue.activity.forEach { ActivityRow(it, onOpenBooking) } }
        }
        revenue.agreement?.let { AgreementCard(it, upcoming = false) }
        revenue.upcomingAgreement?.let { AgreementCard(it, upcoming = true) }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Count(value: Int) = Text(ltrIsolated(value.toString()), style = TanyTheme.typography.bodyStrong)

/** Locally formatted month of a server period key (`YYYY-MM`) — never the server's French label. */
@Composable
private fun monthLabel(key: String): String {
    val locale = LocalTanyFormatters.current.language.locale
    return runCatching { DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(YearMonth.parse(key)) }.getOrDefault(key)
        .replaceFirstChar { it.titlecase(locale) }
}

/** Target of a bonus metric: a number of rentals, or an amount of rental revenue. */
@Composable
private fun metricTarget(metric: RevenueMetric, value: MoneyAmount): String = when (metric) {
    RevenueMetric.RENTAL_REVENUE -> stringResource(R.string.revenue_metric_amount, LocalTanyFormatters.current.money(value))
    else -> stringResource(R.string.revenue_metric_count, value.major.toInt())
}

@Composable
private fun BonusRow(tier: BonusTier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(metricTarget(tier.metric, tier.threshold), style = TanyTheme.typography.body, modifier = Modifier.weight(1f))
            Text("+" + LocalTanyFormatters.current.money(tier.reward), style = TanyTheme.typography.bodyStrong)
        }
        if (tier.achieved) {
            TanyStatusChip(stringResource(R.string.revenue_bonus_achieved), TanyTone.SUCCESS)
        } else {
            tier.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    color = TanyTheme.colors.accent,
                    trackColor = TanyTheme.colors.neutral,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            tier.remaining?.let {
                Text(
                    stringResource(R.string.revenue_bonus_remaining, metricTarget(tier.metric, it)),
                    style = TanyTheme.typography.caption,
                    color = TanyTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(item: RevenueActivity, onOpenBooking: (String) -> Unit) {
    val bookingId = item.bookingId
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(item.kind.label()), style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
            MoneyText(item.amount)
        }
        val context = listOfNotNull(item.productName, item.bookingReference?.let(::ltrIsolated), item.note).joinToString(" · ")
        if (context.isNotEmpty()) Text(context, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        BusinessDateTimeText(item.at, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        if (bookingId != null) {
            TanyButton(stringResource(R.string.revenue_open_booking), { onOpenBooking(bookingId) }, style = TanyButtonStyle.TEXT)
        }
    }
}

private fun RevenueActivityKind.label(): Int = when (this) {
    RevenueActivityKind.RENTAL_COMPLETED -> R.string.revenue_kind_rental
    RevenueActivityKind.BONUS_UNLOCKED -> R.string.revenue_kind_bonus
    RevenueActivityKind.DEPOSIT_RECEIVED -> R.string.revenue_kind_deposit_received
    RevenueActivityKind.DEPOSIT_REFUNDED -> R.string.revenue_kind_deposit_refunded
    RevenueActivityKind.ADJUSTMENT -> R.string.revenue_kind_adjustment
    RevenueActivityKind.UNKNOWN -> R.string.revenue_kind_other
}

@Composable
private fun AgreementCard(agreement: RevenueAgreement, upcoming: Boolean) {
    TanyCard {
        Text(stringResource(if (upcoming) R.string.revenue_agreement_upcoming else R.string.revenue_agreement), style = TanyTheme.typography.headline)
        agreement.commissionRatePercent?.let { rate ->
            val text = java.math.BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString()
            TanyInfoRow(stringResource(R.string.revenue_commission_rate)) { Text(ltrIsolated("$text %"), style = TanyTheme.typography.bodyStrong) }
        }
        agreement.effectiveFrom?.let { TanyInfoRow(stringResource(R.string.revenue_agreement_from)) { BusinessDateTimeText(it) } }
        agreement.bonuses.forEach { bonus ->
            TanyInfoRow(metricTarget(bonus.metric, bonus.threshold)) {
                Text("+" + LocalTanyFormatters.current.money(bonus.reward), style = TanyTheme.typography.bodyStrong)
            }
        }
        // Admin note: verbatim.
        agreement.note?.let { Text(it, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted) }
    }
}
