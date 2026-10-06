package ma.tany.collect.feature.booking

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import ma.tany.core.designsystem.component.TanyBottomSheet
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.model.collect.ActorRole
import ma.tany.core.model.collect.AssetStatus
import ma.tany.core.model.collect.BookingEventType
import ma.tany.core.model.common.IncidentStatus
import ma.tany.core.model.common.PhotoType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.media.OperationPhotos
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.collect.feature.operations.confirmHaptic
import ma.tany.collect.feature.operations.text
import ma.tany.collect.feature.today.icon
import ma.tany.collect.feature.today.durationText
import ma.tany.collect.feature.today.label
import ma.tany.collect.feature.today.ui
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.ConfirmationKind
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationSheetHost
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyAmountPanel
import ma.tany.core.designsystem.component.TanyAvatar
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyCheckRow
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyChoiceChip
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.component.TanyDetailSkeleton
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyStepState
import ma.tany.core.designsystem.component.TanyTimelineStep
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyToneIcon
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.component.tanyFieldColors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.IncidentBody
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.collect.ReturnBody
import ma.tany.core.model.collect.ReturnIncident
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.IncidentType
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.PaymentStatus
import ma.tany.core.model.common.QrPurpose
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectOperationError
import ma.tany.core.network.CollectOperationsRepository
import ma.tany.core.network.CollectRepository
import java.io.File
import javax.inject.Inject

/** The merchant gesture currently being sent (one at a time). */
enum class PickupGesture { PHOTO, PAYMENT, HANDOVER, RETURN_STATEMENT, DEPOSIT_REFUND, INCIDENT }

/** What the customer just confirmed (seen in the server's timestamps between two reads) — drives the success card. */
enum class CompletedStep { PICKUP, RETURN, DEPOSIT }

/** Outcome of « Relancer le client » (server answer; `nudged:false` = a reminder was already sent recently). */
enum class NudgeOutcome { SENT, ALREADY_SENT }

/** Merchant return statement being prepared (sent once, with the final confirmation). */
data class ReturnForm(
    val condition: AssetCondition = AssetCondition.GOOD,
    /** Accessory names as listed by the server (`product.includedAccessories`), verbatim. */
    val missingAccessories: Set<String> = emptySet(),
    val incidentType: IncidentType? = null,
    val incidentDescription: String = "",
)

data class PickupUiState(
    val busy: PickupGesture? = null,
    val error: CollectOperationError? = null,
    /** Gesture [error] belongs to (each card shows its own refusals). */
    val failed: PickupGesture? = null,
    /** Condition the merchant declares with the next photo. */
    val photoCondition: AssetCondition = AssetCondition.GOOD,
    val returnForm: ReturnForm = ReturnForm(),
    /** A pickup problem was reported to TANY in this session (the server keeps the incident). */
    val incidentReported: Boolean = false,
    val nudging: Boolean = false,
    val nudgeOutcome: NudgeOutcome? = null,
    /** Server cooldown end of the next reminder (epoch ms). */
    val nudgeAvailableAt: Long = 0L,
    /** The customer confirmed while the merchant was on the screen. */
    val completed: CompletedStep? = null,
)

/**
 * Merchant booking + pickup preparation. Every step is a server gesture (scan customer QR → scan label → photo → cash
 * → handover); their ORDER, the windows, the identity gate and the amounts are checked by the server only — the
 * screen shows the server's facts per step and maps refusals. The merchant never finalizes the pickup: the customer
 * confirms in TANY (phase `pickup_awaiting_customer`).
 */
