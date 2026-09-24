package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.Region

/**
 * Результат декодирования VIN.
 */
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

/**
 * Декодер VIN.
 *
 * WMI-база обязательна и передаётся снаружи — экземпляр WmiDatabase
 * создаётся в CarPulseApp через WmiJsonLoader и переиспользуется
 * всеми вызовами.
 *
 * @param wmiDb     WMI-база (обязательна).
 * @param vdsLookup расшифровка VDS (символы 4–8). По умолчанию — заглушка.
 */
class VinDecoder(
    private val wmiDb: WmiDatabase,
    private val vdsLookup: VdsLookup = VdsLookup.Empty,
) {

    fun decode(rawVin: String): VinDecodeResult {
        val vin = rawVin.trim().uppercase()
        val validation = VinValidator.validate(vin)

        val wmi = if (vin.length >= 3) wmiDb.find(vin) else null

        // Регион: приоритет — точный WMI, fallback — по 1-му символу VIN.
        // Это же значение используется для эвристики года.
        val region = wmi?.region ?: regionByFirstChar(vin.firstOrNull())

        // Год: эвристика для EU/ASIA работает независимо от того,
        // нашёлся WMI в базе или нет.
        val year = ModelYearDecoder.decode(vin, region)

        val modelHint = if (vin.length >= 8) vdsLookup.lookup(vin) else null

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

    private fun regionByFirstChar(c: Char?): Region = when (c) {
        '1', '2', '3', '4', '5' -> Region.US
        '6', '7', '8', '9' -> Region.OTHER
        'J', 'K', 'L', 'M', 'N', 'P', 'R' -> Region.ASIA
        'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' -> Region.EU
        else -> Region.UNKNOWN
    }
}