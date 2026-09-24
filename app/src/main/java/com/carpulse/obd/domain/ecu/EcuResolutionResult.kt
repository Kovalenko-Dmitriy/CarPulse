package com.carpulse.obd.domain.ecu

/**
 * Результат подбора ЭБУ.
 *
 * @param entry       найденный ЭБУ или null (тогда работаем через OBD2_STANDARD)
 * @param protocol    итоговый протокол (из entry или OBD2_STANDARD)
 * @param confidence  насколько уверен резолвер
 * @param alternatives альтернативные записи для UI «уточните модель»
 * @param reason      человекочитаемая причина для лога/отладки
 */
data class EcuResolutionResult(
    val entry: EcuEntry?,
    val protocol: Protocol,
    val confidence: Confidence,
    val alternatives: List<EcuEntry> = emptyList(),
    val reason: String = "",
) {
    enum class Confidence {
        /** Точное совпадение: марка + модель + год. */
        EXACT,
        /** Частичное: марка + модель (без года) или марка + год (без модели). */
        PARTIAL,
        /** Только марка. */
        MAKE_ONLY,
        /** Ничего не нашли — работаем через стандартный OBD-II. */
        FALLBACK,
    }

    val isExact: Boolean get() = confidence == Confidence.EXACT
    val hasAlternatives: Boolean get() = alternatives.isNotEmpty()

    companion object {
        /** Fallback: работаем через стандартный OBD-II. */
        fun fallback(reason: String = "Профиль не заполнен"): EcuResolutionResult =
            EcuResolutionResult(
                entry = null,
                protocol = Protocol.OBD2_STANDARD,
                confidence = Confidence.FALLBACK,
                reason = reason,
            )
    }
}
