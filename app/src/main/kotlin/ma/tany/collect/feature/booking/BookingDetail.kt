package ma.tany.collect.feature.booking

import ma.tany.collect.core.push.PushEvents
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import ma.tany.collect.core.ui.dayLabel
import ma.tany.core.designsystem.component.TanyCardStyle
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.colors
import java.time.Instant
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import ma.tany.core.designsystem.component.TanyBottomSheet
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.component.tanyFieldColors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.IncidentBody
import ma.tany.core.model.collect.KitCheckState
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantDepositAction
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.collect.ReturnBody
import ma.tany.core.model.collect.ReturnIncident
import ma.tany.core.model.collect.handoverChecks
import ma.tany.core.model.collect.returnChecks
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.IncidentType
import ma.tany.core.model.common.MoneyAmount
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
    /** Rental kit V1: elements returned MISSING / DAMAGED (absent = present). Used instead of [missingAccessories] with a kit. */
    val kitIssues: Map<String, KitCheckState> = emptyMap(),
) {
    /** The statement reports something (accessory, kit element or incident): never declared « good ». */
    val reportsIssue: Boolean
        get() = incidentType != null || missingAccessories.isNotEmpty() ||
            kitIssues.values.any { it == KitCheckState.MISSING || it == KitCheckState.DAMAGED }
}

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
    /**
     * Guided flow only: the merchant reviewed the uploaded photo(s) and moved on. A UI acknowledgement — the server still
     * requires (and checks) the photo for the handover / return statement.
     */
    val photosAccepted: Boolean = false,
    /** Rental kit V1 — handover: elements the merchant notes as NOT handed over (everything is handed over by default). */
    val kitNotHandedOver: Set<String> = emptySet(),
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
    pushEvents: PushEvents = PushEvents(),
) : ViewModel() {
    val bookingId: String = checkNotNull(savedStateHandle["bookingId"])

    /** Point of the last read: a push about THIS booking (cancelled, asset replaced, deposit…) re-reads it. */
    private var lastPoint: String? = null

    init {
        viewModelScope.launch {
            pushEvents.received.collect { push ->
                val point = lastPoint ?: return@collect
                if (push.bookingId == bookingId || push.deepLink?.endsWith("/$bookingId") == true) reload(point)
            }
        }
    }

    /** Opened from the Home hero CTA: show the guided flow first (only where [detailMode] allows it). */
    val startFlow: Boolean = savedStateHandle.get<Boolean>("start") ?: false
    private val _state = MutableStateFlow<LoadState<MerchantBookingDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<MerchantBookingDetail>> = _state.asStateFlow()

    private val _pickup = MutableStateFlow(PickupUiState())
    val pickup: StateFlow<PickupUiState> = _pickup.asStateFlow()

    private var pendingPhoto: File? = null

    fun load(pointId: String) {
        lastPoint = pointId
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

    /** Guided flow: the photo is reviewed, show the next step (no server call). */
    fun acceptPhotos() = _pickup.update { it.copy(photosAccepted = true) }

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
                    .also { if (it is ApiResult.Success) _pickup.update { s -> s.copy(photosAccepted = false) } }
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
        val booking = (_state.value as? LoadState.Loaded)?.value
        val condition = booking?.pickup?.condition
            ?.takeIf { it == AssetCondition.GOOD || it == AssetCondition.ISSUE_REPORTED }
        // Rental kit: what physically goes out (differences only; `checks: []` = everything handed over).
        val kit = booking?.kit?.handoverChecks(_pickup.value.kitNotHandedOver)
        send(pointId, PickupGesture.HANDOVER, onDone) {
            operations.handover(bookingId, pointId, condition, kit).also { if (it is ApiResult.Success) _pickup.update { s -> s.copy(kitNotHandedOver = emptySet()) } }
        }
    }

    /** Handover kit checklist: one tap notes an element as not handed over, a second tap restores it. */
    fun toggleHandoverKitItem(itemId: String) = _pickup.update {
        it.copy(kitNotHandedOver = if (itemId in it.kitNotHandedOver) it.kitNotHandedOver - itemId else it.kitNotHandedOver + itemId)
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
        // Same rule as the handover: the condition the SERVER recorded with the return photos, unless the statement
        // itself reports an issue (a missing accessory or an incident is never declared « good »).
        val recorded = (_state.value as? LoadState.Loaded)?.value?.returnInfo?.condition
            ?.takeIf { it == AssetCondition.GOOD || it == AssetCondition.ISSUE_REPORTED }
        // Rental kit: the frozen kit replaces the historical accessory names (differences only, handed-over elements).
        val kit = (_state.value as? LoadState.Loaded)?.value?.kit
        val body = ReturnBody(
            collectPointId = pointId,
            condition = if (form.reportsIssue) AssetCondition.ISSUE_REPORTED else recorded ?: form.condition,
            missingAccessories = if (kit != null) emptyList() else form.missingAccessories.toList(),
            incident = form.incidentType?.let { ReturnIncident(it, form.incidentDescription.trim().take(INCIDENT_DESCRIPTION_MAX).ifBlank { null }) },
            kit = kit?.returnChecks(form.kitIssues),
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
    // null = follow the SERVER stage (guided while an operation is under way at the counter); true / false = the
    // merchant opened or left the guided flow.
    var flowChoice by rememberSaveable { mutableStateOf<Boolean?>(if (viewModel.startFlow) true else null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val loaded = (state as? LoadState.Loaded)?.value
    val phase = loaded?.phase
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
    val mode = loaded?.let { detailMode(it.stage(), flowChoice, pickup.completed != null) } ?: DetailMode.OVERVIEW
    BackHandler(enabled = mode != DetailMode.OVERVIEW) {
        if (mode == DetailMode.SUCCESS) viewModel.dismissCompleted()
        flowChoice = false
    }

    val context = LocalContext.current
    val formatters = LocalTanyFormatters.current
    Column(Modifier.fillMaxSize()) {
        val returnFlow = loaded?.isReturnFlow() == true
        val (title, subtitle) = when {
            loaded == null -> stringResource(R.string.booking_title) to null
            mode == DetailMode.GUIDED && returnFlow -> stringResource(R.string.return_title) to
                stringResource(R.string.flow_step_of, loaded.returnStep(pickup.photosAccepted).position(), loaded.returnStepCount())
            mode == DetailMode.GUIDED -> stringResource(R.string.pickup_title) to
                stringResource(R.string.flow_step_of, loaded.pickupStep(pickup.photosAccepted).position(), PICKUP_STEP_COUNT)
            else -> stringResource(R.string.booking_title) to ltrIsolated(loaded.reference)
        }
        TanyTopBar(
            title = title,
            subtitle = subtitle,
            onBack = if (mode == DetailMode.OVERVIEW) onBack else ({ if (mode == DetailMode.SUCCESS) viewModel.dismissCompleted(); flowChoice = false }),
        )
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> {
                val booking = s.value
                // The scan purpose follows the server's operation kind (pickup or return).
                val purpose = if (booking.isReturnFlow()) QrPurpose.RETURN else QrPurpose.PICKUP
                val actions = PickupActions(
                    reportProblem = { incidentOpen = true },
                    nudge = { viewModel.nudge(pointId) },
                    finish = onBack,
                    dismissCompleted = {
                        viewModel.dismissCompleted()
                        flowChoice = false
                    },
                    scanCustomer = { onScanCustomer(viewModel.bookingId, purpose) },
                    scanAsset = { onScanAsset(viewModel.bookingId, purpose) },
                    takePhoto = { camera.launch(viewModel.photoTarget()) },
                    onCondition = viewModel::onPhotoCondition,
                    acceptPhotos = viewModel::acceptPhotos,
                    cash = { amount ->
                        confirmation.show(
                            ConfirmationRequest(
                                id = "pickup-cash",
                                title = context.getString(R.string.pickup_cash_title),
                                message = context.getString(R.string.pickup_cash_message),
                                confirmLabel = context.getString(R.string.pickup_cash_cta_amount, formatters.money(amount)),
                                kind = ConfirmationKind.FINANCIAL,
                                amount = amount,
                                amountLabel = context.getString(R.string.pickup_cash_amount_label),
                            ),
                        )
                    },
                    handover = {
                        confirmation.show(
                            ConfirmationRequest(
                                id = "pickup-handover",
                                title = context.getString(R.string.pickup_handover_title),
                                message = context.getString(R.string.pickup_handover_message_named, booking.product.name, booking.customer.shortName) +
                                    // Rental kit: elements noted as not handed over are restated (never blocking).
                                    pickup.kitNotHandedOver.size.takeIf { booking.kit != null && it > 0 }?.let { n ->
                                        "\n\n" + context.resources.getQuantityString(R.plurals.kit_not_handed_over_count, n, n)
                                    }.orEmpty(),
                                confirmLabel = context.getString(R.string.pickup_handover_cta),
                                icon = DsR.drawable.ic_tany_pickup,
                            ),
                        )
                    },
                    scanDepositQr = { onScanCustomer(viewModel.bookingId, QrPurpose.DEPOSIT_REFUND) },
                    handBackDeposit = { amount ->
                        confirmation.show(
                            ConfirmationRequest(
                                id = "deposit-refund",
                                title = context.getString(R.string.deposit_confirm_title),
                                message = context.getString(R.string.deposit_confirm_message),
                                confirmLabel = context.getString(R.string.deposit_confirm_cta),
                                kind = ConfirmationKind.FINANCIAL,
                                amount = amount,
                                amountLabel = context.getString(R.string.deposit_amount_label),
                            ),
                        )
                    },
                    updateReturn = viewModel::updateReturnForm,
                    toggleHandoverKitItem = viewModel::toggleHandoverKitItem,
                    declareReturn = {
                        val form = pickup.returnForm
                        val issue = form.reportsIssue || booking.returnInfo?.condition == AssetCondition.ISSUE_REPORTED
                        confirmation.show(
                            ConfirmationRequest(
                                id = "return-statement",
                                title = context.getString(R.string.return_statement_title),
                                message = context.getString(if (issue) R.string.return_statement_message_issue else R.string.return_statement_message),
                                confirmLabel = context.getString(R.string.return_statement_cta),
                                icon = DsR.drawable.ic_tany_return,
                            ),
                        )
                    },
                )
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { tanyScreenTransition(forward = targetState.ordinal > initialState.ordinal) },
                    label = "booking-mode",
                ) { current ->
                    when (current) {
                        DetailMode.SUCCESS -> pickup.completed?.let { SuccessView(it, booking, endpoint = viewModel.endpoint, actions = actions) }
                            ?: Overview(booking, viewModel.endpoint, onStart = { flowChoice = true })
                        DetailMode.GUIDED -> GuidedFlow(booking, viewModel.endpoint, pickup, actions)
                        DetailMode.OVERVIEW -> Overview(booking, viewModel.endpoint, onStart = { flowChoice = true })
                    }
                }
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
        when (request.id) {
            "pickup-cash" -> request.amount?.let { amount -> viewModel.confirmPayment(pointId, amount, confirmation::finish) } ?: confirmation.finish()
            "pickup-handover" -> if (loaded != null) viewModel.handover(pointId, confirmation::finish) else confirmation.finish()
            "return-statement" -> viewModel.declareReturn(pointId, confirmation::finish)
            "deposit-refund" -> request.amount?.let { amount -> viewModel.handBackDeposit(pointId, amount, confirmation::finish) } ?: confirmation.finish()
            else -> confirmation.finish()
        }
    }
}

/** What the booking screen shows: the state-based overview, the guided flow, or the short success state. */
enum class DetailMode { OVERVIEW, GUIDED, SUCCESS }

/**
 * Pure choice of the screen mode. The guided flow is only ever offered in stages where the SERVER lets an operation
 * start or continue ([STARTABLE_STAGES]); it opens by itself while one is under way ([GUIDED_STAGES]) unless the
 * merchant left it ([choice] = false). A customer confirmation seen on screen shows the success state.
 */
fun detailMode(stage: BookingStage, choice: Boolean?, completed: Boolean): DetailMode = when {
    completed -> DetailMode.SUCCESS
    stage in STARTABLE_STAGES && (choice ?: (stage in GUIDED_STAGES)) -> DetailMode.GUIDED
    else -> DetailMode.OVERVIEW
}

/** Return flow (vs pickup flow): the booking is collected, or a deposit hand-back follows its return. */
fun MerchantBookingDetail.isReturnFlow(): Boolean = stage() in setOf(BookingStage.RENTAL_ACTIVE, BookingStage.RETURN_EXPECTED, BookingStage.RETURN_ACTIVE)

internal class PickupActions(
    val reportProblem: () -> Unit,
    val nudge: () -> Unit,
    val finish: () -> Unit,
    val dismissCompleted: () -> Unit,
    val scanCustomer: () -> Unit,
    val scanAsset: () -> Unit,
    val takePhoto: () -> Unit,
    val onCondition: (AssetCondition) -> Unit,
    val acceptPhotos: () -> Unit,
    val cash: (MoneyAmount) -> Unit,
    val handover: () -> Unit,
    val scanDepositQr: () -> Unit,
    val handBackDeposit: (MoneyAmount) -> Unit,
    val updateReturn: ((ReturnForm) -> ReturnForm) -> Unit,
    val declareReturn: () -> Unit,
    val toggleHandoverKitItem: (String) -> Unit = {},
)

/** Screen-to-screen motion of the booking: a short horizontal slide + fade (mirrored in RTL by the layout). */
internal fun tanyScreenTransition(forward: Boolean): ContentTransform {
    val distance = { full: Int -> full / 8 }
    return if (forward) {
        (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260)) { distance(it) })
            .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(260)) { -distance(it) })
    } else {
        (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260)) { -distance(it) })
            .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(260)) { distance(it) })
    }
}

