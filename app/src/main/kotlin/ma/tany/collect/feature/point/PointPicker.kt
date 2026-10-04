package ma.tany.collect.feature.point

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import ma.tany.collect.core.preferences.CollectPreferences
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.messageRes
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.model.collect.MerchantPoint
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectRepository
import javax.inject.Inject

/** Active point selection (ADMIN multi-point; a MERCHANT with one point is selected automatically). */
@HiltViewModel
class PointPickerViewModel @Inject constructor(
    private val repository: CollectRepository,
    private val preferences: CollectPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<List<MerchantPoint>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<MerchantPoint>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.me()
            if (result is ApiResult.Success) result.value.collectPoints.singleOrNull()?.let { select(it) }
            _state.value = when (result) {
                is ApiResult.Success -> LoadState.Loaded(result.value.collectPoints)
                is ApiResult.Failure -> LoadState.Failed(result.error)
            }
        }
    }

    fun select(point: MerchantPoint) {
        viewModelScope.launch { preferences.setActivePoint(point.id) }
    }
}

@Composable
fun PointPickerScreen(viewModel: PointPickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.point_select_title), chrome = true)
        when (val s = state) {
            LoadState.Loading -> TanyLoadingState()
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = viewModel::load)
            is LoadState.Loaded -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.value, key = { it.id }) { point ->
                    TanyCard(contentPadding = 0.dp) {
                        TanyRow(
                            title = point.name,
                            subtitle = "${point.address} · ${point.city}",
                            leadingIcon = DsR.drawable.ic_tany_box,
                            onClick = { viewModel.select(point) },
                        )
                    }
                }
            }
        }
    }
}
