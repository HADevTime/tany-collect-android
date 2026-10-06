package ma.tany.collect.feature.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.TanyBadge
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyCardStyle
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyIconContainer
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanyMetricTile
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.MerchantPoint
import ma.tany.core.model.collect.TodayCounts
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.BusinessTime
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectRepository
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TodayResponse>>(LoadState.Loading)
    val state: StateFlow<LoadState<TodayResponse>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)

    /** Pull-to-refresh in progress (the current list stays visible). */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.today(pointId).toLoadState() }
    }

    /** Re-reads the server; a failure keeps the list on screen (the merchant can pull again). */
    fun refresh(pointId: String) {
        if (_refreshing.value) return
        if (_state.value !is LoadState.Loaded) return load(pointId)
        _refreshing.value = true
        viewModelScope.launch {
            val result = repository.today(pointId)
            if (result is ApiResult.Success) _state.value = LoadState.Loaded(result.value)
            _refreshing.value = false
        }
    }
}

/** Shortcuts offered by Today (navigation only — no business action). */
class TodayShortcuts(
    val scan: () -> Unit,
    val activity: () -> Unit,
    val equipment: (() -> Unit)?,
)

/** Operations of the day for the ACTIVE point (server phases, grouped for the counter; server order kept). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    pointId: String,
    pointName: String?,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    onOpenBooking: (String) -> Unit,
    shortcuts: TodayShortcuts,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TodayHeader(unreadNotifications, onOpenNotifications)
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(header = true, rows = 3)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { viewModel.refresh(pointId) },
                modifier = Modifier.fillMaxSize(),
            ) {
                TodayContent(s.value, pointName, viewModel.endpoint, onOpenBooking, shortcuts)
            }
        }
    }
}

@Composable
private fun TodayHeader(unread: Int, onOpenNotifications: () -> Unit) {
    val formatters = LocalTanyFormatters.current
    val date = remember(formatters) { formatters.businessLongDay(BusinessTime.businessDate(Instant.now())) }
    val notificationsLabel = if (unread > 0) {
        stringResource(R.string.notifications_unread_count, unread)
    } else {
        stringResource(R.string.notifications_title)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted, maxLines = 1)
            Text(
                stringResource(R.string.today_title),
                style = TanyTheme.typography.largeTitle,
                color = TanyTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        IconButton(onClick = onOpenNotifications, modifier = Modifier.semantics { contentDescription = notificationsLabel }) {
            BadgedBox(badge = { if (unread > 0) TanyBadge(if (unread > 99) "99+" else unread.toString()) }) {
                Icon(painterResource(DsR.drawable.ic_tany_bell), contentDescription = null, tint = TanyTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun TodayContent(
    today: TodayResponse,
    fallbackPointName: String?,
    endpoint: ApiEndpoint,
    onOpenBooking: (String) -> Unit,
    shortcuts: TodayShortcuts,
) {
    val sections = remember(today.operations) { groupBySection(today.operations) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "point") { PointCard(today.collectPoint, fallbackPointName, today.counts) }
        if (today.operations.isEmpty()) {
            item(key = "empty") { EmptyDay(completed = today.completedToday.size, shortcuts = shortcuts) }
        }
        sections.forEach { (section, operations) ->
            item(key = "header-${section.name}") {
                TanySectionHeader(
                    title = stringResource(section.title()),
                    trailing = operations.size.toString(),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(operations, key = { "op-${it.id}" }) { op ->
                OperationCard(op, endpoint, onClick = { onOpenBooking(op.id) })
            }
        }
        if (today.completedToday.isNotEmpty()) {
            item(key = "completed-header") {
                TanySectionHeader(
                    title = pluralStringResource(R.plurals.today_completed_count, today.completedToday.size, today.completedToday.size),
                    trailing = stringResource(if (showCompleted) R.string.today_completed_hide else R.string.today_completed_show),
                    onTrailingClick = { showCompleted = !showCompleted },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (showCompleted) {
                items(today.completedToday, key = { "done-${it.id}" }) { op ->
                    OperationCard(op, endpoint, onClick = { onOpenBooking(op.id) })
                }
            }
        }
    }
}

/** Dark identity card: active point, opening state (server), today's counters (server). */
@Composable
private fun PointCard(point: MerchantPoint, fallbackName: String?, counts: TodayCounts) {
    val colors = TanyTheme.colors
    val formatters = LocalTanyFormatters.current
    TanyCard(style = TanyCardStyle.CHROME, contentPadding = 18.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TanyIconContainer(DsR.drawable.ic_tany_store, contentDescription = null, container = colors.chromeRaised, tint = colors.onChrome, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.today_active_point), style = TanyTheme.typography.caption, color = colors.onChromeMuted)
                Text(
                    point.name.ifBlank { fallbackName.orEmpty() },
                    style = TanyTheme.typography.headline,
                    color = colors.onChrome,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${point.address} · ${point.city}", style = TanyTheme.typography.caption, color = colors.onChromeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TanyStatusChip(
                stringResource(if (point.isOpenNow) R.string.point_open else R.string.point_closed),
                if (point.isOpenNow) TanyTone.SUCCESS else TanyTone.NEUTRAL,
                size = TanyChipSize.SMALL,
            )
            point.todayHours?.let { hours ->
                Text(
                    stringResource(R.string.point_today_hours, "${formatters.clock(hours.open)}–${formatters.clock(hours.close)}"),
                    style = TanyTheme.typography.caption,
                    color = colors.onChromeMuted,
                )
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TanyMetricTile(counts.toCollect.toString(), stringResource(R.string.count_pickups), Modifier.weight(1f), onChrome = true)
            TanyMetricTile(counts.toReturn.toString(), stringResource(R.string.count_returns), Modifier.weight(1f), onChrome = true)
            TanyMetricTile(counts.awaitingCustomer.toString(), stringResource(R.string.count_awaiting_customer), Modifier.weight(1f), onChrome = true)
            TanyMetricTile(
                counts.late.toString(),
                stringResource(R.string.count_late),
                Modifier.weight(1f),
                tone = TanyTone.DANGER.takeIf { counts.late > 0 },
                onChrome = true,
            )
        }
    }
}

