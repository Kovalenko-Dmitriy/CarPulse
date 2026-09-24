package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.profile.Region

/**
 * Применяет результат декодирования VIN к текущему профилю автомобиля.
 *
 * Правила:
 *  - VIN записывается всегда (это явное действие пользователя).
 *  - Остальные поля — по принципу «не затирать введённое вручную»:
 *    если overwriteExisting == false (по умолчанию), уже заполненные
 *    поля сохраняются, а декодированные значения используются только
 *    для пустых полей.
 *  - unitSystemOverride НЕ трогается: это явный выбор пользователя.
 *    Если override == null, effectiveUnitSystem посчитается из region.
 *  - Если region в профиле UNKNOWN — берём регион из декодера.
 *    Если оба UNKNOWN — оставляем UNKNOWN.
 *
 * @param current            текущий профиль из DataStore
 * @param overwriteExisting  true — данные VIN затирают существующие поля
 *                           (полезно при явной смене VIN);
 *                           false — существующие поля сохраняются
 * @return обновлённый CarProfile
 */
fun VinDecodeResult.applyTo(
    current: CarProfile,
    overwriteExisting: Boolean = false,
): CarProfile {
    fun <T> pick(existing: T?, decoded: T?): T? =
        if (overwriteExisting) (decoded ?: existing) else (existing ?: decoded)

    val newRegion = pick(
        current.region.takeIf { it != Region.UNKNOWN },
        region.takeIf { it != Region.UNKNOWN },
    ) ?: Region.UNKNOWN

    return current.copy(
        vin = vin,
        make = pick(current.make, manufacturer),
        model = pick(current.model, modelHint),
        year = pick(current.year, year),
        region = newRegion,
    )
}