package com.carpulse.obd.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dtc_codes",
    indices = [
        Index("category"),
        Index("mil"),
        Index("emissionsRelevant")
    ]
)
data class DtcCodeEntity(
    @PrimaryKey val code: String,             // "P0301"
    val category: String,                      // "powertrain" / "body" / ...
    val titleEn: String,
    val titleDe: String,
    val titleRu: String? = null,
    val descriptionEn: String,
    val descriptionDe: String,
    val descriptionRu: String? = null,
    val affectedComponentsJson: String,        // JSON-массив
    val symptomsJson: String,                  // JSON-массив
    val difficulty: String,                    // "easy" / "medium" / "hard"
    val diyPossible: Boolean,
    val costMinEur: Int,
    val costMaxEur: Int,
    val hoursMin: Float,
    val hoursMax: Float,
    val mil: Boolean,                          // Check Engine
    val emissionsRelevant: Boolean
)