@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    private val repository: CollectRepository,
    private val operations: CollectOperationsRepository,
    private val photos: OperationPhotos,
    val endpoint: ApiEndpoint,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val bookingId: String = checkNotNull(savedStateHandle["bookingId"])
    private val _state = MutableStateFlow<LoadState<MerchantBookingDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<MerchantBookingDetail>> = _state.asStateFlow()

    private val _pickup = MutableStateFlow(PickupUiState())
    val pickup: StateFlow<PickupUiState> = _pickup.asStateFlow()

    private var pendingPhoto: File? = null

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.booking(bookingId, pointId).toLoadState() }
    }

    /** Back on the screen (after a scan…): re-read without flashing the skeleton when a booking is already shown. */
    fun reload(pointId: String) {
        if (_state.value is LoadState.Loaded) viewModelScope.launch { reloadQuietly(pointId) } else load(pointId)
    }

    /** Waiting for the customer: the screen re-reads every few seconds (idempotent GET, never a gesture). */
    fun poll(pointId: String) {
        if (_pickup.value.busy != null) return
        viewModelScope.launch { reloadQuietly(pointId) }
    }

    private suspend fun reloadQuietly(pointId: String) {
        val result = repository.booking(bookingId, pointId)
        if (result is ApiResult.Success) applyBooking(result.value)
    }

    /**
     * Applies a server booking. When a customer confirmation timestamp appears between two reads, the success card is
     * shown — a server FACT (the customer confirmed), never a client inference.
     */
    private fun applyBooking(next: MerchantBookingDetail) {
        val previous = (_state.value as? LoadState.Loaded)?.value
        if (previous != null && previous.id == next.id) {
            val done = when {
                previous.pickup?.customerConfirmedAt == null && next.pickup?.customerConfirmedAt != null -> CompletedStep.PICKUP
                previous.returnInfo?.customerConfirmedAt == null && next.returnInfo?.customerConfirmedAt != null -> CompletedStep.RETURN
                previous.deposit?.customerRefundConfirmedAt == null && next.deposit?.customerRefundConfirmedAt != null -> CompletedStep.DEPOSIT
                else -> null
            }
            if (done != null) _pickup.update { it.copy(completed = done) }
        }
        _state.value = LoadState.Loaded(next)
    }

    fun dismissCompleted() = _pickup.update { it.copy(completed = null) }

    /**
     * « Relancer le client » (`POST nudge`): one tap = one request, never retried; the server throttles (60 s) and says
     * when the next reminder is possible.
     */
    fun nudge(pointId: String, now: () -> Long = System::currentTimeMillis) {
        val s = _pickup.value
        if (s.nudging || now() < s.nudgeAvailableAt) return
        _pickup.update { it.copy(nudging = true, nudgeOutcome = null) }
        viewModelScope.launch {
            when (val result = operations.nudge(bookingId, pointId)) {
                is ApiResult.Success -> {
                    result.value.booking?.let(::applyBooking)
                    _pickup.update {
                        it.copy(
                            nudging = false,
                            nudgeOutcome = if (result.value.nudged) NudgeOutcome.SENT else NudgeOutcome.ALREADY_SENT,
                            nudgeAvailableAt = now() + result.value.retryAfterSeconds * 1_000L,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _pickup.update { it.copy(nudging = false, error = CollectOperationError.from(result.error), failed = PickupGesture.HANDOVER) }
                    reloadQuietly(pointId)
                }
            }
        }
    }

    /** Pickup problem reported to TANY (`POST incidents`) — the handover can continue; TANY decides. */
    fun reportIncident(pointId: String, type: IncidentType, description: String, onDone: () -> Unit) =
        send(pointId, PickupGesture.INCIDENT, onDone) {
            operations.reportIncident(
                bookingId,
                IncidentBody(pointId, type, description.trim().take(INCIDENT_DESCRIPTION_MAX).ifBlank { null }),
            ).also { if (it is ApiResult.Success) _pickup.update { s -> s.copy(incidentReported = true) } }
        }

    fun onPhotoCondition(condition: AssetCondition) = _pickup.update { it.copy(photoCondition = condition) }

    /** Private capture target for the system camera. */
    fun photoTarget(): Uri = photos.uriFor(preparePhoto())

    fun preparePhoto(): File {
        pendingPhoto?.let(photos::discard)
        return photos.newCaptureFile().also { pendingPhoto = it }
    }

    /** Uploads the captured photo with the declared condition; the file is deleted whatever the outcome. */
    fun onPhotoCaptured(pointId: String, success: Boolean) {
        val file = pendingPhoto ?: return
        pendingPhoto = null
        if (!success || file.length() == 0L) {
            photos.discard(file)
            return
        }
        val condition = _pickup.value.photoCondition
        // Same gesture for pickup and return photos: the purpose follows the server's operation kind.
        val purpose = if ((_state.value as? LoadState.Loaded)?.value?.kind == OperationKind.RETURN) QrPurpose.RETURN else QrPurpose.PICKUP
        send(pointId, PickupGesture.PHOTO) {
            try {
                val jpeg = runCatching { photos.encode(file) }.getOrNull()
                    ?: return@send ApiResult.Failure(PHOTO_UNREADABLE)
                operations.uploadPhoto(bookingId, pointId, purpose, condition, jpeg)
            } finally {
                photos.discard(file)
            }
        }
    }

    /** "I received X": [amount] is the server's amount due, sent back exactly. */
    fun confirmPayment(pointId: String, amount: MoneyAmount, onDone: () -> Unit) =
        send(pointId, PickupGesture.PAYMENT, onDone) { operations.confirmPayment(bookingId, pointId, amount) }

    /**
     * Merchant half of the pickup. Sends the condition the SERVER recorded with the pickup photos (`pickup.condition`):
     * without it the server would store « good » even after a « problem » photo.
     */
    fun handover(pointId: String, onDone: () -> Unit) {
        val condition = (_state.value as? LoadState.Loaded)?.value?.pickup?.condition
            ?.takeIf { it == AssetCondition.GOOD || it == AssetCondition.ISSUE_REPORTED }
        send(pointId, PickupGesture.HANDOVER, onDone) { operations.handover(bookingId, pointId, condition) }
    }

    private fun send(
        pointId: String,
        gesture: PickupGesture,
        onDone: () -> Unit = {},
        call: suspend () -> ApiResult<MerchantBookingDetail>,
    ) {
        if (_pickup.value.busy != null) {
            onDone()
            return
        }
        _pickup.update { it.copy(busy = gesture, error = null, failed = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    applyBooking(result.value)
                    _pickup.update { it.copy(busy = null) }
                }
                is ApiResult.Failure -> {
                    _pickup.update { it.copy(busy = null, error = CollectOperationError.from(result.error), failed = gesture) }
                    // Never retried: the server state is re-read (a network failure may hide a success).
                    reloadQuietly(pointId)
                }
            }
            onDone()
        }
    }

    fun updateReturnForm(change: (ReturnForm) -> ReturnForm) = _pickup.update { it.copy(returnForm = change(it.returnForm)) }

    /**
     * Merchant return statement (`POST bookings/{id}/return`). Missing accessories and the incident are a statement only:
     * the customer confirms the return in TANY, and any deposit consequence is decided by TANY.
     */
    fun declareReturn(pointId: String, onDone: () -> Unit) {
        val form = _pickup.value.returnForm
        val body = ReturnBody(
            collectPointId = pointId,
            condition = if (form.incidentType != null || form.missingAccessories.isNotEmpty()) AssetCondition.ISSUE_REPORTED else form.condition,
            missingAccessories = form.missingAccessories.toList(),
            incident = form.incidentType?.let { ReturnIncident(it, form.incidentDescription.trim().take(INCIDENT_DESCRIPTION_MAX).ifBlank { null }) },
        )
        send(pointId, PickupGesture.RETURN_STATEMENT, onDone) {
            operations.declareReturn(bookingId, body).also { if (it is ApiResult.Success) _pickup.update { s -> s.copy(returnForm = ReturnForm()) } }
        }
    }

    /**
     * « J'ai remis X » (`POST bookings/{id}/deposit-refund`): [amount] = the server's amount to hand back shown on screen,
     * sent back exactly. If TANY changed it meanwhile the server refuses (`deposit_amount_changed`), the booking is re-read
     * and the merchant sees the new amount before any cash moves. The customer alone confirms the amount received.
     */
    fun handBackDeposit(pointId: String, amount: MoneyAmount, onDone: () -> Unit) =
        send(pointId, PickupGesture.DEPOSIT_REFUND, onDone) { operations.depositRefund(bookingId, pointId, amount) }

    fun clearError() = _pickup.update { it.copy(error = null, failed = null) }

    companion object {
        /** Backend limit on the incident description. */
        const val INCIDENT_DESCRIPTION_MAX = 500

        /** Local encoding failure, reported like the server's photo_error. */
        private val PHOTO_UNREADABLE = ma.tany.core.network.ApiError.Http(
            422, ma.tany.core.model.common.ApiErrorCode.PHOTO_ERROR, "photo_error", null,
        )
    }
}

