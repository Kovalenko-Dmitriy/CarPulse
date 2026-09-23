package com.carpulse.obd.domain.vin

/**
 * Определяет модельный год по 10-му символу VIN.
 *
 * Стандарт FMVSS 115 (США, 1980) + ISO 3779. 30-летний цикл:
 *   A..Y → 1980..2000,  1..9 → 2001..2009,
 *   A..Y → 2010..2030,  1..9 → 2031..2039.
 *
 * Как определить, к какому циклу относится буква? По 7-му символу VIN
 * (позиция типа кузова/двигателя). У легковых авто США/Европы:
 *   - 7-й символ — цифра → VIN выпущен до ~2010 года;
 *   - 7-й символ — буква → VIN выпущен после ~2010 года.
 *
 * Ограничение: у грузовиков и мотоциклов правило 7-го символа может
 * отличаться — там возможны ложные срабатывания, но это редкий кейс.
 */
object ModelYearDecoder {

    // Буквы без I, O, Q, U, Z — они запрещены в VIN по стандарту.
    private const val LETTERS = "ABCDEFGHJKLMNPRSTVWXY"
    private const val DIGITS = "123456789"

    fun decode(vin: String): Int? {
        if (vin.length < 10) return null
        val code = vin[9].uppercaseChar()
        val seventh = vin[6].uppercaseChar()
        val seventhIsDigit = seventh.isDigit()

        val letterIdx = LETTERS.indexOf(code)
        if (letterIdx >= 0) {
            return if (seventhIsDigit) 1980 + letterIdx else 2010 + letterIdx
        }
        val digitIdx = DIGITS.indexOf(code)
        if (digitIdx >= 0) {
            return if (seventhIsDigit) 2001 + digitIdx else 2031 + digitIdx
        }
        return null
    }
}