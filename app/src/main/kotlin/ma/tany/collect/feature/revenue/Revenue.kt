package ma.tany.collect.feature.revenue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.model.collect.RevenueAmountKind
import ma.tany.core.model.collect.SettlementOverview
import ma.tany.core.network.ApiResult
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

    private val _loadingPeriod = MutableStateFlow(false)

    /** A period / refresh is loading while the previous figures stay on screen (no skeleton flash). */
    val loadingPeriod: StateFlow<Boolean> = _loadingPeriod.asStateFlow()

    private val _settlement = MutableStateFlow<SettlementOverview?>(null)

    /** Settlement summary of the same point (Revenus › « Règlement TANY » card); null when unavailable. */
    val settlement: StateFlow<SettlementOverview?> = _settlement.asStateFlow()

    /** [period] = a key given by the server (`previousKey` / `nextKey` / `periods[]`), null = current month. */
    fun load(pointId: String, period: String? = null) {
        if (_state.value is LoadState.Loaded) _loadingPeriod.value = true else _state.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.revenue(pointId, period)
            if (result is ApiResult.Success || _state.value !is LoadState.Loaded) _state.value = result.toLoadState()
            _loadingPeriod.value = false
        }
        viewModelScope.launch {
            val result = repository.settlement(pointId)
            _settlement.value = (result as? ApiResult.Success)?.value?.takeIf { it.enabled && it.collectPointId.let { id -> id == null || id == pointId } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val loadingPeriod by viewModel.loadingPeriod.collectAsStateWithLifecycle()
    val settlement by viewModel.settlement.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.revenue_title), onBack = onBack)
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled) {
                TanyEmptyState(
                    title = stringResource(R.string.revenue_disabled_title),
                    message = stringResource(R.string.revenue_disabled_message),
                    icon = DsR.drawable.ic_tany_wallet,
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = loadingPeriod,
                    onRefresh = { viewModel.load(pointId, s.value.period?.key) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Content(
                        s.value,
                        settlement = settlement,
                        loadingPeriod = loadingPeriod,
                        onPeriod = { viewModel.load(pointId, it) },
                        onOpenBooking = onOpenBooking,
                        onOpenSettlement = onOpenSettlement,
                    )
                }
            }
        }
    }
}

