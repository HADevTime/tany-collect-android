package ma.tany.collect.feature.today

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.TanyBadge
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyListSkeleton
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
    /** Opens the booking straight on its guided flow (the booking screen re-checks the server stage). */
    onStartBooking: (String) -> Unit = onOpenBooking,
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
                TodayContent(s.value, viewModel.endpoint, onOpenBooking, onStartBooking, shortcuts, refreshError, updatedAt)
            }
        }
    }
}

/**
 * Header (iOS structure, Android execution): TANY COLLECT brand line with the notifications bell and the Scanner pill,
 * then the date, the active point's name as the dominant title and its structured opening state.
 */
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.auth_brand).uppercase(formatters.language.locale),
                style = TanyTheme.typography.overline,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (inboxEnabled) {
                Box {
                    HeaderAction(DsR.drawable.ic_tany_bell, notificationsLabel, onOpenNotifications)
                    if (unread > 0) {
                        TanyBadge(
                            if (unread > 99) "99+" else unread.toString(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clearAndSetSemantics { },
                        )
                    }
                }
            }
            ScannerPill(onClick = onScan)
        }
        Text(date, style = TanyTheme.typography.label, color = colors.textMuted)
        // The active point is the title: every operation below belongs to it.
        Text(
            point?.shortName?.ifBlank { null } ?: fallbackName ?: stringResource(R.string.today_title),
            style = TanyTheme.typography.display,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
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

/** 48 dp round header action. */
@Composable
private fun HeaderAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val colors = TanyTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.neutral)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}

/**
 * Home, in the iOS order: 2 × 2 stat cards → « Prochaine opération » hero (or a calm state) → « À collecter » →
 * « À retourner » → urgent / waiting groups → completed today and the last update. The hero is never repeated in the
 * lists below (docs/UX_REWORK.md § 1).
 */
@Composable
private fun TodayContent(
    today: TodayResponse,
    endpoint: ApiEndpoint,
    onOpenBooking: (String) -> Unit,
    onStartBooking: (String) -> Unit,
    shortcuts: TodayShortcuts,
    refreshError: ApiError?,
    updatedAt: Instant?,
) {
    val hero = remember(today.operations) { pickHero(today.operations) }
    val sections = remember(today.operations, hero) { homeSectionsWithAnchors(today.operations, hero) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val formatters = LocalTanyFormatters.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (refreshError != null) {
            item(key = "stale") {
                TanyNotice(
                    message = stringResource(if (refreshError is ApiError.Network) R.string.today_offline else R.string.today_stale),
                    tone = TanyTone.WARNING,
                    icon = DsR.drawable.ic_tany_refresh,
                )
            }
        }
        item(key = "stats") {
            HomeStatsGrid(today.counts, onJump = { target ->
                val index = homeItemIndex(sections, target, hero, hasStale = refreshError != null)
                if (index != null) scope.launch { listState.animateScrollToItem(index) }
            })
        }
        item(key = "hero") {
            if (hero != null) {
                NextOperationHero(
                    hero,
                    onOpen = { onOpenBooking(hero.id) },
                    onStart = { onStartBooking(hero.id) },
                    modifier = Modifier.animateItem(),
                )
            } else {
                NoNextOperationCard(completedToday = today.completedToday.size)
            }
        }
        sections.forEach { (section, operations) ->
            item(key = "section-${section.name}") {
                TanySectionHeader(
                    title = stringResource(section.title()),
                    trailing = operations.size.toString(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (operations.isEmpty()) {
                item(key = "empty-${section.name}") {
                    Text(
                        stringResource(section.emptyLine(heroInSection = hero?.let { section.contains(it) } == true)),
                        style = TanyTheme.typography.label,
                        color = TanyTheme.colors.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(operations, key = { "op-${it.id}" }) { op ->
                    OperationCard(op, endpoint, onClick = { onOpenBooking(op.id) }, modifier = Modifier.animateItem())
                }
            }
        }
        if (today.operations.isEmpty()) {
            item(key = "shortcuts") { TodayShortcutsCard(shortcuts) }
        }
        if (today.completedToday.isNotEmpty()) {
            item(key = "completed-header") {
                TanySectionHeader(
                    title = stringResource(R.string.today_completed_title),
                    trailing = stringResource(if (showCompleted) R.string.today_completed_hide else R.string.today_completed_show) +
                        " (${today.completedToday.size})",
                    onTrailingClick = { showCompleted = !showCompleted },
                    modifier = Modifier.padding(top = 6.dp),
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

/** No operation today: shortcuts under the calm « Aucune opération à venir » card. */
@Composable
private fun TodayShortcutsCard(shortcuts: TodayShortcuts) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TanySectionHeader(stringResource(R.string.today_shortcuts), modifier = Modifier.padding(top = 4.dp))
        TanyCard(contentPadding = 0.dp) {
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
