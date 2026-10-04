package ma.tany.collect.feature.revenue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.ConfirmationKind
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationSheetHost
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
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.qr.QrCodeImage
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

    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.settlement_title), onBack = onBack, chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled) {
                TanyEmptyState(title = stringResource(R.string.settlement_disabled))
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

@Composable
private fun Content(
    overview: SettlementOverview,
    ui: SettlementUi,
    onRefresh: () -> Unit,
    onShowQr: (String) -> Unit,
    onConfirm: (SettlementCollection) -> Unit,
    onDispute: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!overview.configured) {
            TanyCard { Text(stringResource(R.string.settlement_not_configured), style = TanyTheme.typography.body) }
        }
        overview.summary?.let { summary ->
            TanyCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TanyStatusChip(stringResource(summary.status.label()), summary.status.tone())
                    if (summary.overdue) TanyStatusChip(stringResource(R.string.settlement_overdue), TanyTone.DANGER)
                }
                Text(stringResource(R.string.settlement_amount_due), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
                MoneyText(summary.amountDue, style = TanyTheme.typography.display)
                if (!summary.alreadyCollected.isZero) TanyInfoRow(stringResource(R.string.settlement_already_collected)) { MoneyText(summary.alreadyCollected) }
                summary.dueAt?.let { TanyInfoRow(stringResource(R.string.settlement_due_at)) { BusinessDateTimeText(it) } }
                summary.nextVisit?.let { visit ->
                    TanyInfoRow(stringResource(R.string.settlement_next_visit)) {
                        Text(LocalTanyFormatters.current.businessDay(visit.date), style = TanyTheme.typography.bodyStrong)
                    }
                    visit.agentName?.let { Text(it, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted) }
                }
                summary.lastCompleted?.let { last ->
                    TanyInfoRow(stringResource(R.string.settlement_last_collection, ltrIsolated(last.reference))) { MoneyText(last.amount) }
                }
                overview.method?.let {
                    Text(
                        stringResource(if (it.code == "CASH_AGENT_COLLECTION") R.string.settlement_method_cash_agent else R.string.settlement_method_other),
                        style = TanyTheme.typography.caption,
                        color = TanyTheme.colors.textMuted,
                    )
                }
            }
        }
        overview.activeCollection?.let { ActiveCollection(it, ui, onShowQr, onConfirm, onDispute) }
        ui.error?.let {
            Text(
                stringResource(it.text()),
                color = TanyTheme.colors.danger.accent,
                style = TanyTheme.typography.label,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        TanyButton(stringResource(R.string.settlement_refresh), onRefresh, style = TanyButtonStyle.TEXT)
        overview.heldDeposits?.takeIf { it.count > 0 }?.let { held ->
            TanyCard {
                TanyInfoRow(stringResource(R.string.settlement_held_deposits, held.count)) { MoneyText(held.amount) }
                Text(stringResource(R.string.settlement_held_deposits_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
            }
        }
        overview.breakdown?.let { b ->
            Section(stringResource(R.string.settlement_breakdown))
            TanyCard {
                TanyInfoRow(stringResource(R.string.settlement_rental_revenue)) { MoneyText(b.rentalRevenue) }
                TanyInfoRow(stringResource(R.string.settlement_commission)) { MoneyText(b.commission) }
                if (!b.bonus.isZero) TanyInfoRow(stringResource(R.string.settlement_bonus)) { MoneyText(b.bonus) }
                if (!b.partnerAdjustments.isZero) TanyInfoRow(stringResource(R.string.settlement_partner_adjustments)) { MoneyText(b.partnerAdjustments) }
                TanyInfoRow(stringResource(R.string.settlement_tany_share)) { MoneyText(b.tanyRentalShare) }
                if (!b.depositsRetained.isZero) TanyInfoRow(stringResource(R.string.settlement_deposits_retained)) { MoneyText(b.depositsRetained) }
                if (!b.settlementAdjustments.isZero) TanyInfoRow(stringResource(R.string.settlement_adjustments)) { MoneyText(b.settlementAdjustments) }
                TanyInfoRow(stringResource(R.string.settlement_total), emphasized = true) { MoneyText(b.total) }
                if (!b.alreadyCollected.isZero) TanyInfoRow(stringResource(R.string.settlement_already_collected)) { MoneyText(b.alreadyCollected) }
                TanyInfoRow(stringResource(R.string.settlement_remaining), emphasized = true) { MoneyText(b.remaining) }
            }
        }
        if (overview.statements.isNotEmpty()) {
            Section(stringResource(R.string.settlement_statements))
            TanyCard {
                overview.statements.forEach { st ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ltrIsolated(st.reference), style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
                            TanyStatusChip(stringResource(st.status.label()), st.status.tone())
                        }
                        val start = st.periodStart
                        val end = st.cutoffAt
                        if (start != null && end != null) {
                            Text(LocalTanyFormatters.current.businessWindow(start, end), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                        }
                        TanyInfoRow(stringResource(R.string.settlement_total)) { MoneyText(st.totalDue) }
                        if (!st.outstanding.isZero) TanyInfoRow(stringResource(R.string.settlement_remaining)) { MoneyText(st.outstanding) }
                    }
                }
            }
        }
        if (overview.history.isNotEmpty()) {
            Section(stringResource(R.string.settlement_history))
            TanyCard {
                overview.history.forEach { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ltrIsolated(c.reference), style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
                            TanyStatusChip(stringResource(c.status.label()), c.status.tone())
                        }
                        c.receivedAmount?.let { TanyInfoRow(stringResource(R.string.settlement_received)) { MoneyText(it) } }
                        c.remainingAmount?.takeIf { !it.isZero }?.let { TanyInfoRow(stringResource(R.string.settlement_remaining)) { MoneyText(it) } }
                        c.completedAt?.let { BusinessDateTimeText(it, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted) }
                    }
                }
            }
        }
        Text(stringResource(R.string.settlement_disclaimer), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ActiveCollection(
    collection: SettlementCollection,
    ui: SettlementUi,
    onShowQr: (String) -> Unit,
    onConfirm: (SettlementCollection) -> Unit,
    onDispute: () -> Unit,
) {
    TanyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ltrIsolated(collection.reference), style = TanyTheme.typography.headline, modifier = Modifier.weight(1f))
            TanyStatusChip(stringResource(collection.status.label()), collection.status.tone())
        }
        collection.scheduledFor?.let {
            TanyInfoRow(stringResource(R.string.settlement_next_visit)) { Text(LocalTanyFormatters.current.businessDay(it), style = TanyTheme.typography.bodyStrong) }
        }
        // Agent's display name: data, verbatim.
        collection.agentName?.let { Text(it, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted) }
        collection.expectedAmount?.let { TanyInfoRow(stringResource(R.string.settlement_expected)) { MoneyText(it) } }

        if (collection.confirmationRequired) {
            val declared = collection.agentConfirmedAmount
            Text(stringResource(R.string.settlement_agent_declared), style = TanyTheme.typography.body)
            declared?.let { MoneyText(it, style = TanyTheme.typography.display) }
            collection.discrepancyReason?.let { Text(it, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted) }
            collection.declaredRemainingAmount?.takeIf { !it.isZero }?.let {
                TanyInfoRow(stringResource(R.string.settlement_remaining)) { MoneyText(it) }
            }
            if (declared != null) {
                TanyButton(
                    stringResource(R.string.settlement_confirm_action, LocalTanyFormatters.current.money(declared)),
                    { onConfirm(collection) },
                    enabled = ui.busy == null,
                    loading = ui.busy == SettlementGesture.CONFIRM,
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
            Text(stringResource(R.string.settlement_qr_hint), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
            val qr = ui.qr?.takeIf { it.collectionId == collection.id }
            if (qr != null) {
                QrCodeImage(
                    payload = qr.qrPayload,
                    contentDescription = stringResource(R.string.settlement_qr_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .align(Alignment.CenterHorizontally),
                )
                Text(
                    ltrIsolated(qr.shortCode.chunked(3).joinToString(" ")),
                    style = TanyTheme.typography.code,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TanyInfoRow(stringResource(R.string.settlement_expected)) { MoneyText(qr.expectedAmount) }
                TanyInfoRow(stringResource(R.string.settlement_qr_expires)) { BusinessDateTimeText(qr.expiresAt) }
            }
            TanyButton(
                stringResource(if (qr == null) R.string.settlement_qr_show else R.string.settlement_qr_renew),
                { onShowQr(collection.id) },
                style = if (qr == null) TanyButtonStyle.PRIMARY else TanyButtonStyle.SECONDARY,
                enabled = ui.busy == null,
                loading = ui.busy == SettlementGesture.QR,
            )
        } else if (collection.status == CollectionStatus.IN_PROGRESS) {
            Text(stringResource(R.string.settlement_in_progress_hint), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
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
