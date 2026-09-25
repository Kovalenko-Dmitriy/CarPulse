package com.carpulse.obd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.ui.connection.ConnectionScreen
import com.carpulse.obd.ui.custom.CustomDashboardScreen
import com.carpulse.obd.ui.dashboard.DashboardScreen
import com.carpulse.obd.ui.errors.ErrorsScreen
import com.carpulse.obd.ui.fuel.FuelStatsScreen
import com.carpulse.obd.ui.live.LiveDataScreen
import com.carpulse.obd.ui.navigation.MainNavItems
import com.carpulse.obd.ui.navigation.Routes
import com.carpulse.obd.ui.profile.CarProfileScreen
import com.carpulse.obd.ui.settings.SettingsScreen
import com.carpulse.obd.ui.trips.TripsScreen
import com.carpulse.obd.ui.vehicle.VehicleScreen

/**
 * Единый хелпер навигации между экранами приложения.
 *
 * Логика:
 *  - popUpTo(Routes.DASHBOARD) — удаляем всё, что выше dashboard,
 *    чтобы не накапливать back stack. `inclusive = false` — dashboard
 *    не удаляем, он остаётся «корнем» и к нему всегда можно вернуться.
 *  - launchSingleTop = true — если экран уже открыт, не создаём дубль.
 *  - restoreState = true — восстанавливаем сохранённое состояние экрана
 *    (например, положение скролла), если оно есть.
 *
 * Используется и в bottomBar, и в TopAppBar — единый подход
 * исключает конфликт между разными обработчиками.
 */
private fun NavHostController.navigateToTab(route: String) {
    // Уже на этом экране — ничего не делаем, чтобы не дёргать стек зря.
    if (currentDestination?.route == route) return

    navigate(route) {
        popUpTo(Routes.DASHBOARD) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarPulseRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentItem = MainNavItems.firstOrNull { it.route == currentRoute }
    val title = when (currentRoute) {
        Routes.SETTINGS -> stringResource(R.string.tab_settings)
        Routes.CONNECTION -> stringResource(R.string.tab_connection)
        Routes.ERRORS -> stringResource(R.string.tab_errors)
        Routes.TRIPS -> stringResource(R.string.tab_trips)
        Routes.PROFILE -> stringResource(R.string.tab_profile)
        else -> currentItem?.let { stringResource(it.labelRes) }
            ?: stringResource(R.string.app_name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    // ---- Статус-точка ----
                    val conn: ConnState = vm.connection
                        ?.collectAsState()?.value
                        ?: ConnState.Disconnected
                    val dotColor = when (conn) {
                        is ConnState.ObdConnected -> MaterialTheme.colorScheme.primary
                        ConnState.BtConnecting,
                        ConnState.ObdInitializing,
                        is ConnState.BtConnected -> MaterialTheme.colorScheme.tertiary
                        is ConnState.BtError,
                        is ConnState.ObdError -> MaterialTheme.colorScheme.error
                        ConnState.Disconnected -> MaterialTheme.colorScheme.outlineVariant
                    }

                    IconButton(onClick = { nav.navigateToTab(Routes.CONNECTION) }) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }

                    // ---- Bluetooth / Связь ----
                    IconButton(onClick = { nav.navigateToTab(Routes.CONNECTION) }) {
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = stringResource(R.string.tab_connection)
                        )
                    }

                    // ---- Ошибки (DTC) ----
                    IconButton(onClick = { nav.navigateToTab(Routes.ERRORS) }) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.tab_errors),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    // ---- Профиль автомобиля ----
                    IconButton(onClick = { nav.navigateToTab(Routes.PROFILE) }) {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = stringResource(R.string.tab_profile)
                        )
                    }

                    // ---- Настройки ----
                    IconButton(onClick = { nav.navigateToTab(Routes.SETTINGS) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.tab_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                MainNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { nav.navigateToTab(item.route) },
                        icon = {
                            Icon(item.icon, contentDescription = stringResource(item.labelRes))
                        },
                        label = null,
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.DASHBOARD)  { DashboardScreen(vm) }
            composable(Routes.CUSTOM)     { CustomDashboardScreen(vm) }
            composable(Routes.TRIPS)      { TripsScreen() }
            composable(Routes.FUEL)       { FuelStatsScreen() }
            composable(Routes.LIVE)       { LiveDataScreen(vm) }
            composable(Routes.ERRORS)     { ErrorsScreen(vm) }
            composable(Routes.VEHICLE)    { VehicleScreen(vm) }
            composable(Routes.CONNECTION) { ConnectionScreen(vm) }
            composable(Routes.SETTINGS)   { SettingsScreen(vm) }
            composable(Routes.PROFILE)    { CarProfileScreen() }
        }
    }
}