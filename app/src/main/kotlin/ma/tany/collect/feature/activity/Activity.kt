package ma.tany.collect.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.collect.feature.today.OperationListRow
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLargeHeader
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanySearchField
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.IncidentItem
import ma.tany.core.model.collect.IncidentsResponse
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.common.BusinessTime
import ma.tany.core.model.common.IncidentStatus
import ma.tany.core.model.common.IncidentType
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** History / search of the active point (server-side search: reference, asset code, name, phone ≥ 3 digits). */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<ActivityResponse>>(LoadState.Loading)
    val state: StateFlow<LoadState<ActivityResponse>> = _state.asStateFlow()

    private val _searching = MutableStateFlow(false)

    /** A search / refresh is in flight while a list is shown (no skeleton flash on each keystroke). */
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _incidents = MutableStateFlow<LoadState<IncidentsResponse>>(LoadState.Loading)
    val incidents: StateFlow<LoadState<IncidentsResponse>> = _incidents.asStateFlow()

    private var search: Job? = null

    fun load(pointId: String, query: String, debounceMs: Long = 0) {
        search?.cancel()
        search = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (_state.value is LoadState.Loaded) _searching.value = true else _state.value = LoadState.Loading
            val result = repository.activity(pointId, query.trim().takeIf { it.isNotEmpty() })
            // A failed refresh keeps the list on screen; only a first load shows the error page.
            if (result is ApiResult.Success || _state.value !is LoadState.Loaded) _state.value = result.toLoadState()
            _searching.value = false
        }
    }

    fun loadIncidents(pointId: String) {
        if (_incidents.value !is LoadState.Loaded) _incidents.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.incidents(pointId)
            if (result is ApiResult.Success || _incidents.value !is LoadState.Loaded) _incidents.value = result.toLoadState()
        }
    }
}

/** History bucket of a row (iOS: Aujourd'hui · Hier · Historique), by its last server update. */
enum class ActivityBucket { TODAY, YESTERDAY, HISTORY }

/**
 * Splits the SERVER-ordered history (most recently updated first) into consecutive buckets by the business day of
 * `updatedAt` (fallback `scheduledAt`). Order is never changed. A future pickup window never yields a « Demain » header.
 */
fun bucketActivity(items: List<Operation>, today: LocalDate): List<Pair<ActivityBucket, List<Operation>>> {
    val groups = mutableListOf<Pair<ActivityBucket, MutableList<Operation>>>()
    items.forEach { op ->
        val day = BusinessTime.businessDate(op.updatedAt ?: op.scheduledAt)
        val bucket = when {
            day >= today -> ActivityBucket.TODAY
            day == today.minusDays(1) -> ActivityBucket.YESTERDAY
            else -> ActivityBucket.HISTORY
        }
        val last = groups.lastOrNull()
        if (last != null && last.first == bucket) last.second += op else groups += bucket to mutableListOf(op)
    }
    return groups.map { (bucket, ops) -> bucket to ops.toList() }
}

