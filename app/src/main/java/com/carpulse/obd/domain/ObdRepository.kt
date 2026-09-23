package com.carpulse.obd.domain

import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.bt.Elm327Client
import com.carpulse.obd.data.obd.ObdParser
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.obd.TimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ObdRepository(private val client: Elm327Client) {

    companion object {
        private const val UNSUPPORTED_THRESHOLD = 10
        private const val SILENCE_THRESHOLD = 14
        private const val EMPTY_CYCLES_THRESHOLD = 10
        private const val STALE_MS = 25000L
        private const val MAX_RETRIES = 10
        private const val NO_DATA_CYCLES_THRESHOLD = 20
        private const val NO_DATA_POLL_INTERVAL_MS = 5000L

        // Приборная панель — каждый цикл.
        private val ALWAYS_PIDS = listOf(Pid.RPM, Pid.SPEED, Pid.COOLANT)

        // Периодичность опроса остальных групп.
        private const val MID_EVERY = 3
        private const val SLOW_EVERY = 10

        private const val CYCLE_DELAY_MS = 500L
        private const val INTER_PID_DELAY_MS = 80L

        private val ADAPTER_ERRORS_CLEAN = setOf(
            "NODATA", "ERROR", "?", "STOPPED",
            "CANERROR", "BUFFERFULL", "UNABLETOCONNECT"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _live = MutableStateFlow(LiveSnapshot.EMPTY)
    val live: StateFlow<LiveSnapshot> = _live

    private val _battery = MutableStateFlow<TimedValue?>(null)
    val battery: StateFlow<TimedValue?> = _battery

    private val _retryExhausted = MutableStateFlow(false)
    val retryExhausted: StateFlow<Boolean> = _retryExhausted

    private val _noDataMode = MutableStateFlow(false)
    val noDataMode: StateFlow<Boolean> = _noDataMode

    @Volatile
    private var supportedPids: Set<Pid> = emptySet()

    private val unsupportedPids = mutableSetOf<Pid>()
    private val noDataStreak = mutableMapOf<Pid, Int>()
    private var consecutiveTimeouts = 0
    private var emptyCycles = 0
    private var cycle = 0

    private var retryCount = 0

    var onSilence: (suspend () -> Unit)? = null

    @Volatile
    private var customPids: Set<Pid> = emptySet()

    @Volatile
    private var retryInProgress = false

    @Volatile
    private var noDataModeActive = false
    @Volatile
    private var noDataCycles = 0

    // ============================================================
    // Настройка supported PID
    // ============================================================

    fun setSupportedPids(pids: Set<Pid>) {
        supportedPids = pids
        FileLogger.i("OBD_REPO", "supportedPids = ${pids.size}: ${pids.map { it.cmd }}")
    }

    fun setCustomPids(pids: Set<Pid>) {
        customPids = pids
        FileLogger.i("OBD_REPO", "customPids = ${pids.map { it.cmd }}")
    }

    fun isPolling(): Boolean = pollingJob?.isActive == true

    // ============================================================
    // Polling
    // ============================================================

    fun startPolling(intervalMs: Long = 300) {
        if (isPolling()) {
            FileLogger.w("OBD_REPO", "polling уже запущен")
            return
        }

        noDataModeActive = false
        _noDataMode.value = false
        noDataCycles = 0

        pollingJob = scope.launch {
            FileLogger.i("OBD_REPO", "Polling запущен (cycle=${CYCLE_DELAY_MS}ms, interPid=${INTER_PID_DELAY_MS}ms)")
            while (isActive) {
                try {
                    pollOnce()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    FileLogger.e("OBD_REPO", "Ошибка опроса: ${e.message}")
                }

                val delayMs = if (noDataModeActive) NO_DATA_POLL_INTERVAL_MS else CYCLE_DELAY_MS
                delay(delayMs)
            }
        }
    }

    suspend fun stopPollingAndJoin() {
        val job = pollingJob
        pollingJob = null
        try {
            job?.cancelAndJoin()
            FileLogger.i("OBD_REPO", "polling остановлен (join)")
        } catch (e: Exception) {
            FileLogger.e("OBD_REPO", "ошибка остановки polling: ${e.message}")
        }

        noDataModeActive = false
        _noDataMode.value = false
        noDataCycles = 0
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null

        noDataModeActive = false
        _noDataMode.value = false
        noDataCycles = 0
        FileLogger.i("OBD_REPO", "polling остановлен")
    }

    private enum class PollResult { Data, NoData, Error, Timeout }

    private suspend fun pollOnce() {
        cycle++
        val now = System.currentTimeMillis()
        val frame = mutableMapOf<Pid, TimedValue>()

        var gotAnyResponse = false
        var gotNoData = false
        var gotAnyData = false

        // 1. ALWAYS_PIDS (RPM, SPEED, COOLANT) — каждый цикл.
        for (pid in ALWAYS_PIDS) {
            if (!isPollable(pid)) {
                FileLogger.d("OBD_REPO", "CYCLE #$cycle: skip ${pid.cmd} (not pollable)")
                continue
            }
            val result = pollPid(pid, now, frame)
            if (result == PollResult.NoData) gotNoData = true
            if (result != PollResult.Timeout) gotAnyResponse = true
            if (result == PollResult.Data) gotAnyData = true
        }

        // 2. BATTERY (ATRV — AT-команда, не OBD)
        if (cycle % MID_EVERY == 0) {
            val vResp = client.request("ATRV", 1000)
            if (vResp != null) {
                val cleaned = stripEcho("ATRV", vResp)
                ObdParser.battery(cleaned)?.let {
                    _battery.value = TimedValue(it, now)
                    FileLogger.d("OBD_REPO", "BATTERY = $it V")
                }
            }
        }

        // 3. Кастомные PID — КАЖДЫЙ ЦИКЛ (приоритет пользователя).
        //    Раньше было cycle % CUSTOM_EVERY == 0 — раз в 5 циклов.
        //    Теперь — сразу, чтобы «Свои датчики» обновлялись мгновенно.
        if (customPids.isNotEmpty()) {
            for (pid in customPids) {
                if (!isPollable(pid)) continue
                val result = pollPid(pid, now, frame)
                if (result == PollResult.NoData) gotNoData = true
                if (result != PollResult.Timeout) gotAnyResponse = true
                if (result == PollResult.Data) gotAnyData = true
            }
        }

        // 4. Остальные supported — раз в 10 циклов.
        if (cycle % SLOW_EVERY == 0) {
            val alreadyPolled = ALWAYS_PIDS.toSet() + customPids
            val others = supportedPids - alreadyPolled - unsupportedPids
            if (others.isNotEmpty()) {
                FileLogger.d("OBD_REPO", "CYCLE #$cycle: other pids = ${others.map { it.cmd }}")
            }
            for (pid in others) {
                val result = pollPid(pid, now, frame)
                if (result == PollResult.NoData) gotNoData = true
                if (result != PollResult.Timeout) gotAnyResponse = true
                if (result == PollResult.Data) gotAnyData = true
            }
        }

        // ---- Итог цикла ----
        if (frame.isNotEmpty()) {
            FileLogger.d("OBD_REPO", "CYCLE #$cycle: DATA = ${frame.map { "${it.key.name}=${it.value.value}" }}")

            if (retryCount > 0 || _retryExhausted.value) {
                retryCount = 0
                _retryExhausted.value = false
                FileLogger.i("OBD_REPO", "данные получены — сброс retry")
            }
            if (noDataModeActive) {
                noDataModeActive = false
                _noDataMode.value = false
                FileLogger.i("OBD_REPO", "выход из noDataMode — данные получены")
            }
            _live.value = LiveSnapshot(
                values = _live.value.values + frame,
                updatedAt = now
            )
        } else {
            FileLogger.d("OBD_REPO", "CYCLE #$cycle: EMPTY (resp=$gotAnyResponse, nodata=$gotNoData, data=$gotAnyData)")
        }

        // ---- Детекторы молчания ----

        if (consecutiveTimeouts >= SILENCE_THRESHOLD) {
            consecutiveTimeouts = 0
            FileLogger.w("OBD_REPO", "$SILENCE_THRESHOLD таймаутов — retry")
            triggerRetry()
            return
        }

        if (frame.isEmpty()) {
            if (!gotAnyResponse) {
                emptyCycles++
                FileLogger.d("OBD_REPO", "emptyCycles=$emptyCycles (порог $EMPTY_CYCLES_THRESHOLD)")
                if (emptyCycles >= EMPTY_CYCLES_THRESHOLD) {
                    emptyCycles = 0
                    FileLogger.w("OBD_REPO", "$EMPTY_CYCLES_THRESHOLD циклов без ответа адаптера — retry")
                    triggerRetry()
                }
            } else if (gotNoData && !gotAnyData) {
                noDataCycles++
                FileLogger.d("OBD_REPO", "noDataCycles=$noDataCycles (порог $NO_DATA_CYCLES_THRESHOLD)")
                if (noDataCycles >= NO_DATA_CYCLES_THRESHOLD && !noDataModeActive) {
                    noDataModeActive = true
                    _noDataMode.value = true
                    FileLogger.w("OBD_REPO", "noDataMode активирован — двигатель заглушён, опрос раз в ${NO_DATA_POLL_INTERVAL_MS} мс")
                }
            }
        } else {
            emptyCycles = 0
        }
    }

    private fun isPollable(pid: Pid): Boolean {
        if (pid in unsupportedPids) return false
        if (supportedPids.isNotEmpty() && pid !in supportedPids) return false
        return true
    }

    private suspend fun pollPid(pid: Pid, now: Long, frame: MutableMap<Pid, TimedValue>): PollResult {
        delay(INTER_PID_DELAY_MS)

        val resp = client.request(pid.cmd)
        if (resp == null) {
            consecutiveTimeouts++
            FileLogger.d("OBD_REPO", "POLL ${pid.cmd} → TIMEOUT (consecutive=$consecutiveTimeouts)")
            return PollResult.Timeout
        }

        val cleaned = stripEcho(pid.cmd, resp)
        val upper = cleaned.uppercase().replace(" ", "")

        if (upper.startsWith("7F")) {
            FileLogger.w("OBD_REPO", "POLL ${pid.cmd} → NEGATIVE '$cleaned'")
            consecutiveTimeouts++
            return PollResult.Error
        }

        val isError = ADAPTER_ERRORS_CLEAN.any { err -> upper.contains(err) }
        if (isError) {
            if (upper.contains("NODATA")) {
                noDataStreak[pid] = (noDataStreak[pid] ?: 0) + 1
                val streak = noDataStreak[pid]!!
                FileLogger.d("OBD_REPO", "POLL ${pid.cmd} → NO DATA (streak=$streak)")

                // RPM, SPEED, COOLANT — не исключаем.
                if (streak >= UNSUPPORTED_THRESHOLD && pid !in ALWAYS_PIDS) {
                    unsupportedPids.add(pid)
                    FileLogger.w("OBD_REPO", "PID ${pid.cmd} не поддерживается (NO DATA × $UNSUPPORTED_THRESHOLD), исключаю")
                }
                return PollResult.NoData
            }

            FileLogger.w("OBD_REPO", "POLL ${pid.cmd} → ADAPTER ERROR '$cleaned'")
            consecutiveTimeouts++
            return PollResult.Error
        }

        val v = parse(pid, cleaned)
        if (v != null) {
            frame[pid] = TimedValue(v, now)
            noDataStreak[pid] = 0
            consecutiveTimeouts = 0
            FileLogger.d("OBD_REPO", "POLL ${pid.cmd} → OK '$cleaned' = ${pid.name}=$v")
            return PollResult.Data
        } else {
            FileLogger.w("OBD_REPO", "POLL ${pid.cmd} → PARSE FAIL '$cleaned'")
            handleMiss(pid)
            return PollResult.Error
        }
    }

    private fun handleMiss(pid: Pid) {
        val streak = (noDataStreak[pid] ?: 0) + 1
        noDataStreak[pid] = streak

        // RPM, SPEED, COOLANT — не исключаем.
        if (streak >= UNSUPPORTED_THRESHOLD && pid !in ALWAYS_PIDS) {
            unsupportedPids.add(pid)
            FileLogger.w("OBD_REPO", "PID ${pid.cmd} не поддерживается, исключаю")
        }
    }

    private fun stripEcho(cmd: String, raw: String): String {
        val upperCmd = cmd.uppercase()
        val upperRaw = raw.uppercase()
        if (upperRaw.startsWith(upperCmd)) {
            val remainder = raw.substring(cmd.length).trim()
            return if (remainder.isNotEmpty()) remainder else raw
        }
        return raw
    }

    private fun triggerRetry() {
        if (retryInProgress) {
            FileLogger.d("OBD_REPO", "triggerRetry: retry уже идёт, пропускаю")
            return
        }
        if (retryCount >= MAX_RETRIES) {
            if (!_retryExhausted.value) {
                _retryExhausted.value = true
                FileLogger.w("OBD_REPO", "лимит retry исчерпан ($MAX_RETRIES). Продолжаю в пониженном режиме.")
            }
            return
        }
        val callback = onSilence ?: run {
            FileLogger.w("OBD_REPO", "triggerRetry: onSilence = null, пропускаю")
            return
        }
        retryInProgress = true
        scope.launch {
            try {
                retryCount++
                FileLogger.w("OBD_REPO", "retry #$retryCount из $MAX_RETRIES — вызов onSilence")
                callback()
                FileLogger.i("OBD_REPO", "retry #$retryCount завершён")
            } catch (e: kotlinx.coroutines.CancellationException) {
                FileLogger.w("OBD_REPO", "retry отменён")
            } catch (e: Exception) {
                FileLogger.e("OBD_REPO", "ошибка retry: ${e.message}")
            } finally {
                retryInProgress = false
            }
        }
    }

    fun resetRetry() {
        retryCount = 0
        _retryExhausted.value = false
        unsupportedPids.clear()
        noDataStreak.clear()
        emptyCycles = 0
        consecutiveTimeouts = 0
        cycle = 0
        noDataCycles = 0
        noDataModeActive = false
        _noDataMode.value = false
        FileLogger.i("OBD_REPO", "сброс retry и счётчиков")
    }

    fun resetUnsupportedPids() {
        unsupportedPids.clear()
        noDataStreak.clear()
        emptyCycles = 0
        consecutiveTimeouts = 0
        cycle = 0
        FileLogger.i("OBD_REPO", "сброс неподдерживаемых PID")
    }

    fun currentValue(pid: Pid): Float? {
        val tv = _live.value.values[pid] ?: return null
        return if (tv.isStale(STALE_MS)) null else tv.value
    }

    // ============================================================
    // DTC
    // ============================================================

    suspend fun readDtcs(): List<String> {
        FileLogger.i("OBD_REPO", "readDtcs()")
        val resp = client.request("03", 4000) ?: return emptyList()
        return ObdParser.dtcList(resp)
    }

    suspend fun clearDtcs(): Boolean {
        FileLogger.i("OBD_REPO", "clearDtcs()")
        val resp = client.request("04", 4000) ?: return false
        return resp.contains("44") || resp.contains("OK")
    }

    // ============================================================
    // Парсинг
    // ============================================================

    private fun parse(pid: Pid, resp: String): Float? = when (pid) {
        Pid.RPM            -> ObdParser.rpm(resp)
        Pid.SPEED          -> ObdParser.speed(resp)
        Pid.COOLANT        -> ObdParser.coolant(resp)
        Pid.LOAD           -> ObdParser.engineLoad(resp)
        Pid.TIMING         -> ObdParser.timing(resp)
        Pid.STFT1          -> ObdParser.stft(resp)
        Pid.LTFT1          -> ObdParser.ltft(resp)
        Pid.STFT2          -> ObdParser.stft2(resp)
        Pid.LTFT2          -> ObdParser.ltft2(resp)
        Pid.FUEL_PRESSURE  -> ObdParser.fuelPressure(resp)
        Pid.FUEL_RAIL_REL  -> ObdParser.fuelRailRel(resp)
        Pid.FUEL_RAIL_ABS  -> ObdParser.fuelRailAbs(resp)
        Pid.FUEL_LEVEL     -> ObdParser.fuel(resp)
        Pid.MAP            -> ObdParser.map(resp)
        Pid.INTAKE         -> ObdParser.intake(resp)
        Pid.MAF            -> ObdParser.maf(resp)
        Pid.THROTTLE       -> ObdParser.throttle(resp)
        Pid.BARO           -> ObdParser.baro(resp)
        Pid.AMBIENT        -> ObdParser.ambient(resp)
        Pid.THROTTLE_REL   -> ObdParser.throttleRelative(resp)
        Pid.THROTTLE_B     -> ObdParser.throttleB(resp)
        Pid.THROTTLE_C     -> ObdParser.throttleC(resp)
        Pid.ACCEL_D        -> ObdParser.accelD(resp)
        Pid.ACCEL_E        -> ObdParser.accelE(resp)
        Pid.ACCEL_F        -> ObdParser.accelF(resp)
        Pid.O2_B1S1_V      -> ObdParser.o2B1S1(resp)
        Pid.O2_B1S2_V      -> ObdParser.o2B1S2(resp)
        Pid.O2_B1S3_V      -> ObdParser.o2B1S3(resp)
        Pid.O2_B1S4_V      -> ObdParser.o2B1S4(resp)
        Pid.O2_B2S1_V      -> ObdParser.o2B2S1(resp)
        Pid.O2_B2S2_V      -> ObdParser.o2B2S2(resp)
        Pid.EGR_CMD        -> ObdParser.egrCommand(resp)
        Pid.EGR_ERR        -> ObdParser.egrError(resp)
        Pid.EVAP_CMD       -> ObdParser.evapCommand(resp)
        Pid.WARMUPS        -> ObdParser.warmups(resp)
        Pid.DIST_CLEARED   -> ObdParser.distCleared(resp)
        Pid.EVAP_PRESSURE  -> ObdParser.evapPressure(resp)
        Pid.CAT_B1S1       -> ObdParser.catB1S1(resp)
        Pid.CAT_B1S2       -> ObdParser.catB1S2(resp)
        Pid.CAT_B2S1       -> ObdParser.catB2S1(resp)
        Pid.CAT_B2S2       -> ObdParser.catB2S2(resp)
        Pid.VOLTAGE        -> ObdParser.controlVoltage(resp)
        Pid.RUN_TIME       -> ObdParser.runTime(resp)
        Pid.MIL_DISTANCE   -> ObdParser.milDistance(resp)
        Pid.MIL_TIME       -> ObdParser.milTime(resp)
        Pid.TIME_CLEARED   -> ObdParser.timeCleared(resp)
        Pid.FUEL_STATUS    -> null
    }
}