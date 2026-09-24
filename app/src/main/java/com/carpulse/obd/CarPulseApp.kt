package com.carpulse.obd

import android.app.Application
import android.util.Log
import com.carpulse.obd.data.billing.BillingManager
import com.carpulse.obd.data.db.AppDatabase
import com.carpulse.obd.data.ecu.EcuJsonLoader
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.data.prefs.UserPreferences
import com.carpulse.obd.data.profile.CarProfileRepository
import com.carpulse.obd.domain.FeatureGate
import com.carpulse.obd.domain.ObdManager
import com.carpulse.obd.domain.TripRepository
import com.carpulse.obd.domain.ecu.EcuDatabase
import com.carpulse.obd.domain.ecu.EcuResolver
import com.carpulse.obd.domain.vin.VdsLookup
import com.carpulse.obd.domain.vin.VinDecoder
import com.carpulse.obd.domain.vin.WmiDatabase
import com.carpulse.obd.domain.vin.WmiJsonLoader
import com.carpulse.obd.ui.trips.TripController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.osmdroid.config.Configuration
import java.io.File
import com.carpulse.obd.domain.vin.VdsDatabase
import com.carpulse.obd.domain.vin.VdsJsonLoader
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

    /**
     * Репозиторий профиля автомобиля (DataStore).
     *
     * Ленивая инициализация: DataStore откроется при первом обращении,
     * а не при старте процесса. Это ускоряет холодный старт.
     *
     * Экземпляр один на приложение — иначе DataStore откроет файл повторно
     * и возможны гонки при записи.
     */
    val carProfileRepository: CarProfileRepository by lazy {
        CarProfileRepository(applicationContext)
    }

    /**
     * Декодер VIN.
     *
     * WMI-база загружается из assets/vin_wmi.json при первом обращении
     * к декодеру — то есть при открытии экрана профиля, а не при старте.
     * Это ускоряет холодный старт и экономит память, если профиль
     * не используется.
     */
    val vinDecoder: VinDecoder by lazy {
        VinDecoder(
            wmiDb = WmiDatabase(WmiJsonLoader(this).load()),
            vdsLookup = VdsLookup.Database(VdsDatabase(VdsJsonLoader(this).load())),
        )
    }

    // ========================================================================
    // База ЭБУ
    // ========================================================================

    /**
     * База ЭБУ. Загружается из assets/ecu_database.json лениво.
     *
     * При ошибке парсинга возвращается пустая база — приложение
     * продолжит работать через OBD2_STANDARD (см. EcuJsonLoader).
     */
    val ecuDatabase: EcuDatabase by lazy {
        EcuDatabase(EcuJsonLoader(this).load())
    }

    /**
     * Резолвер ЭБУ. Работает поверх ecuDatabase.
     * Чистая доменная логика, без Android-зависимостей.
     */
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

        // [FIX] ObdManager теперь принимает SettingsStore в конструктор —
        // нужен для загрузки кэша supportedPids и сохранения detect().
        obd = ObdManager(this, settings)
        trips = TripRepository(db, settings)

        // [FIX D] Передаём TripRepository в ObdManager,
        // чтобы retryObdInternal() не прерывал активную поездку.
        obd.setTripRepository(trips)

        Log.d("CarPulse", "ObdManager создан один раз")

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

        // ---- Авто-реконнект OBD ----
        obd.startAutoReconnect(
            autoConnectProvider = { settings.settings.first().autoConnect },
            lastMacProvider = { settings.settings.first().lastMac }
        )

        // ---- Автозапуск/остановка трекинга поездок по состоянию OBD ----
        TripController(this, obd, settings).startObserving()

        // ---- Инициализация глобальной ссылки для фабрик ViewModel ----
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        obd.stopAutoReconnect()
        obd.disconnect()
        FileLogger.stop()
    }

    companion object {
        /**
         * Глобальный доступ для фабрик ViewModel.
         *
         * Даёт доступ к carProfileRepository, vinDecoder, ecuResolver
         * без проброса через конструкторы Composables.
         */
        lateinit var instance: CarPulseApp
            private set
    }
}