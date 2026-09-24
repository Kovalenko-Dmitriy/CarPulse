package com.carpulse.obd.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpulse.obd.CarPulseApp
import com.carpulse.obd.R
import com.carpulse.obd.domain.profile.Region
import com.carpulse.obd.domain.units.UnitSystem
import com.carpulse.obd.domain.vin.VinDecodeResult
import com.carpulse.obd.domain.vin.VinKind
import com.carpulse.obd.domain.vin.VinValidation
import com.carpulse.obd.domain.vin.VinValidator

@Composable
fun CarProfileScreen(
    onBack: () -> Unit = {},
    viewModel: CarProfileViewModel = viewModel(
        factory = CarProfileViewModelFactory(
            profileRepo = CarPulseApp.instance.carProfileRepository,
            decoder = CarPulseApp.instance.vinDecoder,
            ecuResolver = CarPulseApp.instance.ecuResolver,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Флаг: пользователь нажал «Применить». Пока true — кнопка disabled,
    // клавиатура скрыта, поле VIN без фокуса. Сбрасывается в false,
    // когда пользователь тапает по полю VIN.
    var isVinApplied by remember { mutableStateOf(false) }

    // Режим ввода: VIN или номер кузова
    var inputMode by remember { mutableStateOf(IdentifierMode.VIN) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CarProfileEvent.ShowError -> {
                    snackbarHostState.showSnackbar(getStringSafe(event.messageRes))
                }
                is CarProfileEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(getStringSafe(event.messageRes))
                }
                CarProfileEvent.ProfileCleared -> {
                    snackbarHostState.showSnackbar(getStringSafe(R.string.profile_cleared))
                    isVinApplied = false
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Здесь ваш существующий TopAppBar.
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- Переключатель VIN / Номер кузова ----
            ModeSwitcher(
                mode = inputMode,
                onModeChange = {
                    inputMode = it
                    viewModel.onVinEntered("")
                    viewModel.onBodyNumberEntered("")
                },
            )

            // ---- VIN ----
            if (inputMode == IdentifierMode.VIN) {
                VinSection(
                    vin = state.profile.vin.orEmpty(),
                    validation = state.vinValidation,
                    decode = state.lastDecode,
                    isDecoding = state.isDecoding,
                    isApplied = isVinApplied,
                    onVinChange = { viewModel.onVinEntered(it) },
                    onVinFocusChanged = { focused -> if (focused) isVinApplied = false },
                    onApply = {
                        viewModel.applyDecodedVin(overwriteExisting = true)
                        isVinApplied = true
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    onSwitchToBody = { body ->
                        viewModel.onBodyNumberEntered(body)
                        inputMode = IdentifierMode.BODY
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                )
            } else {
                BodyNumberSection(
                    bodyNumber = state.profile.bodyNumber.orEmpty(),
                    onBodyNumberChange = { viewModel.onBodyNumberEntered(it) },
                    onConfirm = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                )
            }

            // ---- ECU (только для VIN) ----
            if (inputMode == IdentifierMode.VIN) {
                state.ecuResolution?.let { resolution ->
                    EcuResolutionCard(
                        result = resolution,
                        onSelectAlternative = { entry ->
                            viewModel.onEcuSelected(entry.id)
                        },
                    )
                }
            }

            // ---- Ручные поля ----
            ManualFieldsSection(
                state = state,
                onMakeChange = viewModel::onMakeChanged,
                onModelChange = viewModel::onModelChanged,
                onYearChange = viewModel::onYearChanged,
            )

            // ---- Регион ----
            RegionSection(
                selected = state.profile.region,
                onSelect = viewModel::onRegionChanged,
            )

            // ---- Единицы ----
            UnitsSection(
                override = state.profile.unitSystemOverride,
                effective = state.profile.effectiveUnitSystem,
                onSelect = viewModel::onUnitSystemChanged,
            )

            // ---- Очистка ----
            ClearButton(onClick = viewModel::clearProfile)

            Spacer(Modifier.height(32.dp))
        }
    }
}

private enum class IdentifierMode { VIN, BODY }

@Composable
private fun ModeSwitcher(
    mode: IdentifierMode,
    onModeChange: (IdentifierMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == IdentifierMode.VIN,
            onClick = { onModeChange(IdentifierMode.VIN) },
            label = { Text(stringResource(R.string.profile_id_mode_vin)) },
        )
        FilterChip(
            selected = mode == IdentifierMode.BODY,
            onClick = { onModeChange(IdentifierMode.BODY) },
            label = { Text(stringResource(R.string.profile_id_mode_body)) },
        )
    }
}

// ========================================================================
// Секция VIN
// ========================================================================

@Composable
private fun VinSection(
    vin: String,
    validation: VinValidation?,
    decode: VinDecodeResult?,
    isDecoding: Boolean,
    isApplied: Boolean,
    onVinChange: (String) -> Unit,
    onVinFocusChanged: (Boolean) -> Unit,
    onApply: () -> Unit,
    onSwitchToBody: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_section_vin),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = vin,
                onValueChange = onVinChange,
                label = { Text(stringResource(R.string.profile_vin_label)) },
                placeholder = { Text(stringResource(R.string.profile_vin_placeholder)) },
                singleLine = true,
                isError = validation is VinValidation.Invalid
                        && VinValidator.classify(vin) != VinKind.BODY,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    if (isDecoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (vin.isNotEmpty()) {
                        IconButton(onClick = { onVinChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.profile_vin_clear),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        onVinFocusChanged(focusState.isFocused)
                    },
            )

            // Если введённое похоже на номер кузова (JDM, ВАЗ, ГАЗ),
            // показываем подсказку вместо ошибки валидации.
            val looksLikeBody = vin.isNotBlank()
                    && VinValidator.classify(vin) == VinKind.BODY

            if (looksLikeBody) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.profile_body_hint_switch),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSwitchToBody(vin) },
                            ) {
                                Text(stringResource(R.string.profile_body_switch))
                            }
                            TextButton(
                                onClick = { onVinChange("") },
                            ) {
                                Text(stringResource(R.string.profile_body_ignore))
                            }
                        }
                    }
                }
            } else if (validation is VinValidation.Invalid) {
                Text(
                    text = validation.toUserMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (decode != null && validation is VinValidation.Valid) {
                DecodeResultCard(
                    decode = decode,
                    applyEnabled = !isApplied,
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun DecodeResultCard(
    decode: VinDecodeResult,
    applyEnabled: Boolean,
    onApply: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DecodeRow(R.string.profile_vin_manufacturer, decode.manufacturer)
            DecodeRow(R.string.profile_vin_country, decode.country)
            DecodeRow(R.string.profile_vin_year, decode.year?.toString())
            DecodeRow(R.string.profile_vin_region, decode.region.localizedName())
            DecodeRow(R.string.profile_vin_model, decode.modelHint)

            if (decode.manufacturer != null || decode.year != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onApply,
                    enabled = applyEnabled,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.profile_vin_apply))
                }
            }
        }
    }
}