@Composable
fun BookingDetailScreen(
    pointId: String,
    onBack: () -> Unit,
    onScanCustomer: (bookingId: String, purpose: QrPurpose) -> Unit,
    onScanAsset: (bookingId: String, purpose: QrPurpose) -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    // Reloaded each time the screen shows again (back from a scan): server state only.
    LifecycleResumeEffect(pointId) {
        viewModel.reload(pointId)
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickup by viewModel.pickup.collectAsStateWithLifecycle()
    val confirmation = rememberConfirmationState()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.onPhotoCaptured(pointId, it) }
    var incidentOpen by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val phase = (state as? LoadState.Loaded)?.value?.phase
    // Waiting for the customer's confirmation in TANY: quiet re-read every 3 s while the screen is visible.
    LaunchedEffect(phase, lifecycleOwner) {
        if (phase in AWAITING_CUSTOMER_PHASES) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(AWAITING_POLL_MS)
                    viewModel.poll(pointId)
                }
            }
        }
    }

    val paymentTitle = stringResource(R.string.pickup_cash_title)
    val paymentMessage = stringResource(R.string.pickup_cash_message)
    val paymentCta = stringResource(R.string.pickup_cash_cta)
    val handoverTitle = stringResource(R.string.pickup_handover_title)
    val handoverMessage = stringResource(R.string.pickup_handover_message)
    val handoverCta = stringResource(R.string.pickup_handover_cta)
    val returnTitle = stringResource(R.string.return_statement_title)
    val returnMessage = stringResource(R.string.return_statement_message)
    val returnMessageIssue = stringResource(R.string.return_statement_message_issue)
    val returnCta = stringResource(R.string.return_statement_cta)
    val depositTitle = stringResource(R.string.deposit_confirm_title)
    val depositMessage = stringResource(R.string.deposit_confirm_message)
    val depositCta = stringResource(R.string.deposit_confirm_cta)
    val paymentAmountLabel = stringResource(R.string.pickup_cash_amount_label)
    val depositAmountLabel = stringResource(R.string.deposit_amount_label)

    Column(Modifier.fillMaxSize()) {
        val loaded = (state as? LoadState.Loaded)?.value
        TanyTopBar(title = stringResource(R.string.booking_title), onBack = onBack, subtitle = loaded?.reference?.let(::ltrIsolated))
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> {
                // The scan purpose follows the server's operation kind (pickup or return).
                val purpose = if (s.value.kind == OperationKind.RETURN) QrPurpose.RETURN else QrPurpose.PICKUP
                Content(
                booking = s.value,
                endpoint = viewModel.endpoint,
                pickup = pickup,
                actions = PickupActions(
                    reportProblem = { incidentOpen = true },
                    nudge = { viewModel.nudge(pointId) },
                    finish = onBack,
                    dismissCompleted = viewModel::dismissCompleted,
                    scanCustomer = { onScanCustomer(viewModel.bookingId, purpose) },
                    scanAsset = { onScanAsset(viewModel.bookingId, purpose) },
                    takePhoto = { camera.launch(viewModel.photoTarget()) },
                    onCondition = viewModel::onPhotoCondition,
                    cash = { amount ->
                        confirmation.show(
                            ConfirmationRequest(
                                id = "pickup-cash",
                                title = paymentTitle,
                                message = paymentMessage,
                                confirmLabel = paymentCta,
                                kind = ConfirmationKind.FINANCIAL,
                                amount = amount,
                                amountLabel = paymentAmountLabel,
                            ),
                        )
                    },
                    handover = {
                        confirmation.show(
                            ConfirmationRequest(
                                id = "pickup-handover",
                                title = handoverTitle,
                                message = handoverMessage,
                                confirmLabel = handoverCta,
                                icon = DsR.drawable.ic_tany_pickup,
                            ),
                        )
                    },
                    scanDepositQr = { onScanCustomer(viewModel.bookingId, QrPurpose.DEPOSIT_REFUND) },
                    handBackDeposit = { amount ->
                        confirmation.show(
                            ConfirmationRequest(
                                id = "deposit-refund",
                                title = depositTitle,
                                message = depositMessage,
                                confirmLabel = depositCta,
                                kind = ConfirmationKind.FINANCIAL,
                                amount = amount,
                                amountLabel = depositAmountLabel,
                            ),
                        )
                    },
                    updateReturn = viewModel::updateReturnForm,
                    declareReturn = {
                        val form = pickup.returnForm
                        val issue = form.incidentType != null || form.missingAccessories.isNotEmpty() || form.condition == AssetCondition.ISSUE_REPORTED
                        confirmation.show(
                            ConfirmationRequest(
                                id = "return-statement",
                                title = returnTitle,
                                message = if (issue) returnMessageIssue else returnMessage,
                                confirmLabel = returnCta,
                                icon = DsR.drawable.ic_tany_return,
                            ),
                        )
                    },
                ),
            )
            }
        }
    }
    if (incidentOpen) {
        IncidentSheet(
            busy = pickup.busy == PickupGesture.INCIDENT,
            onDismiss = { incidentOpen = false },
            onSubmit = { type, description -> viewModel.reportIncident(pointId, type, description) { incidentOpen = false } },
        )
    }
    ConfirmationSheetHost(confirmation) { request ->
        val booking = (state as? LoadState.Loaded)?.value
        when (request.id) {
            "pickup-cash" -> request.amount?.let { amount -> viewModel.confirmPayment(pointId, amount, confirmation::finish) } ?: confirmation.finish()
            "pickup-handover" -> if (booking != null) viewModel.handover(pointId, confirmation::finish) else confirmation.finish()
            "return-statement" -> viewModel.declareReturn(pointId, confirmation::finish)
            "deposit-refund" -> request.amount?.let { amount -> viewModel.handBackDeposit(pointId, amount, confirmation::finish) } ?: confirmation.finish()
            else -> confirmation.finish()
        }
    }
}

private class PickupActions(
    val reportProblem: () -> Unit,
    val nudge: () -> Unit,
    val finish: () -> Unit,
    val dismissCompleted: () -> Unit,
    val scanCustomer: () -> Unit,
    val scanAsset: () -> Unit,
    val takePhoto: () -> Unit,
    val onCondition: (AssetCondition) -> Unit,
    val cash: (MoneyAmount) -> Unit,
    val handover: () -> Unit,
    val scanDepositQr: () -> Unit,
    val handBackDeposit: (MoneyAmount) -> Unit,
    val updateReturn: ((ReturnForm) -> ReturnForm) -> Unit,
    val declareReturn: () -> Unit,
)

@Composable
private fun Content(booking: MerchantBookingDetail, endpoint: ApiEndpoint, pickup: PickupUiState, actions: PickupActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pickup.completed?.let { SuccessCard(it, booking, onFinish = actions.finish, onDismiss = actions.dismissCompleted) }
        SummaryCard(booking, endpoint)
        when (booking.phase) {
            MerchantPhase.CANCELLED -> CancelledCard(booking)
            MerchantPhase.DEPOSIT_DISPUTED, MerchantPhase.BLOCKED_PENDING_REVIEW -> InterventionCard(booking)
            else -> Unit
        }
        if (booking.phase in AWAITING_CUSTOMER_PHASES) WaitingCard(booking, pickup, actions)
        CustomerCard(booking, endpoint)
        if (booking.kind == OperationKind.PICKUP && booking.status == BookingStatus.RESERVED) {
            PickupCard(booking, pickup, actions)
        }
        if (booking.kind == OperationKind.RETURN && booking.status == BookingStatus.COLLECTED) {
            ReturnCard(booking, pickup, actions)
        }
        DepositCard(booking, pickup, actions)
        ScheduleCard(booking)
        BookingDetailsSections(booking, endpoint)
    }
}

/** Phases where the customer must confirm in TANY (the screen re-reads, the merchant can send a reminder). */
private val AWAITING_CUSTOMER_PHASES = setOf(
    MerchantPhase.PICKUP_AWAITING_CUSTOMER,
    MerchantPhase.RETURN_AWAITING_CUSTOMER,
    MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
)

/** Re-read cadence while the customer confirms (same as TANY Collect iOS). */
private const val AWAITING_POLL_MS = 3_000L

/** Product, asset, kind and the SERVER phase with its explanation (never a locally inferred next step). */
@Composable
private fun SummaryCard(booking: MerchantBookingDetail, endpoint: ApiEndpoint) {
    val phase = booking.phase.ui()
    val colors = TanyTheme.colors
    TanyCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(booking.product.displayImage),
                contentDescription = booking.product.name,
                modifier = Modifier.width(88.dp),
                padding = 8.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TanyToneIcon(booking.kind.icon(), phase.tone, size = 24.dp)
                    Text(stringResource(booking.kind.label()), style = TanyTheme.typography.label, color = colors.textMuted)
                }
                Text(booking.product.name, style = TanyTheme.typography.title)
                (booking.asset?.code ?: booking.assetCode)?.let { TanyCodePill(ltrIsolated(it)) }
            }
        }
        TanyNotice(
            title = stringResource(phase.label),
            message = stringResource(phase.description),
            tone = phase.tone,
            icon = phase.tone.takeIf { it == TanyTone.NEUTRAL }?.let { DsR.drawable.ic_tany_info },
        )
        if (booking.requiresTanyIntervention) {
            TanyNotice(message = stringResource(R.string.booking_tany_intervention), tone = TanyTone.WARNING, icon = DsR.drawable.ic_tany_shield)
        }
    }
}

