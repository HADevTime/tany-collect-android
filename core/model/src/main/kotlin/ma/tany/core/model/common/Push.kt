package ma.tany.core.model.common

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * `POST /devices` (client) · `POST /collect/devices` (merchant) — Android registers its FCM token with
 * `platform:"android"` (API_CONTRACT_V1 § 6; `environment` is ignored for FCM). `Accept-Language` sets the push
 * language. The backend sends via FCM HTTP v1; nothing push-specific is computed by the app.
 */
@Serializable
data class DeviceRegistrationBody(
    val token: String,
    // Always sent: TanyJson does not encode defaults, and the backend needs it to treat the token as FCM.
    @EncodeDefault val platform: String = ANDROID,
) {
    companion object {
        const val ANDROID = "android"
    }
}

/** `DELETE /devices` body (on logout). */
@Serializable
data class DeviceTokenBody(val token: String)

/**
 * FCM `data` payload sent by the backend (all values are strings): `deeplink`, `notificationId`, `type`, `badge`.
 * A push only OPENS the deep link; the screen re-reads server state. [badge] = server unread count.
 */
data class PushPayload(val deepLink: String?, val notificationId: String?, val type: NotificationType, val badge: Int?) {
    companion object {
        fun fromData(data: Map<String, String>): PushPayload = PushPayload(
            deepLink = data["deeplink"]?.takeIf { it.isNotBlank() },
            notificationId = data["notificationId"]?.takeIf { it.isNotBlank() },
            type = NotificationType.Serializer.fromWire(data["type"]),
            badge = data["badge"]?.toIntOrNull(),
        )
    }
}
