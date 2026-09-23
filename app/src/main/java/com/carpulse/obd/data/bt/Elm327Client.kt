package com.carpulse.obd.data.bt

import android.util.Log
import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.obd.VehicleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Elm327Client, заточенный под Honda Civic EU1 (7-е поколение, 2001–2005).
 *
 * Протокол: ISO 9141-2, 5 baud init, 10.4 kbaud.
 * Адрес ЭБУ: 6A. Header: DA F1 10. Wakeup: 82 6A F1 3E.
 *
 * Последовательность инициализации:
 *  1. drainFrames() + delay(200) — очистка очереди.
 *  2. ATZ — сброс адаптера (только если skipReset = false).
 *  3. drainFrames() + delay(10000) — КРИТИЧНАЯ пауза после сброса.
 *     На ISO 9141-2 K-line требует 10+ секунд, чтобы ЭБУ «остыл»
 *     и снова отвечал на 5 baud init. 5 секунд — НЕ ХВАТАЕТ.
 *  4. ATE0, ATL0, ATS0, ATH0, ATCAF1 — формат ответов.
 *  5. ATSP3 — принудительно ISO 9141-2.
 *  6. ATIB 10 — baud 10.4 kbaud.
 *  7. ATIIA 6A — address ЭБУ Honda (запускает 5 baud init).
 *  8. ATWM 82 6A F1 3E — wakeup message.
 *  9. ATSW 00 — wakeup interval.
 * 10. ATSH DA F1 10 — header Honda.
 * 11. ATAT 2 — aggressive adaptive timing.
 * 12. ATST FF — timeout ~1 сек.
 * 13. delay(300) — дать шине инициализироваться.
 * 14. 0100 — ЭБУ должен ответить 4100.
 *
 * @param skipReset если true — НЕ делать ATZ. Используется при повторном
 *                  подключении (когда адаптер уже ObdConnected), чтобы
 *                  сохранить ATSP3/ATIIA/ATWM/ATSH и «разбуженную» K-line.
 */
class Elm327Client(private val transport: BluetoothTransport) {

