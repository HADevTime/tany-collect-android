package ma.tany.collect.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import ma.tany.collect.core.ui.messageRes
import ma.tany.collect.core.ui.toLoadState
import ma.tany.collect.feature.today.OperationCard
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectRepository
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

@Composable
fun ActivityScreen(pointId: String, onOpenBooking: (String) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(pointId) { viewModel.load(pointId, query) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.activity_title), chrome = true)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it.take(60)
                viewModel.load(pointId, query, debounceMs = 350)
            },
            label = { Text(stringResource(R.string.activity_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = { viewModel.load(pointId, query) })
            is LoadState.Loaded -> if (s.value.items.isEmpty()) {
                TanyEmptyState(title = stringResource(R.string.activity_empty_title))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.value.items) { op -> OperationCard(op, viewModel.endpoint) { onOpenBooking(op.id) } }
                }
            }
        }
    }
}
