package ma.tany.collect.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.SavedStateHandle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import ma.tany.collect.feature.today.label
import ma.tany.collect.feature.today.tone
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetDetail
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.BookingLite
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectRepository
import javax.inject.Inject

/** READ-ONLY inventory of the active point (flag). No customer data; states derived server-side. */
@HiltViewModel
class EquipmentViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<AssetsResponse>>(LoadState.Loading)
    val state: StateFlow<LoadState<AssetsResponse>> = _state.asStateFlow()

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.assets(pointId).toLoadState() }
    }
}

@Composable
fun EquipmentScreen(pointId: String, onOpenAsset: (String) -> Unit, viewModel: EquipmentViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.equipment_title), chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled || s.value.assets.isEmpty()) {
                TanyEmptyState(title = stringResource(R.string.equipment_empty_title))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.value.assets, key = { it.id }) { asset ->
                        TanyCard(onClick = { onOpenAsset(asset.id) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProductImageSurface(
                                    url = viewModel.endpoint.resolveMedia(asset.product.displayImage),
                                    contentDescription = asset.product.name,
                                    modifier = Modifier.width(56.dp),
                                    padding = 6.dp,
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(asset.product.name, style = TanyTheme.typography.bodyStrong)
                                    Text(ltrIsolated(asset.code), style = TanyTheme.typography.code, color = TanyTheme.colors.textMuted)
                                    TanyStatusChip(stringResource(asset.status.label()), asset.tone.tone())
                                    asset.nextBooking?.let { next ->
                                        Text(
                                            stringResource(R.string.asset_next_booking, ltrIsolated(next.reference), LocalTanyFormatters.current.businessDayTime(next.pickupWindowStart)),
                                            style = TanyTheme.typography.caption,
                                            color = TanyTheme.colors.textMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Read-only asset sheet (`GET points/{id}/assets/{assetId}`): states and bookings WITHOUT any customer data. */
@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val assetId: String = checkNotNull(savedStateHandle["assetId"])
    private val _state = MutableStateFlow<LoadState<AssetDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<AssetDetail>> = _state.asStateFlow()

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.asset(pointId, assetId).toLoadState() }
    }
}

@Composable
fun AssetDetailScreen(pointId: String, onBack: () -> Unit, onOpenBooking: (String) -> Unit, viewModel: AssetDetailViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.asset_title), onBack = onBack, chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> {
                val asset = s.value
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProductImageSurface(
                        url = viewModel.endpoint.resolveMedia(asset.product.displayImage),
                        contentDescription = asset.product.name,
                        modifier = Modifier.fillMaxWidth(),
                        aspectRatio = 4f / 3f,
                    )
                    Text(asset.product.name, style = TanyTheme.typography.title)
                    Text(ltrIsolated(asset.code), style = TanyTheme.typography.code, color = TanyTheme.colors.textMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TanyStatusChip(stringResource(asset.status.label()), asset.tone.tone())
                        if (asset.attention) TanyStatusChip(stringResource(R.string.asset_attention), TanyTone.WARNING)
                        if (asset.maintenance) TanyStatusChip(stringResource(R.string.asset_maintenance), TanyTone.WARNING)
                    }
                    asset.currentBooking?.let { BookingLiteCard(stringResource(R.string.asset_current_booking), it, onOpenBooking) }
                    asset.nextBooking?.let { BookingLiteCard(stringResource(R.string.asset_next_booking_title), it, onOpenBooking) }
                    TanyCard {
                        TanyInfoRow(stringResource(R.string.asset_completed_bookings)) { Count(asset.completedBookings) }
                        TanyInfoRow(stringResource(R.string.asset_upcoming_bookings)) { Count(asset.upcomingBookings) }
                        TanyInfoRow(stringResource(R.string.asset_incidents)) { Count(asset.incidentsCount) }
                        if (asset.turnaroundMinutes > 0) {
                            TanyInfoRow(stringResource(R.string.asset_turnaround)) {
                                Text(stringResource(R.string.asset_minutes, asset.turnaroundMinutes), style = TanyTheme.typography.bodyStrong)
                            }
                        }
                        asset.inServiceSince?.let { TanyInfoRow(stringResource(R.string.asset_in_service_since)) { BusinessDateTimeText(it) } }
                    }
                    if (asset.recentBookings.isNotEmpty()) {
                        Text(stringResource(R.string.asset_recent_bookings), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
                        TanyCard {
                            asset.recentBookings.forEach { b ->
                                val period = b.usagePeriod ?: ma.tany.core.model.common.UsagePeriod(b.usageDate, b.usageEndDate ?: b.usageDate, b.dayCount)
                                TanyInfoRow(ltrIsolated(b.reference)) {
                                    Text(LocalTanyFormatters.current.usagePeriod(period.startDate, period.endDate), style = TanyTheme.typography.body)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingLiteCard(title: String, booking: BookingLite, onOpenBooking: (String) -> Unit) {
    TanyCard(onClick = { onOpenBooking(booking.id) }) {
        Text(title, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        Text(ltrIsolated(booking.reference), style = TanyTheme.typography.bodyStrong)
        TanyInfoRow(stringResource(R.string.booking_pickup_window)) { BusinessDateTimeText(booking.pickupWindowStart, end = booking.pickupWindowEnd) }
        TanyInfoRow(stringResource(R.string.booking_return_by)) { BusinessDateTimeText(booking.returnDeadline) }
    }
}

@Composable
private fun Count(value: Int) = Text(ltrIsolated(value.toString()), style = TanyTheme.typography.bodyStrong)
