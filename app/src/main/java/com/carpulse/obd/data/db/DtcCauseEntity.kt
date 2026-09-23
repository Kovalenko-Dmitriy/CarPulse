package com.carpulse.obd.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dtc_causes",
    foreignKeys = [
        ForeignKey(
            entity = DtcCodeEntity::class,
            parentColumns = ["code"],
            childColumns = ["code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("code"), Index("likelihood")]
)
data class DtcCauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val causeId: String,
    val likelihood: String,                    // "high" / "medium" / "low"
    val labelEn: String,
    val labelDe: String,
    val labelRu: String? = null
)