/**
 * State-based overview, ordered for a glance: STATUS (stage card with the one relevant action) → CUSTOMER → MONEY →
 * PLANNING → photos / incidents / history (secondary, history folded). No execution control is shown before the
 * server opens the pickup (`pickup_upcoming`).
 */
@Composable
private fun Overview(booking: MerchantBookingDetail, endpoint: ApiEndpoint, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StageCard(booking, endpoint, onStart)
        when (booking.phase) {
            MerchantPhase.CANCELLED -> CancelledCard(booking)
            MerchantPhase.DEPOSIT_DISPUTED, MerchantPhase.BLOCKED_PENDING_REVIEW -> InterventionCard(booking)
            else -> Unit
        }
        CustomerCard(booking, endpoint)
        MoneyCard(booking)
        ScheduleCard(booking)
        // Rental kit: frozen snapshot + what was recorded at the handover / return (read-only).
        booking.kit?.takeIf { it.items.isNotEmpty() }?.let { KitOverviewCard(it, endpoint) }
        BookingDetailsSections(booking, endpoint)
    }
}

/** Phases where the customer must confirm in TANY (the screen re-reads, the merchant can send a reminder). */
internal val AWAITING_CUSTOMER_PHASES = setOf(
    MerchantPhase.PICKUP_AWAITING_CUSTOMER,
    MerchantPhase.RETURN_AWAITING_CUSTOMER,
    MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
)

