package ma.tany.collect.feature.revenue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyCardStyle
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDetailSkeleton
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyMetricTile
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyToneIcon
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyDimens
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
        TanyTopBar(title = stringResource(R.string.revenue_title), onBack = onBack)
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled) {
                TanyEmptyState(title = stringResource(R.string.revenue_disabled), icon = DsR.drawable.ic_tany_wallet)
            } else {
                Content(s.value, onPeriod = { viewModel.load(pointId, it) }, onOpenBooking = onOpenBooking, onOpenSettlement = onOpenSettlement)
            }
        }
    }
}

@Composable
private fun Content(revenue: RevenueOverview, onPeriod: (String) -> Unit, onOpenBooking: (String) -> Unit, onOpenSettlement: () -> Unit) {
    val colors = TanyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        revenue.period?.let { period -> PeriodSwitcher(period.key, period.previousKey, period.nextKey, onPeriod) }
        revenue.earnings?.let { earnings ->
            // ESTIMATED earnings, never « paid / settled / balance ».
            TanyCard(contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.revenue_earnings), style = TanyTheme.typography.label, color = colors.textMuted, modifier = Modifier.weight(1f))
                    TanyStatusChip(stringResource(R.string.revenue_estimated), TanyTone.INFO, size = TanyChipSize.SMALL)
                }
                MoneyText(earnings.amount, style = TanyTheme.typography.amountHero)
                TanyDivider()
                TanyInfoRow(stringResource(R.string.revenue_commission)) { MoneyText(earnings.commissionAmount) }
                TanyInfoRow(stringResource(R.string.revenue_bonus)) { MoneyText(earnings.bonusAmount) }
                if (!earnings.adjustmentAmount.isZero) TanyInfoRow(stringResource(R.string.revenue_adjustments)) { MoneyText(earnings.adjustmentAmount) }
                TanyInfoRow(stringResource(R.string.revenue_previous)) { MoneyText(earnings.previousAmount, color = colors.textMuted) }
                if (!earnings.hasAgreement) {
                    TanyNotice(message = stringResource(R.string.revenue_no_agreement), tone = TanyTone.NEUTRAL)
                }
            }
        }
        revenue.performance?.let { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TanyMetricTile(p.rentals.toString(), stringResource(R.string.revenue_rentals), Modifier.weight(1f))
                TanyMetricTile(LocalTanyFormatters.current.money(p.rentalRevenue), stringResource(R.string.revenue_rental_revenue), Modifier.weight(1f))
            }
        }
        if (revenue.actions.isNotEmpty()) {
            TanySectionHeader(stringResource(R.string.revenue_actions), trailing = revenue.actions.size.toString(), modifier = Modifier.padding(top = 8.dp))
            revenue.actions.forEach { action ->
                val bookingId = action.bookingId
                TanyCard(onClick = bookingId?.let { { onOpenBooking(it) } }, accent = TanyTone.WARNING) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TanyStatusChip(
                            stringResource(if (action.kind == RevenueActionKind.HAND_BACK_DEPOSIT) R.string.deposit_action_hand_back else R.string.revenue_action_other),
                            TanyTone.WARNING,
                            size = TanyChipSize.SMALL,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.weight(1f))
                        action.amount?.let { MoneyText(it, style = TanyTheme.typography.headline) }
                    }
                    action.productName?.let { Text(it, style = TanyTheme.typography.bodyStrong) }
                    val context = listOfNotNull(action.bookingReference?.let(::ltrIsolated), action.customerShortName).joinToString(" · ")
                    if (context.isNotEmpty()) Text(context, style = TanyTheme.typography.caption, color = colors.textMuted)
                }
            }
        }
        revenue.bonus?.takeIf { it.tiers.isNotEmpty() }?.let { bonus ->
            TanySectionHeader(stringResource(R.string.revenue_bonus_title), modifier = Modifier.padding(top = 8.dp))
            TanyCard {
                TanyInfoRow(stringResource(R.string.revenue_bonus_unlocked, bonus.unlockedCount), icon = DsR.drawable.ic_tany_star, emphasized = true) {
                    MoneyText(bonus.unlockedAmount)
                }
                bonus.tiers.forEach {
                    TanyDivider()
                    BonusRow(it)
                }
            }
        }
        revenue.deposits?.let { d ->
            // A deposit is never revenue: shown apart, on a neutral filled card.
            TanySectionHeader(stringResource(R.string.revenue_deposits_title), modifier = Modifier.padding(top = 8.dp))
            TanyCard(style = TanyCardStyle.FILLED) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(
                        painterResource(DsR.drawable.ic_tany_lock),
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.revenue_deposits_note), style = TanyTheme.typography.caption, color = colors.textMuted)
                }
                TanyInfoRow(stringResource(R.string.revenue_deposits_held, d.count)) { MoneyText(d.amount) }
                TanyInfoRow(stringResource(R.string.revenue_deposits_to_hand_back, d.toHandBack.count)) { MoneyText(d.toHandBack.amount) }
                TanyInfoRow(stringResource(R.string.revenue_deposits_awaiting, d.awaitingCustomer.count)) { MoneyText(d.awaitingCustomer.amount) }
                TanyInfoRow(stringResource(R.string.revenue_deposits_review, d.underReview.count)) { MoneyText(d.underReview.amount) }
            }
        }
        TanyCard(contentPadding = 0.dp) {
            TanyRow(
                title = stringResource(R.string.settlement_title),
                subtitle = stringResource(R.string.account_settlement_hint),
                leadingIcon = DsR.drawable.ic_tany_receipt,
                leadingTone = TanyTone.INFO,
                onClick = onOpenSettlement,
            )
        }
        if (revenue.activity.isNotEmpty()) {
            TanySectionHeader(stringResource(R.string.revenue_activity), modifier = Modifier.padding(top = 8.dp))
            TanyCard(contentPadding = 0.dp) {
                Column {
                    revenue.activity.forEachIndexed { i, item ->
                        if (i > 0) TanyDivider(inset = 66.dp)
                        ActivityRow(item, onOpenBooking)
                    }
                }
            }
        }
        revenue.agreement?.let { AgreementCard(it, upcoming = false) }
        revenue.upcomingAgreement?.let { AgreementCard(it, upcoming = true) }
        Text(
            stringResource(R.string.revenue_disclaimer),
            style = TanyTheme.typography.caption,
            color = colors.textSubtle,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

/** Month navigation between the SERVER's period keys (previous / next given by the server). */
@Composable
private fun PeriodSwitcher(key: String, previous: String?, next: String?, onPeriod: (String) -> Unit) {
    val colors = TanyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TanyTheme.radii.large)
            .background(colors.surface)
            .border(TanyDimens.BorderWidth, colors.border, TanyTheme.radii.large)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { previous?.let(onPeriod) }, enabled = previous != null) {
            androidx.compose.material3.Icon(
                painterResource(DsR.drawable.ic_tany_chevron_start),
                contentDescription = stringResource(R.string.revenue_previous_period),
                tint = if (previous != null) colors.textPrimary else colors.border,
            )
        }
        Text(
            monthLabel(key),
            style = TanyTheme.typography.headline,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = { next?.let(onPeriod) }, enabled = next != null) {
            androidx.compose.material3.Icon(
                painterResource(DsR.drawable.ic_tany_chevron),
                contentDescription = stringResource(R.string.revenue_next_period),
                tint = if (next != null) colors.textPrimary else colors.border,
            )
        }
    }
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
    val colors = TanyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bookingId != null) Modifier.clickable(role = Role.Button, onClick = { onOpenBooking(bookingId) }) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TanyToneIcon(item.kind.icon(), item.kind.tone(), size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(item.kind.label()), style = TanyTheme.typography.bodyStrong)
            val context = listOfNotNull(item.productName, item.bookingReference?.let(::ltrIsolated), item.note).joinToString(" · ")
            if (context.isNotEmpty()) Text(context, style = TanyTheme.typography.caption, color = colors.textMuted, maxLines = 2)
            BusinessDateTimeText(item.at, style = TanyTheme.typography.caption, color = colors.textSubtle)
        }
        MoneyText(item.amount)
    }
}

