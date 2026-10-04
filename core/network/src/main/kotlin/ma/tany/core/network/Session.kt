package ma.tany.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What is persisted for a session. The token is a secret: it is only stored through [SessionStore]. */
data class StoredSession(val token: String, val userId: String) {
    override fun toString(): String = "StoredSession(userId=$userId, token=██)"
}

/**
 * Persistence of the session. The Android implementation encrypts the token with an Android Keystore key;
 * tokens are never written in clear text, never logged.
 */
interface SessionStore {
    suspend fun read(): StoredSession?

    suspend fun write(session: StoredSession)

    suspend fun clear()
}

sealed interface SessionState {
    data object Loading : SessionState

    /** [expired] = the server rejected the previous session (401), as opposed to a user logout. */
    data class SignedOut(val expired: Boolean = false) : SessionState

    data class SignedIn(val userId: String) : SessionState
}

/**
 * Single owner of the session. Keeps the token in memory for the interceptor (no blocking I/O on OkHttp threads)
 * and mirrors every change to the [SessionStore].
 */
class SessionManager(
    private val store: SessionStore,
    private val scope: CoroutineScope,
) : AccessTokenProvider, UnauthorizedHandler {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var token: String? = null

    /** Called once at app start. A corrupted/unreadable store ⇒ signed out (fail-safe). */
    suspend fun restore() = mutex.withLock {
        val stored = runCatching { store.read() }.getOrNull()
        token = stored?.token
        _state.value = if (stored != null) SessionState.SignedIn(stored.userId) else SessionState.SignedOut()
    }

    suspend fun signIn(session: StoredSession) = mutex.withLock {
        store.write(session)
        synchronized(this) { token = session.token }
        _state.value = SessionState.SignedIn(session.userId)
    }

    suspend fun signOut() = mutex.withLock { clearLocked(expired = false) }

    /**
     * Called from [UnauthorizedInterceptor] (OkHttp thread): only clears if the rejected token is still the current
     * one, so a session obtained meanwhile survives. In-memory state changes immediately; storage is cleared async.
     */
    override fun onUnauthorized(rejectedToken: String) {
        synchronized(this) {
            if (token != rejectedToken) return
            token = null
        }
        _state.value = SessionState.SignedOut(expired = true)
        scope.launch { mutex.withLock { if (token == null) runCatching { store.clear() } } }
    }

    override fun currentToken(): String? = token

    private suspend fun clearLocked(expired: Boolean) {
        token = null
        runCatching { store.clear() }
        _state.value = SessionState.SignedOut(expired)
    }
}
