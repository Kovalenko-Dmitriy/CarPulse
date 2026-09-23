package com.carpulse.obd.data.obd

/**
 * Парсер ответов ELM327 (SAE J1979 / ISO 15031-5).
 *
 * Все методы возвращают Float? — null, если ответ не распознан
 * или не относится к запрошенному PID.
 *
 * Формат ответа: "41 XX A B C D", где:
 *   • 41     — ответ на Mode 01
 *   • XX     — номер PID
 *   • A B C D — байты данных (зависит от PID)
 */
object ObdParser {

    /** Нормализация: убрать пробелы, переводы строк, привести к верхнему регистру. */
    private fun hex(s: String): String =
        s.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()

    /** Проверка, что ответ начинается с "41 XX". */
    private fun isResponse(h: String, pid: String): Boolean =
        h.startsWith("41$pid")

    // ============================================================
    // Двигатель
    // ============================================================

    /** 0104 — Нагрузка на двигатель: A * 100 / 255 % */
    fun engineLoad(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "04") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 0105 — Температура ОЖ: A - 40 °C */
    fun coolant(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "05") || h.length < 6) return null
        return (h.substring(4, 6).toInt(16) - 40).toFloat()
    }

    /** 010C — Обороты двигателя: (A*256 + B) / 4 */
    fun rpm(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "0C") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 4f
    }

    /** 010D — Скорость: A км/ч */
    fun speed(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "0D") || h.length < 6) return null
        return h.substring(4, 6).toInt(16).toFloat()
    }

    /** 010E — Опережение зажигания: A/2 - 64 ° */
    fun timing(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "0E") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 2f - 64f
    }

    // ============================================================
    // Топливная система
    // ============================================================

    /** 0106 — Краткосрочная коррекция (банк 1): A/1.28 - 100 % */
    fun stft(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "06") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 1.28f - 100f
    }

    /** 0107 — Долгосрочная коррекция (банк 1): A/1.28 - 100 % */
    fun ltft(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "07") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 1.28f - 100f
    }

    /** 0108 — Краткосрочная коррекция (банк 2): A/1.28 - 100 % */
    fun stft2(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "08") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 1.28f - 100f
    }

    /** 0109 — Долгосрочная коррекция (банк 2): A/1.28 - 100 % */
    fun ltft2(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "09") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 1.28f - 100f
    }

    /** 010A — Давление топлива: A * 3 кПа */
    fun fuelPressure(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "0A") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 3f
    }

    /** 0122 — Давление в рампе (относительное): (A*256 + B) * 0.079 кПа */
    fun fuelRailRel(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "22") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) * 0.079f
    }

    /** 0123 — Давление в рампе (абсолютное): (A*256 + B) * 10 кПа */
    fun fuelRailAbs(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "23") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) * 10f
    }

    /** 012F — Уровень топлива: A * 100 / 255 % */
    fun fuel(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "2F") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    // ============================================================
    // Воздух
    // ============================================================

    /** 010B — Давление во впуске (MAP): A кПа */
    fun map(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "0B") || h.length < 6) return null
        return h.substring(4, 6).toInt(16).toFloat()
    }

    /** 010F — Температура впуска: A - 40 °C */
    fun intake(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "0F") || h.length < 6) return null
        return (h.substring(4, 6).toInt(16) - 40).toFloat()
    }

    /** 0110 — Расход воздуха (MAF): (A*256 + B) / 100 г/с */
    fun maf(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "10") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 100f
    }

    /** 0111 — Положение дросселя: A * 100 / 255 % */
    fun throttle(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "11") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 0133 — Барометрическое давление: A кПа */
    fun baro(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "33") || h.length < 6) return null
        return h.substring(4, 6).toInt(16).toFloat()
    }

    /** 0145 — Относительное положение дросселя: A * 100 / 255 % */
    fun throttleRelative(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "45") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 0146 — Температура окружающего воздуха: A - 40 °C */
    fun ambient(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "46") || h.length < 6) return null
        return (h.substring(4, 6).toInt(16) - 40).toFloat()
    }

    /** 0147 — Абсолютное положение дросселя B: A * 100 / 255 % */
    fun throttleB(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "47") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 0148 — Абсолютное положение дросселя C: A * 100 / 255 % */
    fun throttleC(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "48") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 0149 — Педаль акселератора D: A * 100 / 255 % */
    fun accelD(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "49") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 014A — Педаль акселератора E: A * 100 / 255 % */
    fun accelE(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "4A") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 014B — Педаль акселератора F: A * 100 / 255 % */
    fun accelF(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "4B") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    // ============================================================
    // Датчики кислорода
    // ============================================================

    /** 0114 — O2 B1S1: A / 200 В */
    fun o2B1S1(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "14") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 200f
    }

    /** 0115 — O2 B1S2: A / 200 В */
    fun o2B1S2(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "15") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 200f
    }

    /** 0116 — O2 B1S3: A / 200 В */
    fun o2B1S3(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "16") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 200f
    }

    /** 0117 — O2 B1S4: A / 200 В */
    fun o2B1S4(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "17") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 200f
    }

    /** 0118 — O2 B2S1: A / 200 В */
    fun o2B2S1(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "18") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 200f
    }

    /** 0119 — O2 B2S2: A / 200 В */
    fun o2B2S2(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "19") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) / 200f
    }

    // ============================================================
    // Экология
    // ============================================================

    /** 012C — Команда EGR: A * 100 / 255 % */
    fun egrCommand(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "2C") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 012D — Ошибка EGR: (A - 128) * 100 / 128 % */
    fun egrError(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "2D") || h.length < 6) return null
        return (h.substring(4, 6).toInt(16) - 128) * 100f / 128f
    }

    /** 012E — Продувка EVAP: A * 100 / 255 % */
    fun evapCommand(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "2E") || h.length < 6) return null
        return h.substring(4, 6).toInt(16) * 100f / 255f
    }

    /** 0130 — Прогревов с последнего сброса: A */
    fun warmups(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "30") || h.length < 6) return null
        return h.substring(4, 6).toInt(16).toFloat()
    }

    /** 0131 — Пробег после сброса кодов: A*256 + B км */
    fun distCleared(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "31") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b).toFloat()
    }

    /** 0132 — Давление паров EVAP: ((A*256 + B) / 4) - 8192 Па */
    fun evapPressure(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "32") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 4f - 8192f
    }

    /** 013C — Температура катализатора B1S1: (A*256 + B) / 10 - 40 °C */
    fun catB1S1(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "3C") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 10f - 40f
    }

    /** 013D — Температура катализатора B1S2: (A*256 + B) / 10 - 40 °C */
    fun catB1S2(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "3D") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 10f - 40f
    }

    /** 013E — Температура катализатора B2S1: (A*256 + B) / 10 - 40 °C */
    fun catB2S1(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "3E") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 10f - 40f
    }

    /** 013F — Температура катализатора B2S2: (A*256 + B) / 10 - 40 °C */
    fun catB2S2(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "3F") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 10f - 40f
    }

    // ============================================================
    // Электрика
    // ============================================================

    /** 0142 — Напряжение бортовой сети: (A*256 + B) / 1000 В */
    fun controlVoltage(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "42") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b) / 1000f
    }

    // ============================================================
    // Дополнительные (время, MIL)
    // ============================================================

    /** 011F — Время работы с запуска: A*256 + B секунд */
    fun runTime(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "1F") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b).toFloat()
    }

    /** 0121 — Пробег с загоранием MIL: A*256 + B км */
    fun milDistance(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "21") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b).toFloat()
    }

    /** 014D — Время с MIL: A*256 + B минут */
    fun milTime(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "4D") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b).toFloat()
    }

    /** 014E — Время с последнего сброса: A*256 + B минут */
    fun timeCleared(resp: String): Float? {
        val h = hex(resp)
        if (!isResponse(h, "4E") || h.length < 8) return null
        val a = h.substring(4, 6).toInt(16)
        val b = h.substring(6, 8).toInt(16)
        return ((a shl 8) + b).toFloat()
    }

    // ============================================================
    // Напряжение АКБ (из AT-команды ATRV)
    // ============================================================

    /**
     * Парсит ответ ATRV, например "14.4V".
     */
    fun battery(resp: String): Float? {
        val m = Regex("""(\d+\.?\d*)V""").find(resp) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }
// ============================================================
    // DTC (Mode 03)
    // ============================================================

    /**
     * 03 — Список кодов ошибок.
     *
     * ISO 9141-2 (несколько строк):
     *   "43 02 01 33 00"
     *   "43 02 02 00 00 00 00"
     * → P0133
     *
     * CAN (один фрейм):
     *   "43 02 01 03 00 01 71 00 00"
     * → P0300, P0171
     */
    fun dtcList(resp: String): List<String> {
        val clean = resp.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()
        if (!clean.contains("43")) return emptyList()

        // Убираем все "43" заголовки, оставляем только данные
        val dataOnly = clean.replace("43", "")
        val codes = mutableListOf<String>()
        var i = 0
        while (i + 4 <= dataOnly.length) {
            val code = dataOnly.substring(i, i + 4)
            if (code != "0000") codes.add(decodeDtc(code))
            i += 4
        }
        return codes
    }

    /** Декодирует 4-символьный hex в код DTC (P0300 и т.д.). */
    private fun decodeDtc(code: String): String {
        val first = code[0]
        val prefix = when (first) {
            '0', '1', '2', '3' -> 'P'
            '4', '5', '6', '7' -> 'C'
            '8', '9', 'A', 'B' -> 'B'
            else -> 'U'
        }
        return "$prefix${code.substring(1)}"
    }
}