/** No operation to handle: reassuring explanation of what appears here + useful shortcuts. */
@Composable
private fun EmptyDay(completed: Int, shortcuts: TodayShortcuts) {
    val colors = TanyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TanyCard(contentPadding = 24.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TanyIllustration(
                    icon = if (completed > 0) DsR.drawable.ic_tany_check else DsR.drawable.ic_tany_calendar,
                    tone = if (completed > 0) TanyTone.SUCCESS else TanyTone.NEUTRAL,
                    size = 88.dp,
                )
                Text(
                    stringResource(if (completed > 0) R.string.today_all_done_title else R.string.today_empty_title),
                    style = TanyTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (completed > 0) R.string.today_all_done_message else R.string.today_empty_message),
                    style = TanyTheme.typography.body,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        TanySectionHeader(stringResource(R.string.today_what_appears), modifier = Modifier.padding(top = 4.dp))
        TanyCard(contentPadding = 0.dp) {
            Explainer(DsR.drawable.ic_tany_pickup, R.string.today_explain_pickup_title, R.string.today_explain_pickup)
            TanyDivider(inset = 66.dp)
            Explainer(DsR.drawable.ic_tany_return, R.string.today_explain_return_title, R.string.today_explain_return)
            TanyDivider(inset = 66.dp)
            Explainer(DsR.drawable.ic_tany_cash, R.string.today_explain_deposit_title, R.string.today_explain_deposit)
        }
        TanySectionHeader(stringResource(R.string.today_shortcuts), modifier = Modifier.padding(top = 4.dp))
        TanyCard(contentPadding = 0.dp) {
            TanyRow(
                title = stringResource(R.string.today_shortcut_scan),
                subtitle = stringResource(R.string.today_shortcut_scan_hint),
                leadingIcon = DsR.drawable.ic_tany_scan,
                leadingTone = TanyTone.ACTION,
                onClick = shortcuts.scan,
            )
            TanyDivider(inset = 66.dp)
            TanyRow(
                title = stringResource(R.string.today_shortcut_search),
                subtitle = stringResource(R.string.today_shortcut_search_hint),
                leadingIcon = DsR.drawable.ic_tany_search,
                onClick = shortcuts.activity,
            )
            shortcuts.equipment?.let { open ->
                TanyDivider(inset = 66.dp)
                TanyRow(
                    title = stringResource(R.string.today_shortcut_equipment),
                    subtitle = stringResource(R.string.today_shortcut_equipment_hint),
                    leadingIcon = DsR.drawable.ic_tany_box,
                    onClick = open,
                )
            }
        }
    }
}

@Composable
private fun Explainer(@DrawableRes icon: Int, @StringRes title: Int, @StringRes text: Int) {
    TanyRow(title = stringResource(title), subtitle = stringResource(text), leadingIcon = icon)
}
