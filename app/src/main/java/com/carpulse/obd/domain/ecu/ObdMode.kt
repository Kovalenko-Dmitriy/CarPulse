package com.carpulse.obd.domain.ecu

/**
 * Режимы OBD-II / UDS, которые может поддерживать ЭБУ.
 *
 * Служат для UI: показать пользователю, что именно умеет его блок.
 */
enum class ObdMode(val displayName: String) {
    MODE_01("Текущие данные (Mode 01)"),
    MODE_02("Freeze Frame (Mode 02)"),
    MODE_03("Чтение DTC (Mode 03)"),
    MODE_04("Сброс DTC (Mode 04)"),
    MODE_06("Мониторы (Mode 06)"),
    MODE_09("Информация о авто (Mode 09)"),
    MODE_22("Расширенные данные (Mode 22, UDS)"),
}