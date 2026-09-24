package com.carpulse.obd.domain.vin

/**
 * Расшифровка VDS (символы 4–8 VIN) — конкретная модель.
 *
 * Возвращает только модель, без марки — марка уже есть в VinDecodeResult
 * (из WMI-базы). Если хочется показать полное имя — UI может склеить
 * manufacturer + modelHint.
 */
interface VdsLookup {

    /**
     * @return человекочитаемое имя модели или null, если не удалось определить.
     */
    fun lookup(vin: String): String?

    /**
     * Реализация поверх VdsDatabase (из assets/vds_database.json).
     */
    class Database(private val db: VdsDatabase) : VdsLookup {
        override fun lookup(vin: String): String? =
            db.find(vin)?.model
    }

    /**
     * Заглушка для тестов и fallback.
     */
    object Empty : VdsLookup {
        override fun lookup(vin: String): String? = null
    }
}