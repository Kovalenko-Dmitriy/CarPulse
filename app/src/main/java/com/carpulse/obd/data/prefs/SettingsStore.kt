package com.carpulse.obd.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "carpulse_settings")

enum class Units { METRIC, IMPERIAL }

/**
 * Тип физического подключения к адаптеру ELM327.
 *
 * Читается один раз при старте приложения (в CarPulseApp.onCreate).
 * Смена типа в настройках требует перезапуска приложения — транспорт
 * создаётся при старте и живёт весь процесс.
 */
enum class ConnectionType { BLUETOOTH, WIFI }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val units: Units = Units.METRIC,

    // --- Подключение ---
    val connectionType: ConnectionType = ConnectionType.BLUETOOTH,

    /** MAC Bluetooth-адаптера (последнее успешное подключение). */
    val lastMac: String? = null,
    val lastDeviceName: String? = null,

    /** Wi-Fi: последний host (обычно IP точки доступа адаптера). */
    val lastWiFiHost: String = "192.168.0.10",

    /** Wi-Fi: порт TCP-сервера ELM327. Стандарт — 35000. */
    val wifiPort: Int = 35000,

    val autoConnect: Boolean = true,
    val pollingIntervalMs: Long = 300L,

    // --- Датчики ---
    val selectedPids: Set<String> = emptySet(),

    // --- Кэш supported PID для данного ЭБУ ---
    // Заполняется один раз при первом успешном detect(),
    // дальше используется без повторного сканирования.
    val supportedPids: Set<String> = emptySet(),

    // --- Топливо ---
    val displacementL: Float = 1.6f,
    val volumetricEfficiency: Float = 0.85f,
    val fuelType: String = "GASOLINE",
    val fuelPricePerLiter: Float = 0f,
    val odometerKm: Float = 0f,

    // --- Поездки ---
    val recordTrips: Boolean = true
)

class SettingsStore(private val ctx: Context) {

    // ---- Ключи ----
    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_UNITS = stringPreferencesKey("units")
    private val KEY_CONNECTION_TYPE = stringPreferencesKey("connection_type")
    private val KEY_MAC = stringPreferencesKey("last_mac")
    private val KEY_DEVICE_NAME = stringPreferencesKey("last_device_name")
    private val KEY_WIFI_HOST = stringPreferencesKey("last_wifi_host")
    private val KEY_WIFI_PORT = intPreferencesKey("wifi_port")
    private val KEY_INTERVAL = intPreferencesKey("interval_ms")
    private val KEY_AUTOCONNECT = booleanPreferencesKey("auto_connect")
    private val KEY_SELECTED_PIDS = stringSetPreferencesKey("selected_pids")

    // Кэш supported PID
    private val KEY_SUPPORTED_PIDS = stringSetPreferencesKey("supported_pids")

    // Топливо
    private val KEY_DISPLACEMENT = floatPreferencesKey("displacement_l")
    private val KEY_VE = floatPreferencesKey("volumetric_efficiency")
    private val KEY_FUEL_TYPE = stringPreferencesKey("fuel_type")
    private val KEY_FUEL_PRICE = floatPreferencesKey("fuel_price")
    private val KEY_ODOMETER = floatPreferencesKey("odometer_km")

    // Поездки
    private val KEY_RECORD_TRIPS = booleanPreferencesKey("record_trips")

    // ---- Поток всех настроек ----
    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[KEY_THEME]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            units = p[KEY_UNITS]
                ?.let { runCatching { Units.valueOf(it) }.getOrNull() }
                ?: Units.METRIC,

            connectionType = p[KEY_CONNECTION_TYPE]
                ?.let { runCatching { ConnectionType.valueOf(it) }.getOrNull() }
                ?: ConnectionType.BLUETOOTH,

            lastMac = p[KEY_MAC],
            lastDeviceName = p[KEY_DEVICE_NAME],
            lastWiFiHost = p[KEY_WIFI_HOST] ?: "192.168.0.10",
            wifiPort = p[KEY_WIFI_PORT] ?: 35000,

