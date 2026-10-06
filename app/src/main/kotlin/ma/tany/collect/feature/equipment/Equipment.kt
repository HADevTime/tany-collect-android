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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.collect.feature.scanner.ScanFailureUi
import ma.tany.collect.feature.scanner.ScanInput
import ma.tany.collect.feature.scanner.ScanTarget
import ma.tany.collect.feature.scanner.ScannerScreen
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
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanyProgressBar
import ma.tany.core.designsystem.component.TanySearchField
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.colors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetAttentionReason
import ma.tany.core.model.collect.AssetCounts
import ma.tany.core.model.collect.AssetDetail
import ma.tany.core.model.collect.AssetFilter
import ma.tany.core.model.collect.AssetGroup
import ma.tany.core.model.collect.AssetItem
import ma.tany.core.model.collect.AssetLifecycle
import ma.tany.core.model.collect.AssetLifecycleStatus
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.BookingLite
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.UsagePeriod
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectRepository
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * READ-ONLY inventory of the active point (flag). No customer data; states derived server-side. Filter and search are
 * SERVER parameters (`filter`, `q`): the app never decides which asset needs attention.
 */
@HiltViewModel
class EquipmentViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<AssetsResponse>>(LoadState.Loading)
    val state: StateFlow<LoadState<AssetsResponse>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var job: Job? = null

    fun load(pointId: String, query: String = "", filter: AssetFilter = AssetFilter.ALL, debounceMs: Long = 0) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (_state.value is LoadState.Loaded) _refreshing.value = true else _state.value = LoadState.Loading
            val result = repository.assets(pointId, query, filter.takeIf { it != AssetFilter.ALL })
            if (result is ApiResult.Success || _state.value !is LoadState.Loaded) _state.value = result.toLoadState()
            _refreshing.value = false
        }
    }
}

/** Server filters of the inventory (labels carry the server counts). */
private val FILTERS = listOf(AssetFilter.ALL, AssetFilter.AVAILABLE, AssetFilter.OUT, AssetFilter.ATTENTION)

@StringRes
private fun AssetFilter.label(): Int = when (this) {
    AssetFilter.AVAILABLE -> R.string.equipment_filter_available
    AssetFilter.OUT -> R.string.equipment_filter_out
    AssetFilter.ATTENTION -> R.string.equipment_filter_attention
    else -> R.string.equipment_filter_all
}

