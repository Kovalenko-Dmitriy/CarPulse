package com.carpulse.obd.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Одна заправка. Используется для расчёта среднего расхода
 * методом "от заправки до заправки".
 */
@Entity(tableName = "fillups")
data class FillUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val liters: Float,
    val odometerKm: Float,
    val pricePerLiter: Float,
    val isFullTank: Boolean = true,

    /**
     * Расход, посчитанный между этой и предыдущей заправкой.
     * null — если это первая заправка или данных недостаточно.
     */
    val consumptionL100: Float? = null,

    val note: String? = null
)