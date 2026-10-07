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
 * FCM `data` payload sent by the backend (all values are strings): `deeplink`, `notificationId`, `type`, `badge`, and
 * additively `category`, `bookingId`, `collectPointId` (TANY Collect only: the point the notification belongs to).
 * A push only IDENTIFIES the context and OPENS the deep link; the screen re-reads server state. [badge] = server
 * unread count. Unknown / missing keys never fail (older backends).
 */
data class PushPayload(
    val deepLink: String?,
    val notificationId: String?,
    val type: NotificationType,
    val badge: Int?,
    val category: NotificationCategory = NotificationCategory.UNKNOWN,
    val bookingId: String? = null,
    val collectPointId: String? = null,
) {
    companion object {
        const val KEY_DEEPLINK = "deeplink"
        const val KEY_NOTIFICATION_ID = "notificationId"
        const val KEY_TYPE = "type"
        const val KEY_BADGE = "badge"
        const val KEY_CATEGORY = "category"
        const val KEY_BOOKING_ID = "bookingId"
        const val KEY_COLLECT_POINT_ID = "collectPointId"

        fun fromData(data: Map<String, String?>): PushPayload = PushPayload(
            deepLink = data[KEY_DEEPLINK]?.trim()?.takeIf { it.isNotEmpty() },
            notificationId = data[KEY_NOTIFICATION_ID]?.trim()?.takeIf { it.isNotEmpty() },
            type = NotificationType.Serializer.fromWire(data[KEY_TYPE]),
            badge = data[KEY_BADGE]?.toIntOrNull()?.takeIf { it >= 0 },
            category = NotificationCategory.Serializer.fromWire(data[KEY_CATEGORY]),
            bookingId = data[KEY_BOOKING_ID]?.trim()?.takeIf { it.isNotEmpty() },
            collectPointId = data[KEY_COLLECT_POINT_ID]?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
