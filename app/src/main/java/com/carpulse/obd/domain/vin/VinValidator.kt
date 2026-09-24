package com.carpulse.obd.domain.vin

sealed interface VinValidation {
    data object Valid : VinValidation
    data class Invalid(val reasons: List<Reason>) : VinValidation

    enum class Reason {
        EMPTY,
        WRONG_LENGTH,
        ILLEGAL_CHARS,
    }
}

/**
 * Проверка формата VIN и классификация идентификатора.
 *
 * Контрольная сумма (9-й символ) — стандарт FMVSS 115 — обязательна
 * только для североамериканских VIN. Для европейских/азиатских её
 * проверять НЕЛЬЗЯ: там 9-й символ может быть любым.
 */
object VinValidator {

    private const val VIN_LENGTH = 17
    private val ALLOWED = Regex("^[A-HJ-NPR-Z0-9]+$")   // без I, O, Q

    private val TRANSLIT = mapOf(
        'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
        'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5,
        'P' to 7, 'R' to 9, 'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9,
    )
    private val WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    /**
     * Проверка формата VIN: ровно 17 символов, без I/O/Q.
     */
    fun validate(vin: String): VinValidation {
        val v = vin.trim().uppercase()
        if (v.isEmpty()) {
            return VinValidation.Invalid(listOf(VinValidation.Reason.EMPTY))
        }
        val reasons = mutableListOf<VinValidation.Reason>()
        if (v.length != VIN_LENGTH) reasons += VinValidation.Reason.WRONG_LENGTH
        if (!ALLOWED.matches(v)) reasons += VinValidation.Reason.ILLEGAL_CHARS
        return if (reasons.isEmpty()) VinValidation.Valid
               else VinValidation.Invalid(reasons)
    }

    /**
     * Классификация идентификатора.
     *
     * Правила:
     *  - 17 символов + без запрещённых I/O/Q → VIN
     *  - 13–14 цифр → номер кузова ВАЗ/ГАЗ/УАЗ
     *  - буквы + цифры + дефис, 8–14 символов → японский 車台番号
     *  - 17 символов, но с недопустимыми символами → BODY (номер рамы)
     *  - иначе → UNKNOWN
     */
    fun classify(raw: String): VinKind {
        val v = raw.trim().uppercase()
        if (v.isEmpty()) return VinKind.UNKNOWN

        // Чистый VIN
        if (v.length == VIN_LENGTH && ALLOWED.matches(v)) return VinKind.VIN

        // 13–14 цифр — номер кузова ВАЗ/ГАЗ/УАЗ
        if (v.length in 13..14 && v.all { it.isDigit() }) return VinKind.BODY

        // Японский 車台番号: буквы+цифры, дефис, 8–14 символов
        if (v.length in 8..14 &&
            v.any { it.isLetter() } &&
            v.any { it.isDigit() } &&
            v.all { it.isLetterOrDigit() || it == '-' }
        ) {
            return VinKind.BODY
        }

        // 17 символов, но с I/O/Q или другими символами — номер рамы грузовика
        if (v.length == VIN_LENGTH) return VinKind.BODY

        return VinKind.UNKNOWN
    }

    /**
     * Контрольная сумма FMVSS 115.
     * @return true/false если проверка применима, null — если VIN не NA или невалиден по формату.
     */
    fun checkNorthAmericanCheckDigit(vin: String): Boolean? {
        if (vin.length != VIN_LENGTH) return null
        val v = vin.uppercase()
        if (!ALLOWED.matches(v)) return null
        var sum = 0
        for (i in v.indices) {
            val c = v[i]
            val value = when {
                c.isDigit() -> c - '0'
                TRANSLIT.containsKey(c) -> TRANSLIT.getValue(c)
                else -> return null
            }
            sum += value * WEIGHTS[i]
        }
        val remainder = sum % 11
        val expected = if (remainder == 10) 'X' else remainder.digitToChar()
        return v[8] == expected
    }
}