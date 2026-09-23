package com.carpulse.obd.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна GPS-точка внутри поездки.
 *
 * Связь: [TripEntity] 1 --- N [TripPointEntity].
 * При удалении поездки все её точки удаляются каскадно (FK CASCADE).
 *
 * Имена полей сохранены от исходной схемы (ts, lat, lon, speed, rpm, coolant),
 * чтобы не ломать уже написанные запросы и UI. Новые поля (accuracyMeters,
 * engineLoad) — добавлены под TripRepository.onLocation().
 *
 * Индексы:
 *  - tripId — выборка всех точек поездки;
 *  - (tripId, ts) — упорядоченный проход по точкам одной поездки
 *    (TripDao.points делает WHERE tripId = ? ORDER BY ts).
 */
@Entity(
    tableName = "trip_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["tripId", "ts"]),
        Index(value = ["ts"])
    ]
)
data class TripPointEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** FK на trips.id. */
    @ColumnInfo(name = "tripId")
    val tripId: Long,

    /** Время фиксации точки, epoch millis. */
    @ColumnInfo(name = "ts")
    val ts: Long,

    /** Широта, градусы. */
    @ColumnInfo(name = "lat")
    val lat: Double,

    /** Долгота, градусы. */
    @ColumnInfo(name = "lon")
    val lon: Double,

    /** Мгновенная скорость, км/ч. */
    @ColumnInfo(name = "speed")
    val speed: Float,

    /** Обороты двигателя в момент точки, об/мин. */
    @ColumnInfo(name = "rpm")
    val rpm: Int,

    /** Температура ОЖ в момент точки, °C. */
    @ColumnInfo(name = "coolant")
    val coolant: Int,

    /**
     * Точность GPS в момент точки, метры.
     * null — если провайдер не сообщил (что редкость, но бывает).
     * Нужна, чтобы на карте можно было показать «плывущие» участки
     * и чтобы TripRepository мог отфильтровать мусор (accuracy > 30 м).
     */
    @ColumnInfo(name = "accuracyMeters")
    val accuracyMeters: Float? = null,

    /**
     * Нагрузка на двигатель в момент точки, %.
     * null — если PID 0104 не поддерживается.
     */
    @ColumnInfo(name = "engineLoad")
    val engineLoad: Float? = null

)