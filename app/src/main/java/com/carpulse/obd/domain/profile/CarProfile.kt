package com.carpulse.obd.domain.profile

import com.carpulse.obd.domain.units.UnitSystem

/**
 * Профиль автомобиля. Все поля опциональны — пользователь может заполнить
 * вручную, либо они автозаполняются из VIN.
 *
 * @param unitSystemOverride — если null, используется region.defaultUnitSystem.
 *        Раздельная настройка нужна, потому что пользователь из США может
 *        диагностировать японскую машину и хотеть метрику.
 * @param ecuId — идентификатор ЭБУ в базе CarPulse (см. файл «Список ЭБУ»).
 *        Подбирается отдельным слоем на основе make/model/year/region.
 */
data class CarProfile(
    val vin: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val region: Region = Region.UNKNOWN,
    val unitSystemOverride: UnitSystem? = null,
    val ecuId: String? = null,
) {
    /** Эффективная система единиц для отображения. */
    val effectiveUnitSystem: UnitSystem
        get() = unitSystemOverride ?: region.defaultUnitSystem
}