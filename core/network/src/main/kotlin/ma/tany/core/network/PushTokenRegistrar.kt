package ma.tany.core.network

/**
 * Push registration seam. The backend only accepts APNs tokens today (`/devices` rejects FCM tokens with 422) and
 * only sends via APNs — Android push is backend gap A-1 (API_CONTRACT_V1 § 5). No FCM API is invented here:
 * the app ships [NoopPushTokenRegistrar] until the additive backend contract (platform "android", FCM token format,
 * data payload) exists.
 */
interface PushTokenRegistrar {
    val isSupported: Boolean

    suspend fun register()

    /** Called on logout before the session is cleared. */
    suspend fun unregister()
}

object NoopPushTokenRegistrar : PushTokenRegistrar {
    override val isSupported: Boolean = false

    override suspend fun register() = Unit

    override suspend fun unregister() = Unit
}
