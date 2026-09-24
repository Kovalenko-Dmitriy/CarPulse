package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.Region

/**
 * Определяет модельный год по 10-му символу VIN.
 *
 * 30-летний цикл FMVSS 115 / ISO 3779:
 *   A..Y → 1980..2000  (7-й символ — цифра)
 *   A..Y → 2010..2030  (7-й символ — буква)
 *   1..9 → 2001..2009  (7-й символ — цифра)
 *   1..9 → 2031..2039  (7-й символ — буква)
 *
 * Эвристика по региону:
 *   Правило «7-й символ-цифра = старый цикл» работает ТОЛЬКО для
 *   североамериканских VIN (FMVSS 115). У европейских, азиатских
 *   и части корейских/китайских VIN 7-й символ — цифра, но год
 *   после 2010. Из-за этого «сырой» расчёт даёт год на 30 лет старше.
 *
 *   Если регион — EU или ASIA, и результат старого цикла < 2000,
 *   сдвигаем год на +30. Для US и RU эвристика не применяется:
 *     - US: правило FMVSS 115 работает корректно;
 *     - RU: у Lada/ГАЗ/УАЗ 7-й символ-цифра действительно означает
 *           старый цикл (машины 1980–2009).
 *
 * @param vin    нормализованный VIN (верхний регистр)
 * @param region регион (из WMI-базы или fallback по 1-му символу VIN).
 *               null → эвристика не применяется.
 */
object ModelYearDecoder {

    private const val LETTERS = "ABCDEFGHJKLMNPRSTVWXY"
    private const val DIGITS = "123456789"

    /**
     * Порог «слишком старый год» для эвристики.
     * Всё, что старше 2000 и определено по «старому циклу», для EU/ASIA
     * считаем ошибкой на 30 лет.
     */
    private const val HEURISTIC_THRESHOLD = 2000

    fun decode(vin: String, region: Region? = null): Int? {
        if (vin.length < 10) return null

        val code = vin[9].uppercaseChar()
        val seventh = vin[6].uppercaseChar()
        val seventhIsDigit = seventh.isDigit()

        val applyHeuristic = region == Region.EU || region == Region.ASIA

        // Буква года.
        val letterIdx = LETTERS.indexOf(code)
        if (letterIdx >= 0) {
            val oldCycle = 1980 + letterIdx
            val newCycle = 2010 + letterIdx

            if (seventhIsDigit && applyHeuristic && oldCycle < HEURISTIC_THRESHOLD) {
                return newCycle
            }
            return if (seventhIsDigit) oldCycle else newCycle
        }

        // Цифра года.
        val digitIdx = DIGITS.indexOf(code)
        if (digitIdx >= 0) {
            val oldCycle = 2001 + digitIdx
            val newCycle = 2031 + digitIdx

            if (seventhIsDigit && applyHeuristic && oldCycle < HEURISTIC_THRESHOLD) {
                return newCycle
            }
            return if (seventhIsDigit) oldCycle else newCycle
        }

        return null
    }
}