/** Re-read cadence while the customer confirms (same as TANY Collect iOS). */
private const val AWAITING_POLL_MS = 3_000L

/** Headline of the stage card and the server instant(s) it puts forward. */
private data class StageTiming(@StringRes val headline: Int, val start: Instant, val end: Instant?)

private fun MerchantBookingDetail.stageTiming(stage: BookingStage): StageTiming = when (stage) {
    BookingStage.PICKUP_PLANNED -> StageTiming(R.string.stage_pickup_planned, pickupWindowStart, pickupWindowEnd)
    BookingStage.PICKUP_READY -> StageTiming(R.string.stage_pickup_ready, pickupWindowStart, pickupWindowEnd)
    BookingStage.PICKUP_ACTIVE -> StageTiming(R.string.stage_pickup_active, pickupWindowStart, pickupWindowEnd)
    BookingStage.RENTAL_ACTIVE -> StageTiming(R.string.stage_rental_active, returnDeadline, null)
    BookingStage.RETURN_EXPECTED ->
        StageTiming(if (phase == MerchantPhase.RETURN_LATE) R.string.stage_return_late else R.string.stage_return_expected, returnWindowStart ?: returnDeadline, returnDeadline.takeIf { returnWindowStart != null })
    BookingStage.RETURN_ACTIVE -> StageTiming(R.string.stage_return_active, returnDeadline, null)
    BookingStage.ATTENTION -> StageTiming(R.string.stage_attention, updatedAt ?: returnDeadline, null)
    BookingStage.CLOSED -> StageTiming(
        when (phase) {
            MerchantPhase.CANCELLED -> R.string.stage_cancelled
            MerchantPhase.NO_SHOW -> R.string.stage_no_show
            else -> R.string.stage_completed
        },
        completedAt ?: updatedAt ?: returnDeadline,
        null,
    )
}

