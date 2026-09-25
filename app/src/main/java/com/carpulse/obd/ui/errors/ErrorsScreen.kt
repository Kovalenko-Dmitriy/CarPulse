package com.carpulse.obd.ui.errors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.R
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.db.DtcCauseEntity
import com.carpulse.obd.data.db.DtcCodeEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorsScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isRussian = configuration.locales[0].language == "ru"

    val connectionFlow = vm.connection
    val state: ConnState = connectionFlow?.collectAsState()?.value ?: ConnState.Disconnected

    // --- Состояние экрана ---
    var dtcs by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var lastReadAt by remember { mutableStateOf<Long?>(null) }

    // Выбранный код для деталей
    var selectedCode by remember { mutableStateOf<DtcCodeEntity?>(null) }
    var selectedCauses by remember { mutableStateOf<List<DtcCauseEntity>>(emptyList()) }
    var showDetails by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val clearedMsg = stringResource(R.string.errors_cleared_success)
    val clearFailedMsg = stringResource(R.string.errors_clear_failed)

    // Поиск
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DtcCodeEntity>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        try {
            searchResults = vm.searchDtc(searchQuery.uppercase())
        } catch (e: Exception) {
            searchResults = emptyList()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.errors_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.errors_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // ---- Поиск ----
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.errors_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (searchResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(searchResults) { entity ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entity.code,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    entity.titleEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    scope.launch {
                                        selectedCode = entity
                                        selectedCauses = vm.getDtcCauses(entity.code)
                                        showDetails = true
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                                }) {
                                    Text(stringResource(R.string.errors_details))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Предупреждение, если OBD не готов ----
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
                                stringResource(R.string.errors_not_connected)
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- Кнопки ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            reading = true
                            dtcs = vm.readDtcs()
                            lastReadAt = System.currentTimeMillis()
                            reading = false
                        }
                    },
                    enabled = state.isObdReady && !reading && !clearing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (reading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.errors_read_in_progress))
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.errors_read))
                    }
                }

                OutlinedButton(
                    onClick = { showClearDialog = true },
                    enabled = state.isObdReady && !reading && !clearing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (clearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.errors_clear_in_progress))
                    } else {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.errors_clear))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Список активных DTC ----
            when {
                dtcs.isEmpty() && lastReadAt == null -> {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.errors_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                dtcs.isEmpty() -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.errors_none),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.errors_active),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(dtcs) { code ->
                            DtcActiveRow(
                                vm = vm,
                                code = code,
                                isRussian = isRussian,
                                onClick = { entity, causes ->
                                    selectedCode = entity
                                    selectedCauses = causes
                                    showDetails = true
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // ---- Диалог сброса ----
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.errors_confirm_clear)) },
            text = { Text(stringResource(R.string.errors_confirm_clear_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        clearing = true
                        val ok = vm.clearDtcs()
                        clearing = false
                        if (ok) {
                            dtcs = emptyList()
                            snackbar.showSnackbar(clearedMsg)
                        } else {
                            snackbar.showSnackbar(clearFailedMsg)
                        }
                    }
                }) { Text(stringResource(R.string.errors_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.errors_confirm_no))
                }
            }
        )
    }

    // ---- Диалог деталей кода ----
    if (showDetails && selectedCode != null) {
        DtcDetailsDialog(
            entity = selectedCode!!,
            causes = selectedCauses,
            isRussian = isRussian,
            onDismiss = { showDetails = false }
        )
    }
}

