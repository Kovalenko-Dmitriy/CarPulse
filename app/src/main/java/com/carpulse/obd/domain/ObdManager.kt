package com.carpulse.obd.domain

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.bt.BluetoothTransport
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.bt.Elm327Client
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

class ObdManager(
    ctx: Context,
    private val settings: SettingsStore
) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val transport: BluetoothTransport?
    val client: Elm327Client?
    val repository: ObdRepository?

    private val _supportedPids = MutableStateFlow<Set<Pid>>(emptySet())
    val supportedPids: StateFlow<Set<Pid>> = _supportedPids

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
        FileLogger.i("OBD_MGR", "init: adapter=${adapter != null}")
        if (adapter != null) {
            val t = BluetoothTransport(adapter)
            val c = Elm327Client(t)
            val repo = ObdRepository(c)
            transport = t
            client = c
            repository = repo
            FileLogger.i("OBD_MGR", "BluetoothTransport + Elm327Client + ObdRepository созданы")

            repo.onSilence = {
                if (retryInProgress) {
                    FileLogger.w("OBD_MGR", "onSilence — retry уже идёт, пропускаю")
                } else if (repository?.retryExhausted?.value == true) {
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
        } else {
            transport = null
            client = null
            repository = null
            FileLogger.e("OBD_MGR", "BluetoothAdapter = null — OBD недоступен")
        }
    }

    /** Вызывается из CarPulseApp после создания TripRepository. */
    fun setTripRepository(repo: TripRepository) {
        FileLogger.i("OBD_MGR", "setTripRepository: ${repo.javaClass.simpleName}")
        tripRepository = repo
    }

    val connection: StateFlow<ConnState>? get() = transport?.state
    val live: StateFlow<LiveSnapshot>? get() = repository?.live
    val battery: StateFlow<TimedValue?>? get() = repository?.battery
    val retryExhausted: StateFlow<Boolean>? get() = repository?.retryExhausted
    val noDataMode: StateFlow<Boolean>? get() = repository?.noDataMode

    val isBluetoothAvailable: Boolean get() = adapter != null

    private var intervalMs: Long = 300L

    @Volatile
    private var userDisconnected = false

    private var autoReconnectJob: Job? = null

    // [FIX 3] Backoff для retry — 5 → 10 → 20 → 30 сек.
    private var silenceRetryDelayMs = 5000L

    // ============================================================
    // ЭТАП 1. Bluetooth
    // ============================================================

    @SuppressLint("MissingPermission")
    suspend fun connectBluetooth(mac: String): Boolean = withContext(Dispatchers.IO) {
        val t = transport ?: run {
            FileLogger.e("OBD_MGR", "connectBluetooth: transport = null")
            return@withContext false
        }
        FileLogger.i("OBD_MGR", "ЭТАП 1 — подключение Bluetooth к $mac")
        val ok = t.connect(mac)
        if (ok) {
            FileLogger.i("OBD_MGR", "ЭТАП 1 завершён успешно")
        } else {
            FileLogger.e("OBD_MGR", "ЭТАП 1 — ошибка Bluetooth")
        }
        ok
    }

    // ============================================================
    // ЭТАП 2. OBD
    // ============================================================

    suspend fun connectObd(startPollingAfter: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        FileLogger.i("OBD_MGR", "connectObd(startPollingAfter=$startPollingAfter) start")
        val t = transport ?: run {
            FileLogger.e("OBD_MGR", "connectObd: transport = null")
            return@withContext false
        }
        val c = client ?: run {
            FileLogger.e("OBD_MGR", "connectObd: client = null")
            return@withContext false
        }
        val repo = repository ?: run {
            FileLogger.e("OBD_MGR", "connectObd: repository = null")
            return@withContext false
        }

        if (!t.state.value.isBluetoothReady) {
            FileLogger.w("OBD_MGR", "ЭТАП 2 невозможен — Bluetooth не подключён (state=${t.state.value})")
            return@withContext false
        }

        val mac = t.connectedMac
        if (mac.isNullOrBlank()) {
            FileLogger.w("OBD_MGR", "ЭТАП 2 невозможен — MAC неизвестен")
            return@withContext false
        }

        // [FIX 1] skipReset вычисляется ДО setObdState(ObdInitializing).
        val skipReset = t.state.value is ConnState.ObdConnected
        FileLogger.i("OBD_MGR", "ЭТАП 2 — инициализация OBD (mac=$mac, skipReset=$skipReset)")
        t.setObdState(ConnState.ObdInitializing)

        delay(1500)

        FileLogger.i("OBD_MGR", "initializeObd(skipReset=$skipReset)")
        val result = c.initializeObd(skipReset = skipReset)
        FileLogger.i("OBD_MGR", "initializeObd result=$result")
        when (result) {
            is Elm327Client.ObdInitResult.Success -> {
                t.setObdState(ConnState.ObdConnected("ELM327", mac, result.protocol))

                // [FIX DETECT] Загружаем supportedPids из SettingsStore.
                //   Если есть — используем, detect() НЕ вызываем.
                //   Если нет — запускаем detect() В ФОНЕ, чтобы не блокировать UI.
                if (_supportedPids.value.isEmpty()) {
                    val saved = settings.settings.first().supportedPids
                    if (saved.isNotEmpty()) {
                        val pids = saved.mapNotNull { cmd ->
                            Pid.entries.firstOrNull { it.cmd == cmd }
                        }.toSet()
                        if (pids.isNotEmpty()) {
                            _supportedPids.value = pids
                            repo.setSupportedPids(pids)
                            FileLogger.i("OBD_MGR", "supportedPids из SettingsStore: ${pids.size}")
                        }
                    }

                    if (_supportedPids.value.isEmpty()) {
                        // Нет сохранённых — detect() в фоне.
                        startDetectInBackground(c, repo)
                    }
                } else {
                    FileLogger.d("OBD_MGR", "supportedPids уже в памяти (${_supportedPids.value.size})")
                }

                // [FIX DETECT] Polling запускается СРАЗУ — приборная панель
                //              и customPids работают, пока detect() идёт в фоне.
                if (startPollingAfter) {
                    FileLogger.i("OBD_MGR", "startPolling(intervalMs=$intervalMs)")
                    repo.startPolling(intervalMs)
                }

                FileLogger.i("OBD_MGR", "ЭТАП 2 завершён. Протокол: ${result.protocol}, поддерживаемых PID: ${_supportedPids.value.size}")
                true
            }
            is Elm327Client.ObdInitResult.Error -> {
                val currentState = t.state.value
                if (currentState is ConnState.Disconnected) {
                    FileLogger.w("OBD_MGR", "ЭТАП 2 — сокет мёртв")
                } else {
                    t.setObdState(ConnState.ObdError(mac, result.message))
                    FileLogger.e("OBD_MGR", "ЭТАП 2 — ошибка: ${result.message}")
                }
                false
            }
        }
    }

    // ============================================================
    //  detect() в фоне
    // ============================================================

    private fun startDetectInBackground(c: Elm327Client, repo: ObdRepository) {
        if (detectInProgress) {
            FileLogger.d("OBD_MGR", "detect() уже идёт, пропускаю")
            return
        }
        detectInProgress = true
        scope.launch {
            try {
                FileLogger.i("OBD_MGR", "detect() в фоне — start")
                val detector = SupportedPidsDetector(c)
                val supported = detector.detect()
                _supportedPids.value = supported
                repo.setSupportedPids(supported)
                FileLogger.i("OBD_MGR", "detect() в фоне — готово: ${supported.size}")

                // Сохраняем в SettingsStore для следующих подключений.
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
        val c = client ?: return
        val repo = repository ?: return
        FileLogger.i("OBD_MGR", "redetectSupportedPids() по запросу")
        startDetectInBackground(c, repo)
    }

    // ============================================================
    // Retry этапа 2
    // ============================================================

    private suspend fun retryObdInternal() {
        FileLogger.i("OBD_MGR", "retryObdInternal start, state=${transport?.state?.value}")

        val state = transport?.state?.value
        if (state !is ConnState.BtConnected
            && state !is ConnState.ObdConnected
            && state !is ConnState.ObdError
            && state !is ConnState.ObdInitializing) {
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
        repository?.stopPollingAndJoin()

        FileLogger.i("OBD_MGR", "retry: delay ${silenceRetryDelayMs} мс")
        delay(silenceRetryDelayMs)

        FileLogger.i("OBD_MGR", "retry: connectObd(startPollingAfter=false)")
        val ok = connectObd(startPollingAfter = false)
        FileLogger.i("OBD_MGR", "retry: connectObd=$ok")
        if (ok) {
            silenceRetryDelayMs = 5000L
            FileLogger.i("OBD_MGR", "retry: startPolling($intervalMs)")
            repository?.startPolling(intervalMs)
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
        lastMacProvider: suspend () -> String?
    ) {
        FileLogger.i("OBD_MGR", "startAutoReconnect")
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            transport?.state?.collect { state ->
                if (userDisconnected) return@collect

                if (retryInProgress) {
                    FileLogger.d("AUTO", "retry уже идёт, пропускаю autoReconnect")
                    return@collect
                }

                val shouldReconnect = when (state) {
                    is ConnState.Disconnected -> true
                    is ConnState.ObdError -> true
                    else -> false
                }
                if (shouldReconnect && autoConnectProvider()) {
                    val mac = lastMacProvider() ?: return@collect
                    FileLogger.w("AUTO", "связь потеряна, пробую восстановить к $mac")
                    tryReconnect(mac)
                }
            }
        }
    }

    private suspend fun tryReconnect(mac: String) {
        var delayMs = 1000L
        repeat(5) { attempt ->
            delay(delayMs)
            val state = transport?.state?.value
            val socketDead = state is ConnState.Disconnected

            FileLogger.i("AUTO", "попытка ${attempt + 1}/5 через ${delayMs} мс (socketDead=$socketDead)")

            if (socketDead) {
                _supportedPids.value = emptySet()
                if (connectBluetooth(mac) && connectObd()) {
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
        return client?.readVehicleInfo()
    }

    suspend fun readDtcs(): List<String> {
        FileLogger.i("OBD_MGR", "readDtcs()")
        return repository?.readDtcs() ?: emptyList()
    }

    suspend fun clearDtcs(): Boolean {
        FileLogger.i("OBD_MGR", "clearDtcs()")
        return repository?.clearDtcs() ?: false
    }

    // ============================================================
    // Отключение и настройки
    // ============================================================

    fun disconnect() {
        FileLogger.i("OBD_MGR", "disconnect()")
        userDisconnected = true
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        repository?.stopPolling()
        scope.launch { client?.disconnect() }
    }

    fun reconnectAfterUserAction() {
        FileLogger.i("OBD_MGR", "reconnectAfterUserAction()")
        userDisconnected = false
        silenceRetryDelayMs = 5000L
        repository?.resetRetry()
    }

    fun setCustomPids(pids: Set<Pid>) {
        FileLogger.i("OBD_MGR", "setCustomPids(${pids.map { it.cmd }})")
        repository?.setCustomPids(pids)
    }

    fun resetRetry() {
        FileLogger.i("OBD_MGR", "resetRetry()")
        repository?.resetRetry()
    }

    fun setPollingInterval(ms: Long) {
        FileLogger.i("OBD_MGR", "setPollingInterval($ms)")
        intervalMs = ms
        val repo = repository ?: return
        if (repo.isPolling()) {
            repo.stopPolling()
            repo.startPolling(ms)
        }
    }
}