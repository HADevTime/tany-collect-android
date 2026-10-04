package ma.tany.collect.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
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
import ma.tany.collect.feature.today.label
import ma.tany.collect.feature.today.ui
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectRepository
import javax.inject.Inject

/**
 * Read-only merchant booking skeleton (deep-link target). Operation steps (scan, asset, photos, cash, handover,
 * return, deposit) belong to the pickup / return slices and will follow the server's [MerchantBookingDetail.phase].
 */
@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])
    private val _state = MutableStateFlow<LoadState<MerchantBookingDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<MerchantBookingDetail>> = _state.asStateFlow()

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.booking(bookingId, pointId).toLoadState() }
    }
}

@Composable
fun BookingDetailScreen(pointId: String, onBack: () -> Unit, viewModel: BookingDetailViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.booking_title), onBack = onBack, chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> Content(s.value, viewModel.endpoint)
        }
    }
}

@Composable
private fun Content(booking: MerchantBookingDetail, endpoint: ApiEndpoint) {
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
        TanyCard {
            // Minimal customer data only — never Trusted, never identity documents.
            Text(booking.customer.shortName, style = TanyTheme.typography.headline)
            Text("•••• ${booking.customer.phoneLast4}", style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
            if (booking.customer.identity?.verified == true) {
                TanyStatusChip(stringResource(R.string.booking_identity_verified), TanyTone.SUCCESS)
            }
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
