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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.openingLabel
import ma.tany.collect.core.ui.openingTone
import ma.tany.collect.core.ui.toLoadState
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.TanyBadge
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanyMetricTile
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.MerchantPoint
import ma.tany.core.model.collect.TodayCounts
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.BusinessTime
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiError
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

    private val _refreshError = MutableStateFlow<ApiError?>(null)

    /** Last background refresh failed: the list on screen may be stale (shown as a notice, never as an error page). */
    val refreshError: StateFlow<ApiError?> = _refreshError.asStateFlow()

    private val _updatedAt = MutableStateFlow<Instant?>(null)

    /** Moment of the last successful read (« Mis à jour à … »). */
    val updatedAt: StateFlow<Instant?> = _updatedAt.asStateFlow()

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { apply(repository.today(pointId), initial = true) }
    }

    /**
     * Re-reads the server (pull, return to the screen, 30 s foreground poll — idempotent GET). A failure keeps the list
     * on screen with a notice.
     */
    fun refresh(pointId: String, silent: Boolean = false) {
        if (_refreshing.value) return
        if (_state.value !is LoadState.Loaded) return load(pointId)
        if (!silent) _refreshing.value = true
        viewModelScope.launch {
            apply(repository.today(pointId), initial = false)
            _refreshing.value = false
        }
    }

    private fun apply(result: ApiResult<TodayResponse>, initial: Boolean) {
        when (result) {
            is ApiResult.Success -> {
                _state.value = LoadState.Loaded(result.value)
                _refreshError.value = null
                _updatedAt.value = Instant.now()
            }
            is ApiResult.Failure -> if (initial || _state.value !is LoadState.Loaded) {
                _state.value = result.toLoadState()
            } else {
                _refreshError.value = result.error
            }
        }
    }
}

/** Shortcuts offered by Today (navigation only — no business action). */
class TodayShortcuts(
    val scan: () -> Unit,
    val activity: () -> Unit,
    val equipment: (() -> Unit)?,
)

/** Foreground refresh cadence of Today (same as TANY Collect iOS). */
private const val TODAY_POLL_MS = 30_000L

