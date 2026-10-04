package ma.tany.collect.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ma.tany.collect.BuildConfig
import ma.tany.collect.R
import ma.tany.collect.core.AppEnvironment
import ma.tany.collect.core.locale.AppLanguage
import ma.tany.collect.core.preferences.CollectPreferences
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.ConfirmationKind
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationSheetHost
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.format.TanyLanguage
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.network.CollectAuthRepository
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: CollectAuthRepository,
    private val preferences: CollectPreferences,
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = preferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { preferences.setTheme(preference) }
    }

    /** ADMIN multi-point: everything (operations, revenue, equipment) reloads for the new point. */
    fun switchPoint(pointId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setActivePoint(pointId)
            onDone()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}

@Composable
fun AccountScreen(
    me: CollectMe?,
    activePointId: String,
    onOpenRevenue: () -> Unit,
    onOpenSettlement: () -> Unit,
    onOpenNotifications: () -> Unit,
    unreadNotifications: Int,
    onOpenShowcase: (() -> Unit)?,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val confirmation = rememberConfirmationState()
    val activePoint = me?.collectPoints?.firstOrNull { it.id == activePointId }
    val switchTitle = stringResource(R.string.point_switch_confirm_title)
    val switchLabel = stringResource(R.string.point_switch_confirm)
    val logoutTitle = stringResource(R.string.logout_confirm_title)
    val logoutMessage = stringResource(R.string.logout_confirm_message)
    val logoutLabel = stringResource(R.string.account_logout)
    val switchMessages = me?.collectPoints.orEmpty().associate { it.id to stringResource(R.string.point_switch_confirm_message, it.name) }

    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.account_title), chrome = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            me?.let {
                TanyCard {
                    Text("${it.user.firstName} ${it.user.lastName}", style = TanyTheme.typography.headline)
                    Text(ltrIsolated(it.user.phone), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
                }
            }
            SectionTitle(stringResource(R.string.account_active_point))
            TanyCard(contentPadding = 0.dp) {
                TanyRow(title = activePoint?.name ?: "—", subtitle = activePoint?.let { "${it.address} · ${it.city}" }, leadingIcon = DsR.drawable.ic_tany_box)
                me?.collectPoints.orEmpty().filter { it.id != activePointId }.forEach { point ->
                    TanyRow(
                        title = point.name,
                        subtitle = stringResource(R.string.point_switch),
                        onClick = {
                            confirmation.show(
                                ConfirmationRequest(id = "switch:${point.id}", title = switchTitle, message = switchMessages.getValue(point.id), confirmLabel = switchLabel),
                            )
                        },
                    )
                }
            }

            SectionTitle(stringResource(R.string.account_point_section))
            TanyCard(contentPadding = 0.dp) {
                TanyRow(title = stringResource(R.string.revenue_title), onClick = onOpenRevenue)
                TanyRow(title = stringResource(R.string.settlement_title), onClick = onOpenSettlement)
                TanyRow(
                    title = stringResource(R.string.notifications_title),
                    subtitle = if (unreadNotifications > 0) stringResource(R.string.notifications_unread_count, unreadNotifications) else null,
                    onClick = onOpenNotifications,
                )
            }

            SectionTitle(stringResource(R.string.account_appearance))
            TanyCard(contentPadding = 0.dp) {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        ThemePreference.SYSTEM to R.string.appearance_system,
                        ThemePreference.LIGHT to R.string.appearance_light,
                        ThemePreference.DARK to R.string.appearance_dark,
                    ).forEach { (preference, label) -> ChoiceRow(stringResource(label), theme == preference) { viewModel.setTheme(preference) } }
                }
            }

            SectionTitle(stringResource(R.string.account_language))
            TanyCard(contentPadding = 0.dp) {
                val current = AppLanguage.current()
                Column(Modifier.selectableGroup()) {
                    listOf(
                        TanyLanguage.FR to R.string.language_fr,
                        TanyLanguage.EN to R.string.language_en,
                        TanyLanguage.AR to R.string.language_ar,
                    ).forEach { (language, label) -> ChoiceRow(stringResource(label), current == language) { AppLanguage.set(language) } }
                }
            }

            if (!AppEnvironment.isProduction) {
                Text(
                    stringResource(R.string.account_environment, "${BuildConfig.TANY_ENVIRONMENT} · ${AppEnvironment.endpoint.baseUrl}"),
                    style = TanyTheme.typography.caption,
                    color = TanyTheme.colors.textMuted,
                )
            }
            if (onOpenShowcase != null) {
                TanyCard(contentPadding = 0.dp) { TanyRow(title = stringResource(R.string.account_internal_tools), onClick = onOpenShowcase) }
            }
            TanyButton(
                text = logoutLabel,
                style = TanyButtonStyle.SECONDARY,
                onClick = {
                    confirmation.show(
                        ConfirmationRequest(id = "logout", title = logoutTitle, message = logoutMessage, confirmLabel = logoutLabel, kind = ConfirmationKind.DESTRUCTIVE),
                    )
                },
            )
        }
    }
    ConfirmationSheetHost(confirmation) { request ->
        when {
            request.id == "logout" -> viewModel.logout(onDone = confirmation::finish)
            request.id.startsWith("switch:") -> viewModel.switchPoint(request.id.removePrefix("switch:"), onDone = confirmation::finish)
            else -> confirmation.finish()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    TanyRow(
        title = label,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        trailing = { RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = TanyTheme.colors.accent)) },
    )
}
