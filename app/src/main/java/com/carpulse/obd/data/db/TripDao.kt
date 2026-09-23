package com.carpulse.obd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO для поездок, точек и истории DTC.
 *
 * Источник истины по схеме — domain/TripRepository.
 * Здесь собраны:
 *  - методы, которые зовёт TripRepository (insertTrip, finishTrip,
 *    insertPoints, observeTrips, observePoints, getActiveTrip, updateTripStats);
 *  - методы, которые уже использовались в проекте (upsertTrip, insertPoint,
 *    allTrips, getTrip, points, deleteTrip);
 *  - DTC-методы (insertDtc, dtcHistory, clearDtcHistory).
 *
 * Принципы:
 *  - observe* возвращают Flow — Room сам перезапросит при изменении таблицы.
 *  - Все write-операции suspend — вызываются из корутин.
 *  - insertPoints — REPLACE, чтобы повторный flush батча не падал на UNIQUE.
 */
@Dao
interface TripDao {

    // =====================================================================
    //  TRIPS — запись
    // =====================================================================

    /**
     * Создать новую поездку. Возвращает сгенерированный id.
     * Вызывается из TripRepository.beginTrip().
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrip(trip: TripEntity): Long

    /**
     * Upsert поездки — для случаев, когда id уже известен.
     * Сохранён от исходной схемы, используется старым кодом.
     */
    @Upsert
    suspend fun upsertTrip(trip: TripEntity): Long

    /** Обновить поездку целиком. */
    @Update
    suspend fun updateTrip(trip: TripEntity)

    /**
     * Обновить только агрегаты (distance / avg / max / rpm / coolant / fuel / points).
     * Отдельный точечный UPDATE — дешевле, чем читать+писать всю строку.
     * Вызывается из TripRepository.endTrip() через finishTrip().
     */
    @Query(
        """
        UPDATE trips
        SET distanceKm   = :distanceKm,
            avgSpeed     = :avgSpeed,
            maxSpeed     = :maxSpeed,
            avgRpm       = :avgRpm,
            maxCoolant   = :maxCoolant,
            fuelUsedL    = :fuelUsedL,
            pointCount   = :pointCount
        WHERE id = :tripId
        """
    )
    suspend fun updateTripStats(
        tripId: Long,
        distanceKm: Float,
        avgSpeed: Float,
        maxSpeed: Float,
        avgRpm: Float,
        maxCoolant: Float,
        fuelUsedL: Float,
        pointCount: Int
    )

    /**
     * Закрыть поездку: проставить end_time, status = finished
     * и все агрегаты одним UPDATE.
     *
     * Сигнатура совпадает с вызовом в domain/TripRepository.endTrip().
     */
    @Query(
        """
        UPDATE trips
        SET endTime     = :end,
            status      = 'finished',
            distanceKm  = :distance,
            maxSpeed    = :maxSpeed,
            avgSpeed    = :avgSpeed,
            pointCount  = :pointCount
        WHERE id = :tripId
        """
    )
    suspend fun finishTrip(
        tripId: Long,
        end: Long,
        distance: Float,
        maxSpeed: Float,
        avgSpeed: Float,
        pointCount: Int
    )

    /**
     * Закрыть поездку с полным набором агрегатов, включая avgRpm,
     * maxCoolant и fuelUsedL. Используется из TripRepository, когда
     * FuelCalculator активен и данные RPM/coolant/расхода накоплены.
     */
    @Query(
        """
        UPDATE trips
        SET endTime     = :end,
            status      = 'finished',
            distanceKm  = :distance,
            maxSpeed    = :maxSpeed,
            avgSpeed    = :avgSpeed,
            avgRpm      = :avgRpm,
            maxCoolant  = :maxCoolant,
            fuelUsedL   = :fuelUsedL,
            pointCount  = :pointCount
        WHERE id = :tripId
        """
    )
    suspend fun finishTripFull(
        tripId: Long,
        end: Long,
        distance: Float,
        maxSpeed: Float,
        avgSpeed: Float,
        avgRpm: Float,
        maxCoolant: Float,
        fuelUsedL: Float,
        pointCount: Int
    )

    /** Поставить статус вручную (например, при recovery «висячей» поездки). */
    @Query("UPDATE trips SET status = :status WHERE id = :tripId")
    suspend fun setTripStatus(tripId: Long, status: String)

    // =====================================================================
    //  TRIPS — чтение
    // =====================================================================