@Composable
private fun CustomerCard(booking: MerchantBookingDetail, endpoint: ApiEndpoint) {
    TanyCard {
        // Minimal customer data only — never Trusted, never identity documents.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Verified profile photo: only sent by the server during the pickup (signed ~5 min) to recognise the customer.
            val photo = booking.customer.identity?.profilePhotoUrl
            if (photo != null) {
                AsyncImage(
                    model = endpoint.resolveMedia(photo),
                    contentDescription = stringResource(R.string.pickup_customer_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                )
            } else {
                TanyAvatar(booking.customer.shortName, size = 48.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.booking_customer), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                Text(booking.customer.shortName, style = TanyTheme.typography.headline)
                Text(ltrIsolated("•••• ${booking.customer.phoneLast4}"), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
            }
        }
        if (booking.customer.identity?.verified == true) {
            TanyStatusChip(stringResource(R.string.booking_identity_verified), TanyTone.SUCCESS, size = TanyChipSize.SMALL)
        }
        if (booking.status == BookingStatus.RESERVED) {
            Text(stringResource(R.string.booking_check_phone), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        }
    }
}

/** Card header: icon tile + title. */
@Composable
private fun FlowHeader(icon: Int, title: String, tone: TanyTone) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TanyToneIcon(icon, tone, size = 36.dp)
        Text(title, style = TanyTheme.typography.headline, modifier = Modifier.semantics { heading() })
    }
}

/**
 * Pickup checklist = the server's facts (timestamps, photo count, payment status); each unfinished step offers its
 * gesture and the server accepts or refuses it (order, window, identity, amount). Nothing is inferred locally.
 */
@Composable
private fun PickupCard(booking: MerchantBookingDetail, pickup: PickupUiState, actions: PickupActions) {
    val facts = booking.pickup
    val handedOver = facts?.merchantConfirmedAt != null
    val busy = pickup.busy != null
    val due = booking.payment?.totalDueAtPickup ?: booking.pricing?.totalDueAtPickup
    val paid = booking.payment?.status == PaymentStatus.PAID
    TanyCard(contentPadding = 18.dp) {
        FlowHeader(DsR.drawable.ic_tany_pickup, stringResource(R.string.pickup_title), TanyTone.ACTION)
        due?.let { amount ->
            TanyAmountPanel(
                label = stringResource(if (paid) R.string.pickup_amount_paid else R.string.booking_total_due),
                amount = amount,
                tone = if (paid) TanyTone.SUCCESS else TanyTone.NEUTRAL,
                icon = if (paid) DsR.drawable.ic_tany_check else DsR.drawable.ic_tany_cash,
                caption = stringResource(R.string.pickup_amount_caption),
            ) {
                // Server breakdown only (rental + deposit), never recomputed.
                val rental = booking.payment?.rentalAmount ?: booking.pricing?.rentalTotal
                val deposit = booking.payment?.depositAmount ?: booking.pricing?.deposit
                rental?.let { TanyInfoRow(stringResource(R.string.booking_rental_amount)) { MoneyText(it) } }
                deposit?.let { TanyInfoRow(stringResource(R.string.booking_deposit_amount)) { MoneyText(it) } }
            }
        }
        if (pickup.incidentReported) {
            TanyNotice(message = stringResource(R.string.incident_reported_continue), tone = TanyTone.WARNING, icon = DsR.drawable.ic_tany_shield)
        }
        if (handedOver) {
            facts?.completionDeadline?.let {
                TanyInfoRow(stringResource(R.string.pickup_complete_before), icon = DsR.drawable.ic_tany_clock) {
                    BusinessDateTimeText(it, style = TanyTheme.typography.label)
                }
            }
        }
        Column(Modifier.padding(top = 4.dp)) {
            Step(
                label = stringResource(R.string.pickup_step_customer),
                done = facts?.clientVerifiedAt != null,
                action = if (!handedOver) stringResource(R.string.pickup_scan_customer) else null,
                actionIcon = DsR.drawable.ic_tany_qr,
                onAction = actions.scanCustomer,
                enabled = !busy,
            )
            Step(
                label = stringResource(R.string.pickup_step_asset, booking.asset?.code?.let(::ltrIsolated) ?: booking.assetCode?.let(::ltrIsolated).orEmpty()),
                done = facts?.assetVerifiedAt != null,
                action = if (!handedOver) stringResource(R.string.pickup_scan_asset) else null,
                actionIcon = DsR.drawable.ic_tany_tag,
                onAction = actions.scanAsset,
                enabled = !busy,
            )
            val photoCount = facts?.photoCount ?: 0
            Step(
                label = stringResource(R.string.pickup_step_photo, photoCount),
                done = photoCount > 0,
                // A photo can be added even once one exists (up to the server's limit).
                action = if (!handedOver) stringResource(if (photoCount > 0) R.string.pickup_add_photo else R.string.pickup_take_photo) else null,
                actionIcon = DsR.drawable.ic_tany_camera,
                onAction = actions.takePhoto,
                enabled = !busy,
                loading = pickup.busy == PickupGesture.PHOTO,
                showActionWhenDone = true,
                supporting = if (!handedOver) stringResource(R.string.photo_guide) else null,
                extra = if (!handedOver) {
                    {
                        ConditionChoice(pickup.photoCondition, actions.onCondition)
                        TanyButton(
                            stringResource(R.string.incident_report_action),
                            actions.reportProblem,
                            style = TanyButtonStyle.TEXT,
                            icon = DsR.drawable.ic_tany_warning,
                            enabled = !busy,
                            compact = true,
                        )
                    }
                } else {
                    null
                },
            )
            Step(
                label = stringResource(R.string.pickup_step_cash),
                done = paid,
                action = if (!handedOver && !paid && due != null) stringResource(R.string.pickup_cash_action) else null,
                actionIcon = DsR.drawable.ic_tany_cash,
                actionStyle = TanyButtonStyle.PRIMARY,
                onAction = { due?.let(actions.cash) },
                enabled = !busy,
                loading = pickup.busy == PickupGesture.PAYMENT,
                trailing = { due?.let { MoneyText(it) } },
            )
            Step(
                label = stringResource(R.string.pickup_step_handover),
                done = handedOver,
                action = if (!handedOver) stringResource(R.string.pickup_handover_action) else null,
                actionIcon = DsR.drawable.ic_tany_pickup,
                actionStyle = TanyButtonStyle.PRIMARY,
                onAction = actions.handover,
                enabled = !busy,
                loading = pickup.busy == PickupGesture.HANDOVER,
            )
            Step(
                label = stringResource(R.string.pickup_step_customer_confirms),
                done = facts?.customerConfirmedAt != null,
                waiting = handedOver,
                action = null,
                onAction = {},
                isLast = true,
            )
        }
        pickup.error?.takeIf { pickup.failed != PickupGesture.DEPOSIT_REFUND }?.let {
            TanyNotice(message = it.text(), tone = TanyTone.DANGER)
        }
    }
}

/**
 * One checklist step on the timeline. State comes from the server's facts: done (✓), waiting for the customer (◷) or
 * to do (○). The step's gesture is offered while it is not done; the server accepts or refuses it.
 */
