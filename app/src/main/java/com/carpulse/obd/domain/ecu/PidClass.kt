package com.carpulse.obd.domain.ecu

/**
 * Класс набора PID, который поддерживает ЭБУ.
 *
 * Вместо того чтобы перечислять десятки команд для каждого из 99 ЭБУ,
 * мы ссылаемся на класс. Класс → конкретный список команд —
 * в PidClassRegistry (реализация в следующей итерации, когда
 * появится транспортный слой Mode 22).
 *
 * @property baseModes базовые режимы, доступные всегда
 */
enum class PidClass(
    val displayName: String,
    val baseModes: Set<ObdMode>,
) {
    /** Только Mode 01 базовые + 03/04/09. Для старых ЭБУ. */
    OBD2_BASIC(
        "OBD-II базовый",
        setOf(ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04, ObdMode.MODE_09),
    ),

    /** Все Mode 01 + 02 + 03/04/06/09. Для современных иномарок. */
    OBD2_FULL(
        "OBD-II полный",
        setOf(
            ObdMode.MODE_01, ObdMode.MODE_02, ObdMode.MODE_03,
            ObdMode.MODE_04, ObdMode.MODE_06, ObdMode.MODE_09,
        ),
    ),

    /** Legacy ВАЗ/ГАЗ/УАЗ K-Line. Собственный протокол, не OBD-II. */
    VAZ_LEGACY(
        "ВАЗ legacy (K-Line)",
        setOf(ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04),
    ),

    /** Ителма M74/M75 (CAN, Mode 22). */
    VAZ_M74(
        "ВАЗ M74 (CAN)",
        setOf(ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04, ObdMode.MODE_22),
    ),

    /** Ителма M86 (Vesta, XRAY). */
    VAZ_M86(
        "ВАЗ M86 (CAN)",
        setOf(ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04, ObdMode.MODE_22),
    ),

    /** Микас 12 (ГАЗ). */
    GAZ_MIKAS12(
        "Микас 12 (CAN)",
        setOf(ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04, ObdMode.MODE_22),
    ),

    /** Hyundai/Kia UDS (Mode 22, ISO 14229). */
    HYUNDAI_UDS(
        "Hyundai/Kia UDS",
        setOf(
            ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04,
            ObdMode.MODE_09, ObdMode.MODE_22,
        ),
    ),

    /** Bosch китайские (ME17.8.8, MED17.8.10). */
    CHINA_BOSCH(
        "China Bosch (CAN)",
        setOf(
            ObdMode.MODE_01, ObdMode.MODE_03, ObdMode.MODE_04,
            ObdMode.MODE_09, ObdMode.MODE_22,
        ),
    ),
}