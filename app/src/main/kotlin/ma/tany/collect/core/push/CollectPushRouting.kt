package ma.tany.collect.core.push

import ma.tany.core.model.common.NotificationCategory
import ma.tany.core.model.common.PushPayload

/**
 * Where a tapped TANY Collect push leads — pure Kotlin (JVM-tested). The backend's `deeplink` is the ONLY source of
 * the destination (never inferred from text or type), opened when this app knows it (same `tanycollect://` links as
 * iOS and the notification centre). Unknown / missing / client link ⇒ the point's notification centre. A notification
 * of ANOTHER point never opens that point's operation from the active one (the backend would refuse it anyway): the
 * merchant stays on Today. Every destination re-reads server state with the active `collectPointId`.
 */
object CollectPushRouting {
    const val NOTIFICATION_CENTRE = "tanycollect://notifications"

    private val known = listOf(
        Regex("^tanycollect://(today|scan|activity|notifications|revenue|revenue/settlement)$"),
        Regex("^tanycollect://(booking|return)/[A-Za-z0-9_-]{1,80}$"),
    )

    fun canOpen(link: String?): Boolean {
        val value = link?.trim().orEmpty()
        return value.isNotEmpty() && known.any { it.matches(value) }
    }

    /** Destination for [activePointId]; null = stay where the shell is (Today) — notification of another point. */
    fun destination(payload: PushPayload, activePointId: String?): String? {
        val notificationPoint = payload.collectPointId
        if (notificationPoint != null && activePointId != null && notificationPoint != activePointId) return null
        return payload.deepLink?.trim()?.takeIf(::canOpen) ?: NOTIFICATION_CENTRE
    }

    /** Point to mark the notification read on: the notification's own point, else the active one (iOS behaviour). */
    fun readPoint(payload: PushPayload, activePointId: String?): String? = payload.collectPointId ?: activePointId?.takeIf { it.isNotEmpty() }

    /** Android channel for a FOREGROUND push the app displays itself (the backend's `channel_id` wins otherwise). */
    fun channelFor(payload: PushPayload): PushChannel = when {
        payload.deepLink?.startsWith("tanycollect://revenue") == true -> PushChannel.ACCOUNT
        payload.category == NotificationCategory.DEPOSIT ||
            payload.category == NotificationCategory.INCIDENT ||
            payload.category == NotificationCategory.RETURN -> PushChannel.RETURNS_DEPOSITS
        else -> PushChannel.OPERATIONS
    }
}

/**
 * TANY Collect notification channels — a SMALL semantic set. Ids are STABLE (backend `android.notification.channel_id`,
 * user settings): never rename them.
 */
enum class PushChannel(val id: String) {
    OPERATIONS("operations"),
    RETURNS_DEPOSITS("returns_deposits"),
    ACCOUNT("account"),
    ;

    companion object {
        fun fromId(id: String?): PushChannel? = entries.firstOrNull { it.id == id }
    }
}