/**
 * Today = the operational dashboard of the ACTIVE point (iOS structure): point header (name, opening state, completed
 * today, Scanner + notifications), the four server counters (tap = jump to the section), then the server phases grouped
 * like the server counters, « Terminées aujourd'hui » and the last update time. Re-read on return and every 30 s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    pointId: String,
    pointName: String?,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    onOpenBooking: (String) -> Unit,
    shortcuts: TodayShortcuts,
    inboxEnabled: Boolean = true,
    onCounts: (TodayCounts) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val updatedAt by viewModel.updatedAt.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // First read, then a quiet re-read each time the screen comes back (after an operation…) and every 30 s.
    LaunchedEffect(pointId, lifecycleOwner) {
        if (viewModel.state.value !is LoadState.Loaded) viewModel.load(pointId)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh(pointId, silent = true)
            while (true) {
                delay(TODAY_POLL_MS)
                viewModel.refresh(pointId, silent = true)
            }
        }
    }
    val loaded = (state as? LoadState.Loaded)?.value
    LaunchedEffect(loaded?.counts) { loaded?.counts?.let(onCounts) }

    Column(Modifier.fillMaxSize()) {
        TodayHeader(
            point = loaded?.collectPoint,
            fallbackName = pointName,
            completedToday = loaded?.counts?.completedToday ?: 0,
            unread = unreadNotifications,
            inboxEnabled = inboxEnabled,
            onOpenNotifications = onOpenNotifications,
            onScan = shortcuts.scan,
        )
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(header = true, rows = 3)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { viewModel.refresh(pointId) },
                modifier = Modifier.fillMaxSize(),
            ) {
                TodayContent(s.value, viewModel.endpoint, onOpenBooking, shortcuts, refreshError, updatedAt)
            }
        }
    }
}

@Composable
private fun TodayHeader(
    point: MerchantPoint?,
    fallbackName: String?,
    completedToday: Int,
    unread: Int,
    inboxEnabled: Boolean,
    onOpenNotifications: () -> Unit,
    onScan: () -> Unit,
) {
    val formatters = LocalTanyFormatters.current
    val colors = TanyTheme.colors
    val date = remember(formatters) { formatters.businessLongDay(BusinessTime.businessDate(Instant.now())) }
    val notificationsLabel = if (unread > 0) {
        pluralStringResource(R.plurals.notifications_bell_unread, unread, unread)
    } else {
        stringResource(R.string.notifications_title)
    }
    val scanHint = stringResource(R.string.today_scan_hint)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = TanyTheme.typography.label, color = colors.textMuted, maxLines = 1, modifier = Modifier.weight(1f))
            TanyButton(
                stringResource(R.string.nav_scan),
                onScan,
                fillWidth = false,
                compact = true,
                icon = DsR.drawable.ic_tany_scan,
                modifier = Modifier.semantics { contentDescription = scanHint },
            )
            if (inboxEnabled) {
                IconButton(onClick = onOpenNotifications, modifier = Modifier.semantics { contentDescription = notificationsLabel }) {
                    BadgedBox(badge = { if (unread > 0) TanyBadge(if (unread > 99) "99+" else unread.toString()) }) {
                        Icon(painterResource(DsR.drawable.ic_tany_bell), contentDescription = null, tint = colors.textPrimary)
                    }
                }
            }
        }
        // The active point is the title: every operation below belongs to it.
        Text(
            point?.shortName?.ifBlank { null } ?: fallbackName ?: stringResource(R.string.today_title),
            style = TanyTheme.typography.largeTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(end = 12.dp)
                .semantics { heading() },
        )
        if (point != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TanyStatusChip(point.openingLabel(), point.openingTone(), size = TanyChipSize.SMALL)
                if (completedToday > 0) {
                    Text(
                        pluralStringResource(R.plurals.today_completed_inline, completedToday, completedToday),
                        style = TanyTheme.typography.caption,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayContent(
    today: TodayResponse,
    endpoint: ApiEndpoint,
    onOpenBooking: (String) -> Unit,
    shortcuts: TodayShortcuts,
    refreshError: ApiError?,
    updatedAt: Instant?,
) {
    val sections = remember(today.operations) { groupBySection(today.operations) }
    var showCompleted by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val formatters = LocalTanyFormatters.current
    val onlyNoShow = today.operations.isNotEmpty() && today.operations.all { it.phase.ui().section == OperationSection.NO_SHOW }
    // Index of each section header in the list (for the counter tiles): counts, [stale notice], [under control].
    val sectionIndex = remember(sections, refreshError, onlyNoShow) {
        val map = mutableMapOf<OperationSection, Int>()
        var index = 1 + (if (refreshError != null) 1 else 0) + (if (onlyNoShow) 1 else 0)
        sections.forEach { (section, ops) ->
            map[section] = index
            index += 1 + (if (ops.isEmpty()) 1 else ops.size)
        }
        map
    }
    val jumpTo: (OperationSection) -> Unit = { section ->
        sectionIndex[section]?.let { scope.launch { listState.animateScrollToItem(it) } }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "counts") { CountsGrid(today.counts, onJump = jumpTo) }
        if (refreshError != null) {
            item(key = "stale") {
                TanyNotice(
                    message = stringResource(if (refreshError is ApiError.Network) R.string.today_offline else R.string.today_stale),
                    tone = TanyTone.WARNING,
                    icon = DsR.drawable.ic_tany_refresh,
                )
            }
        }
        if (today.operations.isEmpty()) {
            item(key = "empty") { EmptyDay(completed = today.completedToday.size, shortcuts = shortcuts) }
        } else {
            if (onlyNoShow) item(key = "under-control") { UnderControlCard() }
            sections.forEach { (section, operations) ->
                item(key = "header-${section.name}") {
                    TanySectionHeader(
                        title = stringResource(section.title()),
                        trailing = operations.size.toString(),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (operations.isEmpty()) {
                    item(key = "empty-${section.name}") {
                        section.emptyLine()?.let {
                            Text(
                                stringResource(it),
                                style = TanyTheme.typography.label,
                                color = TanyTheme.colors.textMuted,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                } else {
                    items(operations, key = { "op-${it.id}" }) { op ->
                        OperationCard(op, endpoint, onClick = { onOpenBooking(op.id) })
                    }
                }
            }
        }
        if (today.completedToday.isNotEmpty()) {
            item(key = "completed-header") {
                TanySectionHeader(
                    title = stringResource(R.string.today_completed_title),
                    trailing = stringResource(if (showCompleted) R.string.today_completed_hide else R.string.today_completed_show) +
                        " (${today.completedToday.size})",
                    onTrailingClick = { showCompleted = !showCompleted },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (showCompleted) {
                items(today.completedToday, key = { "done-${it.id}" }) { op ->
                    OperationCard(op, endpoint, onClick = { onOpenBooking(op.id) })
                }
            }
        }
        updatedAt?.let {
            item(key = "updated") {
                Text(
                    stringResource(R.string.today_updated_at, formatters.businessTime(it)),
                    style = TanyTheme.typography.caption,
                    color = TanyTheme.colors.textSubtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/** The four server counters (2 × 2); a tile jumps to its section. A zero is muted, a late return is red. */
@Composable
private fun CountsGrid(counts: TodayCounts, onJump: (OperationSection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(counts.toCollect, R.string.count_pickups, DsR.drawable.ic_tany_pickup, null, Modifier.weight(1f)) { onJump(OperationSection.TO_COLLECT) }
            CountTile(counts.toReturn, R.string.count_returns, DsR.drawable.ic_tany_return, null, Modifier.weight(1f)) { onJump(OperationSection.TO_RETURN) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(counts.awaitingCustomer, R.string.count_awaiting_customer, DsR.drawable.ic_tany_clock, TanyTone.WARNING, Modifier.weight(1f)) {
                onJump(OperationSection.AWAITING_CUSTOMER)
            }
            CountTile(counts.late, R.string.count_late, DsR.drawable.ic_tany_warning, TanyTone.DANGER, Modifier.weight(1f)) { onJump(OperationSection.LATE) }
        }
    }
}

@Composable
private fun CountTile(value: Int, @StringRes label: Int, @DrawableRes icon: Int, tone: TanyTone?, modifier: Modifier, onClick: () -> Unit) {
    TanyMetricTile(
        value = value.toString(),
        label = stringResource(label),
        modifier = modifier,
        tone = tone.takeIf { value > 0 },
        icon = icon,
        muted = value == 0,
        onClick = onClick,
    )
}

/** Only no-shows left: nothing urgent (iOS « Tout est sous contrôle »). */
@Composable
private fun UnderControlCard() {
    TanyCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TanyIllustration(DsR.drawable.ic_tany_check, tone = TanyTone.SUCCESS, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.today_under_control_title), style = TanyTheme.typography.headline)
                Text(stringResource(R.string.today_under_control_message), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
            }
        }
    }
}

/** No operation today: reassuring explanation of what appears here + useful shortcuts. */
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
