package com.carpulse.obd.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carpulse.obd.data.profile.CarProfileRepository
import com.carpulse.obd.domain.ecu.EcuResolutionResult
import com.carpulse.obd.domain.ecu.EcuResolver
import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.profile.Region
import com.carpulse.obd.domain.units.UnitSystem
import com.carpulse.obd.domain.vin.VinDecodeResult
import com.carpulse.obd.domain.vin.VinDecoder
import com.carpulse.obd.domain.vin.VinKind
import com.carpulse.obd.domain.vin.VinValidation
import com.carpulse.obd.domain.vin.VinValidator
import com.carpulse.obd.domain.vin.applyTo
import com.carpulse.obd.domain.vin.jdm.JdmDecodeResult
import com.carpulse.obd.domain.vin.jdm.JdmDecoder
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class CarProfileUiState(
    val profile: CarProfile = CarProfile(),
    val lastDecode: VinDecodeResult? = null,
    val isDecoding: Boolean = false,
    val isSaving: Boolean = false,
    val vinValidation: VinValidation? = null,
    val ecuResolution: EcuResolutionResult? = null,
    val vinKind: VinKind = VinKind.UNKNOWN,
    /** Результат декодирования JDM-номера кузова (車台番号). null — не вводили. */
    val jdmResult: JdmDecodeResult? = null,
)

sealed interface CarProfileEvent {
    data class ShowError(val messageRes: Int) : CarProfileEvent
    data class ShowMessage(val messageRes: Int) : CarProfileEvent
    data object ProfileCleared : CarProfileEvent
}

class CarProfileViewModel(
    private val decoder: VinDecoder,
    private val profileRepo: CarProfileRepository,
    private val ecuResolver: EcuResolver,
    private val jdmDecoder: JdmDecoder,
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
    private var resolveJob: Job? = null
    private var jdmDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = profileRepo.load()
            _state.update {
                it.copy(
                    profile = saved,
                    vinKind = when {
                        saved.vin != null -> VinValidator.classify(saved.vin)
                        saved.bodyNumber != null -> VinKind.BODY
                        else -> VinKind.UNKNOWN
                    },
                )
            }
            resolveEcu(saved)
        }

        profileRepo.profileFlow
            .onEach { saved ->
                if (!_state.value.isSaving && !_state.value.isDecoding) {
                    _state.value = _state.value.copy(profile = saved)
                }
            }
            .catch { e ->
                Log.e(TAG, "Неожиданная ошибка в profileFlow", e)
            }
            .launchIn(viewModelScope)
    }

    // ====================================================================
    // VIN
    // ====================================================================

    fun onVinEntered(rawVin: String, overwriteExisting: Boolean = false) {
        val trimmed = rawVin.trim().uppercase()

        val kind = if (trimmed.isEmpty()) VinKind.UNKNOWN
                   else VinValidator.classify(trimmed)
        val validation = if (trimmed.isEmpty()) null
                         else VinValidator.validate(trimmed)

        _state.value = _state.value.copy(
            profile = _state.value.profile.copy(
                vin = trimmed.ifEmpty { null },
                bodyNumber = null,
            ),
            vinValidation = validation,
            vinKind = kind,
            jdmResult = null,   // при вводе VIN JDM-результат неактуален
        )

        vinDebounceJob?.cancel()

        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(lastDecode = null, isDecoding = false)
            return
        }

        if (kind != VinKind.VIN) {
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
            resolveEcu(updatedProfile)
        }
    }

    fun applyDecodedVin(overwriteExisting: Boolean = true) {
        val decoded = _state.value.lastDecode ?: return
        val updated = decoded.applyTo(
            current = _state.value.profile,
            overwriteExisting = overwriteExisting,
        )
        _state.value = _state.value.copy(profile = updated)

        viewModelScope.launch {
            persistProfile(updated)
            resolveEcu(updated)
        }
    }

    // ====================================================================
    // Номер кузова (JDM / ВАЗ / ГАЗ)
    // ====================================================================

    /**
     * Пользователь ввёл номер кузова.
     *
     * Пытаемся найти его в JDM-базе (車台番号). Если нашли — показываем
     * карточку с предложением применить найденные данные.
     */
    fun onBodyNumberEntered(raw: String) {
        val trimmed = raw.trim().uppercase()

        _state.update {
            it.copy(
                profile = it.profile.copy(
                    bodyNumber = trimmed.ifEmpty { null },
                    vin = null,
                ),
                lastDecode = null,
                vinValidation = null,
                vinKind = if (trimmed.isEmpty()) VinKind.UNKNOWN else VinKind.BODY,
                jdmResult = null,   // сброс, пока не найдём
            )
        }

        scheduleSave(_state.value.profile)

        // Ищем в JDM-базе с debounce, чтобы не гонять на каждый символ.
        jdmDebounceJob?.cancel()

        if (trimmed.length < 3) return

        jdmDebounceJob = viewModelScope.launch {
            delay(JDM_DEBOUNCE_MS)
            val result = withContext(Dispatchers.Default) {
                jdmDecoder.decode(trimmed)
            }
            _state.update { it.copy(jdmResult = result) }
        }
    }

    /**
     * Применяет найденную JDM-запись к профилю:
     * марка, модель, год, регион заполняются автоматически.
     */
    fun applyJdmResult() {
        val jdm = _state.value.jdmResult?.entry ?: return

        val updated = _state.value.profile.copy(
            make = jdm.make,
            model = jdm.model,
            year = jdm.yearFrom,       // берём начало выпуска, пользователь может поправить
            region = jdm.region,
        )

        _state.update { it.copy(profile = updated) }

        viewModelScope.launch {
            persistProfile(updated)
            resolveEcu(updated)
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
        updateProfile(_state.value.profile.copy(region = region))
    }

    fun onUnitSystemChanged(system: UnitSystem?) {
        updateProfile(_state.value.profile.copy(unitSystemOverride = system))
    }

    fun onEcuSelected(ecuId: String?) {
        val updated = _state.value.profile.copy(ecuId = ecuId)
        _state.value = _state.value.copy(profile = updated)
        viewModelScope.launch { persistProfile(updated) }
    }

    // ====================================================================
    // Очистка
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
        val old = _state.value.profile
        _state.value = _state.value.copy(profile = newProfile)
        scheduleSave(newProfile)

        if (old.make != newProfile.make ||
            old.model != newProfile.model ||
            old.year != newProfile.year
        ) {
            resolveEcu(newProfile)
        }
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

    private fun resolveEcu(profile: CarProfile) {
        if (profile.ecuId != null) return

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            delay(RESOLVE_DEBOUNCE_MS)
            val result = withContext(Dispatchers.Default) {
                ecuResolver.resolve(profile)
            }
            _state.update { it.copy(ecuResolution = result) }
        }
    }

    companion object {
        private const val TAG = "CarProfileViewModel"
        private const val VIN_DEBOUNCE_MS = 500L
        private const val SAVE_DEBOUNCE_MS = 300L
        private const val RESOLVE_DEBOUNCE_MS = 200L
        private const val JDM_DEBOUNCE_MS = 300L
        private const val MIN_YEAR = 1950
        private const val MAX_YEAR = 2100
        private const val ERROR_SAVE_FAILED = com.carpulse.obd.R.string.error_profile_save_failed
    }
}