@Composable
private fun DecodeRow(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ========================================================================
// Секция номера кузова
// ========================================================================

@Composable
private fun BodyNumberSection(
    bodyNumber: String,
    onBodyNumberChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_section_body),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.profile_body_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = bodyNumber,
                onValueChange = onBodyNumberChange,
                label = { Text(stringResource(R.string.profile_body_label)) },
                placeholder = { Text(stringResource(R.string.profile_body_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    if (bodyNumber.isNotEmpty()) {
                        IconButton(onClick = { onBodyNumberChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.profile_vin_clear),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ========================================================================
// Секция ручного ввода
// ========================================================================

@Composable
private fun ManualFieldsSection(
    state: CarProfileUiState,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_section_manual),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.profile.make.orEmpty(),
                onValueChange = onMakeChange,
                label = { Text(stringResource(R.string.profile_make_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.profile.model.orEmpty(),
                onValueChange = onModelChange,
                label = { Text(stringResource(R.string.profile_model_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.profile.year?.toString().orEmpty(),
                onValueChange = onYearChange,
                label = { Text(stringResource(R.string.profile_year_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ========================================================================
// Секция региона
// ========================================================================

@Composable
private fun RegionSection(
    selected: Region,
    onSelect: (Region) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_section_region),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.profile_region_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Region.entries
                .filter { it != Region.UNKNOWN }
                .chunked(3)
                .forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { region ->
                            FilterChip(
                                selected = region == selected,
                                onClick = { onSelect(region) },
                                label = { Text(region.localizedName()) },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun Region.localizedName(): String = stringResource(
    when (this) {
        Region.US -> R.string.region_us
        Region.EU -> R.string.region_eu
        Region.ASIA -> R.string.region_asia
        Region.RU -> R.string.region_ru
        Region.OTHER -> R.string.region_other
        Region.UNKNOWN -> R.string.region_unknown
    }
)

// ========================================================================
// Секция единиц измерения
// ========================================================================

@Composable
private fun UnitsSection(
    override: UnitSystem?,
    effective: UnitSystem,
    onSelect: (UnitSystem?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_section_units),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.profile_units_effective,
                    stringResource(
                        if (effective == UnitSystem.METRIC) R.string.units_metric
                        else R.string.units_imperial,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = override == null,
                    onClick = { onSelect(null) },
                    label = { Text(stringResource(R.string.units_auto)) },
                )
                FilterChip(
                    selected = override == UnitSystem.METRIC,
                    onClick = { onSelect(UnitSystem.METRIC) },
                    label = { Text(stringResource(R.string.units_metric)) },
                )
                FilterChip(
                    selected = override == UnitSystem.IMPERIAL,
                    onClick = { onSelect(UnitSystem.IMPERIAL) },
                    label = { Text(stringResource(R.string.units_imperial)) },
                )
            }
        }
    }
}

// ========================================================================
// Кнопка очистки
// ========================================================================

@Composable
private fun ClearButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.profile_clear_button))
    }
}

// ========================================================================
// Вспомогательные функции
// ========================================================================

private fun getStringSafe(resId: Int): String = CarPulseApp.instance.getString(resId)

@Composable
private fun VinValidation.Invalid.toUserMessage(): String {
    val messages = mutableListOf<String>()
    for (reason in reasons) {
        messages += when (reason) {
            VinValidation.Reason.EMPTY -> stringResource(R.string.vin_error_empty)
            VinValidation.Reason.WRONG_LENGTH -> stringResource(R.string.vin_error_length)
            VinValidation.Reason.ILLEGAL_CHARS -> stringResource(R.string.vin_error_chars)
        }
    }
    return messages.joinToString(separator = "; ")
}