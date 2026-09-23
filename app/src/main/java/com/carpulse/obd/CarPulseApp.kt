package com.carpulse.obd

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.carpulse.obd.data.billing.BillingManager
import com.carpulse.obd.data.db.AppDatabase
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.data.prefs.UserPreferences
import com.carpulse.obd.domain.FeatureGate
import com.carpulse.obd.domain.ObdManager
import com.carpulse.obd.domain.TripRepository
import com.carpulse.obd.ui.trips.TripController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.osmdroid.config.Configuration
import java.io.File

class CarPulseApp : Application() {
    lateinit var obd: ObdManager
    lateinit var trips: TripRepository
    lateinit var prefs: UserPreferences
    lateinit var settings: SettingsStore
    lateinit var billingManager: BillingManager
    lateinit var featureGate: FeatureGate

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
        //       нужен для загрузки кэша supportedPids и сохранения detect().
        obd = ObdManager(this, settings)
        trips = TripRepository(db, settings)

        // [FIX D] Передаём TripRepository в ObdManager,
        //         чтобы retryObdInternal() не прерывал активную поездку.
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
    }

    override fun onTerminate() {
        super.onTerminate()
        obd.stopAutoReconnect()
        obd.disconnect()
        FileLogger.stop()
    }
}