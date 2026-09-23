package com.carpulse.obd.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.ui.components.LineChart

@Composable
fun LiveDataScreen(
    vm: AppViewModel,
    histVm: LiveHistoryViewModel = viewModel()
) {
    // Запускаем сбор истории (единожды, если уже подписаны — не переподписываемся)
    LaunchedEffect(vm) {
        histVm.startCollecting(vm)
    }

    val history by histVm.history.collectAsState()
    val connectionFlow = vm.connection
    val state: ConnState = connectionFlow?.collectAsState()?.value ?: ConnState.Disconnected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.live_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.live_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { histVm.clear() }) {
                Text(stringResource(R.string.live_clear))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Плашка статуса, если OBD не готов ----
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
                            stringResource(R.string.live_no_data)
                    },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- График RPM ----
        ChartCard(
            title = stringResource(R.string.gauge_rpm) + " · " + stringResource(R.string.unit_rpm),
            data = history[Pid.RPM] ?: emptyList(),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        // ---- График Speed ----
        ChartCard(
            title = stringResource(R.string.gauge_speed) + " · " + stringResource(R.string.unit_speed_kmh),
            data = history[Pid.SPEED] ?: emptyList(),
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(16.dp))

        // ---- График Coolant ----
        ChartCard(
            title = stringResource(R.string.gauge_coolant) + " · " + stringResource(R.string.unit_celsius),
            data = history[Pid.COOLANT] ?: emptyList(),
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(16.dp))

        // ---- График Load ----
        ChartCard(
            title = stringResource(R.string.gauge_load) + " · " + stringResource(R.string.unit_percent),
            data = history[Pid.LOAD] ?: emptyList(),
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ChartCard(
    title: String,
    data: List<Float>,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    data.lastOrNull()?.toInt()?.toString() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(Modifier.height(12.dp))
            LineChart(
                data = data,
                lineColor = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}