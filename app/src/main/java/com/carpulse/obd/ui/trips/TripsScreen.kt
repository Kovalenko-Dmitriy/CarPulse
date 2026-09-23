package com.carpulse.obd.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.db.TripEntity
import com.carpulse.obd.ui.components.OsmMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Экран «Поездки». Read-only:
 *  - карта с треком выбранной поездки;
 *  - live-трек активной поездки, если идёт запись;
 *  - список завершённых поездок;
 *  - удаление поездки с подтверждением.
 *
 * Ручного старта/стопа нет — запись управляется TripController
 * автоматически по состоянию OBD.
 */
@Composable
fun TripsScreen(
    vm: TripsViewModel = viewModel()
) {
    val trips by vm.trips.collectAsState()
    val activeTrip by vm.activeTrip.collectAsState()
    val selectedTripId by vm.selectedTripId.collectAsState()
    val selectedPoints by vm.selectedPoints.collectAsState()
    val activePoints by vm.activePoints.collectAsState()

    var pendingDelete by remember { mutableStateOf<TripEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {

        // -----------------------------------------------------------------
        //  Карта: трек выбранной поездки ИЛИ live-трек активной
        // -----------------------------------------------------------------
        val mapPoints = when {
            selectedTripId != null -> selectedPoints
            activeTrip != null -> activePoints
            else -> emptyList()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (mapPoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.trips_map_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // OsmMap принимает список пар (lat, lon) — конвертируем здесь.
                OsmMap(
                    points = mapPoints.map { it.lat to it.lon },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // -----------------------------------------------------------------
        //  Плашка «идёт запись» / «всего поездок»
        // -----------------------------------------------------------------
        val at = activeTrip
        if (at != null) {
            StatusBanner(
                title = stringResource(R.string.trips_recording_title),
                subtitle = buildString {
                    append(formatDistance(at.distanceKm))
                    append(" • ")
                    append(formatDuration(System.currentTimeMillis() - at.startTime))
                    if (at.fuelUsedL > 0f) {
                        append(" • ")
                        append(formatFuel(at.fuelUsedL))
                    }
                }
            )
        } else {
            StatusBanner(
                title = stringResource(R.string.trips_all_title),
                subtitle = stringResource(R.string.trips_count_fmt, trips.size)
            )
        }

        Spacer(Modifier.height(12.dp))

        // -----------------------------------------------------------------
        //  Список поездок
        // -----------------------------------------------------------------
        if (trips.isEmpty() && at == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.trips_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Активная поездка — первой строкой, если есть
                if (at != null) {
                    item(key = "active_${at.id}") {
                        TripCard(
                            trip = at,
                            isActive = true,
                            isSelected = false,
                            onClick = { vm.clearSelection() },
                            onDelete = null
                        )
                    }
                }

                items(
                    items = trips,
                    key = { it.id }
                ) { t ->
                    TripCard(
                        trip = t,
                        isActive = false,
                        isSelected = selectedTripId == t.id,
                        onClick = { vm.selectTrip(t.id) },
                        onDelete = { pendingDelete = t }
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Диалог подтверждения удаления
    // ---------------------------------------------------------------------
    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.trips_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.trips_delete_message,
                        formatDateTime(t.startTime)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTrip(t.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.trips_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.trips_delete_cancel))
                }
            }
        )
    }
}

// =========================================================================
//  Компоненты
// =========================================================================

@Composable
private fun StatusBanner(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TripCard(
    trip: TripEntity,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val container = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Заголовок: дата + (для активной) индикатор
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDateTime(trip.startTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.trips_delete_title),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Метрики: дистанция / время / расход
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricItem(
                    icon = Icons.Filled.Speed,
                    value = formatDistance(trip.distanceKm)
                )
                MetricItem(
                    icon = Icons.Filled.Timer,
                    value = if (isActive) {
                        formatDuration(System.currentTimeMillis() - trip.startTime)
                    } else {
                        formatDuration((trip.endTime ?: trip.startTime) - trip.startTime)
                    }
                )
                if (trip.fuelUsedL > 0f) {
                    MetricItem(
                        icon = Icons.Filled.LocalGasStation,
                        value = formatFuel(trip.fuelUsedL)
                    )
                }
            }

            // Вторая строка метрик: средняя/макс скорость, обороты, ОЖ
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(stringResource(R.string.trips_metric_avg))
                    append(": ")
                    append(formatSpeed(trip.avgSpeed))
                    append("  •  ")
                    append(stringResource(R.string.trips_metric_max))
                    append(": ")
                    append(formatSpeed(trip.maxSpeed))
                    if (trip.avgRpm > 0f) {
                        append("  •  ")
                        append(formatRpm(trip.avgRpm))
                    }
                    if (trip.maxCoolant > 0f) {
                        append("  •  ")
                        append(formatCoolant(trip.maxCoolant))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// =========================================================================
//  Форматтеры
// =========================================================================

private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

private fun formatDateTime(ms: Long): String = dateFmt.format(Date(ms))

private fun formatDistance(km: Float): String =
    if (km < 1f) "${(km * 1000).toInt()} м"
    else String.format(Locale.getDefault(), "%.2f км", km)

private fun formatSpeed(kmh: Float): String =
    String.format(Locale.getDefault(), "%.0f км/ч", kmh)

private fun formatFuel(l: Float): String =
    String.format(Locale.getDefault(), "%.2f л", l)

private fun formatRpm(rpm: Float): String =
    String.format(Locale.getDefault(), "%.0f об/мин", rpm)

private fun formatCoolant(c: Float): String =
    String.format(Locale.getDefault(), "%.0f °C", c)

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 мин"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h} ч ${m} мин" else "${m} мин"
}