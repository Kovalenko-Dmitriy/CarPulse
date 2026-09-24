package com.carpulse.obd.domain.ecu

/**
 * Транспортный протокол диагностики.
 *
 * Порядок объявления — от самого современного (CAN) к самому старому (K-Line).
 * Это используется в резолвере: при неоднозначности предпочитаем более
 * современный протокол, потому что он стабильнее и поддерживает больше PID.
 */
enum class Protocol(val displayName: String) {
    CAN_EXTENDED("CAN (Mode 22)"),
    KWP2000("KWP2000"),
    KLINE_CUSTOM("K-Line (custom)"),
    OBD2_STANDARD("OBD-II (Mode 01)");

    /** Пропускает ли ELM327 команды init_lines для этого протокола. */
    val requiresInitLines: Boolean
        get() = this == KLINE_CUSTOM || this == KWP2000
}