private fun AssetFilter.count(counts: AssetCounts?): Int? = when (this) {
    AssetFilter.AVAILABLE -> counts?.available
    AssetFilter.OUT -> counts?.out
    AssetFilter.ATTENTION -> counts?.attention
    else -> counts?.all
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentScreen(
    pointId: String,
    onOpenAsset: (String) -> Unit,
    onScanAsset: () -> Unit = {},
    viewModel: EquipmentViewModel = hiltViewModel(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(AssetFilter.ALL.name) }
    val filter = AssetFilter.entries.firstOrNull { it.name == filterName } ?: AssetFilter.ALL
    val currentQuery by rememberUpdatedState(query)
    val currentFilter by rememberUpdatedState(filter)
    LifecycleResumeEffect(pointId) {
        viewModel.load(pointId, currentQuery, currentFilter)
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyLargeHeader(title = stringResource(R.string.equipment_title), subtitle = stringResource(R.string.equipment_subtitle)) {
            IconButton(onClick = onScanAsset) {
                Icon(painterResource(DsR.drawable.ic_tany_scan), contentDescription = stringResource(R.string.equipment_scan), tint = TanyTheme.colors.textPrimary)
            }
        }
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(header = false, rows = 4)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId, query, filter) })
            is LoadState.Loaded -> when {
                !s.value.enabled -> TanyEmptyState(
                    title = stringResource(R.string.equipment_disabled_title),
                    message = stringResource(R.string.equipment_disabled_message),
                    icon = DsR.drawable.ic_tany_box,
                )
                (s.value.counts?.all ?: s.value.assets.size) == 0 && query.isBlank() && filter == AssetFilter.ALL -> TanyEmptyState(
                    title = stringResource(R.string.equipment_empty_title),
                    message = stringResource(R.string.equipment_empty_message),
                    icon = DsR.drawable.ic_tany_box,
                )
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.load(pointId, query, filter) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "search") {
                            TanySearchField(
                                value = query,
                                onValueChange = {
                                    query = it.take(60)
                                    viewModel.load(pointId, query, filter, debounceMs = 350)
                                },
                                placeholder = stringResource(R.string.equipment_search),
                            )
                        }
                        item(key = "filters") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FILTERS.forEach { f ->
                                    val count = f.count(s.value.counts)
                                    TanyChoiceChip(
                                        label = stringResource(f.label()) + (count?.let { " ($it)" } ?: ""),
                                        selected = filter == f,
                                        onClick = {
                                            filterName = f.name
                                            viewModel.load(pointId, query, f)
                                        },
                                    )
                                }
                            }
                        }
                        if (s.value.assets.isEmpty()) {
                            item(key = "none") {
                                Text(
                                    stringResource(if (query.isNotBlank()) R.string.equipment_no_result else R.string.equipment_filter_empty),
                                    style = TanyTheme.typography.body,
                                    color = TanyTheme.colors.textMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                )
                            }
                        }
                        items(s.value.assets, key = { it.id }) { asset -> AssetCard(asset, viewModel.endpoint, onClick = { onOpenAsset(asset.id) }) }
                    }
                }
            }
        }
    }
}

/** Localized reason an asset needs attention (server enum; the French `attentionLabel` is never shown). */
@StringRes
fun AssetAttentionReason?.label(): Int = when (this) {
    AssetAttentionReason.RETURN_LATE -> R.string.attention_return_late
    AssetAttentionReason.LOCATION_TO_CONFIRM -> R.string.attention_location
    AssetAttentionReason.INSPECTION -> R.string.attention_inspection
    AssetAttentionReason.OUT_OF_SERVICE -> R.string.attention_out_of_service
    AssetAttentionReason.LOST -> R.string.attention_lost
    AssetAttentionReason.ISSUE_REPORTED -> R.string.attention_issue
    AssetAttentionReason.REPLACE_NOW -> R.string.lifecycle_replace_now
    AssetAttentionReason.REPLACE_SOON -> R.string.lifecycle_replace_soon
    else -> R.string.asset_attention
}

/**
 * Secondary line of an asset row (iOS « AssetRowLine »), first match wins: attention reason · current booking (return
 * due when the unit is out, else pickup planned) · next booking. Server fields only.
 */
