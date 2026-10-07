package ma.tany.collect.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import ma.tany.core.network.PushTokenSource

/**
 * FCM registration token. Firebase exists only when this build carries its environment's Firebase app config
 * (`app/src/<buildType>/google-services.json`, see README › Push): without it there is no token, no registration, and
 * the in-app notification centre works exactly the same. Never logged.
 */
class FirebasePushTokenSource(private val context: Context) : PushTokenSource {
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return try {
            withTimeoutOrNull(TOKEN_TIMEOUT_MS) { FirebaseMessaging.getInstance().token.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Google Play services, offline first launch… : best effort, retried on next launch / onNewToken.
            PushDiagnostics.log("token_unavailable", e.javaClass.simpleName)
            null
        }
    }

    private companion object {
        const val TOKEN_TIMEOUT_MS = 8_000L
    }
}