/**
 * The status card: server phase chip, what this booking is about NOW and when (large time), the object (image, full
 * name, unit code) and the single relevant action of the stage. Before the pickup window it is a preparation card.
 */
@Composable
private fun StageCard(booking: MerchantBookingDetail, endpoint: ApiEndpoint, onStart: () -> Unit) {
    val stage = booking.stage()
    val phase = booking.phase.ui()
    val formatters = LocalTanyFormatters.current
    val colors = TanyTheme.colors
    val timing = booking.stageTiming(stage)
    val time = timing.end?.let { formatters.businessTimeRange(timing.start, it) } ?: formatters.businessTime(timing.start)
    TanyCard(contentPadding = 20.dp, accent = phase.tone.takeIf { it == TanyTone.DANGER }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(timing.headline),
                style = TanyTheme.typography.overline,
                color = colors.textMuted,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TanyStatusChip(stringResource(phase.short), phase.tone, size = TanyChipSize.SMALL)
        }
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(dayLabel(timing.start), style = TanyTheme.typography.label, color = colors.textMuted)
            Text(ltrIsolated(time), style = TanyTheme.typography.largeTitle.copy(fontFeatureSettings = "tnum"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(booking.product.displayImage),
                contentDescription = booking.product.name,
                modifier = Modifier.width(96.dp),
                padding = 8.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Full product name: never truncated on the booking screen.
                Text(booking.product.name, style = TanyTheme.typography.title)
                (booking.asset?.code ?: booking.assetCode)?.let { TanyCodePill(ltrIsolated(it)) }
                Text(
                    "${ltrIsolated(booking.reference)} · ${booking.customer.shortName}",
                    style = TanyTheme.typography.label,
                    color = colors.textMuted,
                )
            }
        }
        if (booking.requiresTanyIntervention) {
            TanyNotice(message = stringResource(R.string.booking_tany_intervention), tone = TanyTone.WARNING, icon = DsR.drawable.ic_tany_shield)
        }
        when (stage) {
            BookingStage.PICKUP_PLANNED -> TanyNotice(
                // Preparation only: the server opens the pickup at the window start (`pickup_ready`).
                title = stringResource(R.string.stage_planned_opens, formatters.businessTime(booking.pickupWindowStart)),
                message = stringResource(R.string.stage_planned_message),
                tone = TanyTone.NEUTRAL,
                icon = DsR.drawable.ic_tany_clock,
            )
            BookingStage.PICKUP_READY -> TanyButton(stringResource(R.string.stage_start_pickup), onStart, icon = DsR.drawable.ic_tany_pickup)
            BookingStage.PICKUP_ACTIVE -> TanyButton(stringResource(R.string.stage_resume_pickup), onStart, icon = DsR.drawable.ic_tany_pickup)
            BookingStage.RENTAL_ACTIVE -> {
                Text(stringResource(R.string.stage_rental_message), style = TanyTheme.typography.label, color = colors.textMuted)
                TanyButton(stringResource(R.string.stage_start_return), onStart, style = TanyButtonStyle.SECONDARY, icon = DsR.drawable.ic_tany_return)
            }
            BookingStage.RETURN_EXPECTED -> {
                // Lateness is the server's (`lateMinutes`) and NEVER blocks the return.
                booking.lateMinutes?.takeIf { it > 0 }?.let { late ->
                    TanyNotice(
                        title = stringResource(R.string.return_late_title, durationText(late)),
                        message = stringResource(R.string.return_late_message),
                        tone = TanyTone.DANGER,
                        icon = DsR.drawable.ic_tany_clock,
                    )
                }
                TanyButton(stringResource(R.string.stage_start_return), onStart, icon = DsR.drawable.ic_tany_return)
            }
            BookingStage.RETURN_ACTIVE -> TanyButton(
                stringResource(
                    if (booking.phase == MerchantPhase.DEPOSIT_TO_REFUND || booking.phase == MerchantPhase.DEPOSIT_AWAITING_CUSTOMER) {
                        R.string.stage_resume_deposit
                    } else {
                        R.string.stage_resume_return
                    },
                ),
                onStart,
                icon = DsR.drawable.ic_tany_return,
            )
            BookingStage.ATTENTION, BookingStage.CLOSED -> Text(
                stringResource(phase.description),
                style = TanyTheme.typography.label,
                color = colors.textMuted,
            )
        }
    }
}

