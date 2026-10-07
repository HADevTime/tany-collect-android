package ma.tany.collect.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ma.tany.collect.di.ApplicationScope
import ma.tany.core.model.common.PushPayload
import ma.tany.core.network.PushTokenRegistrar
import ma.tany.core.network.SessionManager
import ma.tany.core.network.SessionState
import javax.inject.Inject

/**
 * FCM entry point (TANY Collect). Background / killed: FCM displays the backend notification itself. Foreground: shown
 * once here (same tag) and the open screens (Today, operation, centre, bell) re-read the server for the active point.
 * A rotated FCM token is handed to the backend only while a merchant session exists.
 */
@AndroidEntryPoint
class TanyCollectMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registrar: PushTokenRegistrar

    @Inject lateinit var session: SessionManager

    @Inject lateinit var events: PushEvents

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        PushDiagnostics.log("token_refreshed")
        if (session.state.value !is SessionState.SignedIn) return
        scope.launch { registrar.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.fromData(message.data)
        PushDiagnostics.log("received", payload.type.wire.ifEmpty { "UNKNOWN" })
        if (!events.publish(payload)) return
        if (session.state.value !is SessionState.SignedIn) return
        val notification = message.notification ?: return
        val title = notification.title ?: return
        PushNotifications.show(this, payload, title, notification.body, notification.channelId)
    }
}
