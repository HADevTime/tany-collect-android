package ma.tany.collect.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.toLoadState
import ma.tany.collect.feature.account.AccountScreen
import ma.tany.collect.feature.activity.ActivityScreen
import ma.tany.collect.feature.auth.OtpScreen
import ma.tany.collect.feature.auth.PhoneScreen
import ma.tany.collect.feature.booking.BookingDetailScreen
import ma.tany.collect.feature.equipment.AssetDetailScreen
import ma.tany.collect.feature.equipment.EquipmentScreen
import ma.tany.collect.feature.notifications.NotificationsScreen
import ma.tany.collect.feature.revenue.RevenueScreen
import ma.tany.collect.feature.revenue.SettlementScreen
import ma.tany.collect.feature.point.PointPickerScreen
import ma.tany.collect.feature.operations.OperationScanScreen
import ma.tany.collect.feature.scanner.ScanTarget
import ma.tany.core.model.common.QrPurpose
import ma.tany.collect.feature.today.TodayScreen
import ma.tany.collect.feature.today.TodayShortcuts
import ma.tany.collect.internal.InternalTools
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanyLoadingState
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectNotificationRepository
import ma.tany.core.network.CollectRepository
import ma.tany.core.network.SessionState
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * App shell. Signed out ⇒ OTP flow; signed in without active point ⇒ point picker; otherwise the operational tabs.
 * A deep link received while signed out is not replayed (the merchant lands on Today after sign-in).
 */
@Composable
fun CollectApp(sessionState: SessionState, activePointId: String?) {
    when {
        sessionState == SessionState.Loading || activePointId == null -> TanyLoadingState()
        sessionState is SessionState.SignedOut -> AuthFlow()
        activePointId.isEmpty() -> PointPickerScreen()
        else -> MainShell(activePointId)
    }
}

@Composable
private fun AuthFlow() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = AuthPhoneRoute) {
        composable<AuthPhoneRoute> { PhoneScreen(onCodeSent = { phone, devCode -> navController.navigate(AuthOtpRoute(phone, devCode)) }) }
        composable<AuthOtpRoute> { OtpScreen(onBack = { navController.popBackStack() }) }
    }
}

/** Loads `/collect/me` once per point (point name, feature flags) and the unread notification count of the point. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val repository: CollectRepository,
    private val notifications: CollectNotificationRepository,
) : ViewModel() {
    private val _me = MutableStateFlow<LoadState<CollectMe>>(LoadState.Loading)
    val me: StateFlow<LoadState<CollectMe>> = _me.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    fun load() {
        viewModelScope.launch { _me.value = repository.me().toLoadState() }
    }

    /** Server count (module OFF ⇒ 0); a failure keeps the last value. */
    fun refreshUnread(pointId: String) {
        viewModelScope.launch {
            val result = notifications.unreadCount(pointId)
            if (result is ApiResult.Success) _unread.value = if (result.value.enabled) result.value.unreadCount else 0
        }
    }
}

private data class Tab(val route: Any, val type: KClass<*>, @StringRes val label: Int, @DrawableRes val icon: Int)

