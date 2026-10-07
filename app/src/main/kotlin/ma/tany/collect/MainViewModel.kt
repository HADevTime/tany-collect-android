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
import ma.tany.collect.core.push.CollectPushRouting
import ma.tany.collect.core.push.PushDiagnostics
import ma.tany.collect.core.push.PushEvents
import ma.tany.core.model.common.PushPayload
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectNotificationRepository
import ma.tany.core.network.PushTokenRegistrar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val push: PushTokenRegistrar,
    private val notifications: CollectNotificationRepository,
    private val pushEvents: PushEvents,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = session.state
    val theme: StateFlow<ThemePreference> = preferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)
    val activePointId: StateFlow<String?> =
        preferences.activePointId.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingPush = MutableStateFlow<PushPayload?>(null)

    /**
     * Tapped push waiting for the operational shell (session restored, OTP sign-in, point chosen): never lost during
     * startup, consumed once by the shell of the active point ([consumePush]).
     */
    val pendingPush: StateFlow<PushPayload?> = _pendingPush.asStateFlow()

    /** Notifications opened while signed out / before a point: marked read once both are known. */
    private val pendingReads = mutableListOf<PushPayload>()

    init {
        if (session.state.value == SessionState.Loading) viewModelScope.launch { session.restore() }
        // Push lifecycle follows the session: restored / signed in ⇒ registration kept current (no duplicates); lost
        // (401) ⇒ the next sign-in registers again. Logout unregisters in the auth repository before clearing.
        viewModelScope.launch {
            combine(session.state, activePointId) { state, point -> state to point }.collect { (state, point) ->
                when (state) {
                    is SessionState.SignedIn -> {
                        push.refresh()
                        if (!point.isNullOrEmpty()) flushReads(point)
                    }
                    is SessionState.SignedOut -> push.forget()
                    SessionState.Loading -> Unit
                }
            }
        }
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

    fun syncPushRegistration() {
        if (session.state.value is SessionState.SignedIn) viewModelScope.launch { push.refresh() }
    }

    /** A push was tapped: kept for the shell, and its server notification marked read on ITS point. */
    fun onPushOpened(payload: PushPayload) {
        PushDiagnostics.log("tapped")
        _pendingPush.value = payload
        if (payload.notificationId == null) return
        val point = activePointId.value
        if (session.state.value is SessionState.SignedIn && !point.isNullOrEmpty()) {
            viewModelScope.launch { markRead(payload, point) }
        } else {
            pendingReads += payload
        }
    }

    /** The shell of [pointId] takes the pending push: its destination, or null (nothing / another point ⇒ Today). */
    fun consumePush(pointId: String): String? {
        val payload = _pendingPush.value ?: return null
        _pendingPush.value = null
        val destination = CollectPushRouting.destination(payload, pointId)
        if (destination == null) PushDiagnostics.log("route_other_point")
        return destination
    }

    private fun flushReads(point: String) {
        if (pendingReads.isEmpty()) return
        val reads = pendingReads.toList()
        pendingReads.clear()
        viewModelScope.launch { reads.forEach { markRead(it, point) } }
    }

    private suspend fun markRead(payload: PushPayload, activePoint: String) {
        val id = payload.notificationId ?: return
        val point = CollectPushRouting.readPoint(payload, activePoint) ?: return
        // Idempotent server-side; the bell re-reads the server count.
        if (notifications.markRead(point, id) is ApiResult.Success) pushEvents.readChanged()
    }
}
