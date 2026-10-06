package ma.tany.collect.feature.revenue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.ConfirmationKind
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationSheetHost
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.TanyAmountPanel
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyCardStyle
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDetailSkeleton
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyToneIcon
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.qr.QrCodeImage
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.CollectionStatus
import ma.tany.core.model.collect.SettlementCollection
import ma.tany.core.model.collect.SettlementOverview
import ma.tany.core.model.collect.SettlementQr
import ma.tany.core.model.collect.SettlementStatus
import ma.tany.core.model.collect.StatementStatus
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectBusinessRepository
import ma.tany.core.network.SettlementActionError
import javax.inject.Inject

/** Gesture being sent (one at a time). */
enum class SettlementGesture { QR, CONFIRM, DISPUTE }

data class SettlementUi(
    val busy: SettlementGesture? = null,
    val error: SettlementActionError? = null,
    /** Last QR issued for the agent (server payload, rendered as-is). */
    val qr: SettlementQr? = null,
)

/**
 * TANY settlement of the active point (≠ revenue ≠ deposits). The merchant shows a server QR to the TANY agent, then
 * confirms « J'ai remis X » with EXACTLY the agent's declared amount, or disputes it — TANY settles any discrepancy.
 * No gesture is retried; after any failure the overview is re-read.
 */
@HiltViewModel
class SettlementViewModel @Inject constructor(private val repository: CollectBusinessRepository) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<SettlementOverview>>(LoadState.Loading)
    val state: StateFlow<LoadState<SettlementOverview>> = _state.asStateFlow()

    private val _ui = MutableStateFlow(SettlementUi())
    val ui: StateFlow<SettlementUi> = _ui.asStateFlow()

    fun load(pointId: String) {
        if (_state.value !is LoadState.Loaded) _state.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.settlement(pointId)
            if (result is ApiResult.Success || _state.value !is LoadState.Loaded) _state.value = result.toLoadState()
            // The agent moved the collection on (scanned / declared): the old QR is no longer useful.
            val active = (result as? ApiResult.Success)?.value?.activeCollection
            if (active?.qrAvailable != true) _ui.update { it.copy(qr = null) }
        }
    }

    fun showQr(pointId: String, collectionId: String) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = SettlementGesture.QR, error = null) }
        viewModelScope.launch {
            when (val result = repository.settlementQr(pointId, collectionId)) {
                is ApiResult.Success -> _ui.update { it.copy(busy = null, qr = result.value) }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(busy = null, error = SettlementActionError.from(result.error)) }
                    load(pointId)
                }
            }
        }
    }

    fun confirm(pointId: String, collection: SettlementCollection, onDone: () -> Unit) =
        send(pointId, SettlementGesture.CONFIRM, onDone) { repository.confirmHandoff(pointId, collection) }

    fun dispute(pointId: String, collectionId: String, onDone: () -> Unit) =
        send(pointId, SettlementGesture.DISPUTE, onDone) { repository.disputeHandoff(pointId, collectionId, null) }

    private fun send(pointId: String, gesture: SettlementGesture, onDone: () -> Unit, call: suspend () -> ApiResult<SettlementOverview>) {
        if (_ui.value.busy != null) {
            onDone()
            return
        }
        _ui.update { it.copy(busy = gesture, error = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    _state.value = LoadState.Loaded(result.value)
                    _ui.update { it.copy(busy = null, qr = null) }
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(busy = null, error = SettlementActionError.from(result.error)) }
                    load(pointId)
                }
            }
            onDone()
        }
    }
}

