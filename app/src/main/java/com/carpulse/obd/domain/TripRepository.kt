package com.carpulse.obd.domain

import android.location.Location
import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.db.AppDatabase
import com.carpulse.obd.data.db.TripEntity
import com.carpulse.obd.data.db.TripPointEntity
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Репозиторий поездок.
 *
 * Отвечает за:
 *  - старт / завершение поездки (beginTrip / endTrip);
 *  - накопление точек в буфере и батчевую запись в БД (flushBuffer);
 *  - подсчёт агрегатов: distanceKm, avgSpeed, maxSpeed, avgRpm, maxCoolant,
 *    fuelUsedL, pointCount;
 *  - публикацию Flow-списка поездок и активной поездки для UI.
 *
 * Не знает ничего про GPS-провайдер и про OBD-транспорт: ему на вход
 * приходит Location + значения PID (rpm, load, maf, map, iat, coolant).
 * Эту связку делает TripTrackingService.
 *
 * Гибридный расчёт топлива вынесен в FuelCalculator:
 *  - если есть MAF — считаем по нему (точно);
 *  - иначе, если есть MAP + IAT + RPM — считаем по speed-density;
 *  - иначе расход не считаем (fuelUsedL остаётся 0).
 *
 * Настройки (displacement, VE, тип топлива) читаются один раз при beginTrip(),
 * а не на каждой точке: DataStore — не hot path.
 */
