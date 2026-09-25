package com.carpulse.obd

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.carpulse.obd.data.billing.BillingManager
import com.carpulse.obd.data.bt.BluetoothTransport
import com.carpulse.obd.data.bt.ObdTransport
import com.carpulse.obd.data.bt.WiFiTransport
import com.carpulse.obd.data.db.AppDatabase
import com.carpulse.obd.data.ecu.EcuJsonLoader
import com.carpulse.obd.data.prefs.ConnectionType
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.data.prefs.UserPreferences
import com.carpulse.obd.data.profile.CarProfileRepository
import com.carpulse.obd.domain.FeatureGate
import com.carpulse.obd.domain.ObdManager
import com.carpulse.obd.domain.TripRepository
import com.carpulse.obd.domain.ecu.EcuDatabase
import com.carpulse.obd.domain.ecu.EcuResolver
import com.carpulse.obd.domain.vin.VdsDatabase
import com.carpulse.obd.domain.vin.VdsJsonLoader
import com.carpulse.obd.domain.vin.VdsLookup
import com.carpulse.obd.domain.vin.VinDecoder
import com.carpulse.obd.domain.vin.WmiDatabase
import com.carpulse.obd.domain.vin.WmiJsonLoader
import com.carpulse.obd.domain.vin.jdm.JdmDatabase
import com.carpulse.obd.domain.vin.jdm.JdmDecoder
import com.carpulse.obd.domain.vin.jdm.JdmJsonLoader
import com.carpulse.obd.ui.trips.TripController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration
import java.io.File

class CarPulseApp : Application() {

    // ========================================================================
    // Существующие зависимости
    // ========================================================================
    lateinit var obd: ObdManager
    lateinit var trips: TripRepository
    lateinit var prefs: UserPreferences
    lateinit var settings: SettingsStore
    lateinit var billingManager: BillingManager
    lateinit var featureGate: FeatureGate

    // ========================================================================
    // Профиль автомобиля + VIN-декодер
    // ========================================================================

    val carProfileRepository: CarProfileRepository by lazy {
        CarProfileRepository(applicationContext)
    }

    /**
     * Декодер VIN. WMI- и VDS-базы загружаются лениво из assets.
     */
    val vinDecoder: VinDecoder by lazy {
        VinDecoder(
            wmiDb = WmiDatabase(WmiJsonLoader(this).load()),
            vdsLookup = VdsLookup.Database(VdsDatabase(VdsJsonLoader(this).load())),
        )
    }

    // ========================================================================
    // JDM-декодер (японские номера кузова)
    // ========================================================================

    val jdmDatabase: JdmDatabase by lazy {
        JdmDatabase(JdmJsonLoader(this).load())
    }

    val jdmDecoder: JdmDecoder by lazy {
        JdmDecoder(jdmDatabase)
    }

    // ========================================================================
    // База ЭБУ
    // ========================================================================

    val ecuDatabase: EcuDatabase by lazy {
        EcuDatabase(EcuJsonLoader(this).load())
    }

    val ecuResolver: EcuResolver by lazy {
        EcuResolver(ecuDatabase)
    }

    // ========================================================================
    // Жизненный цикл
    // ========================================================================

    override fun onCreate() {
        super.onCreate()

        // ---- Настройки и подписки ----
        prefs = UserPreferences(this)
        settings = SettingsStore(this)
        billingManager = BillingManager(
            this,
            prefs,
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
        featureGate = FeatureGate(prefs)

        // ---- База и доменные сервисы ----
        val db = AppDatabase.get(this)

        // ---- Транспорт: BT или Wi-Fi ----
        // Читаем connectionType синхронно: транспорт должен быть готов
        // до первого обращения к obd. DataStore на первом чтении занимает
        // единицы миллисекунд — приемлемо на старте.
        val initial = runBlocking { settings.settings.first() }
        val transport: ObdTransport = createTransport(initial.connectionType)
        Log.d("CarPulse", "Transport: ${transport.javaClass.simpleName}")

        obd = ObdManager(this, settings, transport)
        trips = TripRepository(db, settings)
        obd.setTripRepository(trips)

        Log.d("CarPulse", "ObdManager создан (${obd.transportKind})")

        // ---- osmdroid: user-agent + пути к кешу (один раз на процесс) ----
        Configuration.getInstance().userAgentValue = "CarPulse/0.1 ($packageName)"
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val osmCacheDir = File(baseDir, "osmdroid")
        if (!osmCacheDir.exists()) osmCacheDir.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmCacheDir
        Configuration.getInstance().osmdroidTileCache = File(osmCacheDir, "tiles")
        Configuration.getInstance().apply {
            cacheMapTileCount = 12
            tileDownloadThreads = 4
            expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30
        }

        FileLogger.start(this)

        // ---- Авто-реконнект ----
        // targetProvider возвращает MAC для BT и host:port для Wi-Fi.
        obd.startAutoReconnect(
            autoConnectProvider = { settings.settings.first().autoConnect },
            lastTargetProvider = {
                val s = settings.settings.first()
                when (s.connectionType) {
                    ConnectionType.BLUETOOTH -> s.lastMac
                    ConnectionType.WIFI -> "${s.lastWiFiHost}:${s.wifiPort}"
                }
            }
        )

        // ---- Автозапуск/остановка трекинга поездок по состоянию OBD ----
        TripController(this, obd, settings).startObserving()

        // ---- Глобальная ссылка для фабрик ViewModel ----
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        obd.stopAutoReconnect()
        obd.disconnect()
        FileLogger.stop()
    }

    // ========================================================================
    // Фабрика транспорта
    // ========================================================================

    /**
     * Создаёт транспорт по пользовательскому выбору.
     *
     * Если выбран Bluetooth, но адаптер недоступен (эмулятор без BT,
     * отключённый чип, отсутствие разрешений на Android 12+),
     * логируем предупреждение и переключаемся на Wi-Fi. UI покажет
     * пользователю фактический тип через [ObdManager.transportKind].
     */
    private fun createTransport(type: ConnectionType): ObdTransport {
        return when (type) {
            ConnectionType.WIFI -> WiFiTransport()

            ConnectionType.BLUETOOTH -> {
                val adapter = resolveBluetoothAdapter()
                if (adapter == null) {
                    Log.w(
                        "CarPulse",
                        "BluetoothAdapter недоступен, fallback на Wi-Fi"
                    )
                    WiFiTransport()
                } else {
                    BluetoothTransport(adapter)
                }
            }
        }
    }

    /**
     * Возвращает BluetoothAdapter системными средствами.
     *
     * Порядок попыток:
     *  1. BluetoothManager через getSystemService (актуальный API);
     *  2. BluetoothAdapter.getDefaultAdapter() как fallback
     *     (deprecated, но работает на старых прошивках).
     */
    @Suppress("DEPRECATION")
    private fun resolveBluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    companion object {
        /**
         * Глобальный доступ для фабрик ViewModel.
         */
        lateinit var instance: CarPulseApp
            private set
    }
}