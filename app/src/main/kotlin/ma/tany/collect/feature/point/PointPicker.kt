package ma.tany.collect.feature.point

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import ma.tany.collect.core.ui.openingLabel
import ma.tany.collect.core.ui.openingTone
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyErrorState
import ma.tany.core.designsystem.component.TanyLargeHeader
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.MerchantPoint
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectAuthRepository
import ma.tany.core.network.CollectRepository
import javax.inject.Inject

/** Active point selection (ADMIN multi-point; a MERCHANT with one point is selected automatically). */
@HiltViewModel
class PointPickerViewModel @Inject constructor(
    private val repository: CollectRepository,
    private val auth: CollectAuthRepository,
    private val preferences: CollectPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<CollectMe>>(LoadState.Loading)
    val state: StateFlow<LoadState<CollectMe>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.me()
            if (result is ApiResult.Success) result.value.collectPoints.singleOrNull()?.let { select(it) }
            _state.value = when (result) {
                is ApiResult.Success -> LoadState.Loaded(result.value)
                is ApiResult.Failure -> LoadState.Failed(result.error)
            }
        }
    }

    fun select(point: MerchantPoint) {
        viewModelScope.launch { preferences.setActivePoint(point.id) }
    }

    /** Signing out from the picker (wrong account): the session is revoked server-side, then cleared. */
    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

@Composable
fun PointPickerScreen(viewModel: PointPickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val firstName = (state as? LoadState.Loaded)?.value?.user?.firstName?.takeIf { it.isNotBlank() }
        TanyLargeHeader(
            title = stringResource(R.string.point_select_title),
            subtitle = firstName?.let { stringResource(R.string.point_select_greeting, it) } ?: stringResource(R.string.point_select_subtitle),
        )
        when (val s = state) {
            LoadState.Loading -> TanyListSkeleton(rows = 3, withMedia = false)
            is LoadState.Failed -> TanyErrorState(stringResource(s.error.messageRes()), onRetry = viewModel::load)
            is LoadState.Loaded -> if (s.value.collectPoints.isEmpty()) {
                TanyEmptyState(
                    title = stringResource(R.string.point_none_title),
                    message = stringResource(R.string.point_none_message),
                    icon = DsR.drawable.ic_tany_store,
                    actionLabel = stringResource(R.string.account_logout),
                    onAction = viewModel::logout,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.value.collectPoints, key = { it.id }) { point ->
                        TanyCard(contentPadding = 0.dp) {
                            TanyRow(
                                title = point.name,
                                subtitle = "${point.address} · ${point.city}",
                                leadingIcon = DsR.drawable.ic_tany_store,
                                onClick = { viewModel.select(point) },
                            )
                            TanyStatusChip(
                                point.openingLabel(),
                                point.openingTone(),
                                size = TanyChipSize.SMALL,
                                modifier = Modifier.padding(start = 66.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                    item {
                        TanyButton(
                            text = stringResource(R.string.account_logout),
                            onClick = viewModel::logout,
                            style = TanyButtonStyle.TEXT,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
