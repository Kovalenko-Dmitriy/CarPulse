package com.carpulse.obd.data.obd

/**
 * Полный каталог PID Mode 01 (SAE J1979).
 * Поддерживаемые конкретной машиной определяются через SupportedPidsDetector.
 *
 * @param cmd         команда ELM327 ("010C")
 * @param labelRu     название на русском
 * @param labelEn     название на английском
 * @param unitRu      единица измерения (русская)
 * @param unitEn      единица измерения (английская)
 * @param min         минимум для шкалы
 * @param max         максимум для шкалы
 * @param warn        порог предупреждения
 * @param danger      порог опасности
 * @param lowerIsWorse true для параметров, где плохо НИЗКОЕ значение
 * @param category    группа для UI (двигатель, топливо, воздух, ...)
 */
enum class Pid(
    val cmd: String,
    val labelRu: String,
    val labelEn: String,
    val unitRu: String,
    val unitEn: String,
    val min: Float,
    val max: Float,
    val warn: Float? = null,
    val danger: Float? = null,
    val lowerIsWorse: Boolean = false,
    val category: PidCategory = PidCategory.OTHER
) {

    // ============================================================
    // Двигатель и топливная система
    // ============================================================
    FUEL_STATUS    ("0103", "Топливная система", "Fuel system status", "", "", 0f, 1f, category = PidCategory.FUEL),
    LOAD           ("0104", "Нагрузка на двигатель", "Calculated engine load", "%", "%", 0f, 100f, 80f, 95f, category = PidCategory.ENGINE),
    COOLANT        ("0105", "Температура ОЖ", "Coolant temperature", "°C", "°C", -40f, 120f, 100f, 110f, category = PidCategory.ENGINE),
    STFT1          ("0106", "Краткосрочная коррекция (банк 1)", "Short term fuel trim (bank 1)", "%", "%", -100f, 100f, category = PidCategory.FUEL),
    LTFT1          ("0107", "Долгосрочная коррекция (банк 1)", "Long term fuel trim (bank 1)", "%", "%", -100f, 100f, category = PidCategory.FUEL),
    STFT2          ("0108", "Краткосрочная коррекция (банк 2)", "Short term fuel trim (bank 2)", "%", "%", -100f, 100f, category = PidCategory.FUEL),
    LTFT2          ("0109", "Долгосрочная коррекция (банк 2)", "Long term fuel trim (bank 2)", "%", "%", -100f, 100f, category = PidCategory.FUEL),
    FUEL_PRESSURE  ("010A", "Давление топлива", "Fuel pressure", "кПа", "kPa", 0f, 765f, category = PidCategory.FUEL),
    FUEL_RAIL_REL  ("0122", "Давление в рампе (отн.)", "Fuel rail pressure (rel.)", "кПа", "kPa", 0f, 5178f, category = PidCategory.FUEL),
    FUEL_RAIL_ABS  ("0123", "Давление в рампе (абс.)", "Fuel rail pressure (abs.)", "кПа", "kPa", 0f, 655350f, category = PidCategory.FUEL),
    FUEL_LEVEL     ("012F", "Уровень топлива", "Fuel level", "%", "%", 0f, 100f, 20f, null, lowerIsWorse = true, category = PidCategory.FUEL),

    // ============================================================
    // Впуск и воздух
    // ============================================================
    MAP            ("010B", "Давление во впуске (MAP)", "Intake manifold pressure", "кПа", "kPa", 0f, 255f, category = PidCategory.AIR),
    INTAKE         ("010F", "Температура впуска (IAT)", "Intake air temperature", "°C", "°C", -40f, 80f, 60f, 70f, category = PidCategory.AIR),
    MAF            ("0110", "Расход воздуха (MAF)", "Mass air flow", "г/с", "g/s", 0f, 655f, category = PidCategory.AIR),
    THROTTLE       ("0111", "Положение дросселя", "Throttle position", "%", "%", 0f, 100f, category = PidCategory.AIR),
    BARO           ("0133", "Барометрическое давление", "Barometric pressure", "кПа", "kPa", 0f, 255f, category = PidCategory.AIR),
    AMBIENT        ("0146", "Температура воздуха", "Ambient air temperature", "°C", "°C", -40f, 80f, category = PidCategory.AIR),
    THROTTLE_REL   ("0145", "Относительное положение дросселя", "Relative throttle position", "%", "%", 0f, 100f, category = PidCategory.AIR),
    THROTTLE_B     ("0147", "Положение дросселя B", "Absolute throttle position B", "%", "%", 0f, 100f, category = PidCategory.AIR),
    THROTTLE_C     ("0148", "Положение дросселя C", "Absolute throttle position C", "%", "%", 0f, 100f, category = PidCategory.AIR),
    ACCEL_D        ("0149", "Педаль акселератора D", "Accelerator pedal position D", "%", "%", 0f, 100f, category = PidCategory.AIR),
    ACCEL_E        ("014A", "Педаль акселератора E", "Accelerator pedal position E", "%", "%", 0f, 100f, category = PidCategory.AIR),
    ACCEL_F        ("014B", "Педаль акселератора F", "Accelerator pedal position F", "%", "%", 0f, 100f, category = PidCategory.AIR),

    // ============================================================
    // Обороты, скорость, зажигание
    // ============================================================
    RPM            ("010C", "Обороты двигателя", "Engine RPM", "об/мин", "rpm", 0f, 8000f, 5500f, 6500f, category = PidCategory.ENGINE),
    SPEED          ("010D", "Скорость автомобиля", "Vehicle speed", "км/ч", "km/h", 0f, 255f, 120f, 160f, category = PidCategory.ENGINE),
    TIMING         ("010E", "Опережение зажигания", "Timing advance", "°", "°", -64f, 64f, category = PidCategory.ENGINE),

    // ============================================================
    // Датчики кислорода
    // ============================================================
    O2_B1S1_V      ("0114", "O2 B1S1 напряжение", "O2 B1S1 voltage", "В", "V", 0f, 1.275f, category = PidCategory.O2),
    O2_B1S2_V      ("0115", "O2 B1S2 напряжение", "O2 B1S2 voltage", "В", "V", 0f, 1.275f, category = PidCategory.O2),
    O2_B1S3_V      ("0116", "O2 B1S3 напряжение", "O2 B1S3 voltage", "В", "V", 0f, 1.275f, category = PidCategory.O2),
    O2_B1S4_V      ("0117", "O2 B1S4 напряжение", "O2 B1S4 voltage", "В", "V", 0f, 1.275f, category = PidCategory.O2),
    O2_B2S1_V      ("0118", "O2 B2S1 напряжение", "O2 B2S1 voltage", "В", "V", 0f, 1.275f, category = PidCategory.O2),
    O2_B2S2_V      ("0119", "O2 B2S2 напряжение", "O2 B2S2 voltage", "В", "V", 0f, 1.275f, category = PidCategory.O2),

    // ============================================================
    // Экология и выхлоп
    // ============================================================
    EGR_CMD        ("012C", "Команда EGR", "Commanded EGR", "%", "%", 0f, 100f, category = PidCategory.EMISSIONS),
    EGR_ERR        ("012D", "Ошибка EGR", "EGR error", "%", "%", -100f, 100f, category = PidCategory.EMISSIONS),
    EVAP_CMD       ("012E", "Продувка адсорбера EVAP", "Commanded evaporative purge", "%", "%", 0f, 100f, category = PidCategory.EMISSIONS),
    WARMUPS        ("0130", "Прогревов с последнего сброса", "Warm-ups since codes cleared", "", "", 0f, 255f, category = PidCategory.EMISSIONS),
    DIST_CLEARED   ("0131", "Пробег после сброса кодов", "Distance since codes cleared", "км", "km", 0f, 65535f, category = PidCategory.EMISSIONS),
    EVAP_PRESSURE  ("0132", "Давление паров EVAP", "Evap system vapor pressure", "Па", "Pa", -8192f, 8191f, category = PidCategory.EMISSIONS),
    CAT_B1S1       ("013C", "Катализатор B1S1", "Catalyst temperature B1S1", "°C", "°C", -40f, 1200f, category = PidCategory.EMISSIONS),
    CAT_B1S2       ("013D", "Катализатор B1S2", "Catalyst temperature B1S2", "°C", "°C", -40f, 1200f, category = PidCategory.EMISSIONS),
    CAT_B2S1       ("013E", "Катализатор B2S1", "Catalyst temperature B2S1", "°C", "°C", -40f, 1200f, category = PidCategory.EMISSIONS),
    CAT_B2S2       ("013F", "Катализатор B2S2", "Catalyst temperature B2S2", "°C", "°C", -40f, 1200f, category = PidCategory.EMISSIONS),

    // ============================================================
    // Электрика
    // ============================================================
    VOLTAGE        ("0142", "Напряжение бортовой сети", "Control module voltage", "В", "V", 0f, 20f, 12.2f, 11.8f, lowerIsWorse = true, category = PidCategory.ELECTRICAL),

    // ============================================================
    // Дополнительные
    // ============================================================
    RUN_TIME       ("011F", "Время работы с запуска", "Run time since engine start", "с", "s", 0f, 65535f, category = PidCategory.OTHER),
    MIL_DISTANCE   ("0121", "Пробег с MIL", "Distance traveled with MIL on", "км", "km", 0f, 65535f, category = PidCategory.OTHER),
    MIL_TIME       ("014D", "Время с MIL", "Time run with MIL on", "мин", "min", 0f, 65535f, category = PidCategory.OTHER),
    TIME_CLEARED   ("014E", "Время с последнего сброса", "Time since codes cleared", "мин", "min", 0f, 65535f, category = PidCategory.OTHER),
}

enum class PidCategory(val labelRu: String, val labelEn: String) {
    ENGINE("Двигатель", "Engine"),
    FUEL("Топливо", "Fuel"),
    AIR("Воздух", "Air"),
    O2("Датчики O₂", "O₂ sensors"),
    EMISSIONS("Экология", "Emissions"),
    ELECTRICAL("Электрика", "Electrical"),
    OTHER("Прочее", "Other")
}