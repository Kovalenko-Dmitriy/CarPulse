package com.carpulse.obd.domain.units

import androidx.annotation.StringRes

/**
 * Конвертер между базовыми (метрическими) единицами и единицами отображения.
 *
 * Все расчёты, сравнения и пороги в приложении работают в метрике.
 * Конвертер применяется ТОЛЬКО в момент показа значения пользователю.
 *
 * Пример:
 *   val converter = UnitConverter(UnitSystem.IMPERIAL)
 *   val displayValue = converter.convert(90f, UnitType.CELSIUS)  // → 194.0 °F
 *   val unitRes = converter.displayUnitRes(UnitType.CELSIUS)      // → R.string.unit_fahrenheit
 *
 * Экземпляр иммутабельный — можно создавать в @Composable через remember().
 */
class UnitConverter(private val system: UnitSystem) {

    /**
     * Метрическое значение → значение для отображения.
     *
     * @return null, если metricValue == null (нет данных от ELM327).
     */
    fun convert(metricValue: Float?, type: UnitType): Float? {
        if (metricValue == null) return null
        if (system == UnitSystem.METRIC) return metricValue
        return when (type) {
            UnitType.NONE,
            UnitType.PERCENT,
            UnitType.RPM,
            UnitType.VOLT,
            UnitType.DEGREE,
            UnitType.SECOND,
            UnitType.MINUTE,
                -> metricValue

            UnitType.CELSIUS -> metricValue * 9f / 5f + 32f
            UnitType.KPA -> metricValue * 0.1450377f
            UnitType.SPEED -> metricValue * 0.6213712f
            UnitType.KM -> metricValue * 0.6213712f
            UnitType.MAF -> metricValue * 0.1322774f      // г/с → lb/min
            UnitType.PA -> metricValue * 0.0001450377f     // Па → psi
        }
    }

    /**
     * Значение из UI → метрическое (например, при ручном вводе порогов).
     * Обратная операция к convert().
     */
    fun toMetric(displayValue: Float, type: UnitType): Float {
        if (system == UnitSystem.METRIC) return displayValue
        return when (type) {
            UnitType.CELSIUS -> (displayValue - 32f) * 5f / 9f
            UnitType.KPA -> displayValue / 0.1450377f
            UnitType.SPEED -> displayValue / 0.6213712f
            UnitType.KM -> displayValue / 0.6213712f
            UnitType.MAF -> displayValue / 0.1322774f
            UnitType.PA -> displayValue / 0.0001450377f
            else -> displayValue
        }
    }

    /**
     * Ресурс единицы измерения для текущей системы.
     */
    @StringRes
    fun displayUnitRes(type: UnitType): Int =
        if (system == UnitSystem.METRIC) type.metricRes else type.imperialRes

    /** Текущая система единиц — для отладки и логики в UI. */
    fun system(): UnitSystem = system
}