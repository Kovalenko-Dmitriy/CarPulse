package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.Region

/**
 * Запись в базе WMI (World Manufacturer Identifier).
 *
 * @param wmi          код производителя (3–6 символов, обычно 3)
 * @param manufacturer человекочитаемое название производителя
 * @param country      страна производства
 * @param region       регион (для дефолтных единиц и подбора PID)
 * @param markets      список рынков, где продаётся авто (для UI-подсказок)
 */
data class WmiEntry(
    val wmi: String,
    val manufacturer: String,
    val country: String,
    val region: Region,
    val markets: List<String> = emptyList(),
)