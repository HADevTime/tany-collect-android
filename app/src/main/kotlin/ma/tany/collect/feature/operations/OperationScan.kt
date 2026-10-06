package ma.tany.collect.feature.operations

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.feature.scanner.ScanFailureUi
import ma.tany.collect.feature.scanner.ScanInput
import ma.tany.collect.feature.scanner.ScanInputs
import ma.tany.collect.feature.scanner.ScanTarget
import ma.tany.collect.feature.scanner.ScannerScreen
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.common.QrPurpose
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectOperationError
import ma.tany.core.network.CollectOperationsRepository
import javax.inject.Inject

data class OperationScanState(val busy: Boolean = false, val error: CollectOperationError? = null)

/**
 * Sends a scanned / typed input to the server, one request at a time, never retried:
 * - [ScanTarget.BOOKING_QR] without booking (Scanner tab): `POST /collect/scan` — the server resolves the booking and
 *   the purpose; with a booking ("Scan the customer's QR" step): same call scoped by `bookingId` + `purpose`;
 * - [ScanTarget.ASSET_LABEL]: `POST bookings/{id}/asset` — the server checks the label against the assigned Asset.
 * On success [done] emits the booking id; the booking screen then re-reads the server state.
 */
@HiltViewModel
class OperationScanViewModel @Inject constructor(
    private val operations: CollectOperationsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val bookingId: String? = savedStateHandle["bookingId"]
    val target: ScanTarget = savedStateHandle.get<String>("target")?.let { name -> ScanTarget.entries.firstOrNull { it.name == name } }
        ?: ScanTarget.BOOKING_QR
    /** Purpose of a booking-step scan (null on the Scanner tab: the server resolves it). */
    val purpose: QrPurpose? = savedStateHandle.get<String>("purpose")?.let { wire -> QrPurpose.entries.firstOrNull { it.wire == wire } }

    private val _state = MutableStateFlow(OperationScanState())
    val state: StateFlow<OperationScanState> = _state.asStateFlow()

    private val _done = Channel<String>(Channel.BUFFERED)
    val done: Flow<String> = _done.receiveAsFlow()

    /** « Scanner à nouveau »: clears the refusal and re-arms the camera (nothing is re-sent). */
    fun clearError() = _state.update { if (it.busy) it else OperationScanState() }

    fun submit(pointId: String, input: ScanInput) {
        if (_state.value.busy || _state.value.error != null) return
        _state.update { OperationScanState(busy = true) }
        viewModelScope.launch {
            val result: ApiResult<String> = when (target) {
                ScanTarget.BOOKING_QR -> when (val r = operations.scan(ScanInputs.toScanBody(pointId, input, bookingId, purpose))) {
                    is ApiResult.Success -> ApiResult.Success(r.value.booking.id)
                    is ApiResult.Failure -> r
                }
                ScanTarget.ASSET_LABEL -> {
                    val id = bookingId
                    val raw = (input as? ScanInput.Qr)?.payload
                    val body = raw?.let { ScanInputs.toAssetBody(pointId, purpose ?: QrPurpose.PICKUP, it) }
                    if (id == null || body == null) {
                        _state.update { OperationScanState() }
                        return@launch
                    }
                    when (val r = operations.verifyAsset(id, body)) {
                        is ApiResult.Success -> ApiResult.Success(r.value.id)
                        is ApiResult.Failure -> r
                    }
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { OperationScanState() }
                    _done.send(result.value)
                }
                is ApiResult.Failure -> _state.update { OperationScanState(error = CollectOperationError.from(result.error)) }
            }
        }
    }
}

/**
 * Scanner bound to an operation. [onDone] receives the booking id (Scanner tab: open it; booking step: go back to it).
 * A refusal pauses the camera and shows a card (title, message, « Scanner à nouveau », manual code); a wrong item label
 * is a blocking screen (« Objet incorrect ») — both rendered from the server's structured refusal.
 */
@Composable
fun OperationScanScreen(
    pointId: String,
    onDone: (bookingId: String) -> Unit,
    onBack: (() -> Unit)?,
    pointName: String? = null,
    viewModel: OperationScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.done.collect {
            view.confirmHaptic()
            onDone(it)
        }
    }
    LaunchedEffect(state.error) { if (state.error != null) view.rejectHaptic() }
    val label = viewModel.target == ScanTarget.ASSET_LABEL
    val error = state.error
    if (label && error is CollectOperationError.AssetMismatch) {
        WrongItemScreen(expected = error.expected, scanned = error.scanned, onRetry = viewModel::clearError)
        return
    }
    val failure = error?.let { ScanFailureUi(title = it.title(), message = it.scanText(), icon = it.icon(), preferCamera = it == CollectOperationError.RateLimited) }
    val (hint, subHint) = when {
        onBack == null -> null to null
        label -> stringResource(R.string.op_scan_asset_title_hint) to stringResource(R.string.scan_label_example)
        else -> stringResource(
            when (viewModel.purpose) {
                QrPurpose.RETURN -> R.string.scan_instruction_return
                QrPurpose.DEPOSIT_REFUND -> R.string.scan_instruction_deposit
                else -> R.string.scan_instruction_pickup
            },
        ) to stringResource(R.string.scan_qr_renewed)
    }
    ScannerScreen(
        onInput = { viewModel.submit(pointId, it) },
        title = if (onBack == null) null else stringResource(if (label) R.string.op_scan_asset_title else R.string.op_scan_customer_title),
        target = viewModel.target,
        onBack = onBack,
        busy = state.busy,
        failure = failure,
        onDismissFailure = viewModel::clearError,
        hint = hint,
        subHint = subHint,
        pointName = pointName,
    )
}

/** Blocking « wrong item » screen (server `asset_mismatch` with the expected / scanned codes). */
@Composable
private fun WrongItemScreen(expected: String?, scanned: String?, onRetry: () -> Unit) {
    val colors = TanyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.danger.container)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        TanyIllustration(DsR.drawable.ic_tany_error, tone = TanyTone.DANGER, size = 96.dp)
        Text(
            stringResource(R.string.op_asset_wrong_item_title),
            style = TanyTheme.typography.largeTitle,
            color = colors.danger.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.op_asset_wrong_item_message),
            style = TanyTheme.typography.headline,
            color = colors.danger.content,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CodeBox(stringResource(R.string.op_asset_expected), expected, Modifier.weight(1f))
            CodeBox(stringResource(R.string.op_asset_scanned), scanned, Modifier.weight(1f))
        }
        TanyButton(stringResource(R.string.op_asset_scan_right_item), onRetry, icon = DsR.drawable.ic_tany_tag)
    }
}

@Composable
private fun CodeBox(label: String, code: String?, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(TanyTheme.radii.large)
            .background(TanyTheme.colors.surface)
            .semantics(mergeDescendants = true) {}
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        Text(code?.let(::ltrIsolated) ?: "—", style = TanyTheme.typography.codeLarge, color = TanyTheme.colors.textPrimary, maxLines = 1)
    }
}

/** Success / refusal haptics (API-level safe constants). */
internal fun View.confirmHaptic() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}

internal fun View.rejectHaptic() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}
