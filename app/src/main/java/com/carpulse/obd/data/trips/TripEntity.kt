package com.carpulse.obd.data.trips

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна поездка (сессия трекинга).
 *
 * Жизненный цикл:
 *  1. [TripRepository.beginTrip] создаёт запись со status = [STATUS_ACTIVE],
 *     endTime = null, счётчики = 0.
 *  2. Пока идёт запись, [TripRepository.onLocation] периодически вызывает
 *     [TripDao.updateTripStats], обновляя distanceMeters / avgSpeedKmh / maxSpeedKmh.
 *  3. [TripRepository.endTrip] выставляет endTime и status = [STATUS_FINISHED].
 *
 * Если приложение убито во время активной поездки, при следующем старте
 * [TripDao.getActiveTrip] найдёт "висячую" запись и её можно будет корректно
 * закрыть (см. TripRepository.recoverActiveTrip).
 */
@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["status"]),
        Index(value = ["startTime"]),
        Index(value = ["status", "startTime"])
    ]
)
data class TripEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Время старта поездки, epoch millis (System.currentTimeMillis). */
    @ColumnInfo(name = "start_time")
    val startTime: Long,

    /** Время окончания поездки, epoch millis. null — поездка ещё идёт. */
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    /** Суммарная дистанция по haversine, метры. */
    @ColumnInfo(name = "distance_meters", defaultValue = "0")
    val distanceMeters: Double = 0.0,

    /** Средняя скорость, км/ч. Считается как distance / duration. */
    @ColumnInfo(name = "avg_speed_kmh", defaultValue = "0")
    val avgSpeedKmh: Double = 0.0,

    /** Максимальная мгновенная скорость, км/ч. */
    @ColumnInfo(name = "max_speed_kmh", defaultValue = "0")
    val maxSpeedKmh: Double = 0.0,

    /** Кол-во точек, попавших в эту поездку. Денормализация для UI. */
    @ColumnInfo(name = "points_count", defaultValue = "0")
    val pointsCount: Int = 0,

    /**
     * Статус: [STATUS_ACTIVE] или [STATUS_FINISHED].
     * Строка, а не enum — чтобы Room не требовал TypeConverter
     * и чтобы в БД было читаемо глазами.
     */
    @ColumnInfo(name = "status")
    val status: String = STATUS_ACTIVE
) {
    val isActive: Boolean
        get() = status == STATUS_ACTIVE

    val isFinished: Boolean
        get() = status == STATUS_FINISHED

    /** Длительность в миллисекундах. null, если поездка ещё идёт. */
    val durationMillis: Long?
        get() = endTime?.let { it - startTime }

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_FINISHED = "finished"
    }
}