@Composable
private fun AssetItem.secondaryLine(): Pair<String, TanyTone?>? {
    val formatters = LocalTanyFormatters.current
    val current = currentBooking
    val next = nextBooking
    return when {
        attention -> stringResource(attentionReason.label()) + (current?.let { " · ${ltrIsolated(it.reference)}" } ?: "") to TanyTone.WARNING
        current != null && group == AssetGroup.OUT ->
            stringResource(R.string.asset_line_return_due, formatters.businessDayTime(current.returnDeadline), ltrIsolated(current.reference)) to null
        current != null ->
            stringResource(R.string.asset_line_pickup_planned, formatters.businessDayTime(current.pickupWindowStart), ltrIsolated(current.reference)) to null
        next != null ->
            stringResource(R.string.asset_line_next_pickup, formatters.businessDayTime(next.pickupWindowStart), ltrIsolated(next.reference)) to null
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetCard(asset: AssetItem, endpoint: ApiEndpoint, onClick: () -> Unit) {
    val tone = asset.tone.tone()
    TanyCard(onClick = onClick, accent = TanyTone.WARNING.takeIf { asset.attention }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(asset.product.displayImage),
                contentDescription = asset.product.name,
                modifier = Modifier.width(64.dp),
                padding = 6.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(asset.product.name, style = TanyTheme.typography.headline, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TanyStatusChip(stringResource(asset.status.label()), tone, size = TanyChipSize.SMALL)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TanyCodePill(ltrIsolated(asset.code))
                    if (asset.maintenance) TanyStatusChip(stringResource(R.string.asset_maintenance), TanyTone.WARNING, size = TanyChipSize.SMALL)
                }
                asset.secondaryLine()?.let { (text, lineTone) ->
                    Text(
                        text,
                        style = if (lineTone != null) TanyTheme.typography.label.copy(fontWeight = TanyTheme.typography.bodyStrong.fontWeight) else TanyTheme.typography.label,
                        color = lineTone?.colors()?.content ?: TanyTheme.colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
        if (_state.value !is LoadState.Loaded) _state.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.asset(pointId, assetId)
            if (result is ApiResult.Success || _state.value !is LoadState.Loaded) _state.value = result.toLoadState()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDetailScreen(pointId: String, onBack: () -> Unit, onOpenBooking: (String) -> Unit, viewModel: AssetDetailViewModel = hiltViewModel()) {
    LifecycleResumeEffect(pointId) {
        viewModel.load(pointId)
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val loaded = (state as? LoadState.Loaded)?.value
        TanyTopBar(title = stringResource(R.string.asset_title), onBack = onBack, subtitle = loaded?.code?.let(::ltrIsolated))
        when (val s = state) {
            LoadState.Loading -> TanyDetailSkeleton()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> {
                val asset = s.value
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TanyCodePill(ltrIsolated(asset.code))
                            TanyStatusChip(stringResource(asset.status.label()), asset.tone.tone())
                            if (asset.maintenance) TanyStatusChip(stringResource(R.string.asset_maintenance), TanyTone.WARNING)
                        }
                    }
                    if (asset.attention) {
                        TanyNotice(message = stringResource(asset.attentionReason.label()), tone = TanyTone.WARNING)
                    }
                    asset.currentBooking?.let { BookingLiteCard(stringResource(R.string.asset_current_booking), it, onOpenBooking) }
                    asset.nextBooking?.let { BookingLiteCard(stringResource(R.string.asset_next_booking_title), it, onOpenBooking) }
                    TanySectionHeader(stringResource(R.string.asset_usage), modifier = Modifier.padding(top = 4.dp))
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
                    if (asset.turnaroundMinutes > 0 || asset.inServiceSince != null || asset.lastIncidentAt != null) {
                        TanyCard {
                            asset.inServiceSince?.let {
                                TanyInfoRow(stringResource(R.string.asset_in_service_since), icon = DsR.drawable.ic_tany_calendar) {
                                    BusinessDateTimeText(it, style = TanyTheme.typography.label)
                                }
                            }
                            asset.lastIncidentAt?.let {
                                TanyInfoRow(stringResource(R.string.asset_last_incident), icon = DsR.drawable.ic_tany_warning) {
                                    BusinessDateTimeText(it, style = TanyTheme.typography.label)
                                }
                            }
                            if (asset.turnaroundMinutes > 0) {
                                TanyInfoRow(stringResource(R.string.asset_turnaround), icon = DsR.drawable.ic_tany_clock) {
                                    Text(stringResource(R.string.asset_minutes, asset.turnaroundMinutes), style = TanyTheme.typography.bodyStrong)
                                }
                            }
                        }
                    }
                    asset.lifecycle?.takeIf { it.status != AssetLifecycleStatus.NOT_CONFIGURED && it.status != AssetLifecycleStatus.UNKNOWN }?.let {
                        LifecycleCard(it)
                    }
                    if (asset.recentBookings.isNotEmpty()) {
                        TanySectionHeader(stringResource(R.string.asset_recent_bookings), modifier = Modifier.padding(top = 4.dp))
                        TanyCard(contentPadding = 0.dp) {
                            Column {
                                val formatters = LocalTanyFormatters.current
                                asset.recentBookings.forEachIndexed { i, b ->
                                    if (i > 0) TanyDivider(inset = 16.dp)
                                    val period = b.usagePeriod ?: UsagePeriod(b.usageDate, b.usageEndDate ?: b.usageDate, b.dayCount)
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(ltrIsolated(b.reference), style = TanyTheme.typography.bodyStrong.copy(fontFamily = TanyTheme.typography.code.fontFamily))
                                            Text(formatters.usagePeriod(period.startDate, period.endDate), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                                        }
                                        Text(stringResource(b.status.label()), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
                                    }
                                }
                            }
                        }
                    }
                    asset.collectPoint?.let {
                        Text(
                            it.name,
                            style = TanyTheme.typography.caption,
                            color = TanyTheme.colors.textSubtle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Lifecycle as computed by the server (status, wear %, estimates) — displayed, never recomputed. */
@Composable
private fun LifecycleCard(lifecycle: AssetLifecycle) {
    val formatters = LocalTanyFormatters.current
    val tone = when (lifecycle.status) {
        AssetLifecycleStatus.WATCH -> TanyTone.WARNING
        AssetLifecycleStatus.REPLACE_SOON, AssetLifecycleStatus.REPLACE_NOW -> TanyTone.DANGER
        else -> TanyTone.SUCCESS
    }
    TanySectionHeader(stringResource(R.string.lifecycle_title), modifier = Modifier.padding(top = 4.dp))
    TanyCard {
        TanyInfoRow(stringResource(R.string.lifecycle_state)) {
            TanyStatusChip(stringResource(lifecycle.status.label()), tone, size = TanyChipSize.SMALL)
        }
        lifecycle.percentage?.let { pct ->
            TanyInfoRow(stringResource(R.string.lifecycle_progress)) {
                Text(ltrIsolated("${pct.roundToInt()} %"), style = TanyTheme.typography.bodyStrong)
            }
            TanyProgressBar((pct / 100.0).toFloat(), tone = tone)
        }
        lifecycle.estimatedReplacementDate?.let {
            TanyInfoRow(stringResource(R.string.lifecycle_replacement)) {
                Text(
                    DateTimeFormatter.ofPattern("LLLL yyyy", formatters.language.locale)
                        .format(ma.tany.core.model.common.BusinessTime.at(it))
                        .replaceFirstChar { c -> c.titlecase(formatters.language.locale) },
                    style = TanyTheme.typography.bodyStrong,
                )
            }
        }
        lifecycle.estimatedRemainingUses?.let {
            TanyInfoRow(stringResource(R.string.lifecycle_remaining)) { Text(it.toString(), style = TanyTheme.typography.bodyStrong) }
        }
    }
}

@StringRes
private fun AssetLifecycleStatus.label(): Int = when (this) {
    AssetLifecycleStatus.GOOD -> R.string.lifecycle_good
    AssetLifecycleStatus.WATCH -> R.string.lifecycle_watch
    AssetLifecycleStatus.REPLACE_SOON -> R.string.lifecycle_replace_soon
    AssetLifecycleStatus.REPLACE_NOW -> R.string.lifecycle_replace_now
    else -> R.string.lifecycle_not_configured
}

@StringRes
private fun BookingStatus.label(): Int = when (this) {
    BookingStatus.RESERVED -> R.string.booking_status_reserved
    BookingStatus.COLLECTED -> R.string.booking_status_collected
    BookingStatus.RETURNED -> R.string.booking_status_returned
    BookingStatus.COMPLETED -> R.string.booking_status_completed
    BookingStatus.CANCELLED -> R.string.booking_status_cancelled
    BookingStatus.EXPIRED -> R.string.booking_status_expired
    else -> R.string.booking_status_other
}

@Composable
private fun BookingLiteCard(title: String, booking: BookingLite, onOpenBooking: (String) -> Unit) {
    val formatters = LocalTanyFormatters.current
    val period = booking.usagePeriod ?: UsagePeriod(booking.usageDate, booking.usageEndDate ?: booking.usageDate, booking.dayCount)
    TanyCard(onClick = { onOpenBooking(booking.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = TanyTheme.typography.overline, color = TanyTheme.colors.textMuted, modifier = Modifier.weight(1f))
            TanyCodePill(ltrIsolated(booking.reference))
        }
        if (period.dayCount > 1) {
            TanyInfoRow(stringResource(R.string.booking_usage_period), icon = DsR.drawable.ic_tany_calendar) {
                Text(formatters.usagePeriod(period.startDate, period.endDate), style = TanyTheme.typography.label)
            }
        } else {
            TanyInfoRow(stringResource(R.string.asset_rental_day), icon = DsR.drawable.ic_tany_calendar) {
                Text(formatters.businessDay(period.startDate), style = TanyTheme.typography.label)
            }
        }
        TanyInfoRow(stringResource(R.string.booking_pickup_window), icon = DsR.drawable.ic_tany_pickup) {
            BusinessDateTimeText(booking.pickupWindowStart, end = booking.pickupWindowEnd, style = TanyTheme.typography.label)
        }
        TanyInfoRow(stringResource(R.string.booking_return_by), icon = DsR.drawable.ic_tany_return) {
            BusinessDateTimeText(booking.returnDeadline, style = TanyTheme.typography.label)
        }
        booking.returnWindowStart?.takeIf { it < booking.returnDeadline }?.let {
            TanyInfoRow(stringResource(R.string.asset_return_window), icon = DsR.drawable.ic_tany_clock) {
                BusinessDateTimeText(it, end = booking.returnDeadline, style = TanyTheme.typography.label)
            }
        }
    }
}

/** Matériel › scan or type an asset label to open its sheet. The code is opaque (trimmed) — the server finds it. */
@HiltViewModel
class AssetLookupViewModel @Inject constructor(private val repository: CollectRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    private val _found = Channel<String>(Channel.BUFFERED)
    val found: Flow<String> = _found.receiveAsFlow()

    fun submit(pointId: String, input: ScanInput) {
        if (_busy.value || _error.value != null) return
        val code = (input as? ScanInput.Qr)?.payload ?: (input as? ScanInput.ShortCode)?.code ?: return
        _busy.value = true
        viewModelScope.launch {
            when (val result = repository.assetLookup(pointId, code)) {
                is ApiResult.Success -> _found.send(result.value.id)
                is ApiResult.Failure -> _error.value = result.error
            }
            _busy.value = false
        }
    }

    fun clearError() {
        if (!_busy.value) _error.value = null
    }
}

@Composable
fun AssetLookupScreen(
    pointId: String,
    pointName: String?,
    onBack: () -> Unit,
    onFound: (assetId: String) -> Unit,
    viewModel: AssetLookupViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.found.collect(onFound) }
    val failure = error?.let {
        val notFound = it is ApiError.Http && it.code == ApiErrorCode.NOT_FOUND
        ScanFailureUi(
            title = stringResource(if (notFound) R.string.equipment_lookup_not_found_title else R.string.op_err_title_generic),
            message = stringResource(
                when {
                    notFound -> R.string.equipment_lookup_not_found
                    it is ApiError.Network -> R.string.equipment_lookup_network
                    else -> it.messageRes()
                },
            ),
            icon = DsR.drawable.ic_tany_box,
        )
    }
    ScannerScreen(
        onInput = { viewModel.submit(pointId, it) },
        title = stringResource(R.string.equipment_scan),
        target = ScanTarget.ASSET_LABEL,
        onBack = onBack,
        busy = busy,
        failure = failure,
        onDismissFailure = viewModel::clearError,
        hint = stringResource(R.string.op_scan_asset_title_hint),
        subHint = stringResource(R.string.scan_label_example),
        pointName = pointName,
    )
}