@Composable
fun SettlementScreen(pointId: String, onBack: () -> Unit, viewModel: SettlementViewModel = hiltViewModel()) {
    // Re-read on each return: the agent acts from another device.
    LifecycleResumeEffect(pointId) {
        viewModel.load(pointId)
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val confirmation = rememberConfirmationState()
    val confirmTitle = stringResource(R.string.settlement_confirm_title)
    val confirmMessage = stringResource(R.string.settlement_confirm_message)
    val confirmCta = stringResource(R.string.settlement_confirm_cta)
    val disputeTitle = stringResource(R.string.settlement_dispute_title)
    val disputeMessage = stringResource(R.string.settlement_dispute_message)
    val disputeCta = stringResource(R.string.settlement_dispute_cta)
    val confirmAmountLabel = stringResource(R.string.settlement_agent_declared)

    Column(Modifier.fillMaxSize()) {
        TanyTopBar(
            title = stringResource(R.string.settlement_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewModel.load(pointId) }) {
                    Icon(painterResource(DsR.drawable.ic_tany_refresh), contentDescription = stringResource(R.string.settlement_refresh))
                }
            },
        )
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled) {
                TanyEmptyState(title = stringResource(R.string.settlement_disabled), icon = DsR.drawable.ic_tany_receipt)
            } else {
                Content(
                    overview = s.value,
                    ui = ui,
                    onRefresh = { viewModel.load(pointId) },
                    onShowQr = { viewModel.showQr(pointId, it) },
                    onConfirm = { collection ->
                        collection.agentConfirmedAmount?.let { amount ->
                            confirmation.show(
                                ConfirmationRequest(
                                    id = "settle-confirm",
                                    title = confirmTitle,
                                    message = confirmMessage,
                                    confirmLabel = confirmCta,
                                    kind = ConfirmationKind.FINANCIAL,
                                    amount = amount,
                                    amountLabel = confirmAmountLabel,
                                ),
                            )
                        }
                    },
                    onDispute = {
                        confirmation.show(
                            ConfirmationRequest(id = "settle-dispute", title = disputeTitle, message = disputeMessage, confirmLabel = disputeCta, kind = ConfirmationKind.DESTRUCTIVE),
                        )
                    },
                )
            }
        }
    }
    ConfirmationSheetHost(confirmation) { request ->
        val active = (state as? LoadState.Loaded)?.value?.activeCollection
        when {
            active == null -> confirmation.finish()
            request.id == "settle-confirm" -> viewModel.confirm(pointId, active, confirmation::finish)
            request.id == "settle-dispute" -> viewModel.dispute(pointId, active.id, confirmation::finish)
            else -> confirmation.finish()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Content(
    overview: SettlementOverview,
    ui: SettlementUi,
    onRefresh: () -> Unit,
    onShowQr: (String) -> Unit,
    onConfirm: (SettlementCollection) -> Unit,
    onDispute: () -> Unit,
) {
    val colors = TanyTheme.colors
    val formatters = LocalTanyFormatters.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!overview.configured) {
            TanyNotice(message = stringResource(R.string.settlement_not_configured), tone = TanyTone.NEUTRAL)
        }
        overview.summary?.let { summary ->
            TanyCard(contentPadding = 20.dp) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TanyStatusChip(stringResource(summary.status.label()), summary.status.tone(), size = TanyChipSize.SMALL)
                    if (summary.overdue) TanyStatusChip(stringResource(R.string.settlement_overdue), TanyTone.DANGER, size = TanyChipSize.SMALL)
                }
                Text(stringResource(R.string.settlement_amount_due), style = TanyTheme.typography.label, color = colors.textMuted)
                MoneyText(summary.amountDue, style = TanyTheme.typography.amountHero, color = if (summary.overdue) colors.danger.content else colors.textPrimary)
                TanyDivider()
                if (!summary.alreadyCollected.isZero) TanyInfoRow(stringResource(R.string.settlement_already_collected)) { MoneyText(summary.alreadyCollected) }
                summary.dueAt?.let { TanyInfoRow(stringResource(R.string.settlement_due_at), icon = DsR.drawable.ic_tany_clock) { BusinessDateTimeText(it, style = TanyTheme.typography.label) } }
                summary.nextVisit?.let { visit ->
                    TanyInfoRow(stringResource(R.string.settlement_next_visit), icon = DsR.drawable.ic_tany_calendar) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatters.businessDay(visit.date), style = TanyTheme.typography.bodyStrong)
                            // Agent's display name: data, verbatim.
                            visit.agentName?.let { Text(it, style = TanyTheme.typography.caption, color = colors.textMuted) }
                        }
                    }
                }
                summary.lastCompleted?.let { last ->
                    TanyInfoRow(stringResource(R.string.settlement_last_collection, ltrIsolated(last.reference)), icon = DsR.drawable.ic_tany_check) { MoneyText(last.amount) }
                }
                overview.method?.let {
                    Text(
                        stringResource(if (it.code == "CASH_AGENT_COLLECTION") R.string.settlement_method_cash_agent else R.string.settlement_method_other),
                        style = TanyTheme.typography.caption,
                        color = colors.textMuted,
                    )
                }
            }
        }
        overview.activeCollection?.let { ActiveCollection(it, ui, onShowQr, onConfirm, onDispute) }
        ui.error?.let { TanyNotice(message = stringResource(it.text()), tone = TanyTone.DANGER) }
        overview.heldDeposits?.takeIf { it.count > 0 }?.let { held ->
            // Customer deposits are not part of the settlement: never handed to the agent.
            TanyCard(style = TanyCardStyle.FILLED) {
                TanyInfoRow(stringResource(R.string.settlement_held_deposits, held.count), icon = DsR.drawable.ic_tany_lock) { MoneyText(held.amount) }
                TanyNotice(message = stringResource(R.string.settlement_held_deposits_note), tone = TanyTone.WARNING)
            }
        }
        overview.breakdown?.let { b ->
            TanySectionHeader(stringResource(R.string.settlement_breakdown), modifier = Modifier.padding(top = 8.dp))
            TanyCard {
                TanyInfoRow(stringResource(R.string.settlement_rental_revenue)) { MoneyText(b.rentalRevenue) }
                TanyInfoRow(stringResource(R.string.settlement_commission)) { MoneyText(b.commission) }
                if (!b.bonus.isZero) TanyInfoRow(stringResource(R.string.settlement_bonus)) { MoneyText(b.bonus) }
                if (!b.partnerAdjustments.isZero) TanyInfoRow(stringResource(R.string.settlement_partner_adjustments)) { MoneyText(b.partnerAdjustments) }
                TanyInfoRow(stringResource(R.string.settlement_tany_share)) { MoneyText(b.tanyRentalShare) }
                if (!b.depositsRetained.isZero) TanyInfoRow(stringResource(R.string.settlement_deposits_retained)) { MoneyText(b.depositsRetained) }
                if (!b.settlementAdjustments.isZero) TanyInfoRow(stringResource(R.string.settlement_adjustments)) { MoneyText(b.settlementAdjustments) }
                TanyDivider()
                TanyInfoRow(stringResource(R.string.settlement_total), emphasized = true) { MoneyText(b.total) }
                if (!b.alreadyCollected.isZero) TanyInfoRow(stringResource(R.string.settlement_already_collected)) { MoneyText(b.alreadyCollected) }
                TanyInfoRow(stringResource(R.string.settlement_remaining), emphasized = true) { MoneyText(b.remaining, style = TanyTheme.typography.headline) }
            }
        }
        if (overview.statements.isNotEmpty()) {
            TanySectionHeader(stringResource(R.string.settlement_statements), modifier = Modifier.padding(top = 8.dp))
            TanyCard(contentPadding = 0.dp) {
                Column {
                    overview.statements.forEachIndexed { i, st ->
                        if (i > 0) TanyDivider(inset = 16.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ltrIsolated(st.reference), style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
                                TanyStatusChip(stringResource(st.status.label()), st.status.tone(), size = TanyChipSize.SMALL)
                            }
                            val start = st.periodStart
                            val end = st.cutoffAt
                            if (start != null && end != null) {
                                Text(formatters.businessWindow(start, end), style = TanyTheme.typography.caption, color = colors.textMuted)
                            }
                            TanyInfoRow(stringResource(R.string.settlement_total)) { MoneyText(st.totalDue) }
                            if (!st.outstanding.isZero) TanyInfoRow(stringResource(R.string.settlement_remaining)) { MoneyText(st.outstanding) }
                        }
                    }
                }
            }
        }
        if (overview.history.isNotEmpty()) {
            TanySectionHeader(stringResource(R.string.settlement_history), modifier = Modifier.padding(top = 8.dp))
            TanyCard(contentPadding = 0.dp) {
                Column {
                    overview.history.forEachIndexed { i, c ->
                        if (i > 0) TanyDivider(inset = 16.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ltrIsolated(c.reference), style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
                                TanyStatusChip(stringResource(c.status.label()), c.status.tone(), size = TanyChipSize.SMALL)
                            }
                            c.receivedAmount?.let { TanyInfoRow(stringResource(R.string.settlement_received)) { MoneyText(it) } }
                            c.remainingAmount?.takeIf { !it.isZero }?.let { TanyInfoRow(stringResource(R.string.settlement_remaining)) { MoneyText(it) } }
                            c.completedAt?.let { BusinessDateTimeText(it, style = TanyTheme.typography.caption, color = colors.textSubtle) }
                        }
                    }
                }
            }
        }
        TanyButton(stringResource(R.string.settlement_refresh), onRefresh, style = TanyButtonStyle.TEXT, icon = DsR.drawable.ic_tany_refresh)
        Text(
            stringResource(R.string.settlement_disclaimer),
            style = TanyTheme.typography.caption,
            color = colors.textSubtle,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * The agent's visit, in three server states: QR to show (white studio, big short code), the agent's declaration to
 * confirm EXACTLY or dispute, or counting in progress.
 */
@Composable
private fun ActiveCollection(
    collection: SettlementCollection,
    ui: SettlementUi,
    onShowQr: (String) -> Unit,
    onConfirm: (SettlementCollection) -> Unit,
    onDispute: () -> Unit,
) {
    val colors = TanyTheme.colors
    val formatters = LocalTanyFormatters.current
    TanyCard(contentPadding = 20.dp, accent = TanyTone.ACTION.takeIf { collection.confirmationRequired }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TanyToneIcon(DsR.drawable.ic_tany_person, collection.status.tone(), size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settlement_agent_visit), style = TanyTheme.typography.headline)
                Text(ltrIsolated(collection.reference), style = TanyTheme.typography.caption, color = colors.textMuted)
            }
            TanyStatusChip(stringResource(collection.status.label()), collection.status.tone(), size = TanyChipSize.SMALL)
        }
        collection.scheduledFor?.let {
            TanyInfoRow(stringResource(R.string.settlement_next_visit), icon = DsR.drawable.ic_tany_calendar) {
                Text(formatters.businessDay(it), style = TanyTheme.typography.bodyStrong)
            }
        }
        // Agent's display name: data, verbatim.
        collection.agentName?.let { TanyInfoRow(stringResource(R.string.settlement_agent), icon = DsR.drawable.ic_tany_person) { Text(it, style = TanyTheme.typography.bodyStrong) } }
        collection.expectedAmount?.let { TanyInfoRow(stringResource(R.string.settlement_expected), icon = DsR.drawable.ic_tany_cash) { MoneyText(it) } }

        if (collection.confirmationRequired) {
            val declared = collection.agentConfirmedAmount
            declared?.let {
                TanyAmountPanel(
                    label = stringResource(R.string.settlement_agent_declared),
                    amount = it,
                    tone = TanyTone.ACTION,
                    icon = DsR.drawable.ic_tany_cash,
                    caption = stringResource(R.string.settlement_confirm_caption),
                )
            }
            // Agent's discrepancy note: data, verbatim.
            collection.discrepancyReason?.let { TanyNotice(message = it, tone = TanyTone.WARNING) }
            collection.declaredRemainingAmount?.takeIf { !it.isZero }?.let {
                TanyInfoRow(stringResource(R.string.settlement_remaining)) { MoneyText(it) }
            }
            if (declared != null) {
                TanyButton(
                    stringResource(R.string.settlement_confirm_action, formatters.money(declared)),
                    { onConfirm(collection) },
                    enabled = ui.busy == null,
                    loading = ui.busy == SettlementGesture.CONFIRM,
                    icon = DsR.drawable.ic_tany_check,
                )
            }
            TanyButton(
                stringResource(R.string.settlement_dispute_action),
                onDispute,
                style = TanyButtonStyle.SECONDARY,
                enabled = ui.busy == null,
                loading = ui.busy == SettlementGesture.DISPUTE,
            )
        } else if (collection.qrAvailable) {
            TanyNotice(message = stringResource(R.string.settlement_qr_hint), tone = TanyTone.INFO, icon = DsR.drawable.ic_tany_qr)
            val qr = ui.qr?.takeIf { it.collectionId == collection.id }
            if (qr != null) {
                // White studio in both themes so any agent scanner reads it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TanyTheme.radii.cardShape)
                        .background(colors.media)
                        .border(TanyDimens.BorderWidth, colors.mediaBorder, TanyTheme.radii.cardShape)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QrCodeImage(
                        payload = qr.qrPayload,
                        contentDescription = stringResource(R.string.settlement_qr_description),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 280.dp),
                    )
                }
                Text(
                    ltrIsolated(qr.shortCode.chunked(3).joinToString(" ")),
                    style = TanyTheme.typography.codeLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TanyInfoRow(stringResource(R.string.settlement_expected), icon = DsR.drawable.ic_tany_cash) { MoneyText(qr.expectedAmount) }
                TanyInfoRow(stringResource(R.string.settlement_qr_expires), icon = DsR.drawable.ic_tany_clock) { BusinessDateTimeText(qr.expiresAt, style = TanyTheme.typography.label) }
            }
            TanyButton(
                stringResource(if (qr == null) R.string.settlement_qr_show else R.string.settlement_qr_renew),
                { onShowQr(collection.id) },
                style = if (qr == null) TanyButtonStyle.PRIMARY else TanyButtonStyle.SECONDARY,
                enabled = ui.busy == null,
                loading = ui.busy == SettlementGesture.QR,
                icon = DsR.drawable.ic_tany_qr,
            )
        } else if (collection.status == CollectionStatus.IN_PROGRESS) {
            TanyNotice(message = stringResource(R.string.settlement_in_progress_hint), tone = TanyTone.INFO, icon = DsR.drawable.ic_tany_clock)
        }
    }
}

