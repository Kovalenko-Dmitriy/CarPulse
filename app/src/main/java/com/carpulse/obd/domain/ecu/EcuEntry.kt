package com.carpulse.obd.domain.ecu

/**
 * Профиль ЭБУ. Одна запись в базе ecu_database.json.
 *
 * @param id             уникальный идентификатор ("vaz_itelma_m86")
 * @param make           марка ("Lada", "Hyundai")
 * @param model          модели (может быть список через запятую)
 * @param yearFrom       год начала выпуска
 * @param yearTo         год окончания (null = по настоящее время)
 * @param ecuName        человекочитаемое имя ЭБУ ("Ителма M86")
 * @param protocol       транспортный протокол
 * @param canRequestId   CAN-ID запроса (null для K-Line)
 * @param canResponseId  CAN-ID ответа (null для K-Line)
 * @param initLines      AT-команды инициализации (пустой список = не нужны)
 * @param pidClass       класс набора PID
 * @param extraPids      дополнительные PID поверх класса
 * @param notes          произвольная заметка (для UI)
 */
data class EcuEntry(
    val id: String,
    val make: String,
    val model: String,
    val yearFrom: Int,
    val yearTo: Int?,
    val ecuName: String,
    val protocol: Protocol,
    val canRequestId: String?,
    val canResponseId: String?,
    val initLines: List<String>,
    val pidClass: PidClass,
    val extraPids: List<String> = emptyList(),
    val notes: String? = null,
) {
    /**
     * Подходит ли этот ЭБУ для указанного года выпуска.
     * Если yearTo == null — считается, что выпускается до сих пор.
     */
    fun matchesYear(year: Int?): Boolean {
        if (year == null) return true
        val to = yearTo ?: Int.MAX_VALUE
        return year in yearFrom..to
    }

    /**
     * Подходит ли ЭБУ для указанной модели.
     * Сравнение — подстрока без учёта регистра, потому что
     * в JSON модель может быть перечислением ("Tiggo 4 / 4 Pro").
     */
    fun matchesModel(query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        return model.contains(query, ignoreCase = true)
    }
}