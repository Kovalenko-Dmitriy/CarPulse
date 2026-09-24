package com.carpulse.obd.domain.vin

/**
 * VDS-база. Longest-prefix match по первым символам VIN.
 *
 * Сортировка по убыванию длины префикса выполняется один раз
 * в конструкторе — find() работает за O(n) без повторной сортировки.
 */
class VdsDatabase(entries: List<VdsEntry>) {

    private val entries: List<VdsEntry> =
        entries.sortedByDescending { it.prefix.length }

    /**
     * Поиск VDS по VIN.
     *
     * @param vin VIN или его префикс. Регистр не важен.
     * @return найденная запись или null.
     */
    fun find(vin: String): VdsEntry? {
        if (vin.length < 4) return null
        val upper = vin.uppercase()
        return entries.firstOrNull { upper.startsWith(it.prefix) }
    }

    fun size(): Int = entries.size

    fun all(): List<VdsEntry> = entries

    companion object {
        val EMPTY: VdsDatabase = VdsDatabase(emptyList())
    }
}