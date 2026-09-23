package com.carpulse.obd.domain.units

import androidx.annotation.StringRes
import com.carpulse.obd.R

/**
 * Тип физической величины. Определяет, какие единицы показывать
 * в метрической и имперской системах.
 *
 * Для величин, которые не конвертируются (проценты, вольты, обороты),
 * metricRes == imperialRes.
 *
 * ВАЖНО: значения min/max/warn/danger в Pid хранятся в метрических
 * единицах (metricRes). Конвертация в имперские — задача UnitConverter.
 */
enum class UnitType(
    @StringRes val metricRes: Int,
    @StringRes val imperialRes: Int,
) {
    NONE(R.string.unit_none, R.string.unit_none),
    PERCENT(R.string.unit_percent, R.string.unit_percent),
    CELSIUS(R.string.unit_celsius, R.string.unit_fahrenheit),
    KPA(R.string.unit_kpa, R.string.unit_psi),
    RPM(R.string.unit_rpm, R.string.unit_rpm),
    SPEED(R.string.unit_speed_kmh, R.string.unit_speed_mph),
    VOLT(R.string.unit_volt, R.string.unit_volt),
    KM(R.string.unit_km, R.string.unit_miles),
    MAF(R.string.unit_grams_per_sec, R.string.unit_pounds_per_min),
    PA(R.string.unit_pa, R.string.unit_psi),
    DEGREE(R.string.unit_degree, R.string.unit_degree),
    SECOND(R.string.unit_second, R.string.unit_second),
    MINUTE(R.string.unit_minute, R.string.unit_minute),
}