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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.prefs.ConnectionType

@Composable
fun ConnectionScreen(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current

    val settings by vm.settings.collectAsState()
    val state: ConnState = vm.connection.collectAsState().value
    val btEnabled by vm.btEnabled.collectAsState()
    val paired by vm.paired.collectAsState()

    var uiMode by rememberSaveable { mutableStateOf(settings.connectionType) }

    LaunchedEffect(settings.connectionType) {
        uiMode = settings.connectionType
    }

    var wifiHost by rememberSaveable { mutableStateOf(settings.lastWiFiHost) }
    var wifiPort by rememberSaveable { mutableStateOf(settings.wifiPort.toString()) }

    LaunchedEffect(settings.lastWiFiHost, settings.wifiPort) {
        wifiHost = settings.lastWiFiHost
        wifiPort = settings.wifiPort.toString()
    }

    var showDevicePicker by remember { mutableStateOf(settings.lastMac.isNullOrBlank()) }
    LaunchedEffect(settings.lastMac) {
        if (settings.lastMac.isNullOrBlank()) showDevicePicker = true
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) vm.loadPairedDevices()
    }

    LaunchedEffect(uiMode) {
        if (uiMode == ConnectionType.BLUETOOTH) {
            val need = mutableListOf<String>()
            for (perm in requiredPermissions()) {
                if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
                    need.add(perm)
                }
            }
            if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
            else vm.loadPairedDevices()
        }
    }

       Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Text(
            text = stringResource(R.string.connection_active_transport, vm.transportKind),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiMode == ConnectionType.BLUETOOTH,
                onClick = {
                    if (uiMode != ConnectionType.BLUETOOTH) {
                        uiMode = ConnectionType.BLUETOOTH
                        vm.setConnectionType(ConnectionType.BLUETOOTH)
                    }
                },
                label = { Text(stringResource(R.string.connection_mode_bluetooth)) },
                leadingIcon = {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null)
                },
            )
            FilterChip(
                selected = uiMode == ConnectionType.WIFI,
                onClick = {
                    if (uiMode != ConnectionType.WIFI) {
                        uiMode = ConnectionType.WIFI
                        vm.setConnectionType(ConnectionType.WIFI)
                    }
                },
                label = { Text(stringResource(R.string.connection_mode_wifi)) },
                leadingIcon = {
                    Icon(Icons.Filled.Wifi, contentDescription = null)
                },
            )
        }

        if (uiMode != settings.connectionType) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.connection_mode_restart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))

        StatusCard(state)

        Spacer(Modifier.height(16.dp))

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

        if (uiMode == ConnectionType.WIFI) {
            WifiConnectCard(
                host = wifiHost,
                port = wifiPort,
                onHostChange = { wifiHost = it },
                onPortChange = { input ->
                    val digitsOnly = input.filter { it in '0'..'9' }
                    wifiPort = digitsOnly.take(5)
                },
                onConnect = {
                    val port = wifiPort.toIntOrNull() ?: 35000
                    vm.connectWiFi(wifiHost.trim(), port)
                },
                isConnected = state.isBluetoothReady,
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
            return@Column
        }

        if (vm.adapter == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.connection_no_bt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Column
        }

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
                                val need = mutableListOf<String>()
                                for (perm in requiredPermissions()) {
                                    if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
                                        need.add(perm)
                                    }
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

@Composable
private fun WifiConnectCard(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    isConnected: Boolean,
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
                    Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.connection_wifi_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.connection_wifi_host_label)) },
                placeholder = { Text("192.168.0.10") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text(stringResource(R.string.connection_wifi_port_label)) },
                placeholder = { Text("35000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onConnect,
                enabled = host.isNotBlank() && !isConnected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.connection_connect))
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.connection_wifi_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedDeviceCard(
    deviceName: String,
    mac: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onChange: () -> Unit,
    onForget: () -> Unit,
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
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isConnected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConnect,
                    enabled = !isConnected,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connection_connect))
                }
                OutlinedButton(
                    onClick = onChange,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connection_change_device))
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onForget,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.connection_forget_device),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: ConnState) {
    val (bg, icon, text) = when (state) {
        ConnState.Disconnected -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Filled.LinkOff,
            stringResource(R.string.connection_status_disconnected),
        )
        ConnState.BtConnecting -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Filled.Link,
            stringResource(R.string.connection_status_bt_connecting),
        )
        is ConnState.BtConnected -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Filled.Bluetooth,
            stringResource(R.string.connection_status_bt_connected, state.deviceName),
        )
        is ConnState.BtError -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Filled.Error,
            stringResource(R.string.connection_status_bt_error, state.message),
        )
        ConnState.ObdInitializing -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Filled.Refresh,
            stringResource(R.string.connection_status_obd_initializing),
        )
        is ConnState.ObdConnected -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Filled.CheckCircle,
            stringResource(R.string.connection_status_obd_connected, state.protocol),
        )
        is ConnState.ObdError -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Filled.Warning,
            stringResource(R.string.connection_status_obd_error, state.message),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Suppress("MissingPermission")
@Composable
private fun DeviceRow(
    device: BluetoothDevice,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (isConnected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isConnected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

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