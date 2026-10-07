package ma.tany.collect.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ma.tany.collect.MainActivity
import ma.tany.collect.R
import ma.tany.core.model.common.PushPayload

/**
 * System-notification side of TANY Collect push (one intentional architecture, no duplicates):
 *  - background / killed: FCM displays the backend `notification` itself (channel, `tag` = notification id); a tap
 *    opens [MainActivity] with the `data` keys as extras → [openFromIntent];
 *  - foreground: [TanyCollectMessagingService] displays it HERE with the SAME tag (iOS also shows the banner).
 * No action buttons: a merchant gesture (hand-over, deposit…) always happens in the app, never from the shade.
 */
object PushNotifications {
    private const val EXTRA_PUSH = "ma.tany.collect.push"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            PushChannel.entries.map { channel ->
                NotificationChannel(channel.id, context.getString(channel.nameRes()), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(channel.descriptionRes())
                    setShowBadge(true)
                }
            },
        )
    }

    // Permission checked just above the notify call (explicit POST_NOTIFICATIONS check).
    @SuppressLint("MissingPermission")
    fun show(context: Context, payload: PushPayload, title: String, body: String?, channelId: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tag = payload.notificationId ?: return
        val channel = PushChannel.fromId(channelId) ?: CollectPushRouting.channelFor(payload)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_tany_collect)
            .setColor(ContextCompat.getColor(context, R.color.tany_notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, payload))
            .apply { payload.badge?.let { setNumber(it) } }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
        } catch (e: SecurityException) {
            PushDiagnostics.log("display_refused")
        }
    }

    /** Same extras as a system-displayed FCM notification, so both taps follow ONE parsing path. */
    private fun openIntent(context: Context, payload: PushPayload): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_PUSH, true)
            .putExtra(PushPayload.KEY_NOTIFICATION_ID, payload.notificationId)
            .putExtra(PushPayload.KEY_DEEPLINK, payload.deepLink)
            .putExtra(PushPayload.KEY_TYPE, payload.type.wire)
            .putExtra(PushPayload.KEY_CATEGORY, payload.category.wire)
            .putExtra(PushPayload.KEY_BOOKING_ID, payload.bookingId)
            .putExtra(PushPayload.KEY_COLLECT_POINT_ID, payload.collectPointId)
        return PendingIntent.getActivity(context, payload.notificationId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The push behind a notification tap (system- or app-displayed), or null for any other launch. */
    fun openFromIntent(intent: Intent?): PushPayload? {
        val extras = intent?.extras ?: return null
        if (extras.getString(PushPayload.KEY_NOTIFICATION_ID) == null) return null
        return PushPayload.fromData(extras.keySet().associateWith { extras.getString(it) })
    }
}

private fun PushChannel.nameRes(): Int = when (this) {
    PushChannel.OPERATIONS -> R.string.push_channel_operations
    PushChannel.RETURNS_DEPOSITS -> R.string.push_channel_returns_deposits
    PushChannel.ACCOUNT -> R.string.push_channel_account
}

private fun PushChannel.descriptionRes(): Int = when (this) {
    PushChannel.OPERATIONS -> R.string.push_channel_operations_description
    PushChannel.RETURNS_DEPOSITS -> R.string.push_channel_returns_deposits_description
    PushChannel.ACCOUNT -> R.string.push_channel_account_description
}
