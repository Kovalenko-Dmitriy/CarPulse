package com.carpulse.obd.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.domain.LiveSnapshot
import com.carpulse.obd.ui.components.CircularGauge

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val liveFlow = vm.live
    val snapshot: LiveSnapshot = liveFlow?.collectAsStateWithLifecycle()?.value
        ?: LiveSnapshot.EMPTY
    val live = snapshot.values

    val connectionFlow = vm.connection
    val state: ConnState = connectionFlow?.collectAsStateWithLifecycle()?.value
        ?: ConnState.Disconnected

    val batteryFlow = vm.battery
    val battery = batteryFlow?.collectAsStateWithLifecycle()?.value

    // Хелпер: возвращает живое значение или null, если протухло
    fun value(pid: Pid): Float? {
        val tv = live[pid] ?: return null
        return if (tv.isStale(15000L)) null else tv.value
    }

    fun batteryValue(): Float? {
        val tv = battery ?: return null
        return if (tv.isStale(15000L)) null else tv.value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.dashboard_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // ---- Плашка статуса ----
        if (!state.isObdReady) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (state) {
                        is ConnState.ObdError -> MaterialTheme.colorScheme.errorContainer
                        is ConnState.BtError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (state) {
                        is ConnState.ObdError ->
                            stringResource(R.string.connection_status_obd_error, state.message)
                        is ConnState.BtError ->
                            stringResource(R.string.connection_status_bt_error, state.message)
                        ConnState.BtConnecting ->
                            stringResource(R.string.connection_status_bt_connecting)
                        is ConnState.BtConnected ->
                            stringResource(R.string.connection_status_bt_connected, state.deviceName)
                        ConnState.ObdInitializing ->
                            stringResource(R.string.connection_status_obd_initializing)
                        else ->
                            stringResource(R.string.dashboard_not_connected)
                    },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Ряд 1: Обороты + Скорость ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularGauge(
                title = stringResource(R.string.gauge_rpm),
                value = value(Pid.RPM),
                unit = stringResource(R.string.unit_rpm),
                min = 0f,
                max = 8000f,
                warnValue = 5500f,
                dangerValue = 6500f,
                modifier = Modifier.weight(1f)
            )
            CircularGauge(
                title = stringResource(R.string.gauge_speed),
                value = value(Pid.SPEED),
                unit = stringResource(R.string.unit_speed_kmh),
                min = 0f,
                max = 200f,
                warnValue = 120f,
                dangerValue = 160f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Ряд 2: ОЖ + Напряжение ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularGauge(
                title = stringResource(R.string.gauge_coolant),
                value = value(Pid.COOLANT),
                unit = stringResource(R.string.unit_celsius),
                min = -40f,
                max = 120f,
                warnValue = 100f,
                dangerValue = 110f,
                modifier = Modifier.weight(1f)
            )
            CircularGauge(
                title = stringResource(R.string.dashboard_battery),
                value = batteryValue(),
                unit = stringResource(R.string.dashboard_unit_volt),
                min = 10f,
                max = 15f,
                warnValue = 12.2f,
                dangerValue = 11.8f,
                lowerIsWorse = true,
                decimals = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Нижний статус ----
        when (state) {
            is ConnState.ObdConnected -> {
                Text(
                    stringResource(R.string.connection_status_obd_connected, state.protocol),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            is ConnState.BtConnected -> {
                Text(
                    stringResource(R.string.connection_status_bt_connected, state.deviceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            ConnState.ObdInitializing -> {
                Text(
                    stringResource(R.string.connection_status_obd_initializing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            else -> {}
        }
    }
}