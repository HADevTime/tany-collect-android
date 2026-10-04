package ma.tany.collect.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
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
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectRepository
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CollectRepository,
    val endpoint: ApiEndpoint,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TodayResponse>>(LoadState.Loading)
    val state: StateFlow<LoadState<TodayResponse>> = _state.asStateFlow()

    fun load(pointId: String) {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.today(pointId).toLoadState() }
    }
}

/** Operations of the day for the ACTIVE point (server-ordered, server phases). */
@Composable
fun TodayScreen(pointId: String, pointName: String?, onOpenBooking: (String) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { viewModel.load(pointId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = pointName ?: stringResource(R.string.today_title), chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId) })
            is LoadState.Loaded -> {
                val today = s.value
                if (today.operations.isEmpty()) {
                    TanyEmptyState(title = stringResource(R.string.today_empty_title))
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Count(stringResource(R.string.count_pickups), today.counts.toCollect, Modifier.weight(1f))
                                Count(stringResource(R.string.count_returns), today.counts.toReturn, Modifier.weight(1f))
                                Count(stringResource(R.string.count_awaiting_customer), today.counts.awaitingCustomer, Modifier.weight(1f))
                                Count(stringResource(R.string.count_late), today.counts.late, Modifier.weight(1f))
                            }
                        }
                        items(today.operations) { op ->
                            OperationCard(op, viewModel.endpoint, onClick = { onOpenBooking(op.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Count(label: String, value: Int, modifier: Modifier) {
    TanyCard(modifier = modifier.semantics(mergeDescendants = true) {}, contentPadding = 10.dp) {
        Text(value.toString(), style = TanyTheme.typography.title)
        Text(label, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted, maxLines = 2)
    }
}