@Composable
private fun Content(
    revenue: RevenueOverview,
    settlement: SettlementOverview?,
    loadingPeriod: Boolean,
    onPeriod: (String) -> Unit,
    onOpenBooking: (String) -> Unit,
    onOpenSettlement: () -> Unit,
) {
    val colors = TanyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val period = revenue.period
        period?.let { PeriodSwitcher(it.key, it.previousKey, it.nextKey, revenue.periods.map { ref -> ref.key }, loadingPeriod, onPeriod) }
        val current = period?.isCurrent != false
        val earnings = revenue.earnings
        // An empty period (no rental, no amount, no activity) gets a reassuring card instead of a « 0 DH » hero.
        val emptyPeriod = earnings != null && earnings.amount.isZero && (revenue.performance?.rentals ?: 0) == 0 && revenue.activity.isEmpty()
        if (earnings != null && !emptyPeriod) {
            // ESTIMATED earnings, never « paid / settled / balance ».
            TanyCard(contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (current || period == null) {
                            stringResource(R.string.revenue_hero_current)
                        } else {
                            stringResource(R.string.revenue_hero_period, monthLabel(period.key))
                        },
                        style = TanyTheme.typography.label,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TanyStatusChip(stringResource(R.string.revenue_estimated), TanyTone.INFO, size = TanyChipSize.SMALL)
                }
                MoneyText(earnings.amount, style = TanyTheme.typography.amountHero)
                // Only the non-zero parts of the server breakdown.
                val formatters = LocalTanyFormatters.current
                val parts = listOfNotNull(
                    earnings.commissionAmount.takeIf { !it.isZero }?.let { stringResource(R.string.revenue_part_commission, formatters.money(it)) },
                    earnings.bonusAmount.takeIf { !it.isZero }?.let { stringResource(R.string.revenue_part_bonus, formatters.money(it)) },
                    earnings.adjustmentAmount.takeIf { !it.isZero }?.let { stringResource(R.string.revenue_part_adjustment, formatters.money(it)) },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = TanyTheme.typography.label, color = colors.textMuted)
                Text(
                    stringResource(if (earnings.hasAgreement) R.string.revenue_estimate_agreement else R.string.revenue_no_agreement_short),
                    style = TanyTheme.typography.caption,
                    color = if (earnings.hasAgreement) colors.textSubtle else colors.warning.content,
                )
                TanyDivider()
                TanyInfoRow(stringResource(R.string.revenue_previous)) { MoneyText(earnings.previousAmount, color = colors.textMuted) }
            }
        } else if (emptyPeriod) {
            TanyCard(contentPadding = 20.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TanyIllustration(DsR.drawable.ic_tany_wallet, size = 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(if (current) R.string.revenue_empty_current else R.string.revenue_empty_period),
                            style = TanyTheme.typography.headline,
                        )
                        Text(stringResource(R.string.revenue_empty_message), style = TanyTheme.typography.label, color = colors.textMuted)
                    }
                }
            }
        }
        settlement?.let { SettlementSummaryCard(it, onOpenSettlement) }
        revenue.performance?.let { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TanyMetricTile(p.rentals.toString(), stringResource(R.string.revenue_rentals), Modifier.weight(1f))
                TanyMetricTile(LocalTanyFormatters.current.money(p.rentalRevenue), stringResource(R.string.revenue_rental_volume), Modifier.weight(1f))
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
                // Next tier exactly as the server designates it (never picked locally).
                bonus.next?.takeIf { !it.achieved }?.let { next ->
                    TanyNotice(
                        message = stringResource(
                            R.string.revenue_bonus_next,
                            next.remaining?.let { metricTarget(next.metric, it) } ?: metricTarget(next.metric, next.threshold),
                            LocalTanyFormatters.current.money(next.reward),
                        ),
                        tone = TanyTone.ACTION,
                    )
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
                // Empty server buckets are not listed.
                if (d.toHandBack.count > 0) {
                    TanyInfoRow(stringResource(R.string.revenue_deposits_to_hand_back, d.toHandBack.count)) { MoneyText(d.toHandBack.amount) }
                }
                if (d.awaitingCustomer.count > 0) {
                    TanyInfoRow(stringResource(R.string.revenue_deposits_awaiting, d.awaitingCustomer.count)) { MoneyText(d.awaitingCustomer.amount) }
                }
                if (d.underReview.count > 0) {
                    TanyInfoRow(stringResource(R.string.revenue_deposits_review, d.underReview.count)) { MoneyText(d.underReview.amount) }
                }
            }
        }
        if (settlement == null) {
            TanyCard(contentPadding = 0.dp) {
                TanyRow(
                    title = stringResource(R.string.settlement_title),
                    subtitle = stringResource(R.string.account_settlement_hint),
                    leadingIcon = DsR.drawable.ic_tany_receipt,
                    leadingTone = TanyTone.INFO,
                    onClick = onOpenSettlement,
                )
            }
        }
        if (revenue.activity.isNotEmpty()) {
            TanySectionHeader(stringResource(R.string.revenue_activity), modifier = Modifier.padding(top = 8.dp))
            TanyCard(contentPadding = 0.dp) {
                Column {
                    revenue.activity.take(REVENUE_ACTIVITY_ROWS).forEachIndexed { i, item ->
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

/**
 * Month navigation between the SERVER's period keys (previous / next / `periods[]` given by the server); the month
 * title opens a menu of every period the server lists.
 */
@Composable
private fun PeriodSwitcher(
    key: String,
    previous: String?,
    next: String?,
    periods: List<String>,
    loading: Boolean,
    onPeriod: (String) -> Unit,
) {
    val colors = TanyTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TanyTheme.radii.large)
            .background(colors.surface)
            .border(TanyDimens.BorderWidth, colors.border, TanyTheme.radii.large)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { previous?.let(onPeriod) }, enabled = previous != null && !loading) {
            androidx.compose.material3.Icon(
                painterResource(DsR.drawable.ic_tany_chevron_start),
                contentDescription = stringResource(R.string.revenue_previous_period),
                tint = if (previous != null) colors.textPrimary else colors.border,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val choose = stringResource(R.string.revenue_choose_period)
            Text(
                monthLabel(key),
                style = TanyTheme.typography.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TanyTheme.radii.medium)
                    .then(
                        if (periods.size > 1) Modifier.clickable(role = Role.Button, onClickLabel = choose) { menu = true } else Modifier,
                    )
                    .padding(vertical = 12.dp)
                    .semantics { heading() },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                periods.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                monthLabel(option),
                                style = if (option == key) TanyTheme.typography.bodyStrong else TanyTheme.typography.body,
                            )
                        },
                        trailingIcon = if (option == key) {
                            { androidx.compose.material3.Icon(painterResource(DsR.drawable.ic_tany_check), contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            menu = false
                            if (option != key) onPeriod(option)
                        },
                    )
                }
            }
        }
        IconButton(onClick = { next?.let(onPeriod) }, enabled = next != null && !loading) {
            androidx.compose.material3.Icon(
                painterResource(DsR.drawable.ic_tany_chevron),
                contentDescription = stringResource(R.string.revenue_next_period),
                tint = if (next != null) colors.textPrimary else colors.border,
            )
        }
    }
}

/** At most this many recent rows (the server's order), like iOS. */
internal const val REVENUE_ACTIVITY_ROWS = 8

/** Sign shown before an activity amount: earnings are +, deposits stay neutral (never revenue). */
internal fun RevenueActivity.signPrefix(): String = when (amountKind) {
    RevenueAmountKind.EARNING -> if (amount.centimes < 0) "\u2212 " else "+ "
    else -> ""
}

/** Settlement summary of the point, from the server's status and amounts (opens the settlement screen). */
@Composable
private fun SettlementSummaryCard(settlement: SettlementOverview, onOpen: () -> Unit) {
    val colors = TanyTheme.colors
    val summary = settlement.summary
    TanyCard(onClick = onOpen, onClickLabel = stringResource(R.string.revenue_settlement_open)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TanyToneIcon(DsR.drawable.ic_tany_receipt, summary?.status?.tone() ?: TanyTone.INFO, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.settlement_title), style = TanyTheme.typography.bodyStrong)
                Text(stringResource(R.string.revenue_settlement_separate), style = TanyTheme.typography.caption, color = colors.textMuted)
            }
            androidx.compose.material3.Icon(
                painterResource(DsR.drawable.ic_tany_chevron),
                contentDescription = null,
                tint = colors.textSubtle,
                modifier = Modifier.size(18.dp),
            )
        }
        summary?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TanyStatusChip(stringResource(it.status.label()), it.status.tone(), size = TanyChipSize.SMALL)
                Spacer(Modifier.weight(1f))
                if (!it.amountDue.isZero) {
                    Text(stringResource(R.string.revenue_settlement_due), style = TanyTheme.typography.caption, color = colors.textMuted)
                    MoneyText(it.amountDue)
                }
            }
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
        Text(
            item.signPrefix() + LocalTanyFormatters.current.money(item.amount).let(::ltrIsolated),
            style = TanyTheme.typography.amount,
            color = if (item.amountKind == RevenueAmountKind.EARNING) colors.textPrimary else colors.textMuted,
        )
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
