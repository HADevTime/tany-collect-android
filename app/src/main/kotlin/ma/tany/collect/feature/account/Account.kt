package ma.tany.collect.feature.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import ma.tany.core.designsystem.component.TanyAvatar
import ma.tany.core.designsystem.component.TanyBadge
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyIconContainer
import ma.tany.core.designsystem.component.TanyLargeHeader
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanySectionHeader
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.format.TanyLanguage
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.CollectRole
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

    val colors = TanyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TanyLargeHeader(title = stringResource(R.string.account_title))
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            me?.let { ProfileCard(it) }

            TanySectionHeader(stringResource(R.string.account_active_point), modifier = Modifier.padding(top = 12.dp))
            TanyCard(contentPadding = 0.dp) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TanyIconContainer(DsR.drawable.ic_tany_store, contentDescription = null, container = colors.chrome, tint = colors.onChrome, size = 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(activePoint?.name ?: "—", style = TanyTheme.typography.headline)
                            activePoint?.let {
                                Text("${it.address} · ${it.city}", style = TanyTheme.typography.caption, color = colors.textMuted)
                            }
                        }
                        activePoint?.let {
                            TanyStatusChip(
                                stringResource(if (it.isOpenNow) R.string.point_open else R.string.point_closed),
                                if (it.isOpenNow) TanyTone.SUCCESS else TanyTone.NEUTRAL,
                                size = TanyChipSize.SMALL,
                            )
                        }
                    }
                    val others = me?.collectPoints.orEmpty().filter { it.id != activePointId }
                    others.forEach { point ->
                        TanyDivider(inset = 16.dp)
                        TanyRow(
                            title = point.name,
                            subtitle = stringResource(R.string.point_switch),
                            leadingIcon = DsR.drawable.ic_tany_swap,
                            onClick = {
                                confirmation.show(
                                    ConfirmationRequest(
                                        id = "switch:${point.id}",
                                        title = switchTitle,
                                        message = switchMessages.getValue(point.id),
                                        confirmLabel = switchLabel,
                                        icon = DsR.drawable.ic_tany_swap,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            TanySectionHeader(stringResource(R.string.account_point_section), modifier = Modifier.padding(top = 12.dp))
            TanyCard(contentPadding = 0.dp) {
                Column {
                    TanyRow(
                        title = stringResource(R.string.revenue_title),
                        subtitle = stringResource(R.string.account_revenue_hint),
                        leadingIcon = DsR.drawable.ic_tany_wallet,
                        leadingTone = TanyTone.SUCCESS,
                        onClick = onOpenRevenue,
                    )
                    TanyDivider(inset = 66.dp)
                    TanyRow(
                        title = stringResource(R.string.settlement_title),
                        subtitle = stringResource(R.string.account_settlement_hint),
                        leadingIcon = DsR.drawable.ic_tany_receipt,
                        leadingTone = TanyTone.INFO,
                        onClick = onOpenSettlement,
                    )
                    TanyDivider(inset = 66.dp)
                    TanyRow(
                        title = stringResource(R.string.notifications_title),
                        subtitle = if (unreadNotifications > 0) {
                            stringResource(R.string.notifications_unread_count, unreadNotifications)
                        } else {
                            stringResource(R.string.account_notifications_hint)
                        },
                        leadingIcon = DsR.drawable.ic_tany_bell,
                        leadingTone = TanyTone.ACTION,
                        onClick = onOpenNotifications,
                        trailing = if (unreadNotifications > 0) {
                            { TanyBadge(if (unreadNotifications > 99) "99+" else unreadNotifications.toString()) }
                        } else {
                            null
                        },
                    )
                }
            }

            TanySectionHeader(stringResource(R.string.account_preferences), modifier = Modifier.padding(top = 12.dp))
            TanyCard {
                PreferenceLabel(DsR.drawable.ic_tany_contrast, stringResource(R.string.account_appearance))
                TanySegmentedControl(
                    options = listOf(
                        TanySegment(ThemePreference.SYSTEM, stringResource(R.string.appearance_system)),
                        TanySegment(ThemePreference.LIGHT, stringResource(R.string.appearance_light)),
                        TanySegment(ThemePreference.DARK, stringResource(R.string.appearance_dark)),
                    ),
                    selected = theme,
                    onSelect = viewModel::setTheme,
                )
                Spacer(Modifier.height(8.dp))
                PreferenceLabel(DsR.drawable.ic_tany_globe, stringResource(R.string.account_language))
                TanySegmentedControl(
                    options = listOf(
                        TanySegment(TanyLanguage.FR, stringResource(R.string.language_fr)),
                        TanySegment(TanyLanguage.EN, stringResource(R.string.language_en)),
                        TanySegment(TanyLanguage.AR, stringResource(R.string.language_ar)),
                    ),
                    selected = AppLanguage.current(),
                    onSelect = { AppLanguage.set(it) },
                )
            }

            if (onOpenShowcase != null || !AppEnvironment.isProduction) {
                TanySectionHeader(stringResource(R.string.account_internal_section), modifier = Modifier.padding(top = 12.dp))
                TanyCard(contentPadding = 0.dp) {
                    Column {
                        if (!AppEnvironment.isProduction) {
                            TanyRow(
                                title = stringResource(R.string.account_environment, BuildConfig.TANY_ENVIRONMENT),
                                subtitle = AppEnvironment.endpoint.baseUrl,
                                leadingIcon = DsR.drawable.ic_tany_info,
                            )
                        }
                        if (onOpenShowcase != null) {
                            if (!AppEnvironment.isProduction) TanyDivider(inset = 66.dp)
                            TanyRow(title = stringResource(R.string.account_internal_tools), leadingIcon = DsR.drawable.ic_tany_list, onClick = onOpenShowcase)
                        }
                    }
                }
            }

            TanyCard(contentPadding = 0.dp, modifier = Modifier.padding(top = 12.dp)) {
                TanyRow(
                    title = logoutLabel,
                    leadingIcon = DsR.drawable.ic_tany_logout,
                    destructive = true,
                    showChevron = false,
                    onClick = {
                        confirmation.show(
                            ConfirmationRequest(
                                id = "logout",
                                title = logoutTitle,
                                message = logoutMessage,
                                confirmLabel = logoutLabel,
                                kind = ConfirmationKind.DESTRUCTIVE,
                                icon = DsR.drawable.ic_tany_logout,
                            ),
                        )
                    },
                )
            }
            Text(
                stringResource(R.string.account_version, BuildConfig.VERSION_NAME),
                style = TanyTheme.typography.caption,
                color = colors.textSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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

/** Merchant identity: monogram, full name, phone (LTR), role. */
@Composable
private fun ProfileCard(me: CollectMe) {
    val name = "${me.user.firstName} ${me.user.lastName}".trim()
    TanyCard(contentPadding = 18.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TanyAvatar(name, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = TanyTheme.typography.title)
                Text(ltrIsolated(me.user.phone), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted)
            }
        }
        TanyStatusChip(
            stringResource(if (me.user.role == CollectRole.ADMIN) R.string.account_role_admin else R.string.account_role_merchant),
            TanyTone.NEUTRAL,
            size = TanyChipSize.SMALL,
        )
    }
}

@Composable
private fun PreferenceLabel(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = TanyTheme.colors.textMuted, modifier = Modifier.size(18.dp))
        Text(text, style = TanyTheme.typography.bodyStrong, modifier = Modifier.semantics { heading() })
    }
}
