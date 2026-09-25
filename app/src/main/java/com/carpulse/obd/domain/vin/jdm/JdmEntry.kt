package com.carpulse.obd.domain.vin.jdm

import com.carpulse.obd.domain.profile.Region

/**
 * Запись в JDM-базе (японские 車台番号 / номер кузова).
 *
 * В отличие от VIN, японский номер кузова не имеет строгой структуры
 * по ISO 3779. Формат: <код модели><серийный номер>, например:
 *   EU1 030002    — Honda Civic Ferio / Domani
 *   AE111 1234567 — Toyota Corolla
 *   B14 123456    — Nissan Sunny
 *
 * @param prefix   префикс кода модели (от 2 до 5 символов)
 * @param make     марка
 * @param model    модель
 * @param yearFrom год начала выпуска (null, если неизвестен)
 * @param yearTo   год окончания (null = по настоящее время)
 * @param region   регион (для JDM — ASIA, но поле оставлено
 *                 для возможных не-JDM номеров кузова)
 */
data class JdmEntry(
    val prefix: String,
    val make: String,
    val model: String,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val region: Region = Region.ASIA,
) {
    /**
     * Годы выпуска как строка для UI:
     *   "1995–2000", "1995–", "–2000" или null.
     */
    val yearsLabel: String?
        get() = when {
            yearFrom == null && yearTo == null -> null
            yearFrom != null && yearTo != null -> "$yearFrom–$yearTo"
            yearFrom != null -> "$yearFrom–"
            else -> "–$yearTo"
        }
}

/**
 * Результат декодирования JDM-номера кузова.
 *
 * @param input          исходная строка от пользователя
 * @param entry          найденная запись JDM-базы или null
 * @param confidence     уверенность подбора
 */
data class JdmDecodeResult(
    val input: String,
    val entry: JdmEntry?,
    val confidence: Confidence,
) {
    enum class Confidence {
        /** Точное совпадение: префикс ≥ 4 символов. */
        EXACT,
        /** Частичное: префикс 2–3 символа. */
        PARTIAL,
        /** Ничего не нашли. */
        NONE,
    }

    val isFound: Boolean get() = entry != null
}