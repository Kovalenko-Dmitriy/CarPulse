package com.carpulse.obd.domain.vin.jdm

/**
 * JDM-база (японские 車台番号 / номера кузова).
 *
 * Longest-prefix match: если в базе есть уточнение "EU1" (Civic Ferio)
 * и "EU" (общий Civic), то "EU11030002" сматчится на "EU1", а не "EU".
 *
 * Сортировка по убыванию длины префикса выполняется один раз
 * в конструкторе — find() работает за O(n) без повторной сортировки.
 */
class JdmDatabase(entries: List<JdmEntry>) {

    private val entries: List<JdmEntry> =
        entries.sortedByDescending { it.prefix.length }

    /**
     * Поиск JDM-записи по номеру кузова.
     *
     * @param input исходная строка от пользователя. Регистр не важен.
     * @return найденная запись или null.
     */
    fun find(input: String): JdmEntry? {
        if (input.length < 2) return null
        val upper = input.uppercase()
        return entries.firstOrNull { upper.startsWith(it.prefix) }
    }

    /**
     * Поиск с указанием уверенности.
     *
     * Уверенность:
     *  - EXACT — совпал префикс длиной ≥ 4 символов;
     *  - PARTIAL — совпал префикс 2–3 символа;
     *  - NONE — ничего не найдено.
     */
    fun findWithConfidence(input: String): JdmDecodeResult {
        val entry = find(input)
        val confidence = when {
            entry == null -> JdmDecodeResult.Confidence.NONE
            entry.prefix.length >= 4 -> JdmDecodeResult.Confidence.EXACT
            else -> JdmDecodeResult.Confidence.PARTIAL
        }
        return JdmDecodeResult(
            input = input.trim().uppercase(),
            entry = entry,
            confidence = confidence,
        )
    }

    fun size(): Int = entries.size

    fun all(): List<JdmEntry> = entries

    companion object {
        val EMPTY: JdmDatabase = JdmDatabase(emptyList())
    }
}