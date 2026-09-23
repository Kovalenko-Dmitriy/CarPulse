package com.carpulse.obd.data.trips

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO для поездок и их точек.
 *
 * Принципы:
 *  - observeTrips() / observePoints() возвращают Flow — Room сам перезапросит
 *    при изменении таблицы. UI подписывается один раз.
 *  - Все write-операции suspend — вызываются из корутин.
 *  - insertPoints() — @Insert с REPLACE, чтобы можно было безопасно
 *    повторно вставлять батчи (на случай гонок при flush).
 */
@Dao
interface TripDao {

    // ---------------------------------------------------------------------
    //  TRIPS
    // ---------------------------------------------------------------------

    /**
     * Создать новую поездку. Возвращает сгенерированный id.
     * Вызывается из TripRepository.beginTrip().
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrip(trip: TripEntity): Long

    /**
     * Обновить поездку целиком.
     * Используется в endTrip() и в updateTripStats().
     */
    @Update
    suspend fun updateTrip(trip: TripEntity)

    /**
     * Обновить только агрегаты (distance / avg / max / pointsCount).
     * Отдельный точечный UPDATE — дешевле, чем читать+писать всю строку.
     */
    @Query(
        """
        UPDATE trips
        SET distance_meters = :distanceMeters,
            avg_speed_kmh   = :avgSpeedKmh,
            max_speed_kmh   = :maxSpeedKmh,
            points_count    = :pointsCount
        WHERE id = :tripId
        """
    )
    suspend fun updateTripStats(
        tripId: Long,
        distanceMeters: Double,
        avgSpeedKmh: Double,
        maxSpeedKmh: Double,
        pointsCount: Int
    )

    /**
     * Закрыть поездку: проставить end_time и status = finished.
     * Вызывается из TripRepository.endTrip().
     */
    @Query(
        """
        UPDATE trips
        SET end_time = :endTime,
            status   = :status
        WHERE id = :tripId
        """
    )
    suspend fun finishTrip(
        tripId: Long,
        endTime: Long,
        status: String = TripEntity.STATUS_FINISHED
    )

    /**
     * Поставить статус вручную (например, пометить «битую» поездку как finished
     * после recovery).
     */
    @Query("UPDATE trips SET status = :status WHERE id = :tripId")
    suspend fun setTripStatus(tripId: Long, status: String)

    /**
     * Найти активную поездку (status = active). Должна быть 0 или 1.
     * LIMIT 1 — на случай рассинхрона; в норме больше одной быть не может.
     */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'active'
        ORDER BY start_time DESC
        LIMIT 1
        """
    )
    suspend fun getActiveTrip(): TripEntity?

    /** То же, но реактивно — для UI, чтобы показывать «идёт запись». */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'active'
        ORDER BY start_time DESC
        LIMIT 1
        """
    )
    fun observeActiveTrip(): Flow<TripEntity?>

    /** Поездка по id. */
    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: Long): TripEntity?

    /** Реактивно — поездка по id (для экрана деталей). */
    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observeTripById(tripId: Long): Flow<TripEntity?>

    /**
     * Все завершённые поездки, свежие сверху.
     * Активные исключаем — для истории они не нужны.
     */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'finished'
        ORDER BY start_time DESC
        """
    )
    fun observeTrips(): Flow<List<TripEntity>>

    /** Последние N поездок — если понадобится быстрый превью-список. */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'finished'
        ORDER BY start_time DESC
        LIMIT :limit
        """
    )
    fun observeRecentTrips(limit: Int): Flow<List<TripEntity>>

    /**
     * Полный список для статистики (пробег, средняя скорость).
     * Ограничивать не нужно — поездок за годы не так много.
     */
    @Query("SELECT * FROM trips WHERE status = 'finished' ORDER BY start_time ASC")
    suspend fun getAllFinishedTrips(): List<TripEntity>

    /** Удалить поездку. Точки уйдут каскадом (FK CASCADE). */
    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)

    /** Удалить все поездки. Точки — каскадом. */
    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    // ---------------------------------------------------------------------
    //  TRIP POINTS
    // ---------------------------------------------------------------------

    /**
     * Батчевая вставка точек. REPLACE — чтобы повторный flush
     * того же батча не падал на UNIQUE (на всякий случай).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TripPointEntity>)

    /** Одна точка — на случай, если батч из одного элемента. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TripPointEntity): Long

    /** Все точки поездки, по возрастанию времени. */
    @Query(
        """
        SELECT * FROM trip_points
        WHERE trip_id = :tripId
        ORDER BY timestamp ASC
        """
    )
    suspend fun getPoints(tripId: Long): List<TripPointEntity>

    /** Реактивно — для отрисовки трека на карте. */
    @Query(
        """
        SELECT * FROM trip_points
        WHERE trip_id = :tripId
        ORDER BY timestamp ASC
        """
    )
    fun observePoints(tripId: Long): Flow<List<TripPointEntity>>

    /** Кол-во точек в поездке (для отладки и счётчиков). */
    @Query("SELECT COUNT(*) FROM trip_points WHERE trip_id = :tripId")
    suspend fun getPointsCount(tripId: Long): Int

    /** Удалить все точки поездки (если понадобится без удаления поездки). */
    @Query("DELETE FROM trip_points WHERE trip_id = :tripId")
    suspend fun deletePoints(tripId: Long)

    // ---------------------------------------------------------------------
    //  ТРАНЗАКЦИИ
    // ---------------------------------------------------------------------

    /**
     * Атомарно: закрыть поездку + записать последний батч точек.
     * Нужно, чтобы при endTrip() не осталось «поездка закрыта, а хвост точек потерян».
     */
    @Transaction
    suspend fun finishTripWithPoints(
        tripId: Long,
        endTime: Long,
        finalPoints: List<TripPointEntity>
    ) {
        if (finalPoints.isNotEmpty()) {
            insertPoints(finalPoints)
        }
        finishTrip(tripId, endTime, TripEntity.STATUS_FINISHED)
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
}