            autoConnect = p[KEY_AUTOCONNECT] ?: true,
            pollingIntervalMs = (p[KEY_INTERVAL] ?: 300).toLong(),
            selectedPids = p[KEY_SELECTED_PIDS] ?: emptySet(),
            supportedPids = p[KEY_SUPPORTED_PIDS] ?: emptySet(),

            displacementL = p[KEY_DISPLACEMENT] ?: 1.6f,
            volumetricEfficiency = p[KEY_VE] ?: 0.85f,
            fuelType = p[KEY_FUEL_TYPE] ?: "GASOLINE",
            fuelPricePerLiter = p[KEY_FUEL_PRICE] ?: 0f,
            odometerKm = p[KEY_ODOMETER] ?: 0f,

            recordTrips = p[KEY_RECORD_TRIPS] ?: true
        )
    }

    // ============================================================
    // Тема / единицы
    // ============================================================

    suspend fun setTheme(mode: ThemeMode) = ctx.dataStore.edit {
        it[KEY_THEME] = mode.name
    }

    suspend fun setUnits(u: Units) = ctx.dataStore.edit {
        it[KEY_UNITS] = u.name
    }

    // ============================================================
    // Подключение: тип транспорта
    // ============================================================

    suspend fun setConnectionType(type: ConnectionType) = ctx.dataStore.edit {
        it[KEY_CONNECTION_TYPE] = type.name
    }

    // ============================================================
    // Bluetooth-устройство
    // ============================================================

    suspend fun setLastMac(mac: String) = ctx.dataStore.edit {
        it[KEY_MAC] = mac
    }

    suspend fun setLastDevice(mac: String, name: String) = ctx.dataStore.edit {
        it[KEY_MAC] = mac
        it[KEY_DEVICE_NAME] = name
    }

    suspend fun forgetDevice() = ctx.dataStore.edit {
        it.remove(KEY_MAC)
        it.remove(KEY_DEVICE_NAME)
    }

    // ============================================================
    // Wi-Fi
    // ============================================================

    suspend fun setLastWiFiHost(host: String) = ctx.dataStore.edit {
        it[KEY_WIFI_HOST] = host
    }

    suspend fun setWiFiPort(port: Int) = ctx.dataStore.edit {
        it[KEY_WIFI_PORT] = port
    }

    // ============================================================
    // Автоподключение и интервал опроса
    // ============================================================

    suspend fun setAutoConnect(v: Boolean) = ctx.dataStore.edit {
        it[KEY_AUTOCONNECT] = v
    }

    suspend fun setPollingInterval(ms: Long) = ctx.dataStore.edit {
        it[KEY_INTERVAL] = ms.toInt()
    }

    // ============================================================
    // Датчики
    // ============================================================

    suspend fun setSelectedPids(pids: Set<String>) = ctx.dataStore.edit {
        it[KEY_SELECTED_PIDS] = pids
    }

    // ============================================================
    // Кэш supported PID
    // ============================================================

    suspend fun setSupportedPids(pids: Set<String>) = ctx.dataStore.edit {
        it[KEY_SUPPORTED_PIDS] = pids
    }

    suspend fun clearSupportedPids() = ctx.dataStore.edit {
        it.remove(KEY_SUPPORTED_PIDS)
    }

    // ============================================================
    // Топливо
    // ============================================================

    suspend fun setDisplacement(v: Float) = ctx.dataStore.edit {
        it[KEY_DISPLACEMENT] = v
    }

    suspend fun setVolumetricEfficiency(v: Float) = ctx.dataStore.edit {
        it[KEY_VE] = v
    }

    suspend fun setFuelType(v: String) = ctx.dataStore.edit {
        it[KEY_FUEL_TYPE] = v
    }

    suspend fun setFuelPrice(v: Float) = ctx.dataStore.edit {
        it[KEY_FUEL_PRICE] = v
    }

    suspend fun setOdometer(v: Float) = ctx.dataStore.edit {
        it[KEY_ODOMETER] = v
    }

    // ============================================================
    // Поездки
    // ============================================================

    suspend fun setRecordTrips(v: Boolean) = ctx.dataStore.edit {
        it[KEY_RECORD_TRIPS] = v
    }
}