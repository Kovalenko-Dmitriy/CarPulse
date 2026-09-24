package com.carpulse.obd.domain.vin

/**
 * Тип идентификатора автомобиля.
 *
 *  - VIN     — стандартный 17-символьный VIN (ISO 3779)
 *  - BODY    — номер кузова/шасси (старые ВАЗ/ГАЗ/УАЗ, японский 車台番号,
 *              номер рамы грузовика)
 *  - UNKNOWN — не удалось классифицировать
 */
enum class VinKind(val displayName: String) {
    VIN("VIN (17 символов)"),
    BODY("Номер кузова"),
    UNKNOWN("Неизвестный формат"),
}