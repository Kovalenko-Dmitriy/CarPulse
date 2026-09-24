package com.carpulse.obd.domain.profile

import com.carpulse.obd.domain.units.UnitSystem

/**
 * Профиль автомобиля.
 *
 * Поля `vin` и `bodyNumber` взаимоисключающие:
 *  - VIN — стандартный 17-символьный идентификатор (ISO 3779).
 *  - bodyNumber — номер кузова/шасси для машин без VIN:
 *      * старые ВАЗ/ГАЗ/УАЗ (13–14 цифр),
 *      * японские JDM (車台番号, например EU11030002),
 *      * грузовики (номер рамы).
 *
 * @param vin                VIN или null
 * @param bodyNumber         номер кузова/шасси или null
 * @param make               марка
 * @param model              модель
 * @param year               год выпуска
 * @param region             регион (влияет на подбор PID и ЭБУ)
 * @param unitSystemOverride система единиц (null = дефолт региона)
 * @param ecuId              идентификатор ЭБУ в базе CarPulse
 */
data class CarProfile(
    val vin: String? = null,
    val bodyNumber: String? = null,
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

    /**
     * Универсальный идентификатор: VIN, или номер кузова, или null.
     * Удобно для истории сессий и логов.
     */
    val identifier: String?
        get() = vin ?: bodyNumber

    /** Есть ли у профиля хоть какой-то идентификатор. */
    val hasIdentifier: Boolean
        get() = !vin.isNullOrBlank() || !bodyNumber.isNullOrBlank()
}