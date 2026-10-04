package ma.tany.collect.feature.operations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
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
import ma.tany.collect.feature.scanner.ScanInput
import ma.tany.collect.feature.scanner.ScanInputs
import ma.tany.collect.feature.scanner.ScanTarget
import ma.tany.collect.feature.scanner.ScannerScreen
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
    private val purpose: QrPurpose? = savedStateHandle.get<String>("purpose")?.let { wire -> QrPurpose.entries.firstOrNull { it.wire == wire } }

    private val _state = MutableStateFlow(OperationScanState())
    val state: StateFlow<OperationScanState> = _state.asStateFlow()

    private val _done = Channel<String>(Channel.BUFFERED)
    val done: Flow<String> = _done.receiveAsFlow()

    fun submit(pointId: String, input: ScanInput) {
        if (_state.value.busy) return
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
 */
@Composable
fun OperationScanScreen(
    pointId: String,
    onDone: (bookingId: String) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: OperationScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect(onDone) }
    val label = viewModel.target == ScanTarget.ASSET_LABEL
    ScannerScreen(
        onInput = { viewModel.submit(pointId, it) },
        title = if (onBack == null) null else stringResource(if (label) R.string.op_scan_asset_title else R.string.op_scan_customer_title),
        target = viewModel.target,
        onBack = onBack,
        busy = state.busy,
        message = state.error?.text(),
        hint = if (onBack == null) null else stringResource(if (label) R.string.op_scan_asset_hint else R.string.op_scan_customer_hint),
    )
}