class TripRepository(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore
) {

    private val dao = db.tripDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Все поездки, свежие сверху — для экрана Trips. */
    val trips: Flow<List<TripEntity>> = dao.observeTrips()

    /**
     * Активная поездка. Вычисляется фильтром по endTime == null —
     * именно поэтому TripDao.observeTrips() НЕ фильтрует по status.
     */
    val activeTrip: Flow<TripEntity?> = dao.observeTrips()
        .map { list -> list.firstOrNull { it.endTime == null } }

    // ---------------------------------------------------------------------
    //  Состояние текущей поездки
    // ---------------------------------------------------------------------

    @Volatile
    private var currentTripId: Long? = null

    /** Буфер точек до flush. */
    private val buffer = mutableListOf<TripPointEntity>()

    /** Предыдущая принятая точка — для haversine и фильтра «прыжков». */
    private var lastLocation: Location? = null

    /** Накопители агрегатов за текущую поездку. */
    private var distanceAccMeters = 0.0
    private var maxSpeedKmh = 0f
    private var speedSumKmh = 0.0
    private var speedSamples = 0
    private var rpmSum = 0.0
    private var rpmSamples = 0
    private var maxCoolantC = 0f
    private var fuelUsedL = 0f
    private var pointCount = 0

    /** Время последней точки — для dt в FuelCalculator. */
    private var lastFuelSampleAtMs = 0L

    /** Настройки, зафиксированные на старте поездки. */
    private var cfgDisplacementL = 1.6f
    private var cfgVe = 0.85f
    private var cfgFuelType = FuelType.GASOLINE

    /** Job периодического flush раз в 10 секунд. */
    private var flushJob: Job? = null

    // ---------------------------------------------------------------------
    //  Публичные геттеры
    // ---------------------------------------------------------------------

    fun currentTripIdOrNull(): Long? = currentTripId

    suspend fun isActive(): Boolean =
        currentTripId != null || dao.getActiveTrip() != null

    // ---------------------------------------------------------------------
    //  Старт поездки
    // ---------------------------------------------------------------------

    /**
     * Начать новую поездку или продолжить незавершённую.
     *
     * Если в БД есть активная поездка (например, приложение упало
     * в прошлый раз), продолжаем её, а не создаём новую.
     */
    suspend fun beginTrip(first: Location): Long {
        val active = dao.getActiveTrip()
        if (active != null) {
            currentTripId = active.id
            lastLocation = first
            lastFuelSampleAtMs = first.time
            FileLogger.write("TRIPS: продолжаю поездку #${active.id}")
            return active.id
        }

        // Читаем настройки один раз — до конца поездки используем эти значения.
        val s = settingsStore.settings.first()
        cfgDisplacementL = s.displacementL
        cfgVe = s.volumetricEfficiency
        cfgFuelType = runCatching { FuelType.valueOf(s.fuelType) }
            .getOrDefault(FuelType.GASOLINE)

        val id = dao.insertTrip(
            TripEntity(
                startTime = System.currentTimeMillis(),
                startLat = first.latitude,
                startLon = first.longitude,
                status = TripEntity.STATUS_ACTIVE
            )
        )
        currentTripId = id
        lastLocation = first
        lastFuelSampleAtMs = first.time

        // Периодический flush, чтобы хвост точек не терялся
        // при долгой поездке без набора 20 штук в буфере.
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushBuffer()
            }
        }

        FileLogger.write(
            "TRIPS: поездка #$id начата " +
                    "(disp=${cfgDisplacementL}L, VE=${cfgVe}, fuel=${cfgFuelType.name})"
        )
        return id
    }

    // ---------------------------------------------------------------------
    //  Точка от GPS + PID
    // ---------------------------------------------------------------------

    /**
     * Каждая новая локация от GpsTracker.
     *
     * @param loc      точка GPS (обязательна)
     * @param rpm      обороты двигателя (PID 010C), null если нет
     * @param load     нагрузка (PID 0104), %
     * @param maf      расход воздуха (PID 0110), г/с
     * @param map      давление во впуске (PID 010B), кПа
     * @param iat      температура впуска (PID 010F), °C
     * @param coolant  температура ОЖ (PID 0105), °C
     */
    fun onLocation(
        loc: Location,
        rpm: Int? = null,
        load: Float? = null,
        maf: Float? = null,
        map: Float? = null,
        iat: Float? = null,
        coolant: Float? = null
    ) {
        val tripId = currentTripId ?: return

        // Отбрасываем мусорные точки по точности GPS.
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_METERS) return

        val prev = lastLocation
        if (prev != null) {
            val dtMs = loc.time - prev.time
            if (dtMs <= 0L) return
            if (dtMs > MAX_GAP_MS) {
                // Долгий пропуск — не накручиваем дистанцию,
                // просто перезапускаем отсчёт от текущей точки.
                lastLocation = loc
                lastFuelSampleAtMs = loc.time
                return
            }

            // Дистанция: haversine + отсечка «прыжков» по скорости.
            val d = haversineMeters(
                prev.latitude, prev.longitude,
                loc.latitude, loc.longitude
            )
            val impliedSpeedMps = d / (dtMs / 1000.0)
            if (impliedSpeedMps <= MAX_SPEED_MPS) {
                distanceAccMeters += d
            }
        }

        // Мгновенная скорость км/ч: берём из Location, если есть,
        // иначе оцениваем по дистанции между точками.
        val kmh = if (loc.hasSpeed()) {
            loc.speed * 3.6f
        } else if (prev != null) {
            val dtMs = loc.time - prev.time
            if (dtMs > 0) {
                (haversineMeters(
                    prev.latitude, prev.longitude,
                    loc.latitude, loc.longitude
                ) / (dtMs / 1000.0) * 3.6).toFloat()
            } else 0f
        } else 0f

        if (kmh > maxSpeedKmh) maxSpeedKmh = kmh
        speedSumKmh += kmh
        speedSamples++

        // Обороты — в агрегаты и в точку.
        val rpmInt = rpm?.coerceAtLeast(0) ?: 0
        if (rpm != null) {
            rpmSum += rpm
            rpmSamples++
        }

        // Температура ОЖ — максимум за поездку.
        if (coolant != null && coolant > maxCoolantC) maxCoolantC = coolant

        // Расход топлива: гибридный FuelCalculator.
        val dtSec = if (lastFuelSampleAtMs > 0L) {
            ((loc.time - lastFuelSampleAtMs) / 1000.0).toFloat()
        } else 0f
        if (dtSec > 0f) {
            val lph = FuelCalculator.calculateLph(
                maf = maf,
                map = map,
                rpm = rpm?.toFloat(),
                iat = iat,
                displacement = cfgDisplacementL,
                ve = cfgVe,
                fuelType = cfgFuelType
            )
            if (lph != null && lph > 0f) {
                fuelUsedL += lph * dtSec / 3600f
            }
        }
        lastFuelSampleAtMs = loc.time

        // Точка в буфер.
        buffer += TripPointEntity(
            tripId = tripId,
            ts = loc.time,
            lat = loc.latitude,
            lon = loc.longitude,
            speed = kmh,
            rpm = rpmInt,
            coolant = coolant?.toInt() ?: 0,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            engineLoad = load
        )
        pointCount++
        lastLocation = loc

        if (buffer.size >= BATCH_SIZE) {
            scope.launch { flushBuffer() }
        }
    }

    // ---------------------------------------------------------------------
    //  Завершение поездки
    // ---------------------------------------------------------------------

    suspend fun endTrip() {
        val tripId = currentTripId
        flushJob?.cancel()
        flushJob = null

        if (tripId == null) {
            // Нет активной в памяти — но могла остаться в БД после падения.
            val active = dao.getActiveTrip() ?: return
            dao.finishTripFull(
                tripId = active.id,
                end = System.currentTimeMillis(),
                distance = active.distanceKm,
                maxSpeed = active.maxSpeed,
                avgSpeed = active.avgSpeed,
                avgRpm = active.avgRpm,
                maxCoolant = active.maxCoolant,
                fuelUsedL = active.fuelUsedL,
                pointCount = active.pointCount
            )
            return
        }

        val finalPoints = buffer.toList()
        buffer.clear()

        val avgSpeed = if (speedSamples > 0) (speedSumKmh / speedSamples).toFloat() else 0f
        val avgRpm = if (rpmSamples > 0) (rpmSum / rpmSamples).toFloat() else 0f

        dao.finishTripWithPoints(
            tripId = tripId,
            end = System.currentTimeMillis(),
            distance = (distanceAccMeters / 1000.0).toFloat(),
            maxSpeed = maxSpeedKmh,
            avgSpeed = avgSpeed,
            avgRpm = avgRpm,
            maxCoolant = maxCoolantC,
            fuelUsedL = fuelUsedL,
            pointCount = pointCount,
            finalPoints = finalPoints
        )

        FileLogger.write(
            "TRIPS: поездка #$tripId завершена " +
                    "(${String.format("%.2f", distanceAccMeters / 1000.0)} км, " +
                    "${String.format("%.2f", fuelUsedL)} л, " +
                    "точек: $pointCount)"
        )

        resetAccumulators()
    }

    // ---------------------------------------------------------------------
    //  Точки поездки для UI
    // ---------------------------------------------------------------------

    fun observePoints(tripId: Long): Flow<List<TripPointEntity>> =
        dao.observePoints(tripId)

    // ---------------------------------------------------------------------
    //  Удаление
    // ---------------------------------------------------------------------

    /** Удалить поездку вместе с точками (атомарно, через @Transaction в DAO). */
    suspend fun deleteTrip(tripId: Long) {
        val wasActive = currentTripId == tripId
        dao.deleteTripWithPoints(tripId)
        if (wasActive) {
            // Если удалили активную поездку — отменяем flush и сбрасываем
            // состояние, иначе flushBuffer продолжит писать в несуществующую
            // запись и точки уйдут в никуда (FK CASCADE не спасает от вставки).
            flushJob?.cancel()
            flushJob = null
            resetAccumulators()
        }
    }

    // ---------------------------------------------------------------------
    //  Внутреннее
    // ---------------------------------------------------------------------

    private suspend fun flushBuffer() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()
        try {
            dao.insertPoints(batch)
        } catch (e: Exception) {
            FileLogger.write("TRIPS: ошибка flushBuffer: ${e.message}")
        }
    }

    private fun resetAccumulators() {
        currentTripId = null
        buffer.clear()
        lastLocation = null
        distanceAccMeters = 0.0
        maxSpeedKmh = 0f
        speedSumKmh = 0.0
        speedSamples = 0
        rpmSum = 0.0
        rpmSamples = 0
        maxCoolantC = 0f
        fuelUsedL = 0f
        pointCount = 0
        lastFuelSampleAtMs = 0L
    }

    /** Haversine, метры. */
    private fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a))
        return r * c
    }

    companion object {
        /** Размер батча точек перед записью в БД. */
        private const val BATCH_SIZE = 20

        /** Периодический flush, если батч не набрался. */
        private const val FLUSH_INTERVAL_MS = 10_000L

        /** Точнее 30 м — считаем пригодной для трека. */
        private const val MAX_ACCURACY_METERS = 30f

        /** Пропуск > 60 сек — не накручиваем дистанцию. */
        private const val MAX_GAP_MS = 60_000L

        /** Отсечка «прыжков»: 70 м/с ≈ 252 км/ч. */
        private const val MAX_SPEED_MPS = 70.0
    }
}