package com.carpulse.obd.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.prefs.ThemeMode
import com.carpulse.obd.data.prefs.Units
import com.carpulse.obd.domain.FuelType

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ============================================================
        // Тема
        // ============================================================
        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.themeMode == mode,
                    onClick = { vm.setTheme(mode) }
                )
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.AUTO -> stringResource(R.string.settings_theme_auto)
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ============================================================
        // Единицы измерения
        // ============================================================
        Text(
            stringResource(R.string.settings_units),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Units.entries.forEach { u ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.units == u,
                    onClick = { vm.setUnits(u) }
                )
                Text(
                    if (u == Units.METRIC) stringResource(R.string.settings_units_metric)
                    else stringResource(R.string.settings_units_imperial)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ============================================================
        // Топливо
        // ============================================================
        Text(
            stringResource(R.string.settings_fuel_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))

        // Литраж
        var dispText by remember { mutableStateOf(settings.displacementL.toString()) }
        OutlinedTextField(
            value = dispText,
            onValueChange = { dispText = it
                it.replace(',', '.').toFloatOrNull()?.let { v -> vm.setDisplacement(v) }
            },
            label = { Text(stringResource(R.string.settings_displacement)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // Объёмный КПД
        var veText by remember {
            mutableStateOf(settings.volumetricEfficiency.toString())
        }
        OutlinedTextField(
            value = veText,
            onValueChange = { veText = it
                it.replace(',', '.').toFloatOrNull()?.let { v -> vm.setVolumetricEfficiency(v) }
            },
            label = { Text(stringResource(R.string.settings_ve)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // Тип топлива
        Text(
            stringResource(R.string.settings_fuel_type),
            style = MaterialTheme.typography.bodyMedium
        )
        FuelType.entries.forEach { ft ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.fuelType == ft.name,
                    onClick = { vm.setFuelType(ft.name) }
                )
                Text(stringResource(ft.labelRes))
            }
        }
        Spacer(Modifier.height(8.dp))

        // Цена за литр
        var priceText by remember {
            mutableStateOf(if (settings.fuelPricePerLiter > 0f) settings.fuelPricePerLiter.toString() else "")
        }
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it
                it.replace(',', '.').toFloatOrNull()?.let { v -> vm.setFuelPrice(v) }
            },
            label = { Text(stringResource(R.string.settings_fuel_price)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        // Одометр
        var odoText by remember {
            mutableStateOf(if (settings.odometerKm > 0f) settings.odometerKm.toInt().toString() else "")
        }
        OutlinedTextField(
            value = odoText,
            onValueChange = { odoText = it
                it.replace(',', '.').toFloatOrNull()?.let { v -> vm.setOdometer(v) }
            },
            label = { Text(stringResource(R.string.settings_odometer)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ============================================================
        // Интервал опроса
        // ============================================================
        Text(
            stringResource(R.string.settings_interval),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        val intervals = listOf(150L, 300L, 500L, 1000L)
        intervals.forEach { ms ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.pollingIntervalMs == ms,
                    onClick = { vm.setPollingInterval(ms) }
                )
                Text("$ms ms")
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ============================================================
        // Автоподключение
        // ============================================================
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_autoconnect),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    settings.lastMac ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.autoConnect,
                onCheckedChange = { vm.setAutoConnect(it) }
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ============================================================
        // Диагностика — поделиться логами
        // ============================================================
        Text(
            "Диагностика",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val file = com.carpulse.obd.FileLogger.getLogFile()
                if (file == null || !file.exists()) {
                    Toast.makeText(ctx, "Файл логов не найден", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                try {
                    val uri = FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.fileProvider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "CarPulse logs")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Поделиться логами"))
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Поделиться логами")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ============================================================
        // О приложении
        // ============================================================
        Text(
            stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_name) + " 0.1.0 (alpha)",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "© 2026 CarPulse",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))
    }
}