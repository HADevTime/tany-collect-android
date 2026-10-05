package ma.tany.collect.feature.equipment

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import ma.tany.collect.feature.today.tone
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyChoiceChip
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.component.TanyDetailSkeleton
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyLargeHeader
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanyMetricTile
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetCounts
import ma.tany.core.model.collect.AssetDetail
import ma.tany.core.model.collect.AssetGroup
import ma.tany.core.model.collect.AssetItem
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.BookingLite
import ma.tany.core.model.common.UsagePeriod
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

/** View filter over the SERVER's own classification (`group`); null = everything. */
fun filterAssets(assets: List<AssetItem>, group: AssetGroup?): List<AssetItem> =
    if (group == null) assets else assets.filter { it.group == group }

@StringRes
private fun AssetGroup?.filterLabel(): Int = when (this) {
    null -> R.string.equipment_filter_all
    AssetGroup.AVAILABLE -> R.string.equipment_filter_available
    AssetGroup.RESERVED -> R.string.equipment_filter_reserved
    AssetGroup.OUT -> R.string.equipment_filter_out
    AssetGroup.UNAVAILABLE -> R.string.equipment_filter_unavailable
    AssetGroup.UNKNOWN -> R.string.equipment_filter_all
}

private val FILTERS: List<AssetGroup?> = listOf(null, AssetGroup.AVAILABLE, AssetGroup.RESERVED, AssetGroup.OUT, AssetGroup.UNAVAILABLE)