private fun RevenueActivityKind.icon(): Int = when (this) {
    RevenueActivityKind.RENTAL_COMPLETED -> DsR.drawable.ic_tany_check
    RevenueActivityKind.BONUS_UNLOCKED -> DsR.drawable.ic_tany_star
    RevenueActivityKind.DEPOSIT_RECEIVED, RevenueActivityKind.DEPOSIT_REFUNDED -> DsR.drawable.ic_tany_lock
    RevenueActivityKind.ADJUSTMENT -> DsR.drawable.ic_tany_swap
    RevenueActivityKind.UNKNOWN -> DsR.drawable.ic_tany_info
}

/** Deposits stay neutral: they are never earnings. */
private fun RevenueActivityKind.tone(): TanyTone = when (this) {
    RevenueActivityKind.RENTAL_COMPLETED -> TanyTone.SUCCESS
    RevenueActivityKind.BONUS_UNLOCKED -> TanyTone.ACTION
    RevenueActivityKind.ADJUSTMENT -> TanyTone.INFO
    else -> TanyTone.NEUTRAL
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
    TanySectionHeader(stringResource(if (upcoming) R.string.revenue_agreement_upcoming else R.string.revenue_agreement), modifier = Modifier.padding(top = 8.dp))
    TanyCard {
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
