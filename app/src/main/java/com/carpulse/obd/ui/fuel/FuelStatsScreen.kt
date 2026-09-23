package com.carpulse.obd.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpulse.obd.data.db.FillUpEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelStatsScreen(vm: FuelStatsViewModel = viewModel()) {
    val stats by vm.stats.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var fillUps by remember { mutableStateOf<List<FillUpEntity>>(emptyList()) }

    LaunchedEffect(Unit) { vm.refreshStats() }

    LaunchedEffect(showHistory) {
        if (showHistory) {
            fillUps = vm.allFillUps()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---- Заголовок ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Расход топлива",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Мгновенный, средний и стоимость",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить заправку")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Мгновенный расход ----
        SectionTitle("Мгновенный расход")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "л/ч",
                value = stats.instantLph?.let { "%.1f".format(it) } ?: "—",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "л/100 км",
                value = stats.instantL100?.let { "%.1f".format(it) } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Пробег ----
        SectionTitle("Пробег")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Сегодня",
                value = "%.1f км".format(stats.todayDistanceKm),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Неделя",
                value = "%.1f км".format(stats.weekDistanceKm),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Общий",
                value = "%.1f км".format(stats.totalDistanceKm),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Средний расход ----
        SectionTitle("Средний расход (л/100 км)")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Сегодня",
                value = stats.todayAvgL100?.let { "%.1f".format(it) } ?: "—",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Неделя",
                value = stats.weekAvgL100?.let { "%.1f".format(it) } ?: "—",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Общий",
                value = stats.totalAvgL100?.let { "%.1f".format(it) } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Стоимость ----
        SectionTitle("Стоимость топлива")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Сегодня",
                value = "%.0f ₽".format(stats.todayCost),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Неделя",
                value = "%.0f ₽".format(stats.weekCost),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Общий",
                value = "%.0f ₽".format(stats.totalCost),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Литры ----
        SectionTitle("Залито топлива")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Сегодня",
                value = "%.1f л".format(stats.todayLiters),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Неделя",
                value = "%.1f л".format(stats.weekLiters),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Общий",
                value = "%.1f л".format(stats.totalLiters),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Информация ----
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Цена за литр: %.2f ₽".format(stats.fuelPricePerLiter))
                Spacer(Modifier.height(4.dp))
                Text("Одометр: %.0f км".format(stats.odometerKm))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- История заправок ----
        OutlinedButton(
            onClick = { showHistory = !showHistory },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showHistory) "Скрыть историю" else "Показать историю заправок")
        }

        if (showHistory) {
            Spacer(Modifier.height(12.dp))
            if (fillUps.isEmpty()) {
                Text(
                    "История пуста",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                fillUps.forEach { fill ->
                    FillUpCard(
                        fill = fill,
                        fmt = fmt,
                        onDelete = {
                            vm.deleteFillUp(fill.id)
                            fillUps = fillUps.filter { it.id != fill.id }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // ---- Диалог добавления заправки ----
    if (showAddDialog) {
        AddFillUpDialog(
            currentOdometer = stats.odometerKm,
            currentPrice = stats.fuelPricePerLiter,
            onDismiss = { showAddDialog = false },
            onSave = { liters, odo, price ->
                vm.addFillUp(liters, odo, price)
                showAddDialog = false
            }
        )
    }
}

// ============================================================
// UI-компоненты
// ============================================================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FillUpCard(
    fill: FillUpEntity,
    fmt: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%.1f л · %.0f км".format(fill.liters, fill.odometerKm),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    fmt.format(Date(fill.timestamp)) +
                            " · %.2f ₽/л".format(fill.pricePerLiter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                fill.consumptionL100?.let { c ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Расход: %.1f л/100 км".format(c),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить")
            }
        }
    }
}

@Composable
private fun AddFillUpDialog(
    currentOdometer: Float,
    currentPrice: Float,
    onDismiss: () -> Unit,
    onSave: (liters: Float, odometer: Float, price: Float) -> Unit
) {
    var litersText by remember { mutableStateOf("") }
    var odoText by remember {
        mutableStateOf(if (currentOdometer > 0f) currentOdometer.toInt().toString() else "")
    }
    var priceText by remember {
        mutableStateOf(if (currentPrice > 0f) currentPrice.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить заправку") },
        text = {
            Column {
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("Литры") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = odoText,
                    onValueChange = { odoText = it },
                    label = { Text("Одометр, км") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Цена за литр") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val liters = litersText.replace(',', '.').toFloatOrNull() ?: return@TextButton
                val odo = odoText.replace(',', '.').toFloatOrNull() ?: return@TextButton
                val price = priceText.replace(',', '.').toFloatOrNull() ?: 0f
                onSave(liters, odo, price)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}