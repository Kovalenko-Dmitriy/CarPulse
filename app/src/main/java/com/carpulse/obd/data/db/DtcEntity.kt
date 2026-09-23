package com.carpulse.obd.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dtc_history")
data class DtcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val code: String,
    val cleared: Boolean
)
