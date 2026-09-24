package com.carpulse.obd.domain.ecu

/**
 * База ЭБУ с индексами для быстрого поиска.
 *
 * При загрузке строит три индекса:
 *  - byMake: марка → список ЭБУ (поиск за O(1))
 *  - byMakeModel: (марка, модель) → список ЭБУ
 *  - byId: id → ЭБУ (для прямого доступа)
 *
 * Индексы строятся один раз при конструировании (99 записей —
 * это микросекунды, но при росте до 1000+ это критично).
 */
class EcuDatabase(private val entries: List<EcuEntry>) {

    private val byMake: Map<String, List<EcuEntry>> =
        entries.groupBy { it.make.lowercase() }

    private val byId: Map<String, EcuEntry> =
        entries.associateBy { it.id }

    private val byMakeModel: Map<Pair<String, String>, List<EcuEntry>> =
        entries.groupBy { it.make.lowercase() to it.model.lowercase() }

    /** Все записи. Для UI-списка «выберите ЭБУ вручную». */
    fun all(): List<EcuEntry> = entries

    /** Поиск по id. */
    fun findById(id: String): EcuEntry? = byId[id]

    /** Все ЭБУ указанной марки. */
    fun findByMake(make: String): List<EcuEntry> =
        byMake[make.lowercase()].orEmpty()

    /** Все ЭБУ указанной марки и модели. */
    fun findByMakeModel(make: String, model: String): List<EcuEntry> =
        byMakeModel[make.lowercase() to model.lowercase()].orEmpty()

    /** Марки, представленные в базе. Для UI. */
    fun allMakes(): List<String> =
        entries.map { it.make }.distinct().sorted()

    fun size(): Int = entries.size

    companion object {
        val EMPTY = EcuDatabase(emptyList())
    }
}