private enum class ActivityTab { OPERATIONS, INCIDENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(pointId: String, onOpenBooking: (String) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(ActivityTab.OPERATIONS) }
    // Re-read each time the tab comes back (after a booking…): server state only.
    val currentQuery by rememberUpdatedState(query)
    LifecycleResumeEffect(pointId, tab) {
        if (tab == ActivityTab.OPERATIONS) viewModel.load(pointId, currentQuery) else viewModel.loadIncidents(pointId)
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyLargeHeader(title = stringResource(R.string.activity_title))
        TanySegmentedControl(
            options = listOf(
                TanySegment(ActivityTab.OPERATIONS, stringResource(R.string.activity_tab_operations)),
                TanySegment(ActivityTab.INCIDENTS, stringResource(R.string.activity_tab_incidents)),
            ),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (tab == ActivityTab.INCIDENTS) {
            IncidentsList(viewModel, pointId, onOpenBooking)
        } else {
            OperationsTab(viewModel, pointId, query, onQuery = { query = it }, state = state, searching = searching, onOpenBooking = onOpenBooking)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationsTab(
    viewModel: ActivityViewModel,
    pointId: String,
    query: String,
    onQuery: (String) -> Unit,
    state: LoadState<ActivityResponse>,
    searching: Boolean,
    onOpenBooking: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TanySearchField(
            value = query,
            onValueChange = {
                val next = it.take(60)
                onQuery(next)
                viewModel.load(pointId, next, debounceMs = 350)
            },
            placeholder = stringResource(R.string.activity_search),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(rows = 5, withMedia = true)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId, query) })
            is LoadState.Loaded -> PullToRefreshBox(
                isRefreshing = searching,
                onRefresh = { viewModel.load(pointId, query) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (s.value.items.isEmpty()) {
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
                                onQuery("")
                                viewModel.load(pointId, "")
                            },
                        )
                    }
                } else {
                    ActivityList(
                        response = s.value,
                        endpoint = viewModel.endpoint,
                        onOpenBooking = onOpenBooking,
                        resultCount = s.value.items.size.takeIf { query.isNotBlank() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityList(response: ActivityResponse, endpoint: ApiEndpoint, onOpenBooking: (String) -> Unit, resultCount: Int?) {
    val today = remember { BusinessTime.businessDate(Instant.now()) }
    val groups = remember(response.items) { bucketActivity(response.items, today) }
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
        } else if (response.completedToday > 0) {
            item(key = "summary") {
                TanyStatusChip(
                    pluralStringResource(R.plurals.activity_completed_today, response.completedToday, response.completedToday),
                    TanyTone.SUCCESS,
                )
            }
        }
        groups.forEachIndexed { index, (bucket, operations) ->
            item(key = "bucket-$index") {
                TanySectionHeader(
                    stringResource(
                        when (bucket) {
                            ActivityBucket.TODAY -> R.string.day_today
                            ActivityBucket.YESTERDAY -> R.string.day_yesterday
                            ActivityBucket.HISTORY -> R.string.activity_history
                        },
                    ),
                    trailing = operations.size.toString(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "group-$index") {
                TanyCard(contentPadding = 0.dp) {
                    Column {
                        operations.forEachIndexed { i, op ->
                            if (i > 0) TanyDivider(inset = 16.dp)
                            OperationListRow(op, endpoint, onClick = { onOpenBooking(op.id) }, showDay = bucket != ActivityBucket.TODAY)
                        }
                    }
                }
            }
        }
    }
}

/** Activité › Incidents (`GET points/{id}/incidents`): server status enum, description verbatim, opens the booking. */
@Composable
private fun IncidentsList(viewModel: ActivityViewModel, pointId: String, onOpenBooking: (String) -> Unit) {
    val state by viewModel.incidents.collectAsStateWithLifecycle()
    when (val s = state) {
        LoadState.Loading -> TanyListSkeleton(rows = 4, withMedia = false)
        is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.loadIncidents(pointId) })
        is LoadState.Loaded -> if (s.value.incidents.isEmpty()) {
            TanyEmptyState(
                title = stringResource(R.string.activity_incidents_empty_title),
                message = stringResource(R.string.activity_incidents_empty_message),
                icon = DsR.drawable.ic_tany_shield,
                tone = TanyTone.SUCCESS,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") {
                    TanySectionHeader(stringResource(R.string.activity_tab_incidents), trailing = s.value.incidents.size.toString())
                }
                items(s.value.incidents, key = { it.id }) { incident ->
                    IncidentRow(incident, onOpen = incident.bookingId?.let { id -> { onOpenBooking(id) } })
                }
            }
        }
    }
}

@Composable
private fun IncidentRow(incident: IncidentItem, onOpen: (() -> Unit)?) {
    val colors = TanyTheme.colors
    TanyCard(onClick = onOpen, accent = TanyTone.WARNING.takeIf { incident.status == IncidentStatus.OPEN }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(incident.type.label()), style = TanyTheme.typography.headline, modifier = Modifier.weight(1f))
            TanyStatusChip(stringResource(incident.status.label()), incident.status.tone(), size = TanyChipSize.SMALL)
        }
        val context = listOfNotNull(incident.reference?.let(::ltrIsolated), incident.assetCode?.let(::ltrIsolated), incident.productName)
        if (context.isNotEmpty()) {
            Text(context.joinToString(" · "), style = TanyTheme.typography.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // User-generated text, verbatim.
        incident.description?.let { Text(it, style = TanyTheme.typography.label, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BusinessDateTimeText(incident.createdAt, style = TanyTheme.typography.caption, color = colors.textSubtle)
            if (incident.createdByMe) {
                Text(stringResource(R.string.activity_incident_by_you), style = TanyTheme.typography.caption, color = colors.textSubtle)
            }
        }
    }
}

private fun IncidentType.label(): Int = when (this) {
    IncidentType.DAMAGED -> R.string.return_incident_damaged
    IncidentType.MISSING_ACCESSORY -> R.string.return_incident_missing
    IncidentType.VERY_DIRTY -> R.string.return_incident_dirty
    IncidentType.DEPOSIT_DISPUTE -> R.string.incident_type_deposit_dispute
    IncidentType.HANDOVER_DISPUTED -> R.string.incident_type_handover_disputed
    IncidentType.ASSET_LOCATION_UNRESOLVED -> R.string.incident_type_location_unresolved
    else -> R.string.return_incident_other
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
