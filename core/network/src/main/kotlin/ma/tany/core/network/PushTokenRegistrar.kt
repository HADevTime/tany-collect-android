package ma.tany.core.network

import kotlinx.coroutines.CancellationException
import ma.tany.core.model.common.DeviceRegistrationBody
import ma.tany.core.model.common.DeviceTokenBody

/**
 * Push registration. The backend supports Android since the pre-prod hardening (API_CONTRACT_V1 § 6):
 * `POST/DELETE /devices` (client) and `/collect/devices` (merchant) accept `platform:"android"` + an FCM token and
 * deliver through FCM HTTP v1 with a `data` payload (`deeplink`, `notificationId`, `type`, `badge`).
 * The app side only obtains the device token ([PushTokenSource]) and hands it to the backend — no push logic,
 * no notification content is computed on the device.
 */
interface PushTokenRegistrar {
    val isSupported: Boolean

    /** After sign-in (and on FCM token refresh). Best effort: a failure never blocks the session. */
    suspend fun register()

    /** On logout, BEFORE the session is cleared (the backend call is authenticated). */
    suspend fun unregister()
}

/**
 * Provides the FCM registration token. The Firebase Messaging implementation needs the per-environment Firebase
 * app configuration (`google-services.json`, one Firebase project per environment — never committed) and lands
 * with the notifications slice. Until then [NoPushTokenSource] is used.
 */
interface PushTokenSource {
    suspend fun currentToken(): String?
}

object NoPushTokenSource : PushTokenSource {
    override suspend fun currentToken(): String? = null
}

/** Registers / unregisters the FCM token through the app's device endpoint. */
class BackendPushTokenRegistrar(
    private val source: PushTokenSource,
    private val registerCall: suspend (DeviceRegistrationBody) -> Unit,
    private val unregisterCall: suspend (DeviceTokenBody) -> Unit,
) : PushTokenRegistrar {
    override val isSupported: Boolean get() = source !== NoPushTokenSource

    @Volatile
    private var registeredToken: String? = null

    override suspend fun register() {
        val token = safeToken() ?: return
        if (apiCall { registerCall(DeviceRegistrationBody(token)) } is ApiResult.Success) registeredToken = token
    }

    override suspend fun unregister() {
        val token = registeredToken ?: safeToken() ?: return
        apiCall { unregisterCall(DeviceTokenBody(token)) }
        registeredToken = null
    }

    private suspend fun safeToken(): String? = try {
        source.currentToken()?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