private fun SettlementActionError.text(): Int = when (this) {
    SettlementActionError.Stale -> R.string.settlement_error_stale
    SettlementActionError.NotAllowedNow -> R.string.settlement_error_not_allowed
    is SettlementActionError.Other -> error.messageRes()
}

private fun SettlementStatus.label(): Int = when (this) {
    SettlementStatus.NOT_CONFIGURED -> R.string.settlement_status_not_configured
    SettlementStatus.NOTHING_DUE -> R.string.settlement_status_nothing_due
    SettlementStatus.DUE -> R.string.settlement_status_due
    SettlementStatus.PARTIALLY_COLLECTED -> R.string.settlement_status_partial
    SettlementStatus.COLLECTION_IN_PROGRESS -> R.string.settlement_status_in_progress
    SettlementStatus.CONFIRMATION_REQUIRED -> R.string.settlement_status_confirmation
    SettlementStatus.DISPUTED -> R.string.settlement_status_disputed
    SettlementStatus.UNKNOWN -> R.string.settlement_status_unknown
}

private fun SettlementStatus.tone(): TanyTone = when (this) {
    SettlementStatus.NOTHING_DUE -> TanyTone.SUCCESS
    SettlementStatus.DUE, SettlementStatus.PARTIALLY_COLLECTED -> TanyTone.WARNING
    SettlementStatus.CONFIRMATION_REQUIRED -> TanyTone.ACTION
    SettlementStatus.DISPUTED -> TanyTone.DANGER
    else -> TanyTone.INFO
}

