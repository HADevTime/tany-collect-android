package ma.tany.collect.feature.booking

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
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
import ma.tany.collect.feature.today.label
import ma.tany.collect.feature.today.ui
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.ConfirmationKind
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationSheetHost
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.BookingStatus
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
enum class PickupGesture { PHOTO, PAYMENT, HANDOVER }

data class PickupUiState(
    val busy: PickupGesture? = null,
    val error: CollectOperationError? = null,
    /** Condition the merchant declares with the next photo. */
    val photoCondition: AssetCondition = AssetCondition.GOOD,
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

    private suspend fun reloadQuietly(pointId: String) {
        val result = repository.booking(bookingId, pointId)
        if (result is ApiResult.Success) _state.value = LoadState.Loaded(result.value)
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
        send(pointId, PickupGesture.PHOTO) {
            try {
                val jpeg = runCatching { photos.encode(file) }.getOrNull()
                    ?: return@send ApiResult.Failure(PHOTO_UNREADABLE)
                operations.uploadPhoto(bookingId, pointId, QrPurpose.PICKUP, condition, jpeg)
            } finally {
                photos.discard(file)
            }
        }
    }

    /** "I received X": [amount] is the server's amount due, sent back exactly. */
    fun confirmPayment(pointId: String, amount: MoneyAmount, onDone: () -> Unit) =
        send(pointId, PickupGesture.PAYMENT, onDone) { operations.confirmPayment(bookingId, pointId, amount) }

    fun handover(pointId: String, onDone: () -> Unit) =
        send(pointId, PickupGesture.HANDOVER, onDone) { operations.handover(bookingId, pointId, null) }

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
        _pickup.update { it.copy(busy = gesture, error = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    _state.value = LoadState.Loaded(result.value)
                    _pickup.update { it.copy(busy = null) }
                }
                is ApiResult.Failure -> {
                    _pickup.update { it.copy(busy = null, error = CollectOperationError.from(result.error)) }
                    // Never retried: the server state is re-read (a network failure may hide a success).
                    reloadQuietly(pointId)
                }
            }
            onDone()
        }
    }

    fun clearError() = _pickup.update { it.copy(error = null) }

    companion object {
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
    onScanCustomer: (bookingId: String) -> Unit,
    onScanAsset: (bookingId: String) -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    // Reloaded each time the screen shows again (back from a scan): server state only.
    LifecycleResumeEffect(pointId) {
        viewModel.load(pointId)
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickup by viewModel.pickup.collectAsStateWithLifecycle()
    val confirmation = rememberConfirmationState()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.onPhotoCaptured(pointId, it) }

    val paymentTitle = stringResource(R.string.pickup_cash_title)
    val paymentMessage = stringResource(R.string.pickup_cash_message)
    val paymentCta = stringResource(R.string.pickup_cash_cta)
    val handoverTitle = stringResource(R.string.pickup_handover_title)
    val handoverMessage = stringResource(R.string.pickup_handover_message)
    val handoverCta = stringResource(R.string.pickup_handover_cta)

    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.booking_title), onBack = onBack, chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> Content(
                booking = s.value,
                endpoint = viewModel.endpoint,
                pickup = pickup,
                actions = PickupActions(
                    scanCustomer = { onScanCustomer(viewModel.bookingId) },
                    scanAsset = { onScanAsset(viewModel.bookingId) },
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
                            ),
                        )
                    },
                    handover = {
                        confirmation.show(
                            ConfirmationRequest(id = "pickup-handover", title = handoverTitle, message = handoverMessage, confirmLabel = handoverCta),
                        )
                    },
                ),
            )
        }
    }
    ConfirmationSheetHost(confirmation) { request ->
        val booking = (state as? LoadState.Loaded)?.value
        when (request.id) {
            "pickup-cash" -> request.amount?.let { amount -> viewModel.confirmPayment(pointId, amount, confirmation::finish) } ?: confirmation.finish()
            "pickup-handover" -> if (booking != null) viewModel.handover(pointId, confirmation::finish) else confirmation.finish()
            else -> confirmation.finish()
        }
    }
}

private class PickupActions(
    val scanCustomer: () -> Unit,
    val scanAsset: () -> Unit,
    val takePhoto: () -> Unit,
    val onCondition: (AssetCondition) -> Unit,
    val cash: (MoneyAmount) -> Unit,
    val handover: () -> Unit,
)

@Composable
private fun Content(booking: MerchantBookingDetail, endpoint: ApiEndpoint, pickup: PickupUiState, actions: PickupActions) {
    val phase = booking.phase.ui()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductImageSurface(
            url = endpoint.resolveMedia(booking.product.displayImage),
            contentDescription = booking.product.name,
            modifier = Modifier.fillMaxWidth(),
            aspectRatio = 4f / 3f,
        )
        TanyStatusChip(stringResource(phase.label), phase.tone)
        Text(booking.product.name, style = TanyTheme.typography.title)
        Text(
            "${ltrIsolated(booking.reference)} · ${booking.assetCode?.let(::ltrIsolated).orEmpty()}",
            style = TanyTheme.typography.caption,
            color = TanyTheme.colors.textMuted,
        )
        CustomerCard(booking, endpoint)
        if (booking.kind == OperationKind.PICKUP && booking.status == BookingStatus.RESERVED) {
            PickupCard(booking, pickup, actions)
        }
        TanyCard {
            TanyInfoRow(stringResource(R.string.booking_pickup_window)) {
                BusinessDateTimeText(booking.pickupWindowStart, end = booking.pickupWindowEnd)
            }
            TanyInfoRow(stringResource(R.string.booking_return_by)) { BusinessDateTimeText(booking.returnDeadline) }
            booking.pricing?.let { TanyInfoRow(stringResource(R.string.booking_total_due), emphasized = true) { MoneyText(it.totalDueAtPickup) } }
            booking.depositAction.label()?.let { TanyStatusChip(stringResource(it), TanyTone.WARNING) }
        }
    }
}

