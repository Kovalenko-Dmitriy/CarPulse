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
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.data.prefs.ThemeMode
import com.carpulse.obd.data.prefs.Units
import com.carpulse.obd.domain.LiveSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ============================================================
    // 1. Синглтон ObdManager
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
    // 4. Bluetooth — состояние адаптера (реактивно)
    // ============================================================
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
    val connection: StateFlow<ConnState>? = obd.connection
    val live: StateFlow<LiveSnapshot>? = obd.live
    val battery: StateFlow<TimedValue?>? = obd.battery
    val supportedPids: StateFlow<Set<Pid>>? = obd.supportedPids

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

    fun connect(mac: String, deviceName: String? = null) = viewModelScope.launch {
        obd.reconnectAfterUserAction()
        val btOk = obd.connectBluetooth(mac)
        if (!btOk) {
            FileLogger.write("VIEWMODEL: Этап 1 не удался")
            return@launch
        }
        settingsStore.setLastDevice(mac, deviceName ?: "ELM327")
        obd.connectObd()
    }

    fun retryObd() = viewModelScope.launch {
        FileLogger.write("VIEWMODEL: повтор этапа 2")
        obd.repository?.resetUnsupportedPids()
        obd.connectObd()
    }

    fun disconnect() {
        obd.disconnect()
    }

    fun forgetDevice() = viewModelScope.launch {
        settingsStore.forgetDevice()
    }

    // ============================================================
    // 8. Список сопряжённых устройств
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

    suspend fun readDtcs(): List<String> = obd.repository?.readDtcs() ?: emptyList()
    suspend fun clearDtcs(): Boolean = obd.repository?.clearDtcs() ?: false

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

    fun setDisplacement(v: Float) = viewModelScope.launch { settingsStore.setDisplacement(v) }
    fun setVolumetricEfficiency(v: Float) = viewModelScope.launch {
        settingsStore.setVolumetricEfficiency(v)
    }
    fun setFuelType(v: String) = viewModelScope.launch { settingsStore.setFuelType(v) }
    fun setFuelPrice(v: Float) = viewModelScope.launch { settingsStore.setFuelPrice(v) }
    fun setOdometer(v: Float) = viewModelScope.launch { settingsStore.setOdometer(v) }
}