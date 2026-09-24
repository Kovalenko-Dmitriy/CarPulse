package com.carpulse.obd.domain.ecu

import com.carpulse.obd.domain.profile.CarProfile

/**
 * Подбор ЭБУ по профилю автомобиля.
 *
 * Порядок поиска:
 *  1. EXACT:    марка + модель + год
 *  2. PARTIAL:  марка + модель (без года) ИЛИ марка + год
 *  3. MAKE_ONLY: только марка
 *  4. FALLBACK: ничего не нашли → OBD2_STANDARD
 *
 * Логика: OBD2 сначала (см. ТЗ), ECU-резолвер как fallback.
 * Это значит, что CarPulse вызывает resolve() ТОЛЬКО если
 * стандартный OBD2-детект не смог определить ЭБУ.
 */
class EcuResolver(private val database: EcuDatabase) {

    /**
     * @param profile профиль авто (заполненный вручную или из VIN)
     * @return результат подбора, никогда не null
     */
    fun resolve(profile: CarProfile): EcuResolutionResult {
        val make = profile.make?.takeIf { it.isNotBlank() }
        val model = profile.model?.takeIf { it.isNotBlank() }
        val year = profile.year

        // Если марки нет — резолвить нечего.
        if (make == null) {
            return EcuResolutionResult.fallback("Марка не заполнена")
        }

        val byMake = database.findByMake(make)
        if (byMake.isEmpty()) {
            return EcuResolutionResult.fallback("Марка '$make' не найдена в базе")
        }

        // ---- Шаг 1: EXACT (марка + модель + год) ----
        if (model != null && year != null) {
            val exact = byMake.filter {
                it.matchesModel(model) && it.matchesYear(year)
            }
            if (exact.isNotEmpty()) {
                return EcuResolutionResult(
                    entry = exact.first(),
                    protocol = exact.first().protocol,
                    confidence = EcuResolutionResult.Confidence.EXACT,
                    alternatives = exact.drop(1),
                    reason = "Точное совпадение: $make $model $year",
                )
            }
        }

        // ---- Шаг 2: PARTIAL (марка + модель без года, или марка + год без модели) ----
        val partial = mutableListOf<EcuEntry>()

        if (model != null) {
            partial += byMake.filter { it.matchesModel(model) }
        }
        if (year != null) {
            partial += byMake.filter { it.matchesYear(year) }
        }
        partial.distinctBy { it.id }

        if (partial.isNotEmpty()) {
            // Сортируем: сначала CAN, потом KWP2000, потом K-Line.
            // Это эвристика: более современный протокол вероятнее.
            val sorted = partial.sortedBy { it.protocol.ordinal }
            return EcuResolutionResult(
                entry = sorted.first(),
                protocol = sorted.first().protocol,
                confidence = EcuResolutionResult.Confidence.PARTIAL,
                alternatives = sorted.drop(1),
                reason = "Частичное совпадение: $make" +
                        (if (model != null) " $model" else "") +
                        (if (year != null) " $year" else ""),
            )
        }

        // ---- Шаг 3: MAKE_ONLY (только марка) ----
        val sorted = byMake.sortedBy { it.protocol.ordinal }
        return EcuResolutionResult(
            entry = sorted.first(),
            protocol = sorted.first().protocol,
            confidence = EcuResolutionResult.Confidence.MAKE_ONLY,
            alternatives = sorted.drop(1),
            reason = "Найдены только ЭБУ марки '$make'",
        )
    }
}