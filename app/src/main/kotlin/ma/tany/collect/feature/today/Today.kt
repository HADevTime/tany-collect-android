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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCardStyle
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
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
    val scanLabel = stringResource(R.string.today_scan_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = TanyTheme.typography.label, color = colors.textMuted)
            // The active point is the title: every operation below belongs to it.
            Text(
                point?.shortName?.ifBlank { null } ?: fallbackName ?: stringResource(R.string.today_title),
                style = TanyTheme.typography.largeTitle,
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
        // Quiet, always-there actions: the scanner is one tap away without dominating the header.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HeaderAction(DsR.drawable.ic_tany_scan, scanLabel, onScan, emphasized = true)
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
        }
    }
}

/** 48 dp round header action (tonal when [emphasized]). */
@Composable
private fun HeaderAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, emphasized: Boolean = false) {
    val colors = TanyTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (emphasized) colors.accentContainer else colors.neutral)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (emphasized) colors.onAccentContainer else colors.textPrimary,
            modifier = Modifier.size(22.dp),
        )
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
    val hero = remember(today.operations) { pickHero(today.operations) }
    val sections = remember(today.operations, hero) { homeSections(today.operations, hero) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val formatters = LocalTanyFormatters.current
    val onlyNoShow = today.operations.isNotEmpty() && today.operations.all { it.phase.ui().section == OperationSection.NO_SHOW }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        if (today.operations.isEmpty()) {
            item(key = "empty") { EmptyDay(completed = today.completedToday.size, shortcuts = shortcuts) }
        } else {
            hero?.let { op ->
                item(key = "hero-${op.id}") {
                    HeroOperationCard(op, endpoint, onOpen = { onOpenBooking(op.id) }, modifier = Modifier.animateItem())
                }
            }
            if (onlyNoShow) item(key = "under-control") { UnderControlCard() }
            item(key = "summary") {
                TodaySummary(today.counts) { target ->
                    sectionIndexOf(sections, target, hasStale = refreshError != null, hasHero = hero != null, onlyNoShow = onlyNoShow)
                        ?.let { scope.launch { listState.animateScrollToItem(it) } }
                }
            }
            sections.forEach { (section, operations) ->
                item(key = "section-${section.name}") {
                    TanySectionHeader(
                        title = stringResource(section.title()),
                        trailing = operations.size.toString(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(operations, key = { "op-${it.id}" }) { op ->
                    OperationCard(op, endpoint, onClick = { onOpenBooking(op.id) }, modifier = Modifier.animateItem())
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
                    modifier = Modifier.padding(top = 8.dp),
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

/** List index of a Home section header (items: [stale], [hero], [under control], summary, then sections). */
internal fun sectionIndexOf(
    sections: List<Pair<HomeSection, List<ma.tany.core.model.collect.Operation>>>,
    target: HomeSection,
    hasStale: Boolean,
    hasHero: Boolean,
    onlyNoShow: Boolean,
): Int? {
    var index = (if (hasStale) 1 else 0) + (if (hasHero) 1 else 0) + (if (onlyNoShow) 1 else 0) + 1
    sections.forEach { (section, ops) ->
        if (section == target) return index
        index += 1 + ops.size
    }
    return null
}

/**
 * The next meaningful operation, large: what (type + server status), WHEN (the dominant line), the object (big image,
 * full name), who, and the cash at stake when relevant. One clear CTA opens it.
 */
@Composable
private fun HeroOperationCard(operation: Operation, endpoint: ApiEndpoint, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val formatters = LocalTanyFormatters.current
    val colors = TanyTheme.colors
    val phase = operation.phase.ui()
    val time = operation.rowTime()
    val timeText = time.end?.let { formatters.businessTimeRange(time.start, it) } ?: formatters.businessTime(time.start)
    val summary = operation.spokenSummary()
    val openLabel = stringResource(R.string.operation_open)
    TanyCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary },
        onClick = onOpen,
        onClickLabel = openLabel,
        accent = phase.tone.takeIf { it == TanyTone.DANGER },
        contentPadding = 20.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(operation.heroHeadline()),
                style = TanyTheme.typography.overline,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            TanyStatusChip(stringResource(phase.short), phase.tone, size = TanyChipSize.SMALL)
        }
        Text(
            (time.prefix?.let { stringResource(it) + " " } ?: "") + ltrIsolated(timeText),
            style = TanyTheme.typography.largeTitle.copy(fontFeatureSettings = "tnum"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(operation.product.displayImage),
                contentDescription = null,
                modifier = Modifier.width(112.dp),
                padding = 10.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(operation.product.name, style = TanyTheme.typography.title)
                Text(
                    "${operation.customer.shortName} · ${ltrIsolated(operation.reference)}",
                    style = TanyTheme.typography.label,
                    color = colors.textMuted,
                )
                operation.assetCode?.let { TanyCodePill(ltrIsolated(it)) }
            }
        }
        val period = operation.effectiveUsagePeriod()
        if (period.dayCount > 1) {
            Text(
                "${pluralStringResource(R.plurals.booking_days, period.dayCount, period.dayCount)} · ${formatters.usagePeriod(period.startDate, period.endDate)}",
                style = TanyTheme.typography.label,
                color = colors.textMuted,
            )
        }
        operation.rowMoney()?.let { money ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(money.label), style = TanyTheme.typography.body, color = colors.textMuted, modifier = Modifier.weight(1f))
                MoneyText(money.amount, style = TanyTheme.typography.amount)
            }
        }
        operation.exceptionLine()?.let { (text, tone) ->
            TanyNotice(message = text, tone = if (tone == TanyTone.NEUTRAL) TanyTone.INFO else tone, icon = DsR.drawable.ic_tany_clock)
        }
        // A real action only when the SERVER says the operation can be worked on now; otherwise just « Voir ».
        TanyButton(
            stringResource(operation.heroCta()),
            onOpen,
            style = if (operation.heroTier()?.let { it <= 2 } == true) TanyButtonStyle.PRIMARY else TanyButtonStyle.SECONDARY,
            icon = operation.kind.icon(),
        )
    }
}

@StringRes
private fun Operation.heroHeadline(): Int = when (phase) {
    MerchantPhase.RETURN_LATE -> R.string.hero_late
    MerchantPhase.PICKUP_IN_PROGRESS, MerchantPhase.RETURN_IN_PROGRESS -> R.string.hero_in_progress
    MerchantPhase.PICKUP_READY, MerchantPhase.PICKUP_UPCOMING -> R.string.hero_to_collect
    MerchantPhase.RETURN_DUE, MerchantPhase.WITH_CUSTOMER -> R.string.hero_to_return
    MerchantPhase.DEPOSIT_TO_REFUND -> R.string.hero_deposit
    else -> R.string.hero_awaiting
}

@StringRes
private fun Operation.heroCta(): Int = when (phase) {
    MerchantPhase.PICKUP_READY -> R.string.stage_start_pickup
    MerchantPhase.PICKUP_IN_PROGRESS -> R.string.stage_resume_pickup
    MerchantPhase.RETURN_DUE, MerchantPhase.RETURN_LATE -> R.string.stage_start_return
    MerchantPhase.RETURN_IN_PROGRESS -> R.string.stage_resume_return
    MerchantPhase.DEPOSIT_TO_REFUND -> R.string.stage_resume_deposit
    else -> R.string.hero_view
}

/**
 * Today's server counters as one calm strip: zeros stay quiet, a late return is the only red. Tapping a non-zero counter
 * scrolls to its section. Everything at zero collapses to a single reassuring line.
 */
@Composable
private fun TodaySummary(counts: TodayCounts, onJump: (HomeSection) -> Unit) {
    val colors = TanyTheme.colors
    val entries = listOf(
        Triple(counts.late, R.string.count_late, HomeSection.NOW),
        Triple(counts.toCollect, R.string.count_pickups, HomeSection.TO_COLLECT),
        Triple(counts.toReturn, R.string.count_returns, HomeSection.TO_RETURN),
        Triple(counts.awaitingCustomer, R.string.count_awaiting_customer, HomeSection.AWAITING_CUSTOMER),
    )
    if (entries.all { it.first == 0 }) {
        Text(
            stringResource(R.string.summary_all_clear),
            style = TanyTheme.typography.label,
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    TanyCard(style = TanyCardStyle.FILLED, contentPadding = 6.dp) {
        Row(Modifier.fillMaxWidth()) {
            entries.forEach { (value, label, target) ->
                val late = target == HomeSection.NOW && value > 0
                val text = stringResource(label)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(TanyTheme.radii.medium)
                        .then(
                            if (value > 0) Modifier.clickable(role = Role.Button, onClickLabel = text) { onJump(target) } else Modifier,
                        )
                        .semantics(mergeDescendants = true) { contentDescription = "$value $text" }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        value.toString(),
                        style = if (value > 0) TanyTheme.typography.title else TanyTheme.typography.headline,
                        color = when {
                            late -> colors.danger.accent
                            value > 0 -> colors.textPrimary
                            else -> colors.textSubtle
                        },
                    )
                    Text(
                        text,
                        style = TanyTheme.typography.caption,
                        color = if (late) colors.danger.accent else colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
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
