package ma.tany.collect

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ma.tany.collect.core.locale.AppLanguage
import ma.tany.collect.navigation.CollectApp
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.format.TanyFormatters
import ma.tany.core.designsystem.theme.TanyTheme

/** AppCompatActivity: required by the per-app language API (FR / EN / AR) on Android < 13. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val session by viewModel.sessionState.collectAsStateWithLifecycle()
            val activePoint by viewModel.activePointId.collectAsStateWithLifecycle()
            val language = remember { AppLanguage.current() }
            TanyTheme(preference = theme) {
                CompositionLocalProvider(LocalTanyFormatters provides remember(language) { TanyFormatters(language) }) {
                    CollectApp(sessionState = session, activePointId = activePoint)
                }
            }
        }
    }
}