@Composable
private fun Step(
    label: String,
    done: Boolean,
    action: String?,
    onAction: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    waiting: Boolean = false,
    isLast: Boolean = false,
    actionIcon: Int? = null,
    actionStyle: TanyButtonStyle = TanyButtonStyle.TONAL,
    showActionWhenDone: Boolean = false,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val state = when {
        done -> TanyStepState.DONE
        waiting -> TanyStepState.WAITING
        else -> TanyStepState.TODO
    }
    val stateLabel = stringResource(
        when (state) {
            TanyStepState.DONE -> R.string.pickup_done
            TanyStepState.WAITING -> R.string.step_waiting
            TanyStepState.TODO -> R.string.pickup_todo
        },
    )
    val showAction = action != null && (!done || showActionWhenDone)
    val showExtra = extra != null && (!done || showAction)
    TanyTimelineStep(
        title = label,
        state = state,
        stateLabel = stateLabel,
        supporting = supporting?.takeIf { !done },
        isLast = isLast,
        trailing = trailing,
        action = if (showAction || showExtra) {
            {
                if (showExtra && extra != null) extra()
                if (showAction && action != null) {
                    TanyButton(
                        action,
                        onAction,
                        style = if (done) TanyButtonStyle.SECONDARY else actionStyle,
                        enabled = enabled,
                        loading = loading,
                        icon = actionIcon,
                        compact = true,
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ConditionChoice(selected: AssetCondition, onSelect: (AssetCondition) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.pickup_condition_label), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        TanySegmentedControl(
            options = listOf(
                TanySegment(AssetCondition.GOOD, stringResource(R.string.pickup_condition_good), DsR.drawable.ic_tany_check),
                TanySegment(AssetCondition.ISSUE_REPORTED, stringResource(R.string.pickup_condition_issue), DsR.drawable.ic_tany_warning),
            ),
            // Any other server condition is shown as « good » until the merchant picks one (same as before).
            selected = if (selected == AssetCondition.ISSUE_REPORTED) AssetCondition.ISSUE_REPORTED else AssetCondition.GOOD,
            onSelect = onSelect,
        )
    }
}

/**
 * Return checklist = the server's facts (`return.*`); the statement (condition, missing accessories, incident) is sent
 * once with the final confirmation. Order and requirements (QR → label → photo → statement) are checked by the server.
 */
@Composable
private fun ReturnCard(booking: MerchantBookingDetail, ui: PickupUiState, actions: PickupActions) {
    val facts = booking.returnInfo
    val declared = facts?.merchantConfirmedAt != null
    val busy = ui.busy != null
    TanyCard(contentPadding = 18.dp) {
        FlowHeader(DsR.drawable.ic_tany_return, stringResource(R.string.return_title), TanyTone.ACTION)
        // Lateness is the server's (`lateMinutes`) and never blocks the return.
        booking.lateMinutes?.takeIf { it > 0 }?.let { late ->
            TanyNotice(
                title = stringResource(R.string.return_late_title, durationText(late)),
                message = stringResource(R.string.return_late_message),
                tone = TanyTone.DANGER,
                icon = DsR.drawable.ic_tany_clock,
            )
        }
        Column(Modifier.padding(top = 4.dp)) {
            Step(
                label = stringResource(R.string.return_step_customer),
                done = facts?.clientVerifiedAt != null,
                action = if (!declared) stringResource(R.string.pickup_scan_customer) else null,
                actionIcon = DsR.drawable.ic_tany_qr,
                onAction = actions.scanCustomer,
                enabled = !busy,
            )
            Step(
                label = stringResource(R.string.pickup_step_asset, booking.asset?.code?.let(::ltrIsolated) ?: booking.assetCode?.let(::ltrIsolated).orEmpty()),
                done = facts?.assetVerifiedAt != null,
                action = if (!declared) stringResource(R.string.pickup_scan_asset) else null,
                actionIcon = DsR.drawable.ic_tany_tag,
                onAction = actions.scanAsset,
                enabled = !busy,
            )
            val photoCount = facts?.photoCount ?: 0
            Step(
                label = stringResource(R.string.pickup_step_photo, photoCount),
                done = photoCount > 0,
                action = if (!declared) stringResource(if (photoCount > 0) R.string.pickup_add_photo else R.string.pickup_take_photo) else null,
                actionIcon = DsR.drawable.ic_tany_camera,
                onAction = actions.takePhoto,
                enabled = !busy,
                loading = ui.busy == PickupGesture.PHOTO,
                showActionWhenDone = true,
                supporting = if (!declared) stringResource(R.string.photo_guide) else null,
                extra = if (!declared) {
                    { ConditionChoice(ui.photoCondition, actions.onCondition) }
                } else {
                    null
                },
            )
            Step(
                label = stringResource(R.string.return_step_statement),
                done = declared,
                action = if (!declared) stringResource(R.string.return_statement_action) else null,
                actionIcon = DsR.drawable.ic_tany_check,
                actionStyle = TanyButtonStyle.PRIMARY,
                onAction = actions.declareReturn,
                enabled = !busy,
                loading = ui.busy == PickupGesture.RETURN_STATEMENT,
                extra = if (!declared) {
                    { ReturnStatementForm(booking, ui.returnForm, actions.updateReturn) }
                } else {
                    null
                },
            )
            Step(
                label = stringResource(R.string.return_step_customer_confirms),
                done = facts?.customerConfirmedAt != null,
                waiting = declared,
                action = null,
                onAction = {},
                isLast = true,
            )
        }
        ui.error?.takeIf { ui.failed != PickupGesture.DEPOSIT_REFUND }?.let {
            TanyNotice(message = it.text(), tone = TanyTone.DANGER)
        }
    }
}

/**
 * Deposit hand-back = the server's facts: amount to hand back (`deposit.toRefundAmount`), deferred-refund QR check
 * (`refundPickup`), merchant / customer confirmations. Shown only while the server expects the gesture
 * (`merchantAction == HAND_BACK`) or awaits the customer's confirmation. The decision and the amount are TANY's; the
 * customer alone confirms the amount received.
 */
@Composable
private fun DepositCard(booking: MerchantBookingDetail, ui: PickupUiState, actions: PickupActions) {
    val deposit = booking.deposit ?: return
    val handedBack = deposit.merchantRefundConfirmedAt != null
    val awaitingCustomer = handedBack && deposit.customerRefundConfirmedAt == null
    if (deposit.merchantAction != MerchantDepositAction.HAND_BACK && !awaitingCustomer) return
    val busy = ui.busy != null
    val amount = deposit.toRefundAmount
    val refundPickup = deposit.refundPickup
    TanyCard(contentPadding = 18.dp, accent = TanyTone.ACTION.takeIf { !handedBack }) {
        FlowHeader(DsR.drawable.ic_tany_cash, stringResource(R.string.deposit_title), TanyTone.WARNING)
        TanyAmountPanel(
            label = stringResource(if (handedBack) R.string.deposit_amount_handed_back else R.string.deposit_amount_to_hand_back),
            amount = amount,
            tone = if (handedBack) TanyTone.SUCCESS else TanyTone.WARNING,
            icon = if (handedBack) DsR.drawable.ic_tany_check else DsR.drawable.ic_tany_cash,
            caption = if (refundPickup?.partial == true) stringResource(R.string.deposit_partial) else stringResource(R.string.deposit_decided_by_tany),
        )
        refundPickup?.let {
            Text(
                stringResource(if (it.partial) R.string.deposit_decision_partial else R.string.deposit_decision_full),
                style = TanyTheme.typography.label,
                color = TanyTheme.colors.textMuted,
            )
        }
        // Deferred refund: the hand-back is offered once the SERVER recorded the customer's deposit QR.
        val qrPending = refundPickup?.qrRequired == true && refundPickup.verifiedAt == null
        Column(Modifier.padding(top = 4.dp)) {
            if (refundPickup?.qrRequired == true) {
                // A refused hand-back (`deposit_refund_qr_required`, check older than 15 min) offers the scan again.
                val qrMissing = ui.failed == PickupGesture.DEPOSIT_REFUND && ui.error == CollectOperationError.DepositRefundQrRequired
                Step(
                    label = stringResource(R.string.deposit_step_qr),
                    done = refundPickup.verifiedAt != null && !qrMissing,
                    action = if (!handedBack) stringResource(R.string.deposit_scan_qr) else null,
                    actionIcon = DsR.drawable.ic_tany_qr,
                    onAction = actions.scanDepositQr,
                    enabled = !busy,
                )
            }
            Step(
                label = stringResource(R.string.deposit_step_hand_back),
                done = handedBack,
                action = if (!handedBack && !qrPending && amount > MoneyAmount.ZERO) stringResource(R.string.deposit_hand_back_action) else null,
                supporting = if (!handedBack) stringResource(R.string.deposit_customer_closes) else null,
                actionIcon = DsR.drawable.ic_tany_cash,
                actionStyle = TanyButtonStyle.PRIMARY,
                onAction = { actions.handBackDeposit(amount) },
                enabled = !busy,
                loading = ui.busy == PickupGesture.DEPOSIT_REFUND,
                trailing = { MoneyText(amount) },
            )
            Step(
                label = stringResource(R.string.deposit_step_customer),
                done = deposit.customerRefundConfirmedAt != null,
                waiting = awaitingCustomer,
                action = null,
                onAction = {},
                isLast = true,
            )
        }
        ui.error?.takeIf { ui.failed == PickupGesture.DEPOSIT_REFUND }?.let {
            TanyNotice(message = it.text(), tone = TanyTone.DANGER)
        }
    }
}

/** Schedule and amounts of the booking (server values, Africa/Casablanca). */
@Composable
private fun ScheduleCard(booking: MerchantBookingDetail) {
    val formatters = LocalTanyFormatters.current
    val period = booking.effectiveUsagePeriod()
    TanySectionHeader(stringResource(R.string.booking_schedule), modifier = Modifier.padding(top = 4.dp))
    TanyCard {
        TanyInfoRow(stringResource(R.string.booking_usage_period), icon = DsR.drawable.ic_tany_calendar) {
            Text(
                "${formatters.usagePeriod(period.startDate, period.endDate)} · " +
                    pluralStringResource(R.plurals.booking_days, period.dayCount, period.dayCount),
                style = TanyTheme.typography.label,
            )
        }
        TanyInfoRow(stringResource(R.string.booking_pickup_window), icon = DsR.drawable.ic_tany_pickup) {
            BusinessDateTimeText(booking.pickupWindowStart, end = booking.pickupWindowEnd, style = TanyTheme.typography.label)
        }
        TanyInfoRow(stringResource(R.string.booking_return_by), icon = DsR.drawable.ic_tany_return) {
            BusinessDateTimeText(booking.returnDeadline, style = TanyTheme.typography.label)
        }
        booking.pricing?.let {
            TanyDivider()
            TanyInfoRow(stringResource(R.string.booking_total_due), emphasized = true) { MoneyText(it.totalDueAtPickup) }
        }
        booking.depositAction.label()?.let { TanyStatusChip(stringResource(it), TanyTone.WARNING, size = TanyChipSize.SMALL) }
    }
}

/** Incident types the return statement accepts (contract: DAMAGED · MISSING_ACCESSORY · VERY_DIRTY · OTHER). */
private val RETURN_INCIDENT_TYPES = listOf(IncidentType.DAMAGED, IncidentType.MISSING_ACCESSORY, IncidentType.VERY_DIRTY, IncidentType.OTHER)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReturnStatementForm(booking: MerchantBookingDetail, form: ReturnForm, update: ((ReturnForm) -> ReturnForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val accessories = booking.product.includedAccessories
        if (accessories.isNotEmpty()) {
            Text(stringResource(R.string.return_missing_title), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
            Column {
                accessories.forEach { name ->
                    // Accessory names are product data: shown verbatim.
                    TanyCheckRow(
                        label = name,
                        checked = name in form.missingAccessories,
                        onCheckedChange = { checked ->
                            update { f -> f.copy(missingAccessories = if (checked) f.missingAccessories + name else f.missingAccessories - name) }
                        },
                    )
                }
            }
        }
        Text(stringResource(R.string.return_incident_title), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf<IncidentType?>(null) + RETURN_INCIDENT_TYPES).forEach { type ->
                TanyChoiceChip(
                    label = stringResource(type.label()),
                    selected = form.incidentType == type,
                    onClick = { update { it.copy(incidentType = type) } },
                )
            }
        }
        if (form.incidentType != null) {
            OutlinedTextField(
                value = form.incidentDescription,
                onValueChange = { text -> update { it.copy(incidentDescription = text.take(BookingDetailViewModel.INCIDENT_DESCRIPTION_MAX)) } },
                label = { Text(stringResource(R.string.return_incident_description)) },
                supportingText = { Text(ltrIsolated("${form.incidentDescription.length}/${BookingDetailViewModel.INCIDENT_DESCRIPTION_MAX}")) },
                minLines = 2,
                colors = tanyFieldColors(),
                shape = TanyTheme.radii.large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun IncidentType?.label(): Int = when (this) {
    null -> R.string.return_incident_none
    IncidentType.DAMAGED -> R.string.return_incident_damaged
    IncidentType.MISSING_ACCESSORY -> R.string.return_incident_missing
    IncidentType.VERY_DIRTY -> R.string.return_incident_dirty
    IncidentType.DEPOSIT_DISPUTE -> R.string.incident_type_deposit_dispute
    IncidentType.HANDOVER_DISPUTED -> R.string.incident_type_handover_disputed
    IncidentType.ASSET_LOCATION_UNRESOLVED -> R.string.incident_type_location_unresolved
    else -> R.string.return_incident_other
}

/**
 * Waiting for the customer's confirmation in TANY (server phase `*_awaiting_customer`): what the merchant did, the
 * reassurance that the screen updates itself, and « Relancer le client » (server-throttled). « Toujours en attente »
 * comes from the SERVER flag `customerConfirmationOverdue` only — never from a local timer. There is never a
 * « confirm for the customer » action.
 */
@Composable
private fun WaitingCard(booking: MerchantBookingDetail, ui: PickupUiState, actions: PickupActions) {
    val overdue = booking.customerConfirmationOverdue
    val tone = if (overdue) TanyTone.DANGER else TanyTone.WARNING
    val (headline, message) = when (booking.phase) {
        MerchantPhase.PICKUP_AWAITING_CUSTOMER -> stringResource(R.string.waiting_pickup_done) to stringResource(R.string.waiting_pickup_message)
        MerchantPhase.RETURN_AWAITING_CUSTOMER -> stringResource(R.string.waiting_return_done) to stringResource(R.string.waiting_return_message)
        else -> {
            val amount = booking.deposit?.refundedAmount?.takeIf { !it.isZero } ?: booking.deposit?.toRefundAmount ?: MoneyAmount.ZERO
            stringResource(R.string.waiting_deposit_done, LocalTanyFormatters.current.money(amount)) to stringResource(R.string.waiting_deposit_message)
        }
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.nudgeAvailableAt) {
        while (System.currentTimeMillis() < ui.nudgeAvailableAt) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val cooldown = ((ui.nudgeAvailableAt - now + 999) / 1_000).toInt().coerceAtLeast(0)
    TanyCard(accent = tone, contentPadding = 18.dp) {
        Text(headline, style = TanyTheme.typography.label, color = TanyTheme.colors.success.accent)
        TanyNotice(
            title = stringResource(if (overdue) R.string.waiting_overdue else R.string.step_waiting),
            message = message,
            tone = tone,
            icon = DsR.drawable.ic_tany_clock,
        )
        booking.waitingMinutes?.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.waiting_since, durationText(it)), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(DsR.drawable.ic_tany_refresh), contentDescription = null, tint = TanyTheme.colors.textSubtle, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.waiting_auto_refresh), style = TanyTheme.typography.caption, color = TanyTheme.colors.textSubtle)
        }
        TanyButton(
            if (cooldown > 0) stringResource(R.string.nudge_cooldown, cooldown) else stringResource(R.string.nudge_action),
            actions.nudge,
            style = TanyButtonStyle.SECONDARY,
            enabled = cooldown == 0,
            loading = ui.nudging,
            icon = DsR.drawable.ic_tany_bell,
        )
        ui.nudgeOutcome?.let {
            Text(
                stringResource(if (it == NudgeOutcome.SENT) R.string.nudge_sent else R.string.nudge_already_sent),
                style = TanyTheme.typography.label,
                color = TanyTheme.colors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (overdue) {
            Text(stringResource(R.string.waiting_later_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
            TanyButton(stringResource(R.string.waiting_back_today), actions.finish, style = TanyButtonStyle.TEXT)
        }
    }
}

/** The customer just confirmed (server timestamp appeared): brief animated success, then back to the list. */
@Composable
private fun SuccessCard(step: CompletedStep, booking: MerchantBookingDetail, onFinish: () -> Unit, onDismiss: () -> Unit) {
    val formatters = LocalTanyFormatters.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible) 1f else 0.6f, label = "success-scale")
    val view = LocalView.current
    LaunchedEffect(step) { view.confirmHaptic() }
    val (title, message) = when (step) {
        CompletedStep.PICKUP -> stringResource(R.string.success_pickup_title) to
            stringResource(R.string.success_pickup_message, booking.product.name, booking.customer.shortName)
        CompletedStep.RETURN -> stringResource(R.string.success_return_title) to
            stringResource(R.string.success_return_message, booking.product.name, booking.customer.shortName)
        CompletedStep.DEPOSIT -> stringResource(R.string.success_deposit_title) to
            stringResource(R.string.success_deposit_message, formatters.money(booking.deposit?.refundedAmount ?: MoneyAmount.ZERO), booking.customer.shortName)
    }
    TanyCard(contentPadding = 20.dp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TanyIllustration(DsR.drawable.ic_tany_check, tone = TanyTone.SUCCESS, size = 88.dp, modifier = Modifier.scale(scale))
            Text(title, style = TanyTheme.typography.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(message, style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted, textAlign = TextAlign.Center)
            // Server facts only: deposit still held after a pickup, asset sent to inspection after a return.
            if (step == CompletedStep.PICKUP) {
                booking.deposit?.heldAmount?.takeIf { !it.isZero }?.let {
                    Text(stringResource(R.string.success_deposit_held, formatters.money(it)), style = TanyTheme.typography.label, textAlign = TextAlign.Center)
                }
            }
            if (booking.asset?.status == AssetStatus.INSPECTION) {
                Text(stringResource(R.string.success_inspection), style = TanyTheme.typography.label, color = TanyTheme.colors.warning.content, textAlign = TextAlign.Center)
            }
        }
        TanyButton(stringResource(R.string.success_finish), onFinish)
        TanyButton(stringResource(R.string.success_stay), onDismiss, style = TanyButtonStyle.TEXT)
    }
}

/** Cancelled booking: who cancelled comes from the server history (`BOOKING_CANCELLED.actorRole`). */
@Composable
private fun CancelledCard(booking: MerchantBookingDetail) {
    val actor = booking.history.lastOrNull { it.type == BookingEventType.BOOKING_CANCELLED }?.actorRole
    TanyCard(accent = TanyTone.DANGER) {
        TanyNotice(
            title = stringResource(
                when (actor) {
                    ActorRole.CUSTOMER -> R.string.cancelled_by_customer
                    ActorRole.TANY -> R.string.cancelled_by_tany
                    else -> R.string.cancelled_generic
                },
            ),
            message = stringResource(R.string.cancelled_do_not_hand_over),
            tone = TanyTone.DANGER,
        )
    }
}

/** Disputed deposit / TANY review: no action is possible from TANY Collect; open incidents are listed. */
@Composable
private fun InterventionCard(booking: MerchantBookingDetail) {
    val disputed = booking.phase == MerchantPhase.DEPOSIT_DISPUTED
    TanyCard(accent = TanyTone.DANGER) {
        TanyNotice(
            title = stringResource(if (disputed) R.string.intervention_disputed_title else R.string.intervention_blocked_title),
            message = stringResource(if (disputed) R.string.intervention_disputed_message else R.string.intervention_blocked_message),
            tone = TanyTone.DANGER,
            icon = DsR.drawable.ic_tany_shield,
        )
        if (booking.asset?.status == AssetStatus.INSPECTION) {
            Text(
                stringResource(R.string.intervention_inspection, ltrIsolated(booking.asset?.code ?: booking.assetCode.orEmpty())),
                style = TanyTheme.typography.label,
                color = TanyTheme.colors.warning.content,
            )
        }
        Text(stringResource(R.string.intervention_informed), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
    }
}

/** Problem reported to TANY during a pickup (type + optional words) — sent once, never retried. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncidentSheet(busy: Boolean, onDismiss: () -> Unit, onSubmit: (IncidentType, String) -> Unit) {
    var type by rememberSaveable { mutableStateOf<IncidentType?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    TanyBottomSheet(onDismiss = onDismiss, dismissible = !busy) {
        Text(stringResource(R.string.incident_sheet_title), style = TanyTheme.typography.title, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.incident_sheet_hint), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RETURN_INCIDENT_TYPES.forEach { t ->
                TanyChoiceChip(label = stringResource(t.label()), selected = type == t, onClick = { type = t })
            }
        }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(BookingDetailViewModel.INCIDENT_DESCRIPTION_MAX) },
            label = { Text(stringResource(R.string.return_incident_description)) },
            minLines = 2,
            colors = tanyFieldColors(),
            shape = TanyTheme.radii.large,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.incident_sheet_footnote), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        TanyButton(
            stringResource(R.string.incident_submit),
            { type?.let { onSubmit(it, description) } },
            enabled = type != null,
            loading = busy,
            icon = DsR.drawable.ic_tany_shield,
        )
        TanyButton(stringResource(DsR.string.tany_action_cancel), onDismiss, style = TanyButtonStyle.TEXT, enabled = !busy)
    }
}

/**
 * Details of the booking (iOS « Détails » sheet), all server data: operation photos, step timestamps, incidents and the
 * history (event TYPES localized — the server's French labels are never shown).
 */
@Composable
private fun BookingDetailsSections(booking: MerchantBookingDetail, endpoint: ApiEndpoint) {
    var zoomed by remember { mutableStateOf<String?>(null) }
    if (booking.photos.isNotEmpty()) {
        TanySectionHeader(stringResource(R.string.details_photos), trailing = booking.photos.size.toString(), modifier = Modifier.padding(top = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(booking.photos, key = { it.id }) { photo ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsyncImage(
                        model = endpoint.resolveMedia(photo.url),
                        contentDescription = stringResource(if (photo.type == PhotoType.RETURN) R.string.details_photo_return else R.string.details_photo_pickup),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(TanyTheme.radii.large)
                            .clickable(role = Role.Image) { zoomed = photo.url },
                    )
                    Text(
                        stringResource(if (photo.type == PhotoType.RETURN) R.string.details_photo_return else R.string.details_photo_pickup),
                        style = TanyTheme.typography.caption,
                        color = TanyTheme.colors.textMuted,
                    )
                }
            }
        }
    }
    val steps = buildList {
        booking.pickup?.let { p ->
            add(R.string.details_step_client_verified_pickup to p.clientVerifiedAt)
            add(R.string.details_step_asset_verified_pickup to p.assetVerifiedAt)
            add(R.string.details_step_handover_merchant to p.merchantConfirmedAt)
            add(R.string.details_step_handover_customer to p.customerConfirmedAt)
        }
        booking.returnInfo?.let { r ->
            add(R.string.details_step_client_verified_return to r.clientVerifiedAt)
            add(R.string.details_step_asset_verified_return to r.assetVerifiedAt)
            add(R.string.details_step_return_merchant to r.merchantConfirmedAt)
            add(R.string.details_step_return_customer to r.customerConfirmedAt)
        }
        booking.deposit?.let { d ->
            if (d.merchantRefundConfirmedAt != null || d.customerRefundConfirmedAt != null) {
                add(R.string.details_step_deposit_merchant to d.merchantRefundConfirmedAt)
                add(R.string.details_step_deposit_customer to d.customerRefundConfirmedAt)
            }
        }
    }.filter { it.second != null }
    if (steps.isNotEmpty()) {
        TanySectionHeader(stringResource(R.string.details_steps), modifier = Modifier.padding(top = 4.dp))
        TanyCard {
            steps.forEach { (label, at) ->
                TanyInfoRow(stringResource(label), icon = DsR.drawable.ic_tany_check) {
                    at?.let { BusinessDateTimeText(it, style = TanyTheme.typography.label) }
                }
            }
        }
    }
    if (booking.incidents.isNotEmpty()) {
        TanySectionHeader(stringResource(R.string.details_incidents), trailing = booking.incidents.size.toString(), modifier = Modifier.padding(top = 4.dp))
        TanyCard {
            booking.incidents.forEachIndexed { i, incident ->
                if (i > 0) TanyDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(incident.type.label()), style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
                    TanyStatusChip(stringResource(incident.status.label()), incident.status.tone(), size = TanyChipSize.SMALL)
                }
                // User-generated text, verbatim.
                incident.description?.let { Text(it, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted, maxLines = 4) }
                BusinessDateTimeText(incident.createdAt, style = TanyTheme.typography.caption, color = TanyTheme.colors.textSubtle)
            }
        }
    }
    if (booking.history.isNotEmpty()) {
        TanySectionHeader(stringResource(R.string.details_history), modifier = Modifier.padding(top = 4.dp))
        TanyCard {
            booking.history.asReversed().forEach { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(entry.type.label()), style = TanyTheme.typography.label)
                        Text(stringResource(entry.actorRole.label()), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                    }
                    BusinessDateTimeText(entry.at, style = TanyTheme.typography.caption, color = TanyTheme.colors.textSubtle)
                }
            }
        }
    }
    zoomed?.let { url ->
        Dialog(onDismissRequest = { zoomed = null }) {
            AsyncImage(
                model = endpoint.resolveMedia(url),
                contentDescription = stringResource(R.string.details_photos),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TanyTheme.radii.large)
                    .clickable { zoomed = null },
            )
        }
    }
}

private fun IncidentStatus.label(): Int = when (this) {
    IncidentStatus.RESOLVED -> R.string.incident_status_resolved
    IncidentStatus.UNDER_REVIEW -> R.string.incident_status_review
    else -> R.string.incident_status_open
}

private fun IncidentStatus.tone(): TanyTone = when (this) {
    IncidentStatus.RESOLVED -> TanyTone.SUCCESS
    IncidentStatus.UNDER_REVIEW -> TanyTone.INFO
    else -> TanyTone.WARNING
}

private fun ActorRole.label(): Int = when (this) {
    ActorRole.SELF -> R.string.actor_self
    ActorRole.CUSTOMER -> R.string.actor_customer
    ActorRole.TANY -> R.string.actor_tany
    ActorRole.MERCHANT -> R.string.actor_merchant
    ActorRole.SYSTEM, ActorRole.UNKNOWN -> R.string.actor_system
}

private fun BookingEventType.label(): Int = when (this) {
    BookingEventType.BOOKING_CREATED -> R.string.event_booking_created
    BookingEventType.PICKUP_QR_VERIFIED -> R.string.details_step_client_verified_pickup
    BookingEventType.PICKUP_ASSET_VERIFIED -> R.string.details_step_asset_verified_pickup
    BookingEventType.PICKUP_PHOTO_CAPTURED -> R.string.event_pickup_photo
    BookingEventType.PAYMENT_CONFIRMED -> R.string.event_payment
    BookingEventType.PICKUP_CONFIRMED_MERCHANT -> R.string.details_step_handover_merchant
    BookingEventType.PICKUP_CONFIRMED_CUSTOMER -> R.string.details_step_handover_customer
    BookingEventType.ASSET_COLLECTED -> R.string.event_asset_collected
    BookingEventType.RETURN_QR_VERIFIED -> R.string.details_step_client_verified_return
    BookingEventType.RETURN_ASSET_VERIFIED -> R.string.details_step_asset_verified_return
    BookingEventType.RETURN_PHOTO_CAPTURED -> R.string.event_return_photo
    BookingEventType.RETURN_CONFIRMED_MERCHANT -> R.string.details_step_return_merchant
    BookingEventType.RETURN_CONFIRMED_CUSTOMER -> R.string.details_step_return_customer
    BookingEventType.ASSET_RETURNED -> R.string.event_asset_returned
    BookingEventType.DEPOSIT_REFUND_QR_VERIFIED -> R.string.event_deposit_qr
    BookingEventType.DEPOSIT_REFUND_MERCHANT -> R.string.details_step_deposit_merchant
    BookingEventType.DEPOSIT_REFUND_CUSTOMER -> R.string.details_step_deposit_customer
    BookingEventType.DEPOSIT_DISPUTED -> R.string.event_deposit_disputed
    BookingEventType.DEPOSIT_DECISION_RECORDED -> R.string.event_deposit_decision
    BookingEventType.DEPOSIT_FORFEITED -> R.string.event_deposit_forfeited
    BookingEventType.DEPOSIT_KEPT_PENDING -> R.string.event_deposit_kept
    BookingEventType.DEPOSIT_REFUND_ADMIN_OVERRIDE -> R.string.event_deposit_override
    BookingEventType.BOOKING_COMPLETED -> R.string.event_booking_completed
    BookingEventType.BOOKING_CANCELLED -> R.string.event_booking_cancelled
    BookingEventType.BOOKING_EXPIRED -> R.string.event_booking_expired
    BookingEventType.INCIDENT_REPORTED -> R.string.event_incident_reported
    BookingEventType.INCIDENT_RESOLVED -> R.string.event_incident_resolved
    BookingEventType.MERCHANT_NUDGED_CUSTOMER -> R.string.event_nudged
    BookingEventType.MERCHANT_OPENED_BOOKING -> R.string.event_opened
    BookingEventType.UNKNOWN -> R.string.event_other
}
