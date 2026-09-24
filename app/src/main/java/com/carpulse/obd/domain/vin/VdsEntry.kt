package com.carpulse.obd.domain.vin

/**
 * Запись в VDS-базе.
 *
 * VDS — Vehicle Descriptor Section, символы 4–8 VIN (иногда 4–9).
 * Используется для определения модели автомобиля, когда WMI даёт только
 * производителя.
 *
 * @param prefix префикс VIN. Может быть от 4 до 8 символов.
 *               Longest-prefix match: "XTAGFL" (Vesta) сматчится
 *               раньше, чем "XTA" (АвтоВАЗ).
 * @param make   марка (для диагностики и UI).
 * @param model  модель — то, что вернётся в VinDecodeResult.modelHint.
 */
data class VdsEntry(
    val prefix: String,
    val make: String,
    val model: String,
)
