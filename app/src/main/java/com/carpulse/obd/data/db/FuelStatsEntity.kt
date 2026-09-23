package com.carpulse.obd.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Агрегированная статистика расхода топлива.
 * Одна строка (id = 1), обновляется по мере поступления данных.
 */
@Entity(tableName = "fuel_stats")
data class FuelStatsEntity(
    @PrimaryKey val id: Int = 1,
    val totalDistanceKm: Float = 0f,
    val totalLiters: Float = 0f,
    val totalCost: Float = 0f,
    val lastUpdated: Long = 0L
)