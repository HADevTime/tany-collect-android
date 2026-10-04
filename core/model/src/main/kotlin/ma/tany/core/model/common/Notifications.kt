package ma.tany.core.model.common

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Notification centre item (`serializeNotification`). Notifications are a PROJECTION, never a source of truth:
 * a deep link only opens a screen, which re-reads server state.
 */
@Serializable
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val category: NotificationCategory,
    val tone: NotificationTone,
    /** Localized by the server (Accept-Language) — one of the assumed multilingual exceptions. */
    val title: String,
    val body: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val readAt: Instant? = null,
    val isRead: Boolean = false,
    val bookingId: String? = null,
    val bookingReference: String? = null,
    val productName: String? = null,
    val incidentId: String? = null,
    /** MERCHANT audience only — the key is ABSENT for customers. */
    val collectPointId: String? = null,
    val action: NotificationAction? = null,
    val isResolved: Boolean = false,
    @Serializable(with = InstantSerializer::class) val resolvedAt: Instant? = null,
    val deepLink: String? = null,
    /** Category DEPOSIT only. */
    val amounts: NotificationAmounts? = null,
)

@Serializable
data class NotificationAction(val kind: NotificationActionKind, val label: String)

@Serializable
data class NotificationAmounts(
    val currency: String,
    val amount: MoneyAmount? = null,
    val refundAmount: MoneyAmount? = null,
    val retainedAmount: MoneyAmount? = null,
    val outstandingAmount: MoneyAmount? = null,
)

/** `GET /notifications` — `enabled:false` ⇒ module OFF (empty list, unreadCount 0). */
@Serializable
data class NotificationsPage(
    val enabled: Boolean,
    val notifications: List<NotificationItem> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

@Serializable
data class UnreadCount(val enabled: Boolean, val unreadCount: Int = 0)

@Serializable
data class NotificationReadResponse(val ok: Boolean = false, val enabled: Boolean = false, val unreadCount: Int = 0)
