package com.carpulse.obd.ui.trips

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpulse.obd.CarPulseApp
import com.carpulse.obd.data.db.TripEntity
import com.carpulse.obd.data.db.TripPointEntity
import com.carpulse.obd.domain.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана Trips. Read-only:
 *  - показывает список поездок и активную поездку;
 *  - отдаёт точки выбранной поездки для карты;
 *  - отдаёт точки активной поездки для live-трека;
 *  - умеет удалить поездку.
 *
 * Не знает ни про GPS, ни про OBD, ни про сервис, ни про TripController.
 * Источник данных — TripRepository. Запись поездок управляется
 * автоматически через ui.trips.TripController по состоянию OBD.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val repo: TripRepository = (app as CarPulseApp).trips

    /** Все поездки, свежие сверху. */
    val trips: StateFlow<List<TripEntity>> = repo.trips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Активная поездка. null — ничего не пишем. */
    val activeTrip: StateFlow<TripEntity?> = repo.activeTrip
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /**
     * ID выбранной пользователем поездки для показа трека.
     * null — ничего не выбрано, карта показывает live-трек активной поездки
     * (если она есть) или пустое состояние.
     */
    private val _selectedTripId = MutableStateFlow<Long?>(null)
    val selectedTripId: StateFlow<Long?> = _selectedTripId.asStateFlow()

    /**
     * Точки выбранной поездки. Если ничего не выбрано — пустой список
     * без подписки на БД (flatMapLatest + flowOf).
     */
    val selectedPoints: StateFlow<List<TripPointEntity>> = _selectedTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repo.observePoints(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Точки активной поездки для live-трека на карте.
     * Если активной нет — пустой список.
     */
    val activePoints: StateFlow<List<TripPointEntity>> = activeTrip
        .flatMapLatest { t ->
            if (t == null) flowOf(emptyList())
            else repo.observePoints(t.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Идёт ли запись — для индикатора и заголовка на экране. */
    val isRecording: StateFlow<Boolean> = activeTrip
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    // ------------------------------------------------------------------
    //  Действия пользователя
    // ------------------------------------------------------------------

    /**
     * Выбрать поездку для показа трека.
     * Повторный клик по той же поездке сбрасывает выбор.
     */
    fun selectTrip(tripId: Long?) {
        _selectedTripId.value =
            if (_selectedTripId.value == tripId) null else tripId
    }

    /** Сбросить выбор — карта возвращается к live-треку активной поездки. */
    fun clearSelection() {
        _selectedTripId.value = null
    }

    /**
     * Удалить поездку вместе с точками.
     * Делает через TripRepository — там @Transaction deleteTripWithPoints.
     */
    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            repo.deleteTrip(tripId)
            if (_selectedTripId.value == tripId) {
                _selectedTripId.value = null
            }
        }
    }
}