package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.profile.Region

data class VinDecodeResult(
    val vin: String,
    val validation: VinValidation,
    val wmi: WmiEntry?,
    val year: Int?,
    val modelHint: String?,
    val region: Region,
    val manufacturer: String?,
    val country: String?,
) {
    val isValid: Boolean get() = validation is VinValidation.Valid
}

class VinDecoder(
    private val wmiDb: WmiDatabase = WmiDatabase,
    private val vdsLookup: VdsLookup = VdsLookup.Empty,
) {

    /**
     * Декодирует VIN. Даже при невалидном формате пытается извлечь максимум:
     * старые ВАЗ/ГАЗ/УАЗ иногда имеют VIN нестандартной длины, и пользователь
     * всё равно хочет видеть подсказки.
     */
    fun decode(rawVin: String): VinDecodeResult {
        val vin = rawVin.trim().uppercase()
        val validation = VinValidator.validate(vin)

        val wmi = if (vin.length >= 3) wmiDb.find(vin) else null
        val year = ModelYearDecoder.decode(vin)
        val modelHint = if (vin.length >= 8) vdsLookup.lookup(vin) else null

        // Регион: приоритет — точный WMI из базы, fallback — по 1-му символу.
        val region = wmi?.region ?: regionByFirstChar(vin.firstOrNull())

        return VinDecodeResult(
            vin = vin,
            validation = validation,
            wmi = wmi,
            year = year,
            modelHint = modelHint,
            region = region,
            manufacturer = wmi?.manufacturer,
            country = wmi?.country,
        )
    }

    /**
     * Fallback-маппинг по первому символу VIN (стандарт ISO 3779).
     * Используется, когда WMI нет в базе.
     */
    private fun regionByFirstChar(c: Char?): Region = when (c) {
        '1', '2', '3', '4', '5' -> Region.US
        '6', '7', '8', '9' -> Region.OTHER      // Австралия, НЗ, Аргентина, Бразилия
        'J', 'K', 'L', 'M', 'N', 'P', 'R' -> Region.ASIA
        'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' -> Region.EU
        else -> Region.UNKNOWN
    }
}

/**
 * Применяет результат декодирования к текущему профилю.
 *
 * @param overwriteExisting если false (по умолчанию) — уже заполненные
 *        пользователем поля не затираются. Если true — декодер имеет приоритет
 *        (полезно при явной смене VIN).
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
        // unitSystemOverride НЕ трогаем: это явный выбор пользователя.
        // Если override == null, effectiveUnitSystem возьмётся из region.
    )
}