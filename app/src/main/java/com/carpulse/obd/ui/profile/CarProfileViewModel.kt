package com.carpulse.obd.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carpulse.obd.data.profile.CarProfileRepository
import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.profile.Region
import com.carpulse.obd.domain.units.UnitSystem
import com.carpulse.obd.domain.vin.VinDecodeResult
import com.carpulse.obd.domain.vin.VinDecoder
import com.carpulse.obd.domain.vin.VinValidation
import com.carpulse.obd.domain.vin.applyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class CarProfileUiState(
    val profile: CarProfile = CarProfile(),
    val lastDecode: VinDecodeResult? = null,
    val isDecoding: Boolean = false,
    val isSaving: Boolean = false,
    val vinValidation: VinValidation? = null,
) {
    val hasUnsavedChanges: Boolean get() = isSaving
}

sealed interface CarProfileEvent {
    data class ShowError(val messageRes: Int) : CarProfileEvent
    data class ShowMessage(val messageRes: Int) : CarProfileEvent
    data object ProfileCleared : CarProfileEvent
}

class CarProfileViewModel(
    private val decoder: VinDecoder,
    private val profileRepo: CarProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CarProfileUiState())
    val state: StateFlow<CarProfileUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CarProfileEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val events: SharedFlow<CarProfileEvent> = _events.asSharedFlow()

    private var vinDebounceJob: Job? = null
    private var saveDebounceJob: Job? = null

    init {
        // Подписка на реактивный поток профиля из DataStore.
        //
        // catch здесь — второй эшелон защиты: repository уже обработал
        // IOException и отдал дефолт. Сюда попадают только неожиданные
        // исключения (например, ошибка парсинга enum). Логируем и
        // завершаем поток — не роняем корутину.
        profileRepo.profileFlow
            .onEach { saved ->
                // Не перезаписываем стейт, пока идёт запись или декодирование.
                // Иначе результат save() вернётся и затрёт введённый VIN.
                if (!_state.value.isSaving && !_state.value.isDecoding) {
                    _state.value = _state.value.copy(profile = saved)
                }
            }
            .catch { e ->
                Log.e(TAG, "Неожиданная ошибка в profileFlow", e)
                // Намеренно ничего не эмитим — поток просто завершается.
            }
            .launchIn(viewModelScope)
    }

    // ====================================================================
    // VIN
    // ====================================================================

    fun onVinEntered(rawVin: String, overwriteExisting: Boolean = false) {
        val trimmed = rawVin.trim().uppercase()

        val validation = if (trimmed.isEmpty()) null else VinValidatorHolder.validate(trimmed)
        _state.value = _state.value.copy(
            profile = _state.value.profile.copy(vin = trimmed.ifEmpty { null }),
            vinValidation = validation,
        )

        vinDebounceJob?.cancel()

        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(lastDecode = null, isDecoding = false)
            return
        }

        vinDebounceJob = viewModelScope.launch {
            delay(VIN_DEBOUNCE_MS)

            _state.value = _state.value.copy(isDecoding = true)

            val result = withContext(Dispatchers.Default) {
                decoder.decode(trimmed)
            }

            val updatedProfile = result.applyTo(
                current = _state.value.profile,
                overwriteExisting = overwriteExisting,
            )

            _state.value = _state.value.copy(
                profile = updatedProfile,
                lastDecode = result,
                isDecoding = false,
            )

            persistProfile(updatedProfile)
        }
    }

    fun applyDecodedVin(overwriteExisting: Boolean = true) {
        val decoded = _state.value.lastDecode ?: return
        val updated = decoded.applyTo(
            current = _state.value.profile,
            overwriteExisting = overwriteExisting,
        )
        _state.value = _state.value.copy(profile = updated)

        // persistProfile — suspend, поэтому оборачиваем в корутину.
        viewModelScope.launch {
            persistProfile(updated)
        }
    }

    // ====================================================================
    // Ручное редактирование полей
    // ====================================================================

    fun onMakeChanged(value: String) {
        updateProfile(_state.value.profile.copy(make = value.trim().ifEmpty { null }))
    }

    fun onModelChanged(value: String) {
        updateProfile(_state.value.profile.copy(model = value.trim().ifEmpty { null }))
    }

    fun onYearChanged(value: String) {
        val year = value.trim().toIntOrNull()
        val sanitized = year?.takeIf { it in MIN_YEAR..MAX_YEAR }
        updateProfile(_state.value.profile.copy(year = sanitized))
    }

    fun onRegionChanged(region: Region) {
        // Смена региона НЕ трогает unitSystemOverride: пользователь мог
        // явно выбрать метрику для японской машины. Если override == null,
        // effectiveUnitSystem пересчитается автоматически.
        updateProfile(_state.value.profile.copy(region = region))
    }

    fun onUnitSystemChanged(system: UnitSystem?) {
        updateProfile(_state.value.profile.copy(unitSystemOverride = system))
    }

    fun onEcuSelected(ecuId: String?) {
        updateProfile(_state.value.profile.copy(ecuId = ecuId))
    }

    // ====================================================================
    // Очистка профиля
    // ====================================================================

    fun clearProfile() {
        viewModelScope.launch {
            try {
                profileRepo.clear()
                _state.value = CarProfileUiState()
                _events.tryEmit(CarProfileEvent.ProfileCleared)
            } catch (e: IOException) {
                _events.tryEmit(CarProfileEvent.ShowError(ERROR_SAVE_FAILED))
            }
        }
    }

    // ====================================================================
    // Внутренние методы
    // ====================================================================

    private fun updateProfile(newProfile: CarProfile) {
        _state.value = _state.value.copy(profile = newProfile)
        scheduleSave(newProfile)
    }

    private fun scheduleSave(profile: CarProfile) {
        saveDebounceJob?.cancel()
        saveDebounceJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistProfile(profile)
        }
    }

    private suspend fun persistProfile(profile: CarProfile) {
        _state.value = _state.value.copy(isSaving = true)
        try {
            profileRepo.save(profile)
        } catch (e: IOException) {
            _events.emit(CarProfileEvent.ShowError(ERROR_SAVE_FAILED))
        } finally {
            _state.value = _state.value.copy(isSaving = false)
        }
    }

    companion object {
        private const val TAG = "CarProfileViewModel"
        private const val VIN_DEBOUNCE_MS = 500L
        private const val SAVE_DEBOUNCE_MS = 300L
        private const val MIN_YEAR = 1950
        private const val MAX_YEAR = 2100
        private const val ERROR_SAVE_FAILED = com.carpulse.obd.R.string.error_profile_save_failed
    }
}

private object VinValidatorHolder {
    fun validate(vin: String) = com.carpulse.obd.domain.vin.VinValidator.validate(vin)
}