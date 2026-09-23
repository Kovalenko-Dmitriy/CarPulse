package com.carpulse.obd.data.trips

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна GPS-точка внутри поездки.
 *
 * Связь: [TripEntity] 1 --- N [TripPointEntity].
 * При удалении поездки все её точки удаляются каскадно.
 *
 * Точки пишутся батчами (см. TripRepository.flushPendingPoints) — по 20 штук
 * или раз в 5 секунд, чтобы не долбить БД на каждое обновление FusedLocation.
 *
 * Индексы:
 *  - trip_id — выборка всех точек поездки;
 *  - (trip_id, timestamp) — упорядоченный проход по точкам одной поездки;
 *  - timestamp — на случай глобальных запросов «что было в это время».
 */
@Entity(
    tableName = "trip_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trip_id"]),
        Index(value = ["trip_id", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class TripPointEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** FK на trips.id. */
    @ColumnInfo(name = "trip_id")
    val tripId: Long,

    /** Широта, градусы. */
    @ColumnInfo(name = "latitude")
    val latitude: Double,

    /** Долгота, градусы. */
    @ColumnInfo(name = "longitude")
    val longitude: Double,

    /** Время фиксации точки, epoch millis. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /**
     * Точность по данным FusedLocationProvider, метры.
     * null, если провайдер не сообщил.
     */
    @ColumnInfo(name = "accuracy_meters")
    val accuracyMeters: Float? = null,

    /**
     * Мгновенная скорость, м/с. Может быть null, если устройство не отдаёт.
     * Храним в м/с как в источнике — конвертация в км/ч на уровне UI.
     */
    @ColumnInfo(name = "speed_mps")
    val speedMps: Float? = null,

    /**
     * Курс, градусы [0..360). null, если неизвестен.
     */
    @ColumnInfo(name = "bearing_degrees")
    val bearingDegrees: Float? = null,

    /**
     * Высота над уровнем моря, метры. null, если неизвестна.
     */
    @ColumnInfo(name = "altitude_meters")
    val altitudeMeters: Double? = null
) {
    /** Удобный геттер: скорость в км/ч. null, если speedMps == null. */
    val speedKmh: Double?
        get() = speedMps?.let { it * 3.6 }
}