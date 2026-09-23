package com.carpulse.obd.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.carpulse.obd.R

data class NavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

val MainNavItems = listOf(
    NavItem(Routes.DASHBOARD,  R.string.tab_dashboard,  Icons.Filled.Speed),
    NavItem(Routes.CUSTOM,     R.string.tab_custom,     Icons.Filled.Star),
    NavItem(Routes.TRIPS,      R.string.tab_trips,      Icons.Filled.Route),
    NavItem(Routes.FUEL,       R.string.tab_fuel,       Icons.Filled.LocalGasStation),
    NavItem(Routes.LIVE,       R.string.tab_live,       Icons.Filled.ShowChart),
    NavItem(Routes.VEHICLE,    R.string.tab_vehicle,    Icons.Filled.DirectionsCar)
)