    /**
     * Найти активную поездку. Должна быть 0 или 1.
     * LIMIT 1 — на случай рассинхрона; в норме больше одной быть не может.
     */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'active'
        ORDER BY startTime DESC
        LIMIT 1
        """
    )
    suspend fun getActiveTrip(): TripEntity?

    /** То же, но реактивно — для UI, чтобы показывать «идёт запись». */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'active'
        ORDER BY startTime DESC
        LIMIT 1
        """
    )
    fun observeActiveTrip(): Flow<TripEntity?>

    /** Поездка по id. */
    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTrip(id: Long): TripEntity?

    /** Реактивно — поездка по id (для экрана деталей). */
    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    fun observeTripById(id: Long): Flow<TripEntity?>

    /**
     * Все поездки, свежие сверху.
     * Не фильтруем по status: TripRepository.activeTrip сам вычисляется
     * из этого Flow через фильтр it.endTime == null.
     */
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    /** Синхронный список всех поездок — для экспорта/отчётов. */
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    suspend fun allTrips(): List<TripEntity>

    /** Последние N поездок. */
    @Query(
        """
        SELECT * FROM trips
        ORDER BY startTime DESC
        LIMIT :limit
        """
    )
    fun observeRecentTrips(limit: Int): Flow<List<TripEntity>>

    /** Удалить поездку. Точки уйдут каскадом (FK CASCADE). */
    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    /** Удалить все поездки. Точки — каскадом. */
    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    // =====================================================================
    //  TRIP POINTS — запись
    // =====================================================================

    /** Одна точка — на случай, если батч из одного элемента. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TripPointEntity)

    /**
     * Батчевая вставка точек. REPLACE — чтобы повторный flush
     * того же батча не падал на UNIQUE.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TripPointEntity>)

    // =====================================================================
    //  TRIP POINTS — чтение
    // =====================================================================

    /** Все точки поездки, по возрастанию времени. */
    @Query("SELECT * FROM trip_points WHERE tripId = :id ORDER BY ts")
    suspend fun points(id: Long): List<TripPointEntity>

    /** Реактивно — для отрисовки трека на карте. */
    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY ts")
    fun observePoints(tripId: Long): Flow<List<TripPointEntity>>

    /** Кол-во точек в поездке (для отладки и счётчиков). */
    @Query("SELECT COUNT(*) FROM trip_points WHERE tripId = :tripId")
    suspend fun getPointsCount(tripId: Long): Int

    /** Удалить все точки поездки (если понадобится без удаления поездки). */
    @Query("DELETE FROM trip_points WHERE tripId = :tripId")
    suspend fun deletePoints(tripId: Long)

    // =====================================================================
    //  ТРАНЗАКЦИИ
    // =====================================================================

    /**
     * Атомарно: закрыть поездку + записать последний батч точек.
     * Нужно, чтобы при endTrip() не осталось «поездка закрыта,
     * а хвост точек потерян».
     */
    @Transaction
    suspend fun finishTripWithPoints(
        tripId: Long,
        end: Long,
        distance: Float,
        maxSpeed: Float,
        avgSpeed: Float,
        avgRpm: Float,
        maxCoolant: Float,
        fuelUsedL: Float,
        pointCount: Int,
        finalPoints: List<TripPointEntity>
    ) {
        if (finalPoints.isNotEmpty()) {
            insertPoints(finalPoints)
        }
        finishTripFull(
            tripId = tripId,
            end = end,
            distance = distance,
            maxSpeed = maxSpeed,
            avgSpeed = avgSpeed,
            avgRpm = avgRpm,
            maxCoolant = maxCoolant,
            fuelUsedL = fuelUsedL,
            pointCount = pointCount
        )
    }

    /**
     * Атомарно: удалить поездку вместе с точками.
     * CASCADE в Room работает и без этого, но @Transaction гарантирует,
     * что не будет промежуточного состояния между двумя удалениями.
     */
    @Transaction
    suspend fun deleteTripWithPoints(tripId: Long) {
        deletePoints(tripId)
        deleteTrip(tripId)
    }

    // =====================================================================
    //  DTC — история ошибок
    // =====================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtc(dtc: DtcEntity)

    @Query("SELECT * FROM dtc_history ORDER BY ts DESC")
    suspend fun dtcHistory(): List<DtcEntity>

    @Query("DELETE FROM dtc_history")
    suspend fun clearDtcHistory()
}