package com.carpulse.obd.data.obd

/**
 * Краткий словарь популярных DTC.
 * Полный список — сотни кодов; здесь только те, что чаще всего встречаются
 * у обычных водителей, чтобы пользователь сразу понял, что произошло.
 */
object DtcDictionary {

    data class DtcInfo(
        val ru: String,
        val en: String,
        val severity: Severity
    )

    enum class Severity { LOW, MEDIUM, HIGH }

    private val map: Map<String, DtcInfo> = mapOf(
        // --- Зажигание ---
        "P0300" to DtcInfo("Случайные пропуски зажигания", "Random/multiple cylinder misfire", Severity.HIGH),
        "P0301" to DtcInfo("Пропуск зажигания в цилиндре 1", "Cylinder 1 misfire", Severity.HIGH),
        "P0302" to DtcInfo("Пропуск зажигания в цилиндре 2", "Cylinder 2 misfire", Severity.HIGH),
        "P0303" to DtcInfo("Пропуск зажигания в цилиндре 3", "Cylinder 3 misfire", Severity.HIGH),
        "P0304" to DtcInfo("Пропуск зажигания в цилиндре 4", "Cylinder 4 misfire", Severity.HIGH),
        "P0305" to DtcInfo("Пропуск зажигания в цилиндре 5", "Cylinder 5 misfire", Severity.HIGH),
        "P0306" to DtcInfo("Пропуск зажигания в цилиндре 6", "Cylinder 6 misfire", Severity.HIGH),
        "P0307" to DtcInfo("Пропуск зажигания в цилиндре 7", "Cylinder 7 misfire", Severity.HIGH),
        "P0308" to DtcInfo("Пропуск зажигания в цилиндре 8", "Cylinder 8 misfire", Severity.HIGH),

        // --- Кислородный датчик / катализатор ---
        "P0420" to DtcInfo("Низкая эффективность катализатора (банк 1)", "Catalyst efficiency below threshold (bank 1)", Severity.MEDIUM),
        "P0430" to DtcInfo("Низкая эффективность катализатора (банк 2)", "Catalyst efficiency below threshold (bank 2)", Severity.MEDIUM),
        "P0130" to DtcInfo("Неисправность датчика кислорода (банк 1, сенсор 1)", "O2 sensor circuit malfunction (B1S1)", Severity.MEDIUM),
        "P0135" to DtcInfo("Подогрев датчика кислорода (банк 1, сенсор 1)", "O2 sensor heater circuit (B1S1)", Severity.MEDIUM),
        "P0141" to DtcInfo("Подогрев датчика кислорода (банк 1, сенсор 2)", "O2 sensor heater circuit (B1S2)", Severity.MEDIUM),

        // --- Топливная система ---
        "P0171" to DtcInfo("Бедная смесь (банк 1)", "Fuel system too lean (bank 1)", Severity.MEDIUM),
        "P0172" to DtcInfo("Богатая смесь (банк 1)", "Fuel system too rich (bank 1)", Severity.MEDIUM),
        "P0174" to DtcInfo("Бедная смесь (банк 2)", "Fuel system too lean (bank 2)", Severity.MEDIUM),
        "P0175" to DtcInfo("Богатая смесь (банк 2)", "Fuel system too rich (bank 2)", Severity.MEDIUM),
        "P0087" to DtcInfo("Низкое давление в топливной рампе", "Fuel rail pressure too low", Severity.HIGH),
        "P0088" to DtcInfo("Высокое давление в топливной рампе", "Fuel rail pressure too high", Severity.HIGH),

        // --- EGR / воздух ---
        "P0401" to DtcInfo("Недостаточный поток EGR", "EGR flow insufficient", Severity.LOW),
        "P0402" to DtcInfo("Избыточный поток EGR", "EGR flow excessive", Severity.LOW),
        "P0440" to DtcInfo("Неисправность системы улавливания паров", "EVAP system malfunction", Severity.LOW),
        "P0442" to DtcInfo("Малая утечка в системе EVAP", "Small EVAP leak", Severity.LOW),
        "P0455" to DtcInfo("Большая утечка в системе EVAP", "Large EVAP leak", Severity.MEDIUM),

        // --- Датчики ---
        "P0100" to DtcInfo("Неисправность датчика массового расхода воздуха (MAF)", "MAF sensor malfunction", Severity.MEDIUM),
        "P0101" to DtcInfo("Датчик MAF: диапазон/производительность", "MAF range/performance", Severity.MEDIUM),
        "P0110" to DtcInfo("Датчик температуры воздуха (IAT)", "IAT sensor malfunction", Severity.LOW),
        "P0115" to DtcInfo("Датчик температуры ОЖ", "Coolant temperature sensor", Severity.MEDIUM),
        "P0116" to DtcInfo("Датчик ОЖ: диапазон/производительность", "Coolant temp range/performance", Severity.MEDIUM),
        "P0120" to DtcInfo("Датчик положения дроссельной заслонки", "Throttle position sensor", Severity.MEDIUM),
        "P0121" to DtcInfo("Датчик дросселя: диапазон/производительность", "TPS range/performance", Severity.MEDIUM),
        "P0335" to DtcInfo("Датчик положения коленвала", "Crankshaft position sensor", Severity.HIGH),
        "P0340" to DtcInfo("Датчик положения распредвала", "Camshaft position sensor", Severity.HIGH),

        // --- Система зажигания ---
        "P0351" to DtcInfo("Катушка зажигания 1", "Ignition coil 1", Severity.HIGH),
        "P0352" to DtcInfo("Катушка зажигания 2", "Ignition coil 2", Severity.HIGH),
        "P0353" to DtcInfo("Катушка зажигания 3", "Ignition coil 3", Severity.HIGH),
        "P0354" to DtcInfo("Катушка зажигания 4", "Ignition coil 4", Severity.HIGH),

        // --- Трансмиссия ---
        "P0700" to DtcInfo("Неисправность в системе управления АКПП", "Transmission control system", Severity.HIGH),
        "P0705" to DtcInfo("Датчик диапазона АКПП", "Transmission range sensor", Severity.HIGH),
        "P0730" to DtcInfo("Неправильное передаточное отношение", "Incorrect gear ratio", Severity.HIGH),

        // --- ABS / тормоза ---
        "C0035" to DtcInfo("Датчик скорости переднего левого колеса", "Front left wheel speed sensor", Severity.HIGH),
        "C0040" to DtcInfo("Датчик скорости переднего правого колеса", "Front right wheel speed sensor", Severity.HIGH),
        "C0045" to DtcInfo("Датчик скорости заднего левого колеса", "Rear left wheel speed sensor", Severity.HIGH),
        "C0050" to DtcInfo("Датчик скорости заднего правого колеса", "Rear right wheel speed sensor", Severity.HIGH)
    )

    fun lookup(code: String, isRussian: Boolean): DtcInfo? {
        val info = map[code] ?: return null
        return info
    }

    fun describe(code: String, isRussian: Boolean): String {
        val info = map[code] ?: return if (isRussian) "Неизвестный код" else "Unknown code"
        return if (isRussian) info.ru else info.en
    }

    fun severity(code: String): Severity {
        return map[code]?.severity ?: Severity.LOW
    }
}