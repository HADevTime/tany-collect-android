package ma.tany.collect.core.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import ma.tany.core.model.common.PushPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process signal "a TANY push arrived while the app runs": screens re-read SERVER state (unread count, notification
 * centre) — the payload itself is never treated as business state. The same notification delivered twice (FCM retry)
 * triggers its in-app effects once ([notificationId] idempotence). Persistence stays on the backend.
 */
@Singleton
class PushEvents @Inject constructor() {
    private val _received = MutableSharedFlow<PushPayload>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val received: SharedFlow<PushPayload> = _received.asSharedFlow()

    private val _readChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Anything that changes server notification state (push received, push opened & read): re-read unread count. */
    val notificationStateChanged: Flow<Unit> = merge(received.map { }, _readChanged)

    private val seen = ArrayDeque<String>()

    fun readChanged() {
        _readChanged.tryEmit(Unit)
    }

    /** Returns false when this notification was already handled (duplicate delivery). */
    @Synchronized
    fun publish(payload: PushPayload): Boolean {
        val id = payload.notificationId
        if (id != null) {
            if (id in seen) return false
            seen.addLast(id)
            while (seen.size > MAX_REMEMBERED) seen.removeFirst()
        }
        _received.tryEmit(payload)
        return true
    }

    private companion object {
        const val MAX_REMEMBERED = 64
    }
}