/** Merchant-safe customer data only (short name, 4 last digits, identity verified) — never Trusted / Saved Places. */
@Composable
internal fun CustomerCard(booking: MerchantBookingDetail, endpoint: ApiEndpoint, prominent: Boolean = false) {
    TanyCard(style = if (prominent) TanyCardStyle.OUTLINED else TanyCardStyle.FILLED, contentPadding = if (prominent) 18.dp else 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Verified profile photo: only sent by the server during the pickup (signed ~5 min) to recognise the customer.
            val photo = booking.customer.identity?.profilePhotoUrl
            if (photo != null) {
                AsyncImage(
                    model = endpoint.resolveMedia(photo),
                    contentDescription = stringResource(R.string.pickup_customer_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (prominent) 72.dp else 48.dp)
                        .clip(CircleShape),
                )
            } else {
                TanyAvatar(booking.customer.shortName, size = if (prominent) 56.dp else 40.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.booking_customer), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                Text(booking.customer.shortName, style = if (prominent) TanyTheme.typography.title else TanyTheme.typography.bodyStrong)
                Text(
                    stringResource(R.string.customer_phone_last4, ltrIsolated(booking.customer.phoneLast4)),
                    style = TanyTheme.typography.label,
                    color = TanyTheme.colors.textMuted,
                )
            }
            if (booking.customer.identity?.verified == true) {
                TanyStatusChip(stringResource(R.string.booking_identity_verified), TanyTone.SUCCESS, size = TanyChipSize.SMALL)
            }
        }
    }
}

