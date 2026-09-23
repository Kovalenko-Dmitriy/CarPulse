package com.carpulse.obd.ui.custom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.CarPulseApp
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.obd.TimedValue
import com.carpulse.obd.domain.LiveSnapshot
import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.units.UnitConverter
import com.carpulse.obd.ui.components.CircularGauge

@Composable
fun CustomDashboardScreen(vm: AppViewModel) {
    // ---- Живые данные с адаптера ----
    val liveFlow = vm.live
    val snapshot: LiveSnapshot = liveFlow?.collectAsStateWithLifecycle()?.value
        ?: LiveSnapshot.EMPTY
    val live = snapshot.values

    // ---- Состояние подключения ----
    val connectionFlow = vm.connection
    val state: ConnState = connectionFlow?.collectAsStateWithLifecycle()?.value
        ?: ConnState.Disconnected

    // ---- Настройки и поддерживаемые PID ----
    val settings by vm.settings.collectAsStateWithLifecycle()
    val supportedPids by (vm.supportedPids?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<Set<Pid>>(emptySet()) })

    // ---- Профиль → система единиц → конвертер ----
    val profile by CarPulseApp.instance.carProfileRepository.profileFlow
        .collectAsStateWithLifecycle(initialValue = CarProfile())
    val unitConverter = remember(profile.effectiveUnitSystem) {
        UnitConverter(profile.effectiveUnitSystem)
    }

    var showPicker by remember { mutableStateOf(false) }

    val selectedPids = remember(settings.selectedPids, supportedPids) {
        settings.selectedPids.mapNotNull { code ->
            Pid.entries.firstOrNull { it.cmd == code }
        }.filter { supportedPids.isEmpty() || it in supportedPids }
    }

    fun valueOf(pid: Pid): Float? {
        val tv: TimedValue = live[pid] ?: return null
        return if (tv.isStale(30000L)) null else tv.value
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
                    stringResource(R.string.custom_dashboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.custom_dashboard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showPicker = true }) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.custom_dashboard_select_pids)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Плашка «не подключено» ----
        if (!state.isObdReady) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.dashboard_not_connected),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Сетка приборов ----
        if (selectedPids.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.custom_dashboard_no_pids),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.custom_dashboard_no_pids_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showPicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.custom_dashboard_select_pids))
                    }
                }
            }
        } else {
            selectedPids.chunked(2).forEach { rowPids ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowPids.forEach { pid ->
                        PidGauge(
                            pid = pid,
                            metricValue = valueOf(pid),
                            converter = unitConverter,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowPids.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ---- Диалог выбора PID ----
    if (showPicker) {
        PidPickerDialog(
            availablePids = if (supportedPids.isEmpty()) Pid.entries.toList()
            else Pid.entries.filter { it in supportedPids },
            currentSelection = settings.selectedPids,
            onDismiss = { showPicker = false },
            onSave = { newSet ->
                vm.setSelectedPids(newSet)
                showPicker = false
            }
        )
    }
}

// ============================================================
// Один прибор — конвертация значений и порогов через UnitConverter
// ============================================================
@Composable
private fun PidGauge(
    pid: Pid,
    metricValue: Float?,
    converter: UnitConverter,
    modifier: Modifier
) {
    // Значение и пороги — в метрике. Все конвертируем в единую систему.
    val displayValue = converter.convert(metricValue, pid.unitType)
    val displayMin = converter.convert(pid.min, pid.unitType) ?: pid.min
    val displayMax = converter.convert(pid.max, pid.unitType) ?: pid.max
    val displayWarn = converter.convert(pid.warn, pid.unitType)
    val displayDanger = converter.convert(pid.danger, pid.unitType)

    CircularGauge(
        title = stringResource(pid.labelRes),
        value = displayValue,
        unit = stringResource(converter.displayUnitRes(pid.unitType)),
        min = displayMin,
        max = displayMax,
        warnValue = displayWarn,
        dangerValue = displayDanger,
        lowerIsWorse = pid.lowerIsWorse,
        modifier = modifier
    )
}

// ============================================================
// Диалог выбора PID
// ============================================================
@Composable
private fun PidPickerDialog(
    availablePids: List<Pid>,
    currentSelection: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var tempSelection by remember { mutableStateOf(currentSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_dashboard_picker_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 500.dp)) {
                items(availablePids.sortedBy { it.category.ordinal }) { pid ->
                    val isSelected = pid.cmd in tempSelection
                    val categoryLabel = stringResource(pid.category.labelRes)

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                tempSelection = if (checked) {
                                    tempSelection + pid.cmd
                                } else {
                                    tempSelection - pid.cmd
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(pid.labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "$categoryLabel · ${pid.cmd}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempSelection) }) {
                Text(stringResource(R.string.custom_dashboard_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custom_dashboard_cancel))
            }
        }
    )
}