package com.carpulse.obd.ui.fuel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpulse.obd.CarPulseApp
import com.carpulse.obd.data.db.AppDatabase
import com.carpulse.obd.data.db.FillUpEntity
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.domain.FuelCalculator
import com.carpulse.obd.domain.FuelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class FuelStatsViewModel(app: Application) : AndroidViewModel(app) {

    private val root = app as CarPulseApp
    private val db = AppDatabase.get(app)
    private val fuelDao = db.fuelDao()
    private val settingsStore = SettingsStore(app)

    // ============================================================
    // Мгновенный расход
    // ============================================================

    private val _instantLph = MutableStateFlow<Float?>(null)
    val instantLph: StateFlow<Float?> = _instantLph

    private val _instantL100 = MutableStateFlow<Float?>(null)
    val instantL100: StateFlow<Float?> = _instantL100

    // ============================================================
    // Полная статистика
    // ============================================================

    private val _stats = MutableStateFlow(FuelStats())
    val stats: StateFlow<FuelStats> = _stats

    // ============================================================
    // init
    // ============================================================

    init {
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            val fuelType = runCatching {
                FuelType.valueOf(s.fuelType)
            }.getOrDefault(FuelType.GASOLINE)

            val liveFlow = root.obd.live ?: return@launch
            liveFlow.collect { snapshot ->
                val lph = FuelCalculator.calculateLph(
                    maf = snapshot.values[Pid.MAF]?.value,
                    map = snapshot.values[Pid.MAP]?.value,
                    rpm = snapshot.values[Pid.RPM]?.value,
                    iat = snapshot.values[Pid.INTAKE]?.value,
                    displacement = s.displacementL,
                    ve = s.volumetricEfficiency,
                    fuelType = fuelType
                )
                _instantLph.value = lph

                val speed = snapshot.values[Pid.SPEED]?.value
                _instantL100.value = FuelCalculator.toL100km(lph, speed)
            }
        }

        refreshStats()
    }

    // ============================================================
    // Пересчёт всей статистики
    // ============================================================

    fun refreshStats() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val todayStart = startOfToday()
        val weekStart = startOfWeek()

        val todayAvg = avgConsumption(todayStart)
        val weekAvg = avgConsumption(weekStart)
        val totalAvg = avgConsumption(0L)

        val todayCost = fuelDao.totalCostSince(todayStart) ?: 0f
        val weekCost = fuelDao.totalCostSince(weekStart) ?: 0f
        val totalCost = fuelDao.totalCostSince(0L) ?: 0f

        val todayL = fuelDao.totalLitersSince(todayStart) ?: 0f
        val weekL = fuelDao.totalLitersSince(weekStart) ?: 0f
        val totalL = fuelDao.totalLitersSince(0L) ?: 0f

        val todayDist = distanceBetweenFills(todayStart)
        val weekDist = distanceBetweenFills(weekStart)
        val totalDist = distanceBetweenFills(0L)

        val s = settingsStore.settings.first()

        _stats.value = FuelStats(
            instantLph = _instantLph.value,
            instantL100 = _instantL100.value,

            todayDistanceKm = todayDist,
            weekDistanceKm = weekDist,
            totalDistanceKm = totalDist,

            todayAvgL100 = todayAvg,
            weekAvgL100 = weekAvg,
            totalAvgL100 = totalAvg,

            todayCost = todayCost,
            weekCost = weekCost,
            totalCost = totalCost,

            todayLiters = todayL,
            weekLiters = weekL,
            totalLiters = totalL,

            fuelPricePerLiter = s.fuelPricePerLiter,
            odometerKm = s.odometerKm
        )
    }

    // ============================================================
    // Расчёты
    // ============================================================

    /**
     * Средний расход л/100 км методом "от заправки до заправки".
     * Учитываются только заправки с полным баком.
     */
    private suspend fun avgConsumption(since: Long): Float? {
        val fills = fuelDao.fillUpsSince(since)
            .filter { it.isFullTank }
        if (fills.size < 2) return null

        val totalLiters = fills.drop(1).sumOf { it.liters.toDouble() }.toFloat()
        val distance = fills.last().odometerKm - fills.first().odometerKm

        if (distance < 1f) return null
        return (totalLiters / distance) * 100f
    }

    /** Пробег по одометру между заправками за период. */
    private suspend fun distanceBetweenFills(since: Long): Float {
        val fills = fuelDao.fillUpsSince(since)
        if (fills.size < 2) return 0f
        return (fills.last().odometerKm - fills.first().odometerKm).coerceAtLeast(0f)
    }

    // ============================================================
    // Добавление / удаление заправки
    // ============================================================

    fun addFillUp(
        liters: Float,
        odometerKm: Float,
        pricePerLiter: Float,
        isFullTank: Boolean = true
    ) = viewModelScope.launch {
        // Считаем расход между предыдущей и текущей заправкой
        val prev = fuelDao.lastFillUp()
        val consumption = if (prev != null && prev.isFullTank && isFullTank) {
            val distance = odometerKm - prev.odometerKm
            if (distance > 1f) (liters / distance) * 100f else null
        } else null

        fuelDao.insertFillUp(
            FillUpEntity(
                timestamp = System.currentTimeMillis(),
                liters = liters,
                odometerKm = odometerKm,
                pricePerLiter = pricePerLiter,
                isFullTank = isFullTank,
                consumptionL100 = consumption
            )
        )
        settingsStore.setOdometer(odometerKm)
        settingsStore.setFuelPrice(pricePerLiter)
        refreshStats()
    }

    fun deleteFillUp(id: Long) = viewModelScope.launch {
        fuelDao.deleteFillUp(id)
        refreshStats()
    }

    suspend fun allFillUps(): List<FillUpEntity> = fuelDao.allFillUps()

    // ============================================================
    // Периоды
    // ============================================================

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Полный снимок статистики расхода.
 */
data class FuelStats(
    val instantLph: Float? = null,
    val instantL100: Float? = null,

    val todayDistanceKm: Float = 0f,
    val weekDistanceKm: Float = 0f,
    val totalDistanceKm: Float = 0f,

    val todayAvgL100: Float? = null,
    val weekAvgL100: Float? = null,
    val totalAvgL100: Float? = null,

    val todayCost: Float = 0f,
    val weekCost: Float = 0f,
    val totalCost: Float = 0f,

    val todayLiters: Float = 0f,
    val weekLiters: Float = 0f,
    val totalLiters: Float = 0f,

    val fuelPricePerLiter: Float = 0f,
    val odometerKm: Float = 0f
)