@Composable
private fun MainShell(pointId: String, shell: ShellViewModel = hiltViewModel()) {
    LaunchedEffect(pointId) { shell.load() }
    val meState by shell.me.collectAsStateWithLifecycle()
    val me = (meState as? LoadState.Loaded)?.value
    val pointName = me?.collectPoints?.firstOrNull { it.id == pointId }?.shortName
    val tabs = buildList {
        add(Tab(TodayRoute, TodayRoute::class, R.string.nav_today, DsR.drawable.ic_tany_home))
        add(Tab(ScanRoute, ScanRoute::class, R.string.nav_scan, DsR.drawable.ic_tany_scan))
        add(Tab(ActivityRoute, ActivityRoute::class, R.string.nav_activity, DsR.drawable.ic_tany_list))
        if (me?.features?.assets == true) add(Tab(EquipmentRoute, EquipmentRoute::class, R.string.nav_equipment, DsR.drawable.ic_tany_box))
        add(Tab(AccountRoute, AccountRoute::class, R.string.nav_account, DsR.drawable.ic_tany_person))
    }

    val navController = rememberNavController()
    NewIntentDeepLinks(navController)
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val unread by shell.unread.collectAsStateWithLifecycle()
    // Re-read the unread count each time the merchant comes back to a tab (after the centre, a booking…).
    LaunchedEffect(pointId, backStack?.id) { shell.refreshUnread(pointId) }
    val openLink: (String) -> Unit = { link ->
        val uri = Uri.parse(link)
        // Only known destinations; an unknown link (newer server) is ignored rather than crashing.
        if (navController.graph.hasDeepLink(uri)) navController.navigate(uri)
    }
    val showBar = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.type) } == true }
    val colors = TanyTheme.colors
    val openBooking: (String) -> Unit = { navController.navigate(BookingRoute(it)) }

    Scaffold(
        containerColor = colors.page,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                Column {
                    TanyDivider()
                    NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = destination?.hierarchy?.any { it.hasRoute(tab.type) } == true,
                                onClick = { navController.navigateTab(tab.route) },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(stringResource(tab.label), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.onAccentContainer,
                                    indicatorColor = colors.accentContainer,
                                    selectedTextColor = colors.textPrimary,
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = TodayRoute, modifier = Modifier.padding(padding)) {
            composable<TodayRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.TODAY })) {
                TodayScreen(
                    pointId = pointId,
                    pointName = pointName,
                    unreadNotifications = unread,
                    onOpenNotifications = { navController.navigate(NotificationsRoute) },
                    onOpenBooking = openBooking,
                    shortcuts = TodayShortcuts(
                        scan = { navController.navigateTab(ScanRoute) },
                        activity = { navController.navigateTab(ActivityRoute) },
                        equipment = if (me?.features?.assets == true) ({ navController.navigateTab(EquipmentRoute) }) else null,
                    ),
                )
            }
            composable<ScanRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.SCAN })) {
                // Scanner tab: the server resolves the booking and the purpose from the customer's code.
                OperationScanScreen(pointId = pointId, onDone = openBooking, onBack = null)
            }
            composable<OperationScanRoute> {
                OperationScanScreen(pointId = pointId, onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable<ActivityRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ACTIVITY })) {
                ActivityScreen(pointId = pointId, onOpenBooking = openBooking)
            }
            composable<EquipmentRoute> { EquipmentScreen(pointId = pointId, onOpenAsset = { navController.navigate(AssetRoute(it)) }) }
            composable<AssetRoute> {
                AssetDetailScreen(pointId = pointId, onBack = { navController.popBackStack() }, onOpenBooking = openBooking)
            }
            composable<AccountRoute> {
                AccountScreen(
                    me = me,
                    activePointId = pointId,
                    onOpenRevenue = { navController.navigate(RevenueRoute) },
                    onOpenSettlement = { navController.navigate(SettlementRoute) },
                    onOpenNotifications = { navController.navigate(NotificationsRoute) },
                    unreadNotifications = unread,
                    onOpenShowcase = if (InternalTools.enabled) ({ navController.navigate(ShowcaseRoute) }) else null,
                )
            }
            composable<RevenueRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.REVENUE })) {
                RevenueScreen(
                    pointId = pointId,
                    onBack = { navController.popBackStack() },
                    onOpenBooking = openBooking,
                    onOpenSettlement = { navController.navigate(SettlementRoute) },
                )
            }
            composable<SettlementRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.SETTLEMENT })) {
                SettlementScreen(pointId = pointId, onBack = { navController.popBackStack() })
            }
            composable<NotificationsRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.NOTIFICATIONS })) {
                NotificationsScreen(pointId = pointId, onBack = { navController.popBackStack() }, onOpenLink = openLink)
            }
            composable<BookingRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.BOOKING }, navDeepLink { uriPattern = DeepLinks.RETURN }),
            ) {
                BookingDetailScreen(
                    pointId = pointId,
                    onBack = { navController.popBackStack() },
                    onScanCustomer = { id, purpose -> navController.navigate(OperationScanRoute(id, ScanTarget.BOOKING_QR.name, purpose.wire)) },
                    onScanAsset = { id, purpose -> navController.navigate(OperationScanRoute(id, ScanTarget.ASSET_LABEL.name, purpose.wire)) },
                )
            }
            if (InternalTools.enabled) {
                composable<ShowcaseRoute> { InternalTools.Showcase(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun NewIntentDeepLinks(navController: NavHostController) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}
