package com.carpulse.obd.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.obd.VehicleInfo
import kotlinx.coroutines.launch

@Composable
fun VehicleScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()

    val connectionFlow = vm.connection
    val state: ConnState = connectionFlow?.collectAsState()?.value ?: ConnState.Disconnected

    var info by remember { mutableStateOf<VehicleInfo?>(null) }
    var reading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.vehicle_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.vehicle_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // Плашка статуса, если OBD не готов
        if (!state.isObdReady) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.vehicle_not_connected),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Кнопки
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        reading = true
                        info = vm.readVehicleInfo()
                        reading = false
                    }
                },
                enabled = state.isObdReady && !reading,
                modifier = Modifier.weight(1f)
            ) {
                if (reading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vehicle_reading))
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vehicle_read))
                }
            }
            OutlinedButton(
                onClick = { info = null },
                enabled = info != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vehicle_clear))
            }
        }

        Spacer(Modifier.height(16.dp))

        val current = info
        if (current == null) {
            Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.vehicle_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            InfoCard(stringResource(R.string.vehicle_vin), current.vin)
            InfoCard(stringResource(R.string.vehicle_cal_id), current.calibrationId)
            InfoCard(stringResource(R.string.vehicle_cvn), current.cvn)
            InfoCard(stringResource(R.string.vehicle_ecu_name), current.ecuName)

            if (current.supportedPids.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.vehicle_supported_pids),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    current.supportedPids.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun InfoCard(title: String, value: String?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