/**
 * Money of the booking: ONE primary amount for the current stage (to collect at pickup, collected, deposit held, deposit
 * to hand back) with its server breakdown below. Server amounts only, never recomputed.
 */
@Composable
private fun MoneyCard(booking: MerchantBookingDetail) {
    val stage = booking.stage()
    val due = booking.amountDueAtPickup()
    val rental = booking.payment?.rentalAmount ?: booking.pricing?.rentalTotal ?: booking.rentalAmount
    val deposit = booking.payment?.depositAmount ?: booking.pricing?.deposit ?: booking.depositAmount
    val (label, amount, tone) = when {
        stage in setOf(BookingStage.PICKUP_PLANNED, BookingStage.PICKUP_READY, BookingStage.PICKUP_ACTIVE) && due != null ->
            if (booking.pickupPaid()) {
                Triple(R.string.money_collected, due, TanyTone.SUCCESS)
            } else {
                Triple(R.string.money_to_collect, due, TanyTone.NEUTRAL)
            }
        booking.deposit?.merchantAction == MerchantDepositAction.HAND_BACK ->
            Triple(R.string.deposit_amount_to_hand_back_customer, booking.depositHandBack()?.handBack ?: MoneyAmount.ZERO, TanyTone.WARNING)
        booking.deposit?.heldAmount?.isZero == false -> Triple(R.string.money_deposit_held, booking.deposit!!.heldAmount, TanyTone.NEUTRAL)
        else -> Triple(R.string.booking_rental_amount, rental, TanyTone.NEUTRAL)
    }
    TanySectionHeader(stringResource(R.string.money_title), modifier = Modifier.padding(top = 4.dp))
    TanyCard {
        Text(stringResource(label), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        MoneyText(amount, style = TanyTheme.typography.amountHero, color = if (tone == TanyTone.NEUTRAL) TanyTheme.colors.textPrimary else tone.colors().content)
        TanyDivider()
        TanyInfoRow(stringResource(R.string.booking_rental_amount)) { MoneyText(rental) }
        deposit?.takeIf { !it.isZero }?.let { TanyInfoRow(stringResource(R.string.booking_deposit_amount)) { MoneyText(it) } }
        // Late Return Policy V1: the server's TANY retention, already subtracted from the amount to hand back.
        booking.deposit?.latePenaltyAmount?.takeIf { !it.isZero }?.let {
            TanyInfoRow(stringResource(R.string.deposit_retention_label)) { MoneyText(it) }
        }
        booking.payment?.let {
            TanyInfoRow(stringResource(R.string.money_payment)) {
                Text(stringResource(R.string.money_payment_cash), style = TanyTheme.typography.bodyStrong)
            }
        }
        booking.depositAction.label()?.let { TanyStatusChip(stringResource(it), TanyTone.WARNING, size = TanyChipSize.SMALL) }
    }
}

/** Planning of the booking (server values, Africa/Casablanca): rental period, pickup window, return. Strong values. */
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
                style = TanyTheme.typography.bodyStrong,
            )
        }
        TanyInfoRow(stringResource(R.string.booking_pickup_window), icon = DsR.drawable.ic_tany_pickup) {
            BusinessDateTimeText(booking.pickupWindowStart, end = booking.pickupWindowEnd, style = TanyTheme.typography.bodyStrong)
        }
        TanyInfoRow(stringResource(R.string.booking_return_by), icon = DsR.drawable.ic_tany_return) {
            BusinessDateTimeText(booking.returnDeadline, style = TanyTheme.typography.bodyStrong)
        }
    }
}