@Composable
fun EquipmentScreen(pointId: String, onOpenAsset: (String) -> Unit, viewModel: EquipmentViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = filterName?.let { name -> AssetGroup.entries.firstOrNull { it.name == name } }
    Column(Modifier.fillMaxSize()) {
        TanyLargeHeader(title = stringResource(R.string.equipment_title), subtitle = stringResource(R.string.equipment_subtitle))
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(header = false, rows = 4)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> if (!s.value.enabled || s.value.assets.isEmpty()) {
                TanyEmptyState(
                    title = stringResource(R.string.equipment_empty_title),
                    message = stringResource(R.string.equipment_empty_message),
                    icon = DsR.drawable.ic_tany_box,
                )
            } else {
                val visible = filterAssets(s.value.assets, filter)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    s.value.counts?.let { counts -> item(key = "counts") { CountsRow(counts) } }
                    item(key = "filters") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .selectableGroup()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FILTERS.forEach { group ->
                                TanyChoiceChip(
                                    label = stringResource(group.filterLabel()),
                                    selected = filter == group,
                                    onClick = { filterName = group?.name },
                                )
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item(key = "none") {
                            Text(
                                stringResource(R.string.equipment_filter_empty),
                                style = TanyTheme.typography.body,
                                color = TanyTheme.colors.textMuted,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(visible, key = { it.id }) { asset -> AssetCard(asset, viewModel.endpoint, onClick = { onOpenAsset(asset.id) }) }
                }
            }
        }
    }
}

/** Server counters of the inventory. */
@Composable
private fun CountsRow(counts: AssetCounts) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TanyMetricTile(counts.available.toString(), stringResource(R.string.equipment_filter_available), Modifier.weight(1f))
        TanyMetricTile(counts.reserved.toString(), stringResource(R.string.equipment_filter_reserved), Modifier.weight(1f))
        TanyMetricTile(counts.out.toString(), stringResource(R.string.equipment_filter_out), Modifier.weight(1f))
        TanyMetricTile(
            counts.attention.toString(),
            stringResource(R.string.asset_attention),
            Modifier.weight(1f),
            tone = TanyTone.WARNING.takeIf { counts.attention > 0 },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetCard(asset: AssetItem, endpoint: ApiEndpoint, onClick: () -> Unit) {
    val formatters = LocalTanyFormatters.current
    val tone = asset.tone.tone()
    TanyCard(onClick = onClick, accent = tone.takeIf { asset.attention }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(asset.product.displayImage),
                contentDescription = asset.product.name,
                modifier = Modifier.width(72.dp),
                padding = 6.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(asset.product.name, style = TanyTheme.typography.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TanyCodePill(ltrIsolated(asset.code))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TanyStatusChip(stringResource(asset.status.label()), tone, size = TanyChipSize.SMALL)
                    if (asset.attention) TanyStatusChip(stringResource(R.string.asset_attention), TanyTone.WARNING, size = TanyChipSize.SMALL)
                    if (asset.maintenance) TanyStatusChip(stringResource(R.string.asset_maintenance), TanyTone.WARNING, size = TanyChipSize.SMALL)
                }
            }
        }
        val next = asset.currentBooking?.let { R.string.asset_current_booking to it } ?: asset.nextBooking?.let { R.string.asset_next_booking_title to it }
        next?.let { (label, booking) ->
            TanyDivider()
            TanyInfoRow(stringResource(label), icon = DsR.drawable.ic_tany_calendar) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatters.businessDayTime(booking.pickupWindowStart), style = TanyTheme.typography.label, color = TanyTheme.colors.textPrimary)
                    Text(ltrIsolated(booking.reference), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDetailScreen(pointId: String, onBack: () -> Unit, onOpenBooking: (String) -> Unit, viewModel: AssetDetailViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val loaded = (state as? LoadState.Loaded)?.value
        TanyTopBar(title = stringResource(R.string.asset_title), onBack = onBack, subtitle = loaded?.code?.let(::ltrIsolated))
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> {
                val asset = s.value
                val formatters = LocalTanyFormatters.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TanyCard(contentPadding = 16.dp) {
                        ProductImageSurface(
                            url = viewModel.endpoint.resolveMedia(asset.product.displayImage),
                            contentDescription = asset.product.name,
                            modifier = Modifier.fillMaxWidth(),
                            aspectRatio = 16f / 10f,
                        )
                        Text(asset.product.name, style = TanyTheme.typography.title)
                        TanyCodePill(ltrIsolated(asset.code))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TanyStatusChip(stringResource(asset.status.label()), asset.tone.tone())
                            if (asset.attention) TanyStatusChip(stringResource(R.string.asset_attention), TanyTone.WARNING)
                            if (asset.maintenance) TanyStatusChip(stringResource(R.string.asset_maintenance), TanyTone.WARNING)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TanyMetricTile(asset.completedBookings.toString(), stringResource(R.string.asset_completed_bookings), Modifier.weight(1f))
                        TanyMetricTile(asset.upcomingBookings.toString(), stringResource(R.string.asset_upcoming_bookings), Modifier.weight(1f))
                        TanyMetricTile(
                            asset.incidentsCount.toString(),
                            stringResource(R.string.asset_incidents),
                            Modifier.weight(1f),
                            tone = TanyTone.WARNING.takeIf { asset.incidentsCount > 0 },
                        )
                    }
                    asset.currentBooking?.let { BookingLiteCard(stringResource(R.string.asset_current_booking), it, onOpenBooking) }
                    asset.nextBooking?.let { BookingLiteCard(stringResource(R.string.asset_next_booking_title), it, onOpenBooking) }
                    if (asset.turnaroundMinutes > 0 || asset.inServiceSince != null) {
                        TanyCard {
                            if (asset.turnaroundMinutes > 0) {
                                TanyInfoRow(stringResource(R.string.asset_turnaround), icon = DsR.drawable.ic_tany_clock) {
                                    Text(stringResource(R.string.asset_minutes, asset.turnaroundMinutes), style = TanyTheme.typography.bodyStrong)
                                }
                            }
                            asset.inServiceSince?.let {
                                TanyInfoRow(stringResource(R.string.asset_in_service_since), icon = DsR.drawable.ic_tany_calendar) {
                                    BusinessDateTimeText(it, style = TanyTheme.typography.bodyStrong)
                                }
                            }
                        }
                    }
                    if (asset.recentBookings.isNotEmpty()) {
                        TanySectionHeader(stringResource(R.string.asset_recent_bookings), modifier = Modifier.padding(top = 8.dp))
                        TanyCard(contentPadding = 0.dp) {
                            Column {
                                asset.recentBookings.forEachIndexed { i, b ->
                                    if (i > 0) TanyDivider(inset = 16.dp)
                                    val period = b.usagePeriod ?: UsagePeriod(b.usageDate, b.usageEndDate ?: b.usageDate, b.dayCount)
                                    TanyInfoRow(ltrIsolated(b.reference), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        Text(formatters.usagePeriod(period.startDate, period.endDate), style = TanyTheme.typography.label)
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

@Composable
private fun BookingLiteCard(title: String, booking: BookingLite, onOpenBooking: (String) -> Unit) {
    TanyCard(onClick = { onOpenBooking(booking.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = TanyTheme.typography.overline, color = TanyTheme.colors.textMuted, modifier = Modifier.weight(1f))
            TanyCodePill(ltrIsolated(booking.reference))
        }
        TanyInfoRow(stringResource(R.string.booking_pickup_window), icon = DsR.drawable.ic_tany_pickup) {
            BusinessDateTimeText(booking.pickupWindowStart, end = booking.pickupWindowEnd, style = TanyTheme.typography.label)
        }
        TanyInfoRow(stringResource(R.string.booking_return_by), icon = DsR.drawable.ic_tany_return) {
            BusinessDateTimeText(booking.returnDeadline, style = TanyTheme.typography.label)
        }
    }
}
