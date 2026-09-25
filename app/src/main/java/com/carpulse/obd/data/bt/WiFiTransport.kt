package com.carpulse.obd.data.bt

import android.util.Log
import com.carpulse.obd.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Wi-Fi транспорт ELM327.
 *
 * ELM327 Wi-Fi работает как TCP-сервер: адаптер поднимает точку доступа
 * (обычно 192.168.0.10) и слушает порт **35000**. После `connect()` по TCP
 * протокол не отличается от Bluetooth SPP — те же AT-команды, тот же
 * терминатор `>` в конце ответа.
 *
 * Особенности Wi-Fi против BT:
 *  - нет понятия MAC, идентификатор — `host:port`;
 *  - соединение устанавливается быстрее (нет RFCOMM handshake);
 *  - чаще теряется при переключении Wi-Fi сетей / уходе в фон —
 *    Android может разорвать сокет при отключении экрана.
 *
 * @param connectTimeoutMs таймаут TCP connect. ELM327 отвечает быстро,
 *                         5 секунд с запасом.
 * @param readTimeoutMs    таймаут socket read. Используется для heartbeat.
 */
class WiFiTransport(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 1_000,
) : ObdTransport {

    companion object {
        private const val TAG = "WIFI_TRANSPORT"
        private const val DEFAULT_PORT = 35000
        private const val HEARTBEAT_TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val socketLock = Any()

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private var readLoopJob: Job? = null

    /**
     * Для Wi-Fi — `host:port`, например `192.168.0.10:35000`.
     */
    override var connectedTarget: String? = null
        private set

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    override val state: StateFlow<ConnState> = _state

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val incoming: SharedFlow<String> = _incoming

    // ============================================================
    // Подключение
    // ============================================================

    override suspend fun connect(target: String): Boolean = withContext(Dispatchers.IO) {
        // Останавливаем старый readLoop и закрываем сокет, если он был.
        synchronized(socketLock) {
            readLoopJob?.cancel()
            readLoopJob = null
            disconnectInternalLocked()
        }

        _state.value = ConnState.BtConnecting
        FileLogger.i("WIFI", "state → BtConnecting")
        FileLogger.i("WIFI", "Подключение к $target")

        val (host, port) = parseTarget(target)
        if (host.isBlank()) {
            FileLogger.e("WIFI", "Пустой host")
            _state.value = ConnState.BtError("Пустой host")
            return@withContext false
        }
        FileLogger.i("WIFI", "TCP connect: $host:$port (timeout=${connectTimeoutMs}ms)")

        try {
            val sock = Socket()
            sock.tcpNoDelay = true          // без Nagle — команды уходят сразу
            sock.soTimeout = readTimeoutMs  // read() не блокирует навсегда
            sock.connect(InetSocketAddress(host, port), connectTimeoutMs)

            synchronized(socketLock) {
                socket = sock
                input = sock.getInputStream()
                output = sock.getOutputStream()
                connectedTarget = "$host:$port"
            }

            _state.value = ConnState.BtConnected(
                deviceName = "ELM327 Wi-Fi",
                mac = "$host:$port",
            )
            FileLogger.i("WIFI", "state → BtConnected ($host:$port)")
            FileLogger.i("WIFI", "Сокет открыт. Устройство: ELM327 Wi-Fi")

            startReadLoop()
            true
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Таймаут подключения к $host:$port", e)
            FileLogger.e("WIFI", "Таймаут подключения: $host:$port (${e.message})")
            synchronized(socketLock) { disconnectInternalLocked() }
            _state.value = ConnState.BtError("Таймаут подключения ($host:$port)")
            false
        } catch (e: SocketException) {
            Log.e(TAG, "Socket error к $host:$port", e)
            FileLogger.e("WIFI", "Ошибка сокета: ${e.message}")
            synchronized(socketLock) { disconnectInternalLocked() }
            _state.value = ConnState.BtError(e.message ?: "socket error")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подключения Wi-Fi", e)
            FileLogger.e("WIFI", "Ошибка подключения: ${e.message}")
            synchronized(socketLock) { disconnectInternalLocked() }
            _state.value = ConnState.BtError(e.message ?: "connect failed")
            false
        }
    }

    /**
     * `host` или `host:port` → пара. Если порт не указан — 35000.
     */
    private fun parseTarget(target: String): Pair<String, Int> {
        val trimmed = target.trim()
        val idx = trimmed.lastIndexOf(':')
        return if (idx > 0 && idx < trimmed.length - 1) {
            val host = trimmed.substring(0, idx)
            val port = trimmed.substring(idx + 1).toIntOrNull() ?: DEFAULT_PORT
            host to port
        } else {
            trimmed to DEFAULT_PORT
        }
    }

    // ============================================================
    // ReadLoop с watchdog-таймаутом
    // ============================================================

    private fun startReadLoop() {
        readLoopJob?.cancel()
        readLoopJob = scope.launch {
            FileLogger.d("WIFI", "readLoop запущен")
            val buf = ByteArray(1024)
            val sb = StringBuilder()
            var lastDataTime = System.currentTimeMillis()

            while (isActive) {
                val sock = synchronized(socketLock) { socket }
                if (sock == null || !sock.isConnected || sock.isClosed) {
                    FileLogger.d("WIFI", "readLoop: socket null/closed — выход")
                    break
                }

                try {
                    val now = System.currentTimeMillis()
                    if (_state.value is ConnState.ObdConnected
                        && now - lastDataTime > HEARTBEAT_TIMEOUT_MS
                    ) {
                        FileLogger.w(
                            "WIFI",
                            "${HEARTBEAT_TIMEOUT_MS / 1000} сек нет данных при активном OBD — разрыв",
                        )
                        synchronized(socketLock) { disconnectInternalLocked() }
                        break
                    }

                    // На Wi-Fi `available()` работает, но мы всё равно сначала
                    // ждём read с таймаутом — так надёжнее при фрагментированных
                    // пакетах, которые не успели доехать к моменту available().
                    val n = synchronized(socketLock) {
                        try {
                            input?.read(buf) ?: -1
                        } catch (e: SocketTimeoutException) {
                            0  // штатный таймаут — просто продолжаем цикл
                        } catch (e: Exception) {
                            -1
                        }
                    }

                    if (n < 0) {
                        FileLogger.w("WIFI", "EOF — адаптер разорвал соединение")
                        synchronized(socketLock) { disconnectInternalLocked() }
                        break
                    }
                    if (n == 0) {
                        // Таймаут read — проверим heartbeat в следующей итерации.
                        continue
                    }

                    lastDataTime = System.currentTimeMillis()

                    sb.append(String(buf, 0, n))
                    while (true) {
                        val idx = sb.indexOf(">")
                        if (idx < 0) break
                        val frame = sb.substring(0, idx).trim()
                        sb.delete(0, idx + 1)
                        if (frame.isNotEmpty()) {
                            FileLogger.d("WIFI", "← '$frame'")
                            if (!_incoming.tryEmit(frame)) {
                                FileLogger.w("WIFI", "буфер входящих переполнен")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка чтения", e)
                    FileLogger.e("WIFI", "Ошибка чтения: ${e.message}")
                    synchronized(socketLock) { disconnectInternalLocked() }
                    break
                }
            }
            FileLogger.d("WIFI", "readLoop завершён")
        }
    }

    // ============================================================
    // Отправка
    // ============================================================

    override suspend fun send(cmd: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(socketLock) {
            try {
                FileLogger.d("WIFI", "→ '$cmd'")
                output?.write("$cmd\r".toByteArray())
                output?.flush()
                output != null
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки", e)
                FileLogger.e("WIFI", "Ошибка отправки: ${e.message}")
                disconnectInternalLocked()
                false
            }
        }
    }

    // ============================================================
    // Состояние OBD (этап 2)
    // ============================================================

    override fun setObdState(newState: ConnState) {
        FileLogger.i("WIFI", "state → $newState")
        _state.value = newState
    }

    // ============================================================
    // Отключение
    // ============================================================

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        FileLogger.i("WIFI", "disconnect()")
        synchronized(socketLock) {
            readLoopJob?.cancel()
            readLoopJob = null
            disconnectInternalLocked()
        }
    }

    /**
     * Синхронизированная версия — вызывается под `socketLock`.
     */
    private fun disconnectInternalLocked() {
        val wasConnected = socket != null
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
        connectedTarget = null
        _state.value = ConnState.Disconnected
        if (wasConnected) {
            FileLogger.i("WIFI", "state → Disconnected (был подключён)")
        } else {
            FileLogger.d("WIFI", "state → Disconnected (уже был отключён)")
        }
    }
}