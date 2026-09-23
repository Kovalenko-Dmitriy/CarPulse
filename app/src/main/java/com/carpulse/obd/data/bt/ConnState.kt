package com.carpulse.obd.data.bt

/**
 * Состояние подключения CarPulse к адаптеру и ЭБУ.
 *
 * Подключение проходит в два этапа:
 *   ЭТАП 1 — Bluetooth: открытие RFCOMM-сокета с ELM327.
 *   ЭТАП 2 — OBD: инициализация протокола и связь с ЭБУ.
 */
sealed class ConnState {

    /** Ничего не подключено. */
    object Disconnected : ConnState()

    // ============================================================
    // ЭТАП 1 — Bluetooth
    // ============================================================

    /** Идёт поиск и открытие RFCOMM-сокета с адаптером. */
    object BtConnecting : ConnState()

    /** Сокет открыт, адаптер отвечает на ATZ. */
    data class BtConnected(
        val deviceName: String,
        val mac: String
    ) : ConnState()

    /** Ошибка на этапе Bluetooth. */
    data class BtError(
        val message: String
    ) : ConnState()

    // ============================================================
    // ЭТАП 2 — OBD
    // ============================================================

    /** Bluetooth подключён, идёт инициализация OBD-протокола. */
    object ObdInitializing : ConnState()

    /** OBD-протокол успешно инициализирован. */
    data class ObdConnected(
        val deviceName: String,
        val mac: String,
        val protocol: String
    ) : ConnState()

    /**
     * Bluetooth подключён, но ЭБУ не отвечает.
     * Можно повторить этап 2, не разрывая Bluetooth.
     */
    data class ObdError(
        val mac: String,
        val message: String
    ) : ConnState()

    // ============================================================
    // Утилиты для UI
    // ============================================================

    /** true, если Bluetooth-сокет открыт (включая ObdError — для retry). */
    val isBluetoothReady: Boolean
        get() = this is BtConnected
                || this is ObdInitializing
                || this is ObdConnected
                || this is ObdError

    /** true, если OBD-протокол инициализирован. */
    val isObdReady: Boolean
        get() = this is ObdConnected

    /** MAC текущего/последнего устройства. */
    val currentMac: String?
        get() = when (this) {
            is BtConnected -> mac
            is ObdConnected -> mac
            is ObdError -> mac
            else -> null
        }
}