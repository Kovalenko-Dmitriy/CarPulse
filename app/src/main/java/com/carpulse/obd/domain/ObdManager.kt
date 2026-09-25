package com.carpulse.obd.domain

import android.content.Context
import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.bt.BluetoothTransport
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.bt.Elm327Client
import com.carpulse.obd.data.bt.ObdTransport
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.obd.SupportedPidsDetector
import com.carpulse.obd.data.obd.TimedValue
import com.carpulse.obd.data.obd.VehicleInfo
import com.carpulse.obd.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Общий менеджер подключения к ELM327.
 *
 * Работает через абстракцию [ObdTransport] — реализации:
 *  - [BluetoothTransport] — Bluetooth Classic SPP;
 *  - [WiFiTransport]      — TCP-сокет (Wi-Fi).
 *
 * Сам транспорт создаётся снаружи (в CarPulseApp) в зависимости от
 * пользовательской настройки `connectionType`. Это значит:
 *  - логика этапов 1/2 не зависит от типа физического соединения;
 *  - смена типа подключения делается пересозданием ObdManager
 *    (или перезапуском приложения — настройка читается на старте).
 *
 * @param context  Application context (пока не используется, оставлен для
 *                 будущих сервисов — например, уведомлений).
 * @param settings хранилище настроек.
 * @param transport активный транспорт (BT или Wi-Fi).
 */
