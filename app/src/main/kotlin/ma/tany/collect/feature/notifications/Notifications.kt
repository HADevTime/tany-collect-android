package ma.tany.collect.feature.notifications

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.common.NotificationItem
import ma.tany.core.model.common.NotificationTone
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectNotificationRepository
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
        item.deepLink?.let { _links.trySend(it) }
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
        TanyTopBar(title = stringResource(R.string.notifications_title), onBack = onBack, chrome = true)
        LifecycleResumeEffect(pointId) {
            viewModel.refresh(pointId)
            onPauseOrDispose { }
        }
        LaunchedEffect(viewModel) { viewModel.links.collect(onOpenLink) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        when {
            state.loading -> TanyLoadingState()
            state.error != null && state.items.isEmpty() -> TanyErrorState(stringResource(state.error!!.messageRes()), onRetry = { viewModel.refresh(pointId) })
            !state.enabled -> TanyEmptyState(title = stringResource(R.string.notifications_disabled))
            state.items.isEmpty() -> TanyEmptyState(title = stringResource(R.string.notifications_empty))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                if (state.unreadCount > 0) {
                    item(key = "mark-all") {
                        TanyButton(stringResource(R.string.notifications_mark_all), { viewModel.markAllRead(pointId) }, style = TanyButtonStyle.TEXT)
                    }
                }
                items(state.items, key = { it.id }) { item -> NotificationRow(item, onOpen = { viewModel.open(pointId, item) }) }
                if (state.nextCursor != null) {
                    item(key = "more") {
                        // Reaching the end requests the next page (cursor-based, server order).
                        LaunchedEffect(state.nextCursor) { viewModel.loadMore(pointId) }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TanyTheme.colors.accent, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, onOpen: () -> Unit) {
    val unreadLabel = stringResource(R.string.notifications_unread)
    TanyCard(onClick = onOpen, modifier = if (!item.isRead) Modifier.semantics { contentDescription = unreadLabel } else Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.isRead) Box(Modifier.size(8.dp).background(TanyTheme.colors.accent, CircleShape))
            Text(item.title, style = TanyTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
        }
        Text(item.body, style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
        item.amounts?.amount?.let { MoneyText(it) }
        val context = listOfNotNull(item.productName, item.bookingReference?.let(::ltrIsolated)).joinToString(" · ")
        if (context.isNotEmpty()) Text(context, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusinessDateTimeText(item.createdAt, style = TanyTheme.typography.caption)
            // Server-revalidated action: shown only while still relevant (`action` null once resolved).
            item.action?.takeIf { !item.isResolved }?.let { TanyStatusChip(it.label, item.tone.ui()) }
        }
    }
}

private fun NotificationTone.ui(): TanyTone = when (this) {
    NotificationTone.SUCCESS -> TanyTone.SUCCESS
    NotificationTone.ACTION_REQUIRED -> TanyTone.ACTION
    NotificationTone.ATTENTION -> TanyTone.WARNING
    NotificationTone.INFO, NotificationTone.UNKNOWN -> TanyTone.INFO
}