// ============================================================
// Строка активного DTC
// ============================================================
@Composable
private fun DtcActiveRow(
    vm: AppViewModel,
    code: String,
    isRussian: Boolean,
    onClick: (DtcCodeEntity?, List<DtcCauseEntity>) -> Unit
) {
    var entity by remember { mutableStateOf<DtcCodeEntity?>(null) }
    var causes by remember { mutableStateOf<List<DtcCauseEntity>>(emptyList()) }

    LaunchedEffect(code) {
        entity = vm.getDtcDetails(code)
        causes = vm.getDtcCauses(code)
    }

    val title = entity?.let {
        if (isRussian) it.titleRu ?: it.titleEn else it.titleEn
    } ?: stringResource(R.string.errors_unknown_code)

    val mil = entity?.mil == true
    val emissions = entity?.emissionsRelevant == true

    val bg = if (mil) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (mil) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onTertiaryContainer

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(entity, causes) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Компактная карточка кода
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            modifier = Modifier.widthIn(min = 68.dp)
        ) {
            Box(
                Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Заголовок и метки
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (mil || emissions) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mil) {
                        Text(
                            stringResource(R.string.errors_mil_yes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                    if (mil && emissions) {
                        Text(
                            "  •  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (emissions) {
                        Text(
                            stringResource(R.string.errors_emissions_yes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Стрелка-указатель (вся строка кликабельна)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.errors_details),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// Диалог с полной информацией о коде
// ============================================================
@Composable
private fun DtcDetailsDialog(
    entity: DtcCodeEntity,
    causes: List<DtcCauseEntity>,
    isRussian: Boolean,
    onDismiss: () -> Unit
) {
    val title = if (isRussian) entity.titleRu ?: entity.titleEn else entity.titleEn
    val description = if (isRussian) entity.descriptionRu ?: entity.descriptionEn else entity.descriptionEn
    val category = categoryLabel(entity.category)
    val difficulty = difficultyLabel(entity.difficulty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    entity.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                Row {
                    Text(
                        "${stringResource(R.string.errors_category)}: $category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entity.mil) {
                    Text(
                        stringResource(R.string.errors_mil_yes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (entity.emissionsRelevant) {
                    Text(
                        stringResource(R.string.errors_emissions_yes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(description, style = MaterialTheme.typography.bodyMedium)

                // Причины
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.errors_causes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (causes.isEmpty()) {
                    Text(
                        stringResource(R.string.errors_no_causes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    causes.forEach { cause ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val likelihoodColor = when (cause.likelihood) {
                                "high" -> MaterialTheme.colorScheme.error
                                "medium" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text("•", color = likelihoodColor, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isRussian) cause.labelRu ?: cause.labelEn else cause.labelEn,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    likelihoodLabel(cause.likelihood),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = likelihoodColor
                                )
                            }
                        }
                    }
                }

                // Ремонт
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.errors_repair_info),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${stringResource(R.string.errors_difficulty)}: $difficulty",
                    style = MaterialTheme.typography.bodySmall
                )
                if (entity.costMinEur > 0 || entity.costMaxEur > 0) {
                    Text(
                        "${stringResource(R.string.errors_cost)}: " +
                                "${entity.costMinEur}–${entity.costMaxEur} €",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (entity.hoursMin > 0 || entity.hoursMax > 0) {
                    Text(
                        "${stringResource(R.string.errors_hours)}: " +
                                "${entity.hoursMin}–${entity.hoursMax} ч",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (entity.diyPossible) stringResource(R.string.errors_diy_yes)
                    else stringResource(R.string.errors_diy_no),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entity.diyPossible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

// ============================================================
// Вспомогательные функции локализации
// ============================================================
@Composable
private fun categoryLabel(category: String): String = when (category.lowercase()) {
    "powertrain" -> stringResource(R.string.errors_category_powertrain)
    "body" -> stringResource(R.string.errors_category_body)
    "chassis" -> stringResource(R.string.errors_category_chassis)
    "network" -> stringResource(R.string.errors_category_network)
    else -> category
}

@Composable
private fun difficultyLabel(difficulty: String): String = when (difficulty.lowercase()) {
    "easy" -> stringResource(R.string.errors_difficulty_easy)
    "medium" -> stringResource(R.string.errors_difficulty_medium)
    "hard" -> stringResource(R.string.errors_difficulty_hard)
    else -> difficulty
}

@Composable
private fun likelihoodLabel(likelihood: String): String = when (likelihood.lowercase()) {
    "high" -> stringResource(R.string.errors_likelihood_high)
    "medium" -> stringResource(R.string.errors_likelihood_medium)
    "low" -> stringResource(R.string.errors_likelihood_low)
    else -> likelihood
}