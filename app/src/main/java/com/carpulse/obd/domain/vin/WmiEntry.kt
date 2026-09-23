package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.Region

/**
 * Запись в базе WMI. wmi может быть 3, 4, 5 или 6 символов —
 * чем длиннее, тем точнее модель (используется longest-prefix match).
 */
data class WmiEntry(
    val wmi: String,
    val manufacturer: String,
    val country: String,
    val region: Region,
)