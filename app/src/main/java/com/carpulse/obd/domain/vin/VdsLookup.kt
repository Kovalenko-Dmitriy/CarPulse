package com.carpulse.obd.domain.vin

/**
 * Расшифровка VDS (символы 4–8 VIN) — конкретная модель.
 *
 * Пока пустая реализация: точная база VDS — задача итерации 2,
 * потому что она требует ручного наполнения по каждой марке
 * (у Toyota, Lada, VW — свои правила кодирования в этих 5 символах).
 *
 * Интерфейс вводим сразу, чтобы VinDecoder не менялся, когда база появится.
 */
interface VdsLookup {
    /** @return человекочитаемое имя модели или null, если не удалось определить. */
    fun lookup(vin: String): String?

    object Empty : VdsLookup {
        override fun lookup(vin: String): String? = null
    }
}