@Composable
private fun CustomerCard(booking: MerchantBookingDetail, endpoint: ApiEndpoint) {
    TanyCard {
        // Minimal customer data only — never Trusted, never identity documents.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Verified profile photo: only sent by the server during the pickup (signed ~5 min) to recognise the customer.
            booking.customer.identity?.profilePhotoUrl?.let { url ->
                AsyncImage(
                    model = endpoint.resolveMedia(url),
                    contentDescription = stringResource(R.string.pickup_customer_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                )
            }
            Column {
                Text(booking.customer.shortName, style = TanyTheme.typography.headline)
                Text("•••• ${booking.customer.phoneLast4}", style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
            }
        }
        if (booking.customer.identity?.verified == true) {
            TanyStatusChip(stringResource(R.string.booking_identity_verified), TanyTone.SUCCESS)
        }
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
    TanyCard {
        Text(stringResource(R.string.pickup_title), style = TanyTheme.typography.headline)
        if (handedOver) {
            Text(stringResource(R.string.pickup_waiting_customer), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
            facts?.completionDeadline?.let {
                Row { Text(stringResource(R.string.pickup_complete_before) + " ", style = TanyTheme.typography.caption); BusinessDateTimeText(it, style = TanyTheme.typography.caption) }
            }
        }
        val busy = pickup.busy != null
        Step(
            label = stringResource(R.string.pickup_step_customer),
            done = facts?.clientVerifiedAt != null,
            action = if (!handedOver) stringResource(R.string.pickup_scan_customer) else null,
            onAction = actions.scanCustomer,
            enabled = !busy,
        )
        Step(
            label = stringResource(R.string.pickup_step_asset, booking.asset?.code?.let(::ltrIsolated) ?: booking.assetCode?.let(::ltrIsolated).orEmpty()),
            done = facts?.assetVerifiedAt != null,
            action = if (!handedOver) stringResource(R.string.pickup_scan_asset) else null,
            onAction = actions.scanAsset,
            enabled = !busy,
        )
        val photoCount = facts?.photoCount ?: 0
        Step(
            label = stringResource(R.string.pickup_step_photo, photoCount),
            done = photoCount > 0,
            action = if (!handedOver) stringResource(if (photoCount > 0) R.string.pickup_add_photo else R.string.pickup_take_photo) else null,
            onAction = actions.takePhoto,
            enabled = !busy,
            loading = pickup.busy == PickupGesture.PHOTO,
        )
        if (!handedOver) ConditionChoice(pickup.photoCondition, actions.onCondition)
        val due = booking.payment?.totalDueAtPickup ?: booking.pricing?.totalDueAtPickup
        val paid = booking.payment?.status == PaymentStatus.PAID
        Step(
            label = stringResource(R.string.pickup_step_cash),
            done = paid,
            action = if (!handedOver && !paid && due != null) stringResource(R.string.pickup_cash_action) else null,
            onAction = { due?.let(actions.cash) },
            enabled = !busy,
            loading = pickup.busy == PickupGesture.PAYMENT,
            trailing = { due?.let { MoneyText(it) } },
        )
        Step(
            label = stringResource(R.string.pickup_step_handover),
            done = handedOver,
            action = if (!handedOver) stringResource(R.string.pickup_handover_action) else null,
            onAction = actions.handover,
            enabled = !busy,
            loading = pickup.busy == PickupGesture.HANDOVER,
        )
        Step(label = stringResource(R.string.pickup_step_customer_confirms), done = facts?.customerConfirmedAt != null, action = null, onAction = {})
        pickup.error?.let {
            Text(
                it.text(),
                color = TanyTheme.colors.danger.accent,
                style = TanyTheme.typography.label,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun Step(
    label: String,
    done: Boolean,
    action: String?,
    onAction: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // State in text (✓ / step status), never colour alone.
            TanyStatusChip(
                stringResource(if (done) R.string.pickup_done else R.string.pickup_todo),
                if (done) TanyTone.SUCCESS else TanyTone.NEUTRAL,
            )
            Text(label, style = TanyTheme.typography.body, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        if (!done && action != null) {
            TanyButton(action, onAction, style = TanyButtonStyle.SECONDARY, enabled = enabled, loading = loading)
        }
    }
}

@Composable
private fun ConditionChoice(selected: AssetCondition, onSelect: (AssetCondition) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.pickup_condition_label), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        listOf(AssetCondition.GOOD to R.string.pickup_condition_good, AssetCondition.ISSUE_REPORTED to R.string.pickup_condition_issue).forEach { (value, text) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == value, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = selected == value, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = TanyTheme.colors.accent))
                Text(stringResource(text), style = TanyTheme.typography.body, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