private fun StatementStatus.label(): Int = when (this) {
    StatementStatus.DUE -> R.string.settlement_status_due
    StatementStatus.PARTIALLY_COLLECTED -> R.string.settlement_status_partial
    StatementStatus.COLLECTED -> R.string.settlement_status_collected
    StatementStatus.UNKNOWN -> R.string.settlement_status_unknown
}

private fun StatementStatus.tone(): TanyTone = when (this) {
    StatementStatus.COLLECTED -> TanyTone.SUCCESS
    StatementStatus.DUE, StatementStatus.PARTIALLY_COLLECTED -> TanyTone.WARNING
    StatementStatus.UNKNOWN -> TanyTone.INFO
}

private fun CollectionStatus.label(): Int = when (this) {
    CollectionStatus.SCHEDULED -> R.string.collection_status_scheduled
    CollectionStatus.IN_PROGRESS -> R.string.collection_status_in_progress
    CollectionStatus.AWAITING_MERCHANT -> R.string.collection_status_awaiting
    CollectionStatus.DISPUTED -> R.string.collection_status_disputed
    CollectionStatus.COMPLETED -> R.string.collection_status_completed
    CollectionStatus.PARTIALLY_COLLECTED -> R.string.collection_status_partial
    CollectionStatus.CANCELLED -> R.string.collection_status_cancelled
    CollectionStatus.UNKNOWN -> R.string.settlement_status_unknown
}

private fun CollectionStatus.tone(): TanyTone = when (this) {
    CollectionStatus.COMPLETED -> TanyTone.SUCCESS
    CollectionStatus.AWAITING_MERCHANT -> TanyTone.ACTION
    CollectionStatus.PARTIALLY_COLLECTED -> TanyTone.WARNING
    CollectionStatus.DISPUTED -> TanyTone.DANGER
    CollectionStatus.CANCELLED -> TanyTone.NEUTRAL
    else -> TanyTone.INFO
}
