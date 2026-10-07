package ma.tany.collect

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ma.tany.collect.core.locale.AppLanguage
import ma.tany.collect.core.push.PushNotifications
import ma.tany.collect.navigation.CollectApp
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.format.TanyFormatters
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference

/** AppCompatActivity: required by the per-app language API (FR / EN / AR) on Android < 13. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Cold start from a notification tap: kept by the ViewModel until the shell of the active point exists.
        if (savedInstanceState == null) PushNotifications.openFromIntent(intent)?.let(viewModel::onPushOpened)
        // Each (re)creation — including a language change — keeps the device registration current (no duplicates).
        viewModel.syncPushRegistration()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val session by viewModel.sessionState.collectAsStateWithLifecycle()
            val activePoint by viewModel.activePointId.collectAsStateWithLifecycle()
            val pendingPush by viewModel.pendingPush.collectAsStateWithLifecycle()
            val language = remember { AppLanguage.current() }
            // System bar icons follow the APP appearance (Compte › Apparence), not only the phone setting.
            val dark = when (theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            TanyTheme(preference = theme) {
                CompositionLocalProvider(LocalTanyFormatters provides remember(language) { TanyFormatters(language) }) {
                    CollectApp(
                        sessionState = session,
                        activePointId = activePoint,
                        pushPending = pendingPush != null,
                        consumePush = viewModel::consumePush,
                    )
                }
            }
        }
    }

    /** singleTask: a notification tap while the app runs. Other intents (tanycollect:// links) reach navigation as before. */
    override fun onNewIntent(intent: Intent) {
        val push = PushNotifications.openFromIntent(intent)
        if (push != null) viewModel.onPushOpened(push)
        // A push is routed by the shell (pending push), never by the navigation listeners: they get a neutral intent.
        super.onNewIntent(if (push != null) Intent() else intent)
    }
}
