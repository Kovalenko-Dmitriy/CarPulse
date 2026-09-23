package com.carpulse.obd.data.obd

/**
 * Значение с отметкой времени — чтобы отличить свежие данные от протухших.
 */
data class TimedValue(
    val value: Float,
    val at: Long
) {
    /**
     * true, если значение старше [maxAgeMs] миллисекунд.
     * По умолчанию 25 секунд — это больше полного цикла опроса на ISO 9141-2.
     */
    fun isStale(maxAgeMs: Long = 25000L): Boolean =
        System.currentTimeMillis() - at > maxAgeMs
}