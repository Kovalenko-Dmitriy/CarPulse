package com.carpulse.obd.data.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
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
import java.util.UUID

/**
 * Отвечает ТОЛЬКО за этап 1 — Bluetooth-соединение с адаптером ELM327.
 */
class BluetoothTransport(private val adapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "BT_TRANSPORT"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val HEARTBEAT_TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val socketLock = Any()

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private var readLoopJob: Job? = null

    var connectedMac: String? = null
        private set

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val incoming: SharedFlow<String> = _incoming

    // ============================================================
    // ЭТАП 1. Подключение Bluetooth
    // ============================================================

    @SuppressLint("MissingPermission")
    suspend fun connect(mac: String): Boolean = withContext(Dispatchers.IO) {
        // Синхронизированная остановка перед новым подключением
        synchronized(socketLock) {
            readLoopJob?.cancel()
            readLoopJob = null
            disconnectInternalLocked()
        }

        _state.value = ConnState.BtConnecting
        FileLogger.i("BT", "state → BtConnecting")
        FileLogger.i("BT", "Подключение к $mac")

        try {
            adapter.cancelDiscovery()
            delay(200)
            val device = adapter.getRemoteDevice(mac)

            val sock = try {
                FileLogger.d("BT", "Стандартный способ: createRfcommSocketToServiceRecord")
                device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
            } catch (e: Exception) {
                FileLogger.w("BT", "Стандартный способ не сработал: ${e.message}")
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                FileLogger.d("BT", "Fallback: createRfcommSocket(1)")
                (m.invoke(device, 1) as BluetoothSocket).also { it.connect() }
            }

            // Синхронизированная установка сокета
            synchronized(socketLock) {
                socket = sock
                input = sock.inputStream
                output = sock.outputStream
                connectedMac = mac
            }

            _state.value = ConnState.BtConnected(
                deviceName = device.name ?: "ELM327",
                mac = mac
            )
            FileLogger.i("BT", "state → BtConnected (${device.name ?: "ELM327"})")
            FileLogger.i("BT", "Сокет открыт. Устройство: ${device.name ?: "ELM327"}")
            startReadLoop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подключения Bluetooth", e)
            FileLogger.e("BT", "Ошибка подключения: ${e.message}")
            synchronized(socketLock) {
                disconnectInternalLocked()
            }
            _state.value = ConnState.BtError(e.message ?: "connect failed")
            FileLogger.e("BT", "state → BtError: ${e.message}")
            false
        }
    }

    // ============================================================
    // Чтение байтов с watchdog-таймаутом
    // ============================================================

    private fun startReadLoop() {
        readLoopJob?.cancel()
        readLoopJob = scope.launch {
            FileLogger.d("BT", "readLoop запущен")
            val buf = ByteArray(1024)
            val sb = StringBuilder()
            var lastDataTime = System.currentTimeMillis()

            while (isActive) {
                val sock = synchronized(socketLock) { socket }
                if (sock == null || !sock.isConnected) {
                    FileLogger.d("BT", "readLoop: socket null или не connected — выход")
                    break
                }

                try {
                    val now = System.currentTimeMillis()
                    if (_state.value is ConnState.ObdConnected
                        && now - lastDataTime > HEARTBEAT_TIMEOUT_MS) {
                        FileLogger.w("BT", "${HEARTBEAT_TIMEOUT_MS / 1000} сек нет данных при активном OBD — разрыв")
                        synchronized(socketLock) {
                            disconnectInternalLocked()
                        }
                        break
                    }

                    val available = synchronized(socketLock) { input?.available() ?: 0 }
                    if (available <= 0) {
                        delay(100)
                        continue
                    }

                    val n = synchronized(socketLock) {
                        try {
                            input?.read(buf) ?: -1
                        } catch (e: Exception) {
                            -1
                        }
                    }
                    if (n < 0) {
                        FileLogger.w("BT", "EOF — адаптер разорвал соединение")
                        synchronized(socketLock) {
                            disconnectInternalLocked()
                        }
                        break
                    }
                    if (n == 0) continue

                    lastDataTime = System.currentTimeMillis()

                    sb.append(String(buf, 0, n))
                    while (true) {
                        val idx = sb.indexOf(">")
                        if (idx < 0) break
                        val frame = sb.substring(0, idx).trim()
                        sb.delete(0, idx + 1)
                        if (frame.isNotEmpty()) {
                            FileLogger.d("BT", "← '$frame'")
                            if (!_incoming.tryEmit(frame)) {
                                FileLogger.w("BT", "буфер входящих переполнен")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка чтения", e)
                    FileLogger.e("BT", "Ошибка чтения: ${e.message}")
                    synchronized(socketLock) {
                        disconnectInternalLocked()
                    }
                    break
                }
            }
            FileLogger.d("BT", "readLoop завершён")
        }
    }

    // ============================================================
    // Отправка байтов
    // ============================================================

    suspend fun send(cmd: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(socketLock) {
            try {
                FileLogger.d("BT", "→ '$cmd'")
                output?.write("$cmd\r".toByteArray())
                output?.flush()
                output != null
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки", e)
                FileLogger.e("BT", "Ошибка отправки: ${e.message}")
                disconnectInternalLocked()
                false
            }
        }
    }

    // ============================================================
    // Управление OBD-состоянием
    // ============================================================

    fun setObdState(newState: ConnState) {
        FileLogger.i("BT", "state → $newState")
        _state.value = newState
    }

    // ============================================================
    // Отключение
    // ============================================================

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        FileLogger.i("BT", "disconnect()")
        synchronized(socketLock) {
            readLoopJob?.cancel()
            readLoopJob = null
            disconnectInternalLocked()
        }
    }

    // Синхронизированная версия — вызывается под socketLock.
    private fun disconnectInternalLocked() {
        val wasConnected = socket != null
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
        connectedMac = null
        _state.value = ConnState.Disconnected
        if (wasConnected) {
            FileLogger.i("BT", "state → Disconnected (был подключён)")
        } else {
            FileLogger.d("BT", "state → Disconnected (уже был отключён)")
        }
    }
}