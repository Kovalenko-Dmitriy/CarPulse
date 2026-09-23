package com.carpulse.obd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FuelDao {

    // ============================================================
    // Заправки
    // ============================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFillUp(f: FillUpEntity): Long

    @Query("SELECT * FROM fillups ORDER BY timestamp DESC")
    suspend fun allFillUps(): List<FillUpEntity>

    @Query("SELECT * FROM fillups WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun fillUpsSince(since: Long): List<FillUpEntity>

    @Query("SELECT * FROM fillups ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastFillUp(): FillUpEntity?

    @Query("SELECT SUM(liters) FROM fillups WHERE timestamp >= :since")
    suspend fun totalLitersSince(since: Long): Float?

    @Query("SELECT SUM(liters * pricePerLiter) FROM fillups WHERE timestamp >= :since")
    suspend fun totalCostSince(since: Long): Float?

    @Query("DELETE FROM fillups WHERE id = :id")
    suspend fun deleteFillUp(id: Long)

    @Query("DELETE FROM fillups")
    suspend fun clearAll()

    // ============================================================
    // Агрегаты
    // ============================================================

    @Upsert
    suspend fun upsertStats(s: FuelStatsEntity)

    @Query("SELECT * FROM fuel_stats WHERE id = 1")
    suspend fun getStats(): FuelStatsEntity?
}