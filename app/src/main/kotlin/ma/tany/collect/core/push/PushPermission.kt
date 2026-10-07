package ma.tany.collect.core.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import ma.tany.collect.R
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.R as DsR

/** What the notification permission allows right now (Android 13+ runtime permission, or app-level switch below). */
enum class PushPermissionStatus {
    GRANTED,

    /** The system dialog can be shown (never asked, or the user declined once). */
    CAN_ASK,

    /** Only the app's system notification settings can change it (declined twice, or switched off). */
    SETTINGS_ONLY,
}

object PushPermission {
    private const val PREFS = "tany_push"
    private const val KEY_ASKED = "permission_asked"

    fun status(context: Context, activity: Activity?): PushPermissionStatus {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (enabled) PushPermissionStatus.GRANTED else PushPermissionStatus.SETTINGS_ONLY
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) return if (enabled) PushPermissionStatus.GRANTED else PushPermissionStatus.SETTINGS_ONLY
        val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } == true
        return if (!wasAsked(context) || rationale) PushPermissionStatus.CAN_ASK else PushPermissionStatus.SETTINGS_ONLY
    }

    fun wasAsked(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    fun settingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Contextual notification invitation (never at launch, after sign-in): explains the value, then the Android dialog.
 * Declined ⇒ the app stays fully usable (Today + the notification centre list every operation); declined for good ⇒ a route
 * to the app's notification settings, only where the user looks for notifications ([onlyFirstTime] = never nag).
 */
@Composable
fun PushPermissionNotice(message: String, modifier: Modifier = Modifier, onlyFirstTime: Boolean = false) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var status by remember { mutableStateOf(PushPermission.status(context, activity)) }
    var askedBefore by remember { mutableStateOf(PushPermission.wasAsked(context)) }
    LifecycleResumeEffect(Unit) {
        status = PushPermission.status(context, activity)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        PushPermission.markAsked(context)
        PushDiagnostics.log("permission", if (granted) "granted" else "denied")
        status = PushPermission.status(context, activity)
    }
    if (status == PushPermissionStatus.GRANTED) return
    if (onlyFirstTime && (askedBefore || status != PushPermissionStatus.CAN_ASK)) return
    val canAsk = status == PushPermissionStatus.CAN_ASK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TanyNotice(
            message = message,
            tone = TanyTone.ACTION,
            title = stringResource(R.string.push_permission_title),
            icon = DsR.drawable.ic_tany_bell,
        )
        TanyButton(
            text = stringResource(if (canAsk) R.string.push_permission_enable else R.string.push_permission_settings),
            onClick = {
                if (canAsk) {
                    askedBefore = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching { context.startActivity(PushPermission.settingsIntent(context)) }
                }
            },
            style = TanyButtonStyle.TONAL,
            compact = true,
        )
    }
}
