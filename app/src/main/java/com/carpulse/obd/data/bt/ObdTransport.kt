package com.carpulse.obd.data.bt

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Общий интерфейс транспорта ELM327.
 *
 * Реализации:
 *  - [BluetoothTransport] — Bluetooth Classic SPP (UUID 0x1101)
 *  - [WiFiTransport]      — TCP-сокет (обычно порт 35000)
 *
 * Контракт намеренно минимален: только то, что нужно [ObdManager] и
 * [Elm327Client]. Вся логика чтения фреймов (отделение по `>`),
 * heartbeat и reconnect — внутри реализации.
 *
 * После успешного `connect()` реализация обязана:
 *  - перевести `state` в [ConnState.BtConnected];
 *  - запустить внутренний readLoop, который эмитит фреймы в [incoming]
 *    БЕЗ символа `>` (терминатора ELM327).
 *
 * При обрыве соединения (EOF, exception, heartbeat timeout) реализация
 * обязана:
 *  - перевести `state` в [ConnState.Disconnected] или [ConnState.BtError];
 *  - остановить readLoop;
 *  - закрыть сокет.
 */
interface ObdTransport {

    /** Текущее состояние подключения. UI и ObdManager подписаны на этот поток. */
    val state: StateFlow<ConnState>

    /**
     * Поток входящих фреймов от адаптера — без символа `>`.
     * SharedFlow с буфером; каждый `receive()` возвращает один ответ ELM327
     * (одну строку или многострочный блок, склеенный до терминатора).
     */
    val incoming: SharedFlow<String>

    /**
     * Идентификатор подключённого адаптера.
     *  - BT: MAC (`AA:BB:CC:11:22:33`)
     *  - Wi-Fi: `host:port` (`192.168.1.42:35000`)
     * null, если не подключён.
     */
    val connectedTarget: String?

    /**
     * Подключение к адаптеру.
     *
     * @param target для BT — MAC-адрес; для Wi-Fi — `host` или `host:port`.
     * @return true при успехе, false при ошибке (state → BtError).
     */
    suspend fun connect(target: String): Boolean

    /**
     * Отправка команды. Реализация обязана сама добавить `\r`.
     *
     * @return true, если команда отправлена; false при ошибке сокета.
     */
    suspend fun send(cmd: String): Boolean

    /**
     * Разрыв соединения. Идемпотентно: повторный вызов — no-op.
     */
    suspend fun disconnect()

    /**
     * Установка OBD-состояния (этап 2). Вызывается [ObdManager] после
     * успешной инициализации (`ObdConnected`) или при ошибке (`ObdError`).
     *
     * Транспорт лишь хранит это состояние — сам он не управляет логикой
     * инициализации ЭБУ.
     */
    fun setObdState(newState: ConnState)
}
