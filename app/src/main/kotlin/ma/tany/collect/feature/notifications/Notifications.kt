package ma.tany.collect.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.messageRes
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyDot
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyToneIcon
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.common.BusinessTime
import ma.tany.core.model.common.NotificationCategory
import ma.tany.core.model.common.NotificationItem
import ma.tany.core.model.common.NotificationTone
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectNotificationRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class NotificationsState(
    val loading: Boolean = true,
    val error: ApiError? = null,
    /** Module OFF server-side (`enabled:false`). */
    val enabled: Boolean = true,
    val items: List<NotificationItem> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val unreadCount: Int = 0,
)

/**
 * Notification centre of the ACTIVE point. Titles / bodies / action labels are localized by the server
 * (`Accept-Language`) — one of the contract's multilingual exceptions. Opening an item marks it read (server) and follows
 * its `deepLink` (`tanycollect://…`): the target screen re-reads server state, a notification never executes an action.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repository: CollectNotificationRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    private val _links = Channel<String>(Channel.BUFFERED)

    /** Deep links to open (one-shot events). */
    val links: Flow<String> = _links.receiveAsFlow()

    fun refresh(pointId: String) {
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val result = repository.page(pointId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        enabled = result.value.enabled,
                        items = result.value.notifications,
                        nextCursor = result.value.nextCursor,
                        unreadCount = result.value.unreadCount,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun loadMore(pointId: String) {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.page(pointId, cursor)) {
                is ApiResult.Success -> _state.update { current ->
                    val known = current.items.map { it.id }.toSet()
                    current.copy(
                        loadingMore = false,
                        items = current.items + result.value.notifications.filter { it.id !in known },
                        nextCursor = result.value.nextCursor,
                        unreadCount = result.value.unreadCount,
                    )
                }
                // Keep the cursor: the user can scroll again to retry.
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Marks [item] read (server count wins) and opens its deep link, if any. */
    fun open(pointId: String, item: NotificationItem) {
        // No link from the server: open the booking it concerns (navigation only; the screen re-reads the server).
        (item.deepLink ?: item.bookingId?.let { "tanycollect://booking/$it" })?.let { _links.trySend(it) }
        if (item.isRead) return
        _state.update { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(isRead = true) else it }) }
        viewModelScope.launch {
            val result = repository.markRead(pointId, item.id)
            if (result is ApiResult.Success) _state.update { it.copy(unreadCount = result.value.unreadCount) }
        }
    }

    fun markAllRead(pointId: String) {
        if (_state.value.unreadCount == 0) return
        viewModelScope.launch {
            val result = repository.markAllRead(pointId)
            if (result is ApiResult.Success) {
                _state.update { s -> s.copy(unreadCount = result.value.unreadCount, items = s.items.map { it.copy(isRead = true) }) }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    pointId: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    Column(Modifier.fillMaxSize()) {
        LifecycleResumeEffect(pointId) {
            viewModel.refresh(pointId)
            onPauseOrDispose { }
        }
        LaunchedEffect(viewModel) { viewModel.links.collect(onOpenLink) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        TanyTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = onBack,
            subtitle = state.unreadCount.takeIf { it > 0 }?.let { stringResource(R.string.notifications_unread_count, it) },
            actions = {
                if (state.unreadCount > 0) {
                    TextButton(onClick = { viewModel.markAllRead(pointId) }) {
                        Text(stringResource(R.string.notifications_mark_all_short), style = TanyTheme.typography.label, color = TanyTheme.colors.textPrimary)
                    }
                }
            },
        )
        val error = state.error
        when {
            state.loading -> TanyListSkeleton(rows = 5, withMedia = false)
            error != null && state.items.isEmpty() -> TanyErrorState(stringResource(error.messageRes()), onRetry = { viewModel.refresh(pointId) })
            !state.enabled -> TanyEmptyState(title = stringResource(R.string.notifications_disabled), icon = DsR.drawable.ic_tany_bell)
            state.items.isEmpty() -> TanyEmptyState(
                title = stringResource(R.string.notifications_empty_title),
                message = stringResource(R.string.notifications_empty_message),
                icon = DsR.drawable.ic_tany_bell,
            )
            else -> {
                val today = remember { BusinessTime.businessDate(Instant.now()) }
                val groups = remember(state.items, today) { bucketNotifications(state.items, today) }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    groups.forEachIndexed { index, (bucket, items) ->
                        item(key = "day-$index") {
                            TanySectionHeader(
                                stringResource(
                                    when (bucket) {
                                        NotificationBucket.TODAY -> R.string.day_today
                                        NotificationBucket.YESTERDAY -> R.string.day_yesterday
                                        NotificationBucket.OLDER -> R.string.notifications_older
                                    },
                                ),
                                trailing = items.size.toString(),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item(key = "group-$index-${items.first().id}") {
                            TanyCard(contentPadding = 0.dp) {
                                Column {
                                    items.forEachIndexed { i, item ->
                                        if (i > 0) TanyDivider(inset = 66.dp)
                                        NotificationRow(item, older = bucket == NotificationBucket.OLDER, onOpen = { viewModel.open(pointId, item) })
                                    }
                                }
                            }
                        }
                    }
                    if (state.nextCursor != null) {
                        item(key = "more") {
                            // Reaching the end requests the next page (cursor-based, server order).
                            LaunchedEffect(state.nextCursor) { viewModel.loadMore(pointId) }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TanyTheme.colors.accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One notification: category glyph tinted by the server tone, title (server-localized), body, amount, booking
 * context, time and the still-relevant action. Unread = bold title + dot + TalkBack label (never the dot alone).
 */
@Composable
private fun NotificationRow(item: NotificationItem, older: Boolean, onOpen: () -> Unit) {
    val unreadLabel = stringResource(R.string.notifications_unread)
    val colors = TanyTheme.colors
    val formatters = LocalTanyFormatters.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .then(if (!item.isRead) Modifier.semantics { stateDescription = unreadLabel } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TanyToneIcon(item.category.icon(), item.tone.ui(), size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.title,
                    style = if (item.isRead) TanyTheme.typography.body else TanyTheme.typography.bodyStrong,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (older) formatters.businessDayTime(item.createdAt) else formatters.businessTime(item.createdAt),
                    style = TanyTheme.typography.caption,
                    color = colors.textSubtle,
                )
                if (!item.isRead) TanyDot()
            }
            Text(item.body, style = TanyTheme.typography.label, color = colors.textMuted)
            val context = listOfNotNull(item.productName, item.bookingReference?.let(::ltrIsolated)).joinToString(" · ")
            if (context.isNotEmpty()) Text(context, style = TanyTheme.typography.caption, color = colors.textSubtle)
            // State badge (iOS): « Action requise » while the server still offers an action, « Traité » once resolved.
            when {
                item.tone == NotificationTone.ACTION_REQUIRED && item.action != null && !item.isResolved ->
                    TanyStatusChip(stringResource(R.string.notifications_action_required), TanyTone.ACTION, size = TanyChipSize.SMALL)
                item.isResolved -> TanyStatusChip(stringResource(R.string.notifications_handled), TanyTone.SUCCESS, size = TanyChipSize.SMALL)
            }
        }
    }
}

/** Notification centre buckets (iOS): today · yesterday · older, by business day of creation, server order kept. */
enum class NotificationBucket { TODAY, YESTERDAY, OLDER }

fun bucketNotifications(items: List<NotificationItem>, today: LocalDate): List<Pair<NotificationBucket, List<NotificationItem>>> {
    val groups = mutableListOf<Pair<NotificationBucket, MutableList<NotificationItem>>>()
    items.forEach { item ->
        val day = BusinessTime.businessDate(item.createdAt)
        val bucket = when {
            day >= today -> NotificationBucket.TODAY
            day == today.minusDays(1) -> NotificationBucket.YESTERDAY
            else -> NotificationBucket.OLDER
        }
        val last = groups.lastOrNull()
        if (last != null && last.first == bucket) last.second += item else groups += bucket to mutableListOf(item)
    }
    return groups.map { (bucket, list) -> bucket to list.toList() }
}

private fun NotificationTone.ui(): TanyTone = when (this) {
    NotificationTone.SUCCESS -> TanyTone.SUCCESS
    NotificationTone.ACTION_REQUIRED -> TanyTone.ACTION
    NotificationTone.ATTENTION -> TanyTone.WARNING
    NotificationTone.INFO, NotificationTone.UNKNOWN -> TanyTone.INFO
}

private fun NotificationCategory.icon(): Int = when (this) {
    NotificationCategory.PICKUP -> DsR.drawable.ic_tany_pickup
    NotificationCategory.RETURN -> DsR.drawable.ic_tany_return
    NotificationCategory.DEPOSIT -> DsR.drawable.ic_tany_cash
    NotificationCategory.BOOKING -> DsR.drawable.ic_tany_calendar
    NotificationCategory.INCIDENT -> DsR.drawable.ic_tany_warning
    NotificationCategory.IDENTITY -> DsR.drawable.ic_tany_shield
    NotificationCategory.ACCOUNT -> DsR.drawable.ic_tany_person
    else -> DsR.drawable.ic_tany_bell
}
