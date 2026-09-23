package com.carpulse.obd.domain.profile

import com.carpulse.obd.domain.units.UnitSystem

/**
 * Регион происхождения автомобиля. Влияет:
 *  - на дефолтную систему единиц (только как подсказка, не как жёсткая привязка);
 *  - на подбор набора PID и ЭБУ в базе (итерация 2).
 *
 * ВАЖНО: регион — не то же самое, что страна производителя.
 * Например, Toyota, собранная в США (4T1...), имеет регион US, а не ASIA.
 */
enum class Region(val defaultUnitSystem: UnitSystem) {
    US(UnitSystem.IMPERIAL),
    EU(UnitSystem.METRIC),
    ASIA(UnitSystem.METRIC),
    RU(UnitSystem.METRIC),
    OTHER(UnitSystem.METRIC),    // Австралия, Латинская Америка, Африка
    UNKNOWN(UnitSystem.METRIC),
}