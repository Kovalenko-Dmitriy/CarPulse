package com.carpulse.obd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction



@Dao
interface DtcDao {



    @Query("SELECT * FROM dtc_codes WHERE code LIKE :query || '%' ORDER BY code LIMIT 50")
    suspend fun searchByCode(query: String): List<DtcCodeEntity>

    @Query("SELECT * FROM dtc_codes WHERE category = :category ORDER BY code")
    suspend fun getByCategory(category: String): List<DtcCodeEntity>

    @Query("SELECT * FROM dtc_codes WHERE mil = 1 ORDER BY code")
    suspend fun getMilCodes(): List<DtcCodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(code: DtcCodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodes(codes: List<DtcCodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCauses(causes: List<DtcCauseEntity>)

    @Transaction
    suspend fun insertFull(codes: List<DtcCodeEntity>, causes: List<DtcCauseEntity>) {
        insertCodes(codes)
        insertCauses(causes)
    }

    @Query("SELECT * FROM dtc_codes WHERE code = :code LIMIT 1")
    suspend fun getCode(code: String): DtcCodeEntity?

    @Query("SELECT * FROM dtc_causes WHERE code = :code ORDER BY " +
            "CASE likelihood WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END")
    suspend fun getCauses(code: String): List<DtcCauseEntity>

    @Query("SELECT * FROM dtc_codes WHERE code LIKE :prefix || '%' ORDER BY code")
    suspend fun getCodesByPrefix(prefix: String): List<DtcCodeEntity>

    @Query("SELECT COUNT(*) FROM dtc_codes")
    suspend fun countCodes(): Int

    @Query("SELECT COUNT(*) FROM dtc_causes")
    suspend fun countCauses(): Int
}