/** Incident types the return statement accepts (contract: DAMAGED · MISSING_ACCESSORY · VERY_DIRTY · OTHER). */
private val RETURN_INCIDENT_TYPES = listOf(IncidentType.DAMAGED, IncidentType.MISSING_ACCESSORY, IncidentType.VERY_DIRTY, IncidentType.OTHER)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReturnStatementForm(booking: MerchantBookingDetail, form: ReturnForm, endpoint: ApiEndpoint, update: ((ReturnForm) -> ReturnForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val accessories = booking.product.includedAccessories
        val kit = booking.kit?.takeIf { it.items.isNotEmpty() }
        if (kit != null) {
            // « Kit rendu »: the frozen kit (with the TANY transport bag) replaces the historical accessory list.
            ReturnKitCheck(
                kit = kit,
                issues = form.kitIssues,
                endpoint = endpoint,
                onState = { id, state ->
                    update { f -> f.copy(kitIssues = if (state == KitCheckState.PRESENT) f.kitIssues - id else f.kitIssues + (id to state)) }
                },
                onAllPresent = { update { f -> f.copy(kitIssues = emptyMap()) } },
            )
            TanyDivider(Modifier.padding(vertical = 4.dp))
        } else if (accessories.isNotEmpty()) {
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
    if (booking.depositAwaitingTanyDecision()) {
        DepositPendingDecisionCard(booking)
        return
    }
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

/**
 * Deposit awaiting a TANY decision without an open incident (e.g. return on a later business day): the physical return
 * is recorded, NO amount is offered (the hand-back appears only once the server sets one), the merchant may leave.
 */
@Composable
private fun DepositPendingDecisionCard(booking: MerchantBookingDetail) {
    TanyCard(accent = TanyTone.WARNING, modifier = Modifier.testTag(DEPOSIT_PENDING_TAG)) {
        if (booking.returnInfo?.merchantConfirmedAt != null || booking.returnInfo?.returnedAt != null) {
            TanyStatusChip(stringResource(R.string.deposit_pending_return_recorded), TanyTone.SUCCESS, size = TanyChipSize.SMALL)
        }
        TanyNotice(
            title = stringResource(R.string.deposit_pending_title),
            message = stringResource(R.string.deposit_pending_message),
            tone = TanyTone.WARNING,
            icon = DsR.drawable.ic_tany_cash,
        )
        (booking.deposit?.amount ?: booking.payment?.depositAmount)?.let {
            TanyInfoRow(stringResource(R.string.deposit_received_label)) { MoneyText(it) }
        }
        Text(stringResource(R.string.deposit_pending_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
    }
}

/** Test tag of the « Caution en attente de décision TANY » card. */
internal const val DEPOSIT_PENDING_TAG = "deposit-pending-decision"

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
    val expandedLabel = stringResource(R.string.state_expanded)
    val collapsedLabel = stringResource(R.string.state_collapsed)
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
        // Secondary: folded by default (« Historique · 4 événements »), compact timeline when opened.
        var open by rememberSaveable { mutableStateOf(false) }
        val count = pluralStringResource(R.plurals.history_events, booking.history.size, booking.history.size)
        TanyCard(contentPadding = 0.dp) {
            TanyRow(
                title = stringResource(R.string.details_history),
                value = count,
                leadingIcon = DsR.drawable.ic_tany_clock,
                onClick = { open = !open },
                showChevron = false,
                trailing = {
                    Icon(
                        painterResource(DsR.drawable.ic_tany_chevron),
                        contentDescription = null,
                        tint = TanyTheme.colors.textSubtle,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (open) 90f else 0f),
                    )
                },
                modifier = Modifier.semantics {
                    stateDescription = if (open) expandedLabel else collapsedLabel
                },
            )
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    booking.history.asReversed().forEach { entry ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(painterResource(DsR.drawable.ic_tany_check), contentDescription = null, tint = TanyTheme.colors.textSubtle, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(entry.type.label()), style = TanyTheme.typography.label)
                                Text(stringResource(entry.actorRole.label()), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                            }
                            BusinessDateTimeText(entry.at, style = TanyTheme.typography.caption, color = TanyTheme.colors.textSubtle)
                        }
                    }
                }
            }
        }
    }
    zoomed?.let { url -> PhotoZoom(endpoint, url) { zoomed = null } }
}

/** Full-size view of an operation photo (tap or back to close). */
@Composable
internal fun PhotoZoom(endpoint: ApiEndpoint, url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        AsyncImage(
            model = endpoint.resolveMedia(url),
            contentDescription = stringResource(R.string.details_photos),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(TanyTheme.radii.large)
                .clickable(onClick = onDismiss),
        )
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
