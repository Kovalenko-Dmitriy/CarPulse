package com.carpulse.obd

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpulse.obd.data.bt.BluetoothTransport
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.db.AppDatabase
import com.carpulse.obd.data.db.DtcCauseEntity
import com.carpulse.obd.data.db.DtcCodeEntity
import com.carpulse.obd.data.db.DtcDao
import com.carpulse.obd.data.db.DtcImporter
import com.carpulse.obd.data.db.FuelDao
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.obd.TimedValue
import com.carpulse.obd.data.obd.VehicleInfo
import com.carpulse.obd.data.prefs.AppSettings
import com.carpulse.obd.data.prefs.ConnectionType
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.data.prefs.ThemeMode
import com.carpulse.obd.data.prefs.Units
import com.carpulse.obd.domain.LiveSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ============================================================
    // 1. Singleton ObdManager
    // ============================================================
    val obd = (app as CarPulseApp).obd

    // ============================================================
    // 2. Настройки
    // ============================================================
    private val settingsStore = SettingsStore(app)

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // ============================================================
    // 3. База данных
    // ============================================================
    private val db = AppDatabase.get(app)
    val dtcDao: DtcDao = db.dtcDao()
    val fuelDao: FuelDao = db.fuelDao()
    private val dtcImporter = DtcImporter(app, dtcDao)

    // ============================================================
    // 4. Bluetooth — адаптер и его состояние
    // ============================================================

    /**
     * BluetoothAdapter нужен ТОЛЬКО для UI-части экрана «Связь»:
     * список сопряжённых устройств, кнопка «Включить Bluetooth».
     *
     * Подключение к адаптеру ELM327 идёт через [com.carpulse.obd.data.bt.BluetoothTransport],
     * который сам берёт адаптер из [CarPulseApp].
     *
     * Если активный транспорт — Wi-Fi, [adapter] всё равно нужен для
     * UI, но [btEnabled] игнорируется.
     */
    val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _btEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val btEnabled: StateFlow<Boolean> = _btEnabled

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )
                _btEnabled.value = (state == BluetoothAdapter.STATE_ON)
            }
        }
    }

    // ============================================================
    // 5. Публичные потоки OBD
    // ============================================================
    val connection: StateFlow<ConnState> = obd.connection
    val live: StateFlow<LiveSnapshot> = obd.live
    val battery: StateFlow<TimedValue?> = obd.battery
    val supportedPids: StateFlow<Set<Pid>> = obd.supportedPids

    /**
     * Тип активного транспорта — для UI.
     * "Bluetooth" или "Wi-Fi".
     */
    val transportKind: String get() = obd.transportKind

    private val _paired = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val paired: StateFlow<List<BluetoothDevice>> = _paired

    // ============================================================
    // 6. init
    // ============================================================
    init {
        // Регистрируем приёмник состояния Bluetooth
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            app.registerReceiver(btStateReceiver, filter)
        } catch (e: Exception) {
            FileLogger.write("VIEWMODEL: не удалось зарегистрировать BT receiver: ${e.message}")
        }

        // Импорт DTC
        viewModelScope.launch {
            val count = dtcImporter.importIfNeeded()
            android.util.Log.d("Carpulse", "DTC import: $count codes")
        }

        // Синхронизация выбранных PID → ObdRepository
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                val pids = s.selectedPids.mapNotNull { code ->
                    Pid.entries.firstOrNull { it.cmd == code }
                }.toSet()
                obd.setCustomPids(pids)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(btStateReceiver)
        } catch (_: Exception) {}
    }

    // ============================================================
    // 7. Двухэтапное подключение
    // ============================================================

    /**
     * Универсальный connect: сам определяет тип транспорта по
     * текущим настройкам.
     *
     * @param target для BT — MAC, для Wi-Fi — host или host:port.
     */
    fun connectTarget(target: String) = viewModelScope.launch {
        obd.reconnectAfterUserAction()
        val stage1Ok = obd.connectBluetooth(target)
        if (!stage1Ok) {
            FileLogger.write("VIEWMODEL: этап 1 не удался (${obd.transportKind})")
            return@launch
        }

        // Сохраняем target — для BT это MAC, для Wi-Fi это host:port.
        val s = settingsStore.settings.first()
        when (s.connectionType) {
            ConnectionType.BLUETOOTH -> {
                settingsStore.setLastDevice(target, "ELM327")
            }
            ConnectionType.WIFI -> {
                // Разбираем "host:port" и сохраняем отдельно
                val (host, _) = target.split(":").let {
                    if (it.size == 2) it[0] to it[1].toIntOrNull() else it[0] to null
                }
                settingsStore.setLastWiFiHost(host)
            }
        }

        obd.connectObd()
    }

    /**
     * Обратная совместимость: вызов из старого ConnectionScreen.
     * @param mac MAC BT-устройства.
     * @param deviceName человекочитаемое имя (для сохранения).
     */
    fun connect(mac: String, deviceName: String? = null) = viewModelScope.launch {
        obd.reconnectAfterUserAction()
        val stage1Ok = obd.connectBluetooth(mac)
        if (!stage1Ok) {
            FileLogger.write("VIEWMODEL: этап 1 не удался")
            return@launch
        }
        settingsStore.setLastDevice(mac, deviceName ?: "ELM327")
        obd.connectObd()
    }

    /**
     * Подключение по Wi-Fi.
     * @param host IP-адрес или hostname адаптера (обычно 192.168.0.10).
     * @param port TCP-порт (обычно 35000).
     */
    fun connectWiFi(host: String, port: Int = 35000) = viewModelScope.launch {
        val target = "$host:$port"
        FileLogger.write("VIEWMODEL: Wi-Fi connect $target")
        obd.reconnectAfterUserAction()
        val stage1Ok = obd.connectWiFi(target)
        if (!stage1Ok) {
            FileLogger.write("VIEWMODEL: Wi-Fi этап 1 не удался")
            return@launch
        }
        settingsStore.setLastWiFiHost(host)
        settingsStore.setWiFiPort(port)
        obd.connectObd()
    }

    fun retryObd() = viewModelScope.launch {
        FileLogger.write("VIEWMODEL: повтор этапа 2")
        obd.resetRetry()
        obd.connectObd()
    }

    fun disconnect() {
        obd.disconnect()
    }

    fun forgetDevice() = viewModelScope.launch {
        settingsStore.forgetDevice()
    }

    // ============================================================
    // 8. Список сопряжённых устройств (только для BT)
    // ============================================================

    @Suppress("MissingPermission")
    fun loadPairedDevices() {
        val list = try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        _paired.value = list
    }

    // ============================================================
    // 9. DTC
    // ============================================================

    suspend fun readDtcs(): List<String> = obd.readDtcs()
    suspend fun clearDtcs(): Boolean = obd.clearDtcs()

    suspend fun getDtcDetails(code: String): DtcCodeEntity? = dtcDao.getCode(code)
    suspend fun getDtcCauses(code: String): List<DtcCauseEntity> = dtcDao.getCauses(code)
    suspend fun searchDtc(query: String): List<DtcCodeEntity> = dtcDao.searchByCode(query)
    suspend fun dtcCount(): Int = dtcDao.countCodes()

    // ============================================================
    // 10. Mode 09
    // ============================================================

    suspend fun readVehicleInfo(): VehicleInfo? = obd.readVehicleInfo()

    // ============================================================
    // 11. Настройки
    // ============================================================

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }
    fun setUnits(u: Units) = viewModelScope.launch { settingsStore.setUnits(u) }
    fun setAutoConnect(v: Boolean) = viewModelScope.launch { settingsStore.setAutoConnect(v) }
    fun setPollingInterval(ms: Long) = viewModelScope.launch {
        settingsStore.setPollingInterval(ms)
        obd.setPollingInterval(ms)
    }
    fun setSelectedPids(pids: Set<String>) = viewModelScope.launch {
        settingsStore.setSelectedPids(pids)
    }

    /**
     * Смена типа транспорта. Вступает в силу при следующем запуске
     * приложения (транспорт создаётся в CarPulseApp.onCreate).
     */
    fun setConnectionType(type: ConnectionType) = viewModelScope.launch {
        settingsStore.setConnectionType(type)
        FileLogger.write("VIEWMODEL: connectionType = $type (перезапустите приложение)")
    }

    fun setWiFiHost(host: String) = viewModelScope.launch {
        settingsStore.setLastWiFiHost(host)
    }

    fun setWiFiPort(port: Int) = viewModelScope.launch {
        settingsStore.setWiFiPort(port)
    }

    fun setDisplacement(v: Float) = viewModelScope.launch { settingsStore.setDisplacement(v) }
    fun setVolumetricEfficiency(v: Float) = viewModelScope.launch {
        settingsStore.setVolumetricEfficiency(v)
    }
    fun setFuelType(v: String) = viewModelScope.launch { settingsStore.setFuelType(v) }
    fun setFuelPrice(v: Float) = viewModelScope.launch { settingsStore.setFuelPrice(v) }
    fun setOdometer(v: Float) = viewModelScope.launch { settingsStore.setOdometer(v) }
}