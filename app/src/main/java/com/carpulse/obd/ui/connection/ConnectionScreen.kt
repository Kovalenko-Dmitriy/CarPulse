package com.carpulse.obd.ui.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState

@Composable
fun ConnectionScreen(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current

    // Реактивное состояние Bluetooth
    val btEnabled by vm.btEnabled.collectAsState()

    // Bluetooth вообще недоступен
    if (vm.adapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.connection_no_bt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    val paired by vm.paired.collectAsState()
    val settings by vm.settings.collectAsState()

    val connectionFlow = vm.connection
    val state: ConnState = connectionFlow?.collectAsState()?.value
        ?: ConnState.Disconnected

    var showDevicePicker by remember {
        mutableStateOf(settings.lastMac.isNullOrBlank())
    }

    LaunchedEffect(settings.lastMac) {
        if (settings.lastMac.isNullOrBlank()) showDevicePicker = true
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) vm.loadPairedDevices()
    }

    LaunchedEffect(Unit) {
        val need = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
        else vm.loadPairedDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // ---- Статус ----
        StatusCard(state)

        Spacer(Modifier.height(16.dp))

        // ---- Кнопка «Повторить ЭБУ» ----
        if (state is ConnState.ObdError) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.connection_obd_error_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.retryObd() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.connection_retry_obd))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Bluetooth выключен ----
        if (!btEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.connection_bt_off),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }) {
                        Text(stringResource(R.string.connection_enable_bt))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Основной блок ----
        val savedMac = settings.lastMac
        if (!showDevicePicker && !savedMac.isNullOrBlank()) {
            SavedDeviceCard(
                deviceName = settings.lastDeviceName ?: "ELM327",
                mac = savedMac,
                isConnected = state.isBluetoothReady,
                onConnect = { vm.connect(savedMac, settings.lastDeviceName) },
                onChange = { showDevicePicker = true },
                onForget = {
                    vm.forgetDevice()
                    showDevicePicker = true
                }
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { vm.disconnect() },
                enabled = state !is ConnState.Disconnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.LinkOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.connection_disconnect))
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.connection_paired_devices),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { vm.loadPairedDevices() }) {
                    Text(stringResource(R.string.connection_refresh))
                }
            }

            if (paired.isEmpty()) {
                Text(
                    stringResource(R.string.connection_no_paired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(paired) { device ->
                        DeviceRow(
                            device = device,
                            isConnected = state.currentMac == device.address,
                            onClick = {
                                val need = requiredPermissions().filter {
                                    ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
                                }
                                if (need.isNotEmpty()) {
                                    permLauncher.launch(need.toTypedArray())
                                } else {
                                    vm.connect(device.address, device.name ?: "ELM327")
                                    showDevicePicker = false
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ============================================================
// Карточка сохранённого устройства
// ============================================================
@Composable
private fun SavedDeviceCard(
    deviceName: String,
    mac: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onChange: () -> Unit,
    onForget: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isConnected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnect,
                    enabled = !isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connection_connect))
                }
                OutlinedButton(
                    onClick = onChange,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connection_change_device))
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onForget,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.connection_forget_device),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ============================================================
// Карточка статуса
// ============================================================
@Composable
private fun StatusCard(state: ConnState) {
    val (bg, icon, text) = when (state) {
        ConnState.Disconnected -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Filled.LinkOff,
            stringResource(R.string.connection_status_disconnected)
        )
        ConnState.BtConnecting -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Filled.Link,
            stringResource(R.string.connection_status_bt_connecting)
        )
        is ConnState.BtConnected -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Filled.Bluetooth,
            stringResource(R.string.connection_status_bt_connected, state.deviceName)
        )
        is ConnState.BtError -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Filled.Error,
            stringResource(R.string.connection_status_bt_error, state.message)
        )
        ConnState.ObdInitializing -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Filled.Refresh,
            stringResource(R.string.connection_status_obd_initializing)
        )
        is ConnState.ObdConnected -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Filled.CheckCircle,
            stringResource(R.string.connection_status_obd_connected, state.protocol)
        )
        is ConnState.ObdError -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Filled.Warning,
            stringResource(R.string.connection_status_obd_error, state.message)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ============================================================
// Строка сопряжённого устройства
// ============================================================
@Suppress("MissingPermission")
@Composable
private fun DeviceRow(
    device: BluetoothDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (isConnected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isConnected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============================================================
// Разрешения
// ============================================================
private fun requiredPermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}