    companion object {
        private const val TAG = "ELM327"
        private const val DEFAULT_TIMEOUT_MS = 3000L
        private const val BUS_INIT_TIMEOUT_MS = 10000L
        private const val BUS_RETRY_TIMEOUT_MS = 5000L

        // [FIX C] Критичная пауза после ATZ. На ISO 9141-2 K-line требует
        //         10+ секунд, чтобы ЭБУ «остыл» и снова отвечал на 5 baud init.
        //         5 секунд — НЕ ХВАТАЕТ. Проверено на логах: после 5 секунд
        //         010C/010D/0105 всё равно NO DATA.
        private const val POST_RESET_DELAY_MS = 10000L

        private val ADAPTER_ERRORS = setOf(
            "NO DATA", "ERROR", "?", "STOPPED",
            "CAN ERROR", "BUFFER FULL", "UNABLE TO CONNECT"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incomingFrames = Channel<String>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val requestMutex = Mutex()

    init {
        scope.launch {
            transport.incoming.collect { frame ->
                incomingFrames.trySend(frame)
            }
        }
    }

    // ============================================================
    // ЭТАП 2. Инициализация OBD под Honda Civic EU1
    // ============================================================

    sealed class ObdInitResult {
        data class Success(val protocol: String) : ObdInitResult()
        data class Error(val message: String) : ObdInitResult()
    }

    /**
     * Инициализация OBD.
     *
     * @param skipReset если true — не делать ATZ. Используется при повторном
     *                  подключении, когда адаптер уже ObdConnected и шина
     *                  K-line «разбужена».
     */
    suspend fun initializeObd(skipReset: Boolean = false): ObdInitResult {
        FileLogger.i("ELM327", "=== ЭТАП 2 — инициализация OBD (Honda Civic EU1, skipReset=$skipReset) ===")

        // Шаг 1. Очистить очередь от старых кадров.
        FileLogger.i("ELM327", "Шаг 1: drainFrames + delay(200)")
        drainFrames()
        delay(200)

        // Шаг 2. Сброс адаптера — только если skipReset = false.
        if (!skipReset) {
            FileLogger.i("ELM327", "Шаг 2: ATZ")
            val version = request("ATZ", 5000)
            FileLogger.i("ELM327", "Шаг 2: ATZ -> '$version'")
            if (version == null || !version.contains("ELM327", ignoreCase = true)) {
                FileLogger.e("ELM327", "Адаптер не отвечает на ATZ (response='$version')")
                return ObdInitResult.Error("Адаптер не отвечает на ATZ")
            }

            // Шаг 3. [FIX C] Критичная пауза 10 секунд после ATZ.
            drainFrames()
            FileLogger.i("ELM327", "Шаг 3: пауза ${POST_RESET_DELAY_MS} мс после ATZ (K-line остывает)")
            delay(POST_RESET_DELAY_MS)
        } else {
            FileLogger.i("ELM327", "Шаг 2-3: ATZ пропущен (skipReset) — сохраняю ATSP3/ATIIA/ATWM/ATSH")
            drainFrames()
            delay(200)
        }

        // Шаг 4. Формат ответов.
        FileLogger.i("ELM327", "Шаг 4: формат ответов (ATE0, ATL0, ATS0, ATH0, ATCAF1)")
        request("ATE0", 1000)
        request("ATL0", 1000)
        request("ATS0", 1000)
        request("ATH0", 1000)
        request("ATCAF1", 1000)

        // Шаги 5–12. Настройка шины K-line для Honda EU1.
        FileLogger.i("ELM327", "Шаги 5-12: настройка K-line Honda EU1")
        request("ATSP3", 2000)                    // ISO 9141-2
        request("ATIB 10", 1000)                  // baud 10.4 kbaud
        request("ATIIA 6A", 1000)                 // address ЭБУ Honda (5 baud init)
        request("ATWM 82 6A F1 3E", 1000)         // wakeup message
        request("ATSW 00", 1000)                  // wakeup interval
        request("ATSH DA F1 10", 1000)            // header Honda
        request("ATAT 2", 1000)                   // aggressive adaptive timing
        request("ATST FF", 1000)                  // timeout ~1 сек

        // Шаг 13. Дать шине инициализироваться.
        FileLogger.i("ELM327", "Шаг 13: delay(300)")
        delay(300)

        // Шаг 14. Первый запрос — ЭБУ должен ответить 4100.
        FileLogger.i("ELM327", "Шаг 14: 0100 (первый)")
        val first = requestObd("0100", BUS_INIT_TIMEOUT_MS)
        FileLogger.i("ELM327", "0100 (первый) -> '$first'")

        val protoRaw = request("ATDPN", 2000)
        FileLogger.i("ELM327", "ATDPN -> '$protoRaw'")
        val protocol = extractProtocol(protoRaw)

        if (first != null && first.uppercase().contains("4100")) {
            drainFrames()
            FileLogger.i("ELM327", "Инициализация успешна, протокол=$protocol")
            return ObdInitResult.Success(protocol)
        }

        // Fallback: BUS INIT → второй запрос.
        if (first != null && first.uppercase().contains("BUS INIT")) {
            FileLogger.i("ELM327", "Fallback: 0100 (второй)")
            val second = requestObd("0100", BUS_RETRY_TIMEOUT_MS)
            FileLogger.i("ELM327", "0100 (второй) -> '$second'")
            if (second != null && second.uppercase().startsWith("41")) {
                drainFrames()
                FileLogger.i("ELM327", "Инициализация успешна (fallback), протокол=$protocol")
                return ObdInitResult.Success(protocol)
            }
        }

        FileLogger.e("ELM327", "Инициализация провалена: 0100 не получен")
        return ObdInitResult.Error("ЭБУ не отвечает на 0100")
    }

    // ============================================================
    // Обмен командами
    // ============================================================

    suspend fun send(cmd: String): Boolean {
        return transport.send(cmd)
    }

    suspend fun request(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        return requestMutex.withLock {
            val ts = transport.state.value
            if (ts !is ConnState.BtConnected
                && ts !is ConnState.ObdConnected
                && ts !is ConnState.ObdInitializing
            ) {
                FileLogger.w("ELM327", "→ $cmd : SKIP (transport=$ts)")
                return@withLock null
            }

            drainFrames()
            FileLogger.d("ELM327", "→ $cmd")
            val sendOk = transport.send(cmd)
            if (!sendOk) {
                FileLogger.e("ELM327", "→ $cmd : SEND FAILED")
                return@withLock null
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val remaining = deadline - System.currentTimeMillis()

                val raw = withTimeoutOrNull(remaining) {
                    incomingFrames.receive()
                } ?: run {
                    FileLogger.w("ELM327", "← $cmd : TIMEOUT (${timeoutMs} ms)")
                    return@withLock null
                }

                val cleaned = stripEcho(cmd, raw)

                if (isStaleFrame(cmd, cleaned)) {
                    FileLogger.w("ELM327", "← $cmd : STALE '$cleaned'")
                    continue
                }

                FileLogger.d("ELM327", "← $cmd : '$cleaned'")
                return@withLock cleaned
            }
            FileLogger.w("ELM327", "← $cmd : TIMEOUT (deadline)")
            return@withLock null
        }
    }

    suspend fun requestObd(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val raw = request(cmd, timeoutMs)?.trim() ?: run {
            FileLogger.d("ELM327", "requestObd($cmd): null")
            return null
        }
        val upper = raw.uppercase()

        if (upper.replace(" ", "").startsWith("7F")) {
            FileLogger.w("ELM327", "Отрицательный ответ ЭБУ на $cmd: $raw")
            return null
        }

        val lines = upper.lines().map { it.trim() }
        if (lines.any { it in ADAPTER_ERRORS }) {
            FileLogger.w("ELM327", "Служебный ответ адаптера на $cmd: $raw")
            return null
        }

        FileLogger.d("ELM327", "requestObd($cmd) = '$raw'")
        return raw
    }

    suspend fun requestMultiline(
        cmd: String,
        firstTimeoutMs: Long = 6000,
        idleMs: Long = 1200
    ): String {
        requestMutex.withLock {
            drainFrames()
            FileLogger.d("ELM327", "→ $cmd (multiline)")
            transport.send(cmd)

            val first = withTimeoutOrNull(firstTimeoutMs) {
                incomingFrames.receive()
            } ?: run {
                FileLogger.w("ELM327", "← $cmd : TIMEOUT (multiline first)")
                return ""
            }

            val sb = StringBuilder(first)
            while (true) {
                val next = withTimeoutOrNull(idleMs) {
                    incomingFrames.receive()
                } ?: break
                sb.append('\n').append(next)
            }
            val result = sb.toString()
            FileLogger.d("ELM327", "← $cmd : multiline ${result.length} chars")
            return result
        }
    }

    suspend fun readBatteryRaw(): String? = request("ATRV", 1000)

    suspend fun disconnect() {
        FileLogger.i("ELM327", "disconnect()")
        transport.disconnect()
    }

    private fun drainFrames() {
        var count = 0
        while (incomingFrames.tryReceive().isSuccess) { count++ }
        if (count > 0) {
            FileLogger.d("ELM327", "drainFrames: отброшено $count кадров")
        }
    }

    // Очистка эха ELM327.
    private fun stripEcho(cmd: String, raw: String): String {
        val upperCmd = cmd.uppercase()
        val upperRaw = raw.uppercase()
        if (upperRaw.startsWith(upperCmd)) {
            val remainder = raw.substring(cmd.length).trim()
            return if (remainder.isNotEmpty()) remainder else raw
        }
        return raw
    }

    // ============================================================
    // [FIX A] Строгая проверка чужого (stale) кадра.
    // ============================================================
    private fun isStaleFrame(cmd: String, response: String): Boolean {
        val clean = response.replace(" ", "").uppercase()

        // AT-команды — проверка соответствия команде.
        if (cmd.startsWith("AT")) {
            return when {
                cmd.startsWith("ATZ") -> !clean.contains("ELM327")
                cmd.startsWith("ATRV") -> !Regex("\\d+\\.\\d+V?").containsMatchIn(clean)
                cmd.startsWith("ATDPN") -> !clean.matches(Regex("[0-9A-Z]+"))
                else -> !clean.contains("OK") && !clean.contains("ELM327")
            }
        }

        // OBD-команды — проверка ожидаемого prefix.
        val errorMarkers = listOf(
            "NODATA", "ERROR", "BUSINIT", "UNABLE",
            "SEARCHING", "STOPPED", "CANERROR", "?", "BUFFERFULL"
        )
        if (errorMarkers.any { clean.contains(it) }) return false

        val cmdUpper = cmd.uppercase().replace(" ", "")
        if (cmdUpper.length >= 4) {
            // "0100" → "4100", "010C" → "410C", "010D" → "410D".
            val expected = "4" + cmdUpper.drop(1)
            if (clean.contains(expected)) return false
        }

        return true
    }

    // ============================================================
    // Mode 09
    // ============================================================

    suspend fun readVehicleInfo(): VehicleInfo {
        FileLogger.i("ELM327", "=== Чтение Mode 09 ===")

        val supported = readSupportedMode09Pids()
        FileLogger.i("ELM327", "Mode 09 поддерживает: $supported")

        val vin = if (supported.isEmpty() || supported.contains("02")) readVin() else null
        val calId = if (supported.isEmpty() || supported.contains("04")) readMultilinePid("0904") else null
        val cvn = if (supported.isEmpty() || supported.contains("06")) readMultilinePid("0906") else null
        val ecuName = if (supported.isEmpty() || supported.contains("0A")) readMultilinePid("090A") else null

        return VehicleInfo(
            vin = vin,
            calibrationId = calId,
            cvn = cvn,
            ecuName = ecuName,
            supportedPids = supported
        )
    }

    private suspend fun readSupportedMode09Pids(): List<String> {
        val resp = requestMultiline("0900", firstTimeoutMs = 4000)
        if (resp.isBlank()) return emptyList()

        val clean = resp.replace(" ", "").replace("\n", "").uppercase()
        val idx = clean.indexOf("4900")
        if (idx < 0) return emptyList()

        val mask = clean.substring(idx + 4).take(8)
        if (mask.length < 8) return emptyList()

        val result = mutableListOf<String>()
        for (byteIdx in 0 until 4) {
            val byte = mask.substring(byteIdx * 2, byteIdx * 2 + 2).toInt(16)
            for (bit in 0 until 8) {
                if ((byte shr (7 - bit)) and 1 == 1) {
                    val pid = byteIdx * 8 + bit + 1
                    if (pid in 1..32) result.add(String.format("%02X", pid))
                }
            }
        }
        return result
    }

    private suspend fun readVin(): String? {
        val resp = requestMultiline("0902", firstTimeoutMs = 6000)
        if (resp.isBlank()) return null
        return parseVin(resp)
    }

    private suspend fun readMultilinePid(cmd: String): String? {
        val resp = requestMultiline(cmd, firstTimeoutMs = 6000)
        if (resp.isBlank()) return null

        val marker = "49" + cmd.substring(2).uppercase()
        val sb = StringBuilder()

        for (line in resp.split('\n')) {
            var clean = line.replace(" ", "").uppercase()
            val idx = clean.indexOf(marker)
            if (idx >= 0) clean = clean.substring(idx + marker.length)

            var i = 0
            while (i + 1 < clean.length) {
                val code = clean.substring(i, i + 2).toIntOrNull(16)
                if (code != null && code in 32..126) sb.append(code.toChar())
                i += 2
            }
        }
        return sb.toString().ifBlank { null }
    }

    private fun parseVin(resp: String): String? {
        val clean = resp.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()

        val sb = StringBuilder()
        var i = 0
        while (i + 1 < clean.length) {
            val byte = clean.substring(i, i + 2)
            val code = byte.toIntOrNull(16)
            if (code != null && code in 32..126) sb.append(code.toChar())
            i += 2
        }

        val candidate = sb.toString().filter { it.isLetterOrDigit() }
        if (candidate.length < 17) return null

        val vinRegex = Regex("[A-HJ-NP-RZ0-9]{17}")
        return vinRegex.find(candidate)?.value
    }

    private fun extractProtocol(raw: String?): String {
        val cleaned = stripEcho("ATDPN", raw ?: "")
        val code = cleaned.trim().removePrefix("A").trim()
        return when (code) {
            "1" -> "ISO 9141-2 (5 baud init)"
            "2" -> "ISO 9141-2 (bus init)"
            "3" -> "ISO 9141-2"
            "4" -> "ISO 14230-4 (KWP 5BAUD)"
            "5" -> "ISO 14230-4 (KWP FAST)"
            "6" -> "ISO 15765-4 (CAN 11/500)"
            "7" -> "ISO 15765-4 (CAN 29/500)"
            "8" -> "ISO 15765-4 (CAN 11/250)"
            "9" -> "ISO 15765-4 (CAN 29/250)"
            else -> "неизвестный ($code)"
        }
    }
}