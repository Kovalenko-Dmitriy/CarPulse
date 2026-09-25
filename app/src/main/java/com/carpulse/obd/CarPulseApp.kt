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

    /**
     * Репозиторий профиля автомобиля (DataStore).
     */
    val carProfileRepository: CarProfileRepository by lazy {
        CarProfileRepository(applicationContext)
    }

    /**
     * Декодер VIN.
     *
     * WMI- и VDS-базы загружаются из assets при первом обращении
     * к декодеру — то есть при открытии экрана профиля, а не при старте.
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

    /**
     * JDM-база (車台番号). Загружается лениво из assets/jdm_database.json.
     * При ошибке парсинга возвращается пустая база.
     */
    val jdmDatabase: JdmDatabase by lazy {
        JdmDatabase(JdmJsonLoader(this).load())
    }

    /**
     * Декодер японских номеров кузова. Работает поверх jdmDatabase.
     * Чистая доменная логика, без Android-зависимостей.
     */
    val jdmDecoder: JdmDecoder by lazy {
        JdmDecoder(jdmDatabase)
    }

    // ========================================================================
    // База ЭБУ
    // ========================================================================

    /**
     * База ЭБУ. Загружается из assets/ecu_database.json лениво.
     */
    val ecuDatabase: EcuDatabase by lazy {
        EcuDatabase(EcuJsonLoader(this).load())
    }

    /**
     * Резолвер ЭБУ. Работает поверх ecuDatabase.
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

        obd = ObdManager(this, settings)
        trips = TripRepository(db, settings)
        obd.setTripRepository(trips)

        Log.d("CarPulse", "ObdManager создан один раз")

        // ---- osmdroid: user-agent + пути к кешу ----
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

        // ---- Автозапуск/остановка трекинга поездок ----
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

    companion object {
        /**
         * Глобальный доступ для фабрик ViewModel.
         */
        lateinit var instance: CarPulseApp
            private set
    }
}