class ObdManager(
    private val context: Context,
    private val settings: SettingsStore,
    val transport: ObdTransport,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val client: Elm327Client = Elm327Client(transport)
    val repository: ObdRepository = ObdRepository(client)

    private val _supportedPids = MutableStateFlow<Set<Pid>>(emptySet())
    val supportedPids: StateFlow<Set<Pid>> = _supportedPids

    // [FIX RACE] Защита от параллельных connectObd().
    // Пока идёт один — остальные вызовы сразу возвращают false.
    // Это критично для PIC18F25K80: два параллельных ATZ сбивают
    // K-Line инициализацию и дают BUS INIT: ...ERROR.
    private val connectInProgress = AtomicBoolean(false)

    // [FIX 2] Флаг защиты от параллельных retry.
    @Volatile
    private var retryInProgress = false

    // [FIX B] Ссылка на TripRepository — нужна, чтобы retry не прерывал
    //         активную поездку. Устанавливается из CarPulseApp после создания.
    @Volatile
    private var tripRepository: TripRepository? = null

    // [FIX DETECT] Флаг: detect() уже запущен в фоне.
    @Volatile
    private var detectInProgress = false

    init {
        FileLogger.i("OBD_MGR", "init: transport=${transport.javaClass.simpleName}")

        repository.onSilence = {
            if (retryInProgress) {
                FileLogger.w("OBD_MGR", "onSilence — retry уже идёт, пропускаю")
            } else if (repository.retryExhausted.value == true) {
                FileLogger.w("OBD_MGR", "onSilence — retry исчерпан, пропускаю")
            } else {
                FileLogger.w("OBD_MGR", "onSilence — retry этапа 2")
                retryInProgress = true
                scope.launch {
                    try {
                        retryObdInternal()
                    } finally {
                        retryInProgress = false
                    }
                }
            }
        }
    }

    /** Вызывается из CarPulseApp после создания TripRepository. */
    fun setTripRepository(repo: TripRepository) {
        FileLogger.i("OBD_MGR", "setTripRepository: ${repo.javaClass.simpleName}")
        tripRepository = repo
    }

    val connection: StateFlow<ConnState> get() = transport.state
    val live: StateFlow<LiveSnapshot> get() = repository.live
    val battery: StateFlow<TimedValue?> get() = repository.battery
    val retryExhausted: StateFlow<Boolean> get() = repository.retryExhausted
    val noDataMode: StateFlow<Boolean> get() = repository.noDataMode

    /**
     * Есть ли физический Bluetooth-адаптер.
     *
     * Возвращает true ТОЛЬКО если активный транспорт — Bluetooth.
     * Для Wi-Fi транспорта возвращает false. Это не ошибка — это
     * информация для UI (например, скрыть выбор BT-устройств).
     */
    val isBluetoothAvailable: Boolean get() = transport is BluetoothTransport

    /**
     * Человекочитаемое имя типа транспорта для UI.
     */
    val transportKind: String
        get() = when (transport) {
            is BluetoothTransport -> "Bluetooth"
            else -> "Wi-Fi"
        }

    private var intervalMs: Long = 300L

    @Volatile
    private var userDisconnected = false

    private var autoReconnectJob: Job? = null

    // [FIX 3] Backoff для retry — 5 → 10 → 20 → 30 сек.
    private var silenceRetryDelayMs = 5000L

    // ============================================================
    // ЭТАП 1. Открытие транспорта (BT или Wi-Fi)
    // ============================================================

    /**
     * Подключение к адаптеру на транспортном уровне.
     *
     * @param target для BT — MAC-адрес (`AA:BB:CC:11:22:33`);
     *               для Wi-Fi — `host` или `host:port` (`192.168.0.10:35000`).
     */
    suspend fun connectBluetooth(target: String): Boolean = withContext(Dispatchers.IO) {
        FileLogger.i("OBD_MGR", "ЭТАП 1 — подключение к $target (${transportKind})")
        val ok = transport.connect(target)
        if (ok) {
            FileLogger.i("OBD_MGR", "ЭТАП 1 завершён успешно")
        } else {
            FileLogger.e("OBD_MGR", "ЭТАП 1 — ошибка транспорта")
        }
        ok
    }

    /**
     * Алиас для Wi-Fi-подключения. Семантически — то же самое, что
     * [connectBluetooth], но позволяет UI выражаться точнее.
     */
    suspend fun connectWiFi(target: String): Boolean = connectBluetooth(target)

    // ============================================================
    // ЭТАП 2. OBD
    // ============================================================

    /**
     * [FIX RACE] Защищён от параллельных вызовов через AtomicBoolean.
     *
     * Если второй вызов приходит, пока первый ещё выполняется,
     * он сразу возвращает false — иначе два параллельных ATZ
     * сбивают K-Line инициализацию.
     */
    suspend fun connectObd(startPollingAfter: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            // [FIX RACE] Защита от повторного входа.
            if (!connectInProgress.compareAndSet(false, true)) {
                FileLogger.w("OBD_MGR", "connectObd уже идёт, пропускаю")
                return@withContext false
            }

            try {
                FileLogger.i("OBD_MGR", "connectObd(startPollingAfter=$startPollingAfter) start")

                if (!transport.state.value.isBluetoothReady) {
                    FileLogger.w(
                        "OBD_MGR",
                        "ЭТАП 2 невозможен — транспорт не подключён (state=${transport.state.value})",
                    )
                    return@withContext false
                }

                val target = transport.connectedTarget
                if (target.isNullOrBlank()) {
                    FileLogger.w("OBD_MGR", "ЭТАП 2 невозможен — target неизвестен")
                    return@withContext false
                }

                // [FIX 1] skipReset вычисляется ДО setObdState(ObdInitializing).
                val skipReset = transport.state.value is ConnState.ObdConnected
                FileLogger.i("OBD_MGR", "ЭТАП 2 — инициализация OBD (target=$target, skipReset=$skipReset)")

                transport.setObdState(ConnState.ObdInitializing)
                delay(1500)

                FileLogger.i("OBD_MGR", "initializeObd(skipReset=$skipReset)")
                val result = client.initializeObd(skipReset = skipReset)
                FileLogger.i("OBD_MGR", "initializeObd result=$result")

                when (result) {
                    is Elm327Client.ObdInitResult.Success -> {
                        transport.setObdState(
                            ConnState.ObdConnected(
                                deviceName = "ELM327",
                                mac = target,
                                protocol = result.protocol,
                            ),
                        )

                        // [FIX DETECT] Загружаем supportedPids из SettingsStore.
                        if (_supportedPids.value.isEmpty()) {
                            val saved = settings.settings.first().supportedPids
                            if (saved.isNotEmpty()) {
                                val pids = saved.mapNotNull { cmd ->
                                    Pid.entries.firstOrNull { it.cmd == cmd }
                                }.toSet()
                                if (pids.isNotEmpty()) {
                                    _supportedPids.value = pids
                                    repository.setSupportedPids(pids)
                                    FileLogger.i("OBD_MGR", "supportedPids из SettingsStore: ${pids.size}")
                                }
                            }

                            if (_supportedPids.value.isEmpty()) {
                                startDetectInBackground(repository)
                            }
                        } else {
                            FileLogger.d("OBD_MGR", "supportedPids уже в памяти (${_supportedPids.value.size})")
                        }

                        if (startPollingAfter) {
                            FileLogger.i("OBD_MGR", "startPolling(intervalMs=$intervalMs)")
                            repository.startPolling(intervalMs)
                        }

                        FileLogger.i(
                            "OBD_MGR",
                            "ЭТАП 2 завершён. Протокол: ${result.protocol}, поддерживаемых PID: ${_supportedPids.value.size}",
                        )
                        true
                    }

                    is Elm327Client.ObdInitResult.Error -> {
                        val currentState = transport.state.value
                        if (currentState is ConnState.Disconnected) {
                            FileLogger.w("OBD_MGR", "ЭТАП 2 — сокет мёртв")
                        } else {
                            transport.setObdState(ConnState.ObdError(target, result.message))
                            FileLogger.e("OBD_MGR", "ЭТАП 2 — ошибка: ${result.message}")
                        }
                        false
                    }
                }
            } finally {
                // [FIX RACE] Сбрасываем флаг — следующий вызов может начаться.
                connectInProgress.set(false)
            }
        }

    // ============================================================
    // detect() в фоне
    // ============================================================

    private fun startDetectInBackground(repo: ObdRepository) {
        if (detectInProgress) {
            FileLogger.d("OBD_MGR", "detect() уже идёт, пропускаю")
            return
        }
        detectInProgress = true
        scope.launch {
            try {
                FileLogger.i("OBD_MGR", "detect() в фоне — start")
                val detector = SupportedPidsDetector(client)
                val supported = detector.detect()
                _supportedPids.value = supported
                repo.setSupportedPids(supported)
                FileLogger.i("OBD_MGR", "detect() в фоне — готово: ${supported.size}")

                settings.setSupportedPids(supported.map { it.cmd }.toSet())
                FileLogger.i("OBD_MGR", "supportedPids сохранены в SettingsStore")
            } catch (e: Exception) {
                FileLogger.e("OBD_MGR", "detect() в фоне упал: ${e.message}")
            } finally {
                detectInProgress = false
            }
        }
    }

    /** Ручной запуск detect() — по кнопке в настройках. */
    fun redetectSupportedPids() {
        FileLogger.i("OBD_MGR", "redetectSupportedPids() по запросу")
        startDetectInBackground(repository)
    }

    // ============================================================
    // Retry этапа 2
    // ============================================================

    private suspend fun retryObdInternal() {
        FileLogger.i("OBD_MGR", "retryObdInternal start, state=${transport.state.value}")

        // [FIX RACE] Если connectObd уже идёт — не мешаем.
        if (connectInProgress.get()) {
            FileLogger.w("OBD_MGR", "retry невозможен — connectObd уже идёт")
            return
        }

        val state = transport.state.value
        if (state !is ConnState.BtConnected
            && state !is ConnState.ObdConnected
            && state !is ConnState.ObdError
            && state !is ConnState.ObdInitializing
        ) {
            FileLogger.w("OBD_MGR", "retry невозможен, сокет мёртв (state=$state)")
            return
        }

        // [FIX B] НЕ прерываем активную поездку.
        val tripActive = tripRepository?.isActive() ?: false
        FileLogger.i("OBD_MGR", "retry: tripActive=$tripActive")
        if (tripActive) {
            FileLogger.w("OBD_MGR", "retry — поездка активна, жду 15 сек")
            delay(15_000)
            if (tripRepository?.isActive() == true) {
                FileLogger.w("OBD_MGR", "retry — поездка всё ещё активна, отменяю retry")
                return
            }
        }

        FileLogger.i("OBD_MGR", "retry: stopPollingAndJoin")
        repository.stopPollingAndJoin()

        FileLogger.i("OBD_MGR", "retry: delay ${silenceRetryDelayMs} мс")
        delay(silenceRetryDelayMs)

        // [FIX RACE] Ещё раз проверяем, не начал ли кто-то другой connectObd.
        if (connectInProgress.get()) {
            FileLogger.w("OBD_MGR", "retry: connectObd уже идёт, пропускаю")
            return
        }

        FileLogger.i("OBD_MGR", "retry: connectObd(startPollingAfter=false)")
        val ok = connectObd(startPollingAfter = false)
        FileLogger.i("OBD_MGR", "retry: connectObd=$ok")

        if (ok) {
            silenceRetryDelayMs = 5000L
            FileLogger.i("OBD_MGR", "retry: startPolling($intervalMs)")
            repository.startPolling(intervalMs)
        } else {
            silenceRetryDelayMs = (silenceRetryDelayMs * 2).coerceAtMost(30_000L)
            FileLogger.w("OBD_MGR", "retry: connectObd failed, следующий delay=${silenceRetryDelayMs}")
        }

        FileLogger.i("OBD_MGR", "retry завершён: $ok (следующая задержка: ${silenceRetryDelayMs} мс)")
    }

    // ============================================================
    // Авто-реконнект
    // ============================================================

    fun startAutoReconnect(
        autoConnectProvider: suspend () -> Boolean,
        lastTargetProvider: suspend () -> String?,
    ) {
        FileLogger.i("OBD_MGR", "startAutoReconnect (${transportKind})")
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            transport.state.collect { state ->
                if (userDisconnected) return@collect

                if (retryInProgress) {
                    FileLogger.d("AUTO", "retry уже идёт, пропускаю autoReconnect")
                    return@collect
                }

                // [FIX RACE] Если connectObd уже идёт — не запускаем параллельный tryReconnect.
                if (connectInProgress.get()) {
                    FileLogger.d("AUTO", "connectObd уже идёт, пропускаю autoReconnect")
                    return@collect
                }

                val shouldReconnect = when (state) {
                    is ConnState.Disconnected -> true
                    is ConnState.ObdError -> true
                    else -> false
                }
                if (shouldReconnect && autoConnectProvider()) {
                    val target = lastTargetProvider() ?: return@collect
                    FileLogger.w("AUTO", "связь потеряна, пробую восстановить к $target")
                    tryReconnect(target)
                }
            }
        }
    }

    private suspend fun tryReconnect(target: String) {
        var delayMs = 1000L
        repeat(5) { attempt ->
            delay(delayMs)

            // [FIX RACE] Если connectObd уже идёт — ждём следующей попытки.
            if (connectInProgress.get()) {
                FileLogger.d("AUTO", "попытка ${attempt + 1}/5 — connectObd уже идёт, пропускаю")
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
                return@repeat
            }

            val state = transport.state.value
            val socketDead = state is ConnState.Disconnected

            FileLogger.i("AUTO", "попытка ${attempt + 1}/5 через ${delayMs} мс (socketDead=$socketDead)")

            if (socketDead) {
                _supportedPids.value = emptySet()
                if (transport.connect(target) && connectObd()) {
                    FileLogger.i("AUTO", "восстановлено (полное)")
                    return
                }
            } else {
                if (connectObd()) {
                    FileLogger.i("AUTO", "восстановлено (retry этапа 2)")
                    return
                }
            }
            delayMs = (delayMs * 2).coerceAtMost(30_000L)
        }
        FileLogger.e("AUTO", "5 попыток не удались")
    }

    fun stopAutoReconnect() {
        FileLogger.i("OBD_MGR", "stopAutoReconnect")
        autoReconnectJob?.cancel()
        autoReconnectJob = null
    }

    // ============================================================
    // Mode 09 / DTC
    // ============================================================

    suspend fun readVehicleInfo(): VehicleInfo? {
        FileLogger.i("OBD_MGR", "readVehicleInfo()")
        return client.readVehicleInfo()
    }

    suspend fun readDtcs(): List<String> {
        FileLogger.i("OBD_MGR", "readDtcs()")
        return repository.readDtcs()
    }

    suspend fun clearDtcs(): Boolean {
        FileLogger.i("OBD_MGR", "clearDtcs()")
        return repository.clearDtcs()
    }

    // ============================================================
    // Отключение и настройки
    // ============================================================

    fun disconnect() {
        FileLogger.i("OBD_MGR", "disconnect()")
        userDisconnected = true
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        repository.stopPolling()
        scope.launch { transport.disconnect() }
    }

    fun reconnectAfterUserAction() {
        FileLogger.i("OBD_MGR", "reconnectAfterUserAction()")
        userDisconnected = false
        silenceRetryDelayMs = 5000L
        repository.resetRetry()
    }

    fun setCustomPids(pids: Set<Pid>) {
        FileLogger.i("OBD_MGR", "setCustomPids(${pids.map { it.cmd }})")
        repository.setCustomPids(pids)
    }

    fun resetRetry() {
        FileLogger.i("OBD_MGR", "resetRetry()")
        repository.resetRetry()
    }

    fun setPollingInterval(ms: Long) {
        FileLogger.i("OBD_MGR", "setPollingInterval($ms)")
        intervalMs = ms
        if (repository.isPolling()) {
            repository.stopPolling()
            repository.startPolling(ms)
        }
    }
}