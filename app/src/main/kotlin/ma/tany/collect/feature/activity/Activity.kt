package ma.tany.collect.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.dayLabel
import ma.tany.collect.core.ui.groupConsecutiveByDay
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.collect.feature.today.icon
import ma.tany.collect.feature.today.label
import ma.tany.collect.feature.today.ui
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLargeHeader
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanySearchField
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyToneIcon
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.common.BusinessTime
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectRepository
import java.time.Instant
import javax.inject.Inject

/** History / search of the active point (server-side search: reference, asset code, name, phone ≥ 3 digits). */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<ActivityResponse>>(LoadState.Loading)
    val state: StateFlow<LoadState<ActivityResponse>> = _state.asStateFlow()
    private var search: Job? = null

    fun load(pointId: String, query: String, debounceMs: Long = 0) {
        search?.cancel()
        search = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _state.value = LoadState.Loading
            _state.value = repository.activity(pointId, query.trim().takeIf { it.isNotEmpty() }).toLoadState()
        }
    }
}

/** Day an operation belongs to in the history (its scheduled moment, Africa/Casablanca). */
private fun Operation.historyDay() = BusinessTime.businessDate(scheduledAt)

@Composable
fun ActivityScreen(pointId: String, onOpenBooking: (String) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(pointId) { viewModel.load(pointId, query) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val completed = (state as? LoadState.Loaded)?.value?.completedToday
        TanyLargeHeader(
            title = stringResource(R.string.activity_title),
            subtitle = completed?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.activity_completed_today, it, it) },
        )
        TanySearchField(
            value = query,
            onValueChange = {
                query = it.take(60)
                viewModel.load(pointId, query, debounceMs = 350)
            },
            placeholder = stringResource(R.string.activity_search),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(rows = 5, withMedia = false)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId, query) })
            is LoadState.Loaded -> if (s.value.items.isEmpty()) {
                if (query.isBlank()) {
                    TanyEmptyState(
                        title = stringResource(R.string.activity_empty_title),
                        message = stringResource(R.string.activity_empty_message),
                        icon = DsR.drawable.ic_tany_list,
                    )
                } else {
                    TanyEmptyState(
                        title = stringResource(R.string.activity_no_result_title),
                        message = stringResource(R.string.activity_no_result_message),
                        icon = DsR.drawable.ic_tany_search,
                        secondaryLabel = stringResource(R.string.activity_clear_search),
                        onSecondary = {
                            query = ""
                            viewModel.load(pointId, "")
                        },
                    )
                }
            } else {
                ActivityList(s.value.items, viewModel.endpoint, onOpenBooking, resultCount = s.value.items.size.takeIf { query.isNotBlank() })
            }
        }
    }
}

@Composable
private fun ActivityList(items: List<Operation>, endpoint: ApiEndpoint, onOpenBooking: (String) -> Unit, resultCount: Int?) {
    val today = remember { BusinessTime.businessDate(Instant.now()) }
    val groups = remember(items) { groupConsecutiveByDay(items) { it.historyDay() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (resultCount != null) {
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.activity_results, resultCount, resultCount),
                    style = TanyTheme.typography.label,
                    color = TanyTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        groups.forEachIndexed { index, (day, operations) ->
            item(key = "day-$index") {
                TanySectionHeader(dayLabel(day, today), trailing = operations.size.toString(), modifier = Modifier.padding(top = 6.dp))
            }
            item(key = "group-$index") {
                TanyCard(contentPadding = 0.dp) {
                    Column {
                        operations.forEachIndexed { i, op ->
                            if (i > 0) TanyDivider(inset = 16.dp)
                            ActivityRow(op, endpoint, onClick = { onOpenBooking(op.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Compact history row: kind tile, product, server phase, customer short name, reference and time. */
@Composable
private fun ActivityRow(operation: Operation, endpoint: ApiEndpoint, onClick: () -> Unit) {
    val phase = operation.phase.ui()
    val formatters = LocalTanyFormatters.current
    val colors = TanyTheme.colors
    val time = if (operation.kind == OperationKind.RETURN) {
        formatters.businessTime(operation.returnDeadline)
    } else {
        formatters.businessTimeRange(operation.pickupWindowStart, operation.pickupWindowEnd)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TanyToneIcon(operation.kind.icon(), phase.tone, size = 36.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(operation.kind.label()),
                    style = TanyTheme.typography.caption,
                    color = colors.textMuted,
                )
                Text("·", style = TanyTheme.typography.caption, color = colors.textSubtle)
                Text(ltrIsolated(time), style = TanyTheme.typography.caption, color = colors.textMuted, maxLines = 1)
            }
            Text(operation.product.name, style = TanyTheme.typography.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    operation.customer.shortName,
                    style = TanyTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TanyCodePill(ltrIsolated(operation.reference))
            }
            TanyStatusChip(stringResource(phase.label), phase.tone, size = TanyChipSize.SMALL)
        }
        ProductImageSurface(
            url = endpoint.resolveMedia(operation.product.displayImage),
            contentDescription = null,
            modifier = Modifier.width(52.dp),
            padding = 4.dp,
        )
    }
}
