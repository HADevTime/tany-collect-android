package ma.tany.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ma.tany.core.model.common.DeviceRegistrationBody
import ma.tany.core.model.common.DeviceTokenBody

/**
 * Push registration. The backend supports Android since the pre-prod hardening (API_CONTRACT_V1 § 6):
 * `POST/DELETE /devices` (client) and `/collect/devices` (merchant) accept `platform:"android"` + an FCM token and
 * deliver through FCM HTTP v1 with a `data` payload (`deeplink`, `notificationId`, `type`, `badge`, + `category`,
 * `bookingId`, `collectPointId`). The app side only obtains the device token ([PushTokenSource]) and hands it to the
 * backend — no push logic, no notification content is computed on the device. The token is NEVER an authentication
 * factor and is never logged.
 */
interface PushTokenRegistrar {
    val isSupported: Boolean

    /** After sign-in: always (re)attaches the token to the signed-in account. Best effort, never blocks the session. */
    suspend fun register()

    /**
     * Launch with a restored session, app language change: registers only when the (token, language) pair differs
     * from the last successful registration of this process — no duplicate calls. The language travels in
     * `Accept-Language` (push copy language on the backend).
     */
    suspend fun refresh()

    /** FCM rotated the token (`onNewToken`): the backend replaces / re-attaches it (call only with a session). */
    suspend fun onNewToken(token: String)

    /** On logout, BEFORE the session is cleared (the backend call is authenticated). */
    suspend fun unregister()

    /** Session lost without a logout call (401): the next sign-in must register again. */
    fun forget()
}

/** Provides the FCM registration token (Firebase Messaging; null when Firebase is not configured for this build). */
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
    /** Current `Accept-Language` tag: a language change must re-register (push copy language). */
    private val language: LanguageProvider = LanguageProvider { "" },
) : PushTokenRegistrar {
    override val isSupported: Boolean get() = source !== NoPushTokenSource

    private val mutex = Mutex()

    @Volatile
    private var registeredToken: String? = null

    @Volatile
    private var registeredKey: String? = null

    @Volatile
    private var latestToken: String? = null

    override suspend fun register() = mutex.withLock { send(safeToken(), force = true) }

    override suspend fun refresh() = mutex.withLock { send(safeToken(), force = false) }

    override suspend fun onNewToken(token: String) = mutex.withLock {
        latestToken = token.takeIf { it.isNotBlank() }
        send(latestToken, force = false)
    }

    override suspend fun unregister() = mutex.withLock {
        val token = registeredToken ?: safeToken()
        registeredToken = null
        registeredKey = null
        if (token != null) apiCall { unregisterCall(DeviceTokenBody(token)) }
        Unit
    }

    override fun forget() {
        registeredToken = null
        registeredKey = null
    }

    private suspend fun send(token: String?, force: Boolean) {
        if (token == null) return
        val key = "$token|${language.languageTag()}"
        if (!force && key == registeredKey) return
        if (apiCall { registerCall(DeviceRegistrationBody(token)) } is ApiResult.Success) {
            registeredToken = token
            registeredKey = key
        }
    }

    private suspend fun safeToken(): String? = try {
        (latestToken ?: source.currentToken())?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
