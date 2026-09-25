package com.carpulse.obd.domain.vin.jdm

/**
 * Декодер японских номеров кузова (車台番号).
 *
 * Не путать с VIN-декодером — здесь работаем с нестандартными
 * идентификаторами JDM-автомобилей:
 *
 *   EU11030002   — Honda Civic Ferio
 *   AE1111234567 — Toyota Corolla
 *   B14123456    — Nissan Sunny
 *
 * В отличие от VIN, японский номер кузова не имеет фиксированной длины
 * и не содержит контрольной суммы. Однозначно распознать его как JDM
 * можно только при наличии в JDM-базе префикса модели.
 *
 * @param database JDM-база (обязательна). Может быть EMPTY.
 */
class JdmDecoder(private val database: JdmDatabase) {

    /**
     * Декодирует японский номер кузова.
     *
     * @param input произвольная строка от пользователя
     * @return JdmDecodeResult — никогда не null
     */
    fun decode(input: String): JdmDecodeResult {
        val trimmed = input.trim().uppercase()

        if (trimmed.length < 3) {
            return JdmDecodeResult(
                input = trimmed,
                entry = null,
                confidence = JdmDecodeResult.Confidence.NONE,
            )
        }

        return database.findWithConfidence(trimmed)
    }

    /**
     * Быстрая проверка — похоже ли, что это JDM-номер.
     * Если find() находит запись — true. Используется в UI для
     * мягкой подсказки «Найдено: Honda Civic Ferio 1995–2000».
     */
    fun isJdm(input: String): Boolean = database.find(input.trim().uppercase()) != null
}