package com.carpulse.obd.domain.vin

/**
 * WMI-база (World Manufacturer Identifier).
 *
 * Longest-prefix match: если в базе есть уточнение "JHMCM" (Honda Accord),
 * оно сматчится раньше, чем "JHM" (Honda в целом).
 *
 * Записи приходят из WmiJsonLoader (assets/vin_wmi.json) или из тестов.
 * Сортировка по убыванию длины WMI выполняется один раз в конструкторе —
 * чтобы find() работал корректно без повторной сортировки на каждый вызов.
 *
 * @param entries список записей WMI. Пустой список допустим — тогда
 *                find() всегда вернёт null, а VinDecoder применит
 *                fallback по первому символу VIN.
 */
class WmiDatabase(entries: List<WmiEntry>) {

    /**
     * Записи, отсортированные по убыванию длины WMI.
     * Это нужно для longest-prefix match: сначала проверяем
     * более специфичные коды (6 символов), потом 5, 4, 3.
     */
    private val entries: List<WmiEntry> =
        entries.sortedByDescending { it.wmi.length }

    /**
     * Поиск WMI по VIN.
     *
     * @param vin VIN или его префикс. Регистр не важен — приводим к верхнему.
     * @return найденная запись или null, если ни один WMI не подошёл.
     */
    fun find(vin: String): WmiEntry? {
        if (vin.length < 3) return null
        val upper = vin.uppercase()
        return entries.firstOrNull { upper.startsWith(it.wmi) }
    }

    /** Количество записей в базе. Полезно для логов и тестов. */
    fun size(): Int = entries.size

    /** Все записи. Для UI-списка «выберите производителя вручную». */
    fun all(): List<WmiEntry> = entries

    companion object {
        /**
         * Пустая база. Используется в тестах и как безопасный fallback,
         * если JSON не загрузился.
         */
        val EMPTY: WmiDatabase = WmiDatabase(emptyList())
    }
}