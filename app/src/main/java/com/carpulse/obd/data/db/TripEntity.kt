package com.carpulse.obd.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна поездка (сессия трекинга).
 *
 * Поля собраны из двух источников:
 *  - агрегаты, которые реально считает TripRepository (distanceKm, avgSpeed,
 *    maxSpeed, avgRpm, maxCoolant, fuelUsedL);
 *  - служебные поля, нужные для корректной работы активной поездки
 *    (endTime: Long?, status) и для UI без JOIN с trip_points
 *    (startLat, startLon, pointCount).
 *
 * Жизненный цикл:
 *  1. TripRepository.beginTrip() создаёт запись со status = active,
 *     endTime = null, агрегаты = 0.
 *  2. Пока идёт запись, TripRepository.onLocation() копит агрегаты
 *     в памяти и периодически флашит точки батчами.
 *  3. TripRepository.endTrip() проставляет endTime, status = finished
 *     и все агрегаты одним UPDATE через TripDao.finishTrip().
 *
 * Индексы:
 *  - status — поиск активной поездки (TripDao.getActiveTrip);
 *  - startTime — сортировка истории;
 *  - (status, startTime) — выборка «последняя завершённая» одним индексом.
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

    /** Время старта поездки, epoch millis. */
    @ColumnInfo(name = "startTime")
    val startTime: Long,

    /**
     * Время завершения поездки, epoch millis.
     * null — поездка ещё идёт. Именно это поле, а не status,
     * используется в TripRepository.activeTrip для фильтра.
     * status добавлен для индекса и для явного состояния.
     */
    @ColumnInfo(name = "endTime")
    val endTime: Long? = null,

    /** Координата старта — для отрисовки в списке без JOIN. */
    @ColumnInfo(name = "startLat")
    val startLat: Double = 0.0,

    /** Координата старта — для отрисовки в списке без JOIN. */
    @ColumnInfo(name = "startLon")
    val startLon: Double = 0.0,

    /** Пробег поездки, км. */
    @ColumnInfo(name = "distanceKm", defaultValue = "0")
    val distanceKm: Float = 0f,

    /** Средняя скорость, км/ч. */
    @ColumnInfo(name = "avgSpeed", defaultValue = "0")
    val avgSpeed: Float = 0f,

    /** Максимальная мгновенная скорость, км/ч. */
    @ColumnInfo(name = "maxSpeed", defaultValue = "0")
    val maxSpeed: Float = 0f,

    /** Средние обороты за поездку, об/мин. */
    @ColumnInfo(name = "avgRpm", defaultValue = "0")
    val avgRpm: Float = 0f,

    /** Максимальная температура ОЖ за поездку, °C. */
    @ColumnInfo(name = "maxCoolant", defaultValue = "0")
    val maxCoolant: Float = 0f,

    /**
     * Израсходовано топлива за поездку, литры.
     * Считается через FuelCalculator (MAF или speed-density).
     * Если ни MAF, ни MAP не поддерживаются — остаётся 0.
     */
    @ColumnInfo(name = "fuelUsedL", defaultValue = "0")
    val fuelUsedL: Float = 0f,

    /**
     * Сколько точек записано в trip_points за эту поездку.
     * Денормализация для UI: показать «N точек» без COUNT(*).
     */
    @ColumnInfo(name = "pointCount", defaultValue = "0")
    val pointCount: Int = 0,

    /**
     * Статус поездки: [STATUS_ACTIVE] или [STATUS_FINISHED].
     * Строка, а не enum — Room не требует TypeConverter,
     * и в БД значение читаемо глазами.
     */
    @ColumnInfo(name = "status", defaultValue = STATUS_ACTIVE)
    val status: String = STATUS_ACTIVE

) {
    val isActive: Boolean
        get() = status == STATUS_ACTIVE

    val isFinished: Boolean
        get() = status == STATUS_FINISHED

    /** Длительность, мс. null, если поездка ещё идёт. */
    val durationMillis: Long?
        get() = endTime?.let { it - startTime }

    /** Средний расход, л/100 км. null, если пробег < 0.1 км. */
    val avgConsumptionL100: Float?
        get() = if (distanceKm > 0.1f) fuelUsedL / distanceKm * 100f else null

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_FINISHED = "finished"
    }
}