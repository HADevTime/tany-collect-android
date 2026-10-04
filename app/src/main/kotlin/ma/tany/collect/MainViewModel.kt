package ma.tany.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ma.tany.collect.core.media.OperationPhotoFiles
import ma.tany.collect.core.preferences.CollectPreferences
import ma.tany.core.designsystem.theme.ThemePreference
import ma.tany.core.network.SessionManager
import ma.tany.core.network.SessionState
import javax.inject.Inject

/** `null` = not loaded yet; empty string = no active point. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val session: SessionManager,
    private val preferences: CollectPreferences,
    private val photos: OperationPhotoFiles,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = session.state
    val theme: StateFlow<ThemePreference> = preferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)
    val activePointId: StateFlow<String?> =
        preferences.activePointId.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (session.state.value == SessionState.Loading) viewModelScope.launch { session.restore() }
        // Sign-out (user or 401): no point scope, no operation photo survives the session.
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is SessionState.SignedOut) {
                    preferences.clearActivePoint()
                    photos.clearAll()
                }
            }
        }
    }
}
