package com.carpulse.obd.trips

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.carpulse.obd.CarPulseApp
import com.carpulse.obd.FileLogger
import com.carpulse.obd.MainActivity
import com.carpulse.obd.R
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.domain.GpsTracker
import com.carpulse.obd.domain.LiveSnapshot
import com.carpulse.obd.domain.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Foreground Service записи поездки.
 *
 * Логика старта:
 *  1. Поднимаем foreground с типом location (иначе Android 10+ убьёт).
 *  2. Пробуем взять lastKnown — если есть, сразу начинаем поездку.
 *  3. Подписываемся на GPS-поток. Первая же хорошая точка:
 *     - если поездка уже начата (lastKnown сработал) — пишем точку;
 *     - если нет — начинаем поездку С ЭТОЙ точки и тоже пишем.
 *
 * Это устраняет ситуацию, когда getLastKnown() вернул null,
 * поток точек идёт, но TripRepository.onLocation() молча выходит
 * по early-return `currentTripId ?: return`.
 *
 * Совместим с ui.trips.TripController:
 *  - startIntent(ctx) — фабрика интентов с ACTION_START;
 *  - ACTION_STOP = "com.carpulse.obd.trips.STOP" — команда остановки.
 */
class TripTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    private lateinit var trips: TripRepository
    private lateinit var gps: GpsTracker
    private var app: CarPulseApp? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FileLogger.i("SERVICE", "onCreate")
        val a = application as? CarPulseApp
        app = a
        trips = a?.trips ?: error("CarPulseApp.trips не инициализирован")
        gps = GpsTracker(applicationContext)
        createNotificationChannel()
        Log.d(TAG, "TripTrackingService создан")
        FileLogger.i("SERVICE", "TripTrackingService создан")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        FileLogger.i("SERVICE", "onStartCommand(action=${intent?.action})")
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            else -> {
                Log.d(TAG, "onStartCommand без известного action: ${intent?.action}")
                FileLogger.w("SERVICE", "onStartCommand без известного action: ${intent?.action}")
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------
    //  Старт
    // ------------------------------------------------------------------

    private fun handleStart() {
        if (trackingJob != null) {
            Log.d(TAG, "handleStart(): уже пишем — игнорирую")
            FileLogger.w("SERVICE", "handleStart(): уже пишем — игнорирую")
            return
        }

        FileLogger.i("SERVICE", "handleStart")
        startForegroundSafely()

        trackingJob = scope.launch {
            try {
                // 1) Бонус: если есть lastKnown — сразу начинаем поездку.
                //    Но НЕ считаем это обязательным условием.
                val lastKnown = gps.getLastKnown()
                if (lastKnown != null) {
                    FileLogger.i("SERVICE", "lastKnown: lat=${lastKnown.latitude}, lon=${lastKnown.longitude}, acc=${lastKnown.accuracy}")
                    trips.beginTrip(lastKnown)
                    FileLogger.i("SERVICE", "старт по lastKnown (acc=${lastKnown.accuracy})")
                } else {
                    FileLogger.w("SERVICE", "lastKnown = null, жду первую точку GPS")
                }

                // 2) Подписки. collectGps() сам начнёт поездку,
                //    если она ещё не начата.
                launch { collectGps() }
                launch { collectObdLive() }

                _running.value = true
                FileLogger.i("SERVICE", "запись поездки запущена")
            } catch (t: Throwable) {
                Log.e(TAG, "ошибка при старте записи", t)
                FileLogger.e("SERVICE", "ошибка старта — ${t.message}")
                stopSelfSafely()
            }
        }
    }

    /**
     * Подписка на GPS.
     *
     * Ключевое: перед записью точки проверяем, начата ли поездка.
     * Если нет — начинаем её с этой точки. Так первая же хорошая
     * точка становится стартовой, независимо от lastKnown.
     */
    private suspend fun collectGps() {
        FileLogger.i("GPS", "collectGps: подписка на locationFlow")
        gps.locationFlow()
            .catch { e ->
                Log.e(TAG, "GpsTracker.locationFlow упал", e)
                FileLogger.e("GPS", "locationFlow упал — ${e.message}")
            }
            .collect { loc ->
                FileLogger.d("GPS", "точка: lat=${loc.latitude}, lon=${loc.longitude}, acc=${loc.accuracy}, speed=${loc.speed}, t=${loc.time}")

                // Поездка ещё не начата? Начинаем с этой точки.
                if (trips.currentTripIdOrNull() == null) {
                    FileLogger.i("GPS", "первая точка GPS — начинаю поездку (acc=${loc.accuracy})")
                    trips.beginTrip(loc)
                }

                val snap = latestSnapshot
                trips.onLocation(
                    loc = loc,
                    rpm = snap?.rpmInt(),
                    load = snap?.floatOf(Pid.LOAD),
                    maf = snap?.floatOf(Pid.MAF),
                    map = snap?.floatOf(Pid.MAP),
                    iat = snap?.floatOf(Pid.INTAKE),
                    coolant = snap?.floatOf(Pid.COOLANT)
                )
                FileLogger.d("GPS", "onLocation: rpm=${snap?.rpmInt()}, load=${snap?.floatOf(Pid.LOAD)}, maf=${snap?.floatOf(Pid.MAF)}, map=${snap?.floatOf(Pid.MAP)}, iat=${snap?.floatOf(Pid.INTAKE)}, coolant=${snap?.floatOf(Pid.COOLANT)}")
            }
    }

    /**
     * Подписка на LiveSnapshot от ObdManager.
     * Сохраняем последний снимок в @Volatile-поле — GPS-цикл его читает.
     */
    private suspend fun collectObdLive() {
        val live: StateFlow<LiveSnapshot>? = app?.obd?.live
        if (live == null) {
            FileLogger.w("SERVICE", "obd.live недоступен — расход не считается")
            return
        }
        FileLogger.i("SERVICE", "collectObdLive: подписка на obd.live")
        live.collect { snap ->
            latestSnapshot = snap
            FileLogger.d("SERVICE", "obd.live: updatedAt=${snap.updatedAt}, values=${snap.values.size}")
        }
    }

    // ------------------------------------------------------------------
    //  Стоп
    // ------------------------------------------------------------------

    private fun handleStop() {
        FileLogger.i("SERVICE", "handleStop")
        scope.launch {
            try {
                trips.endTrip()
                FileLogger.i("SERVICE", "запись поездки остановлена")
            } catch (t: Throwable) {
                Log.e(TAG, "ошибка при остановке", t)
                FileLogger.e("SERVICE", "ошибка стопа — ${t.message}")
            } finally {
                _running.value = false
                stopForegroundSafely()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.i("SERVICE", "onDestroy")
        trackingJob?.cancel()
        scope.cancel()
        _running.value = false
        Log.d(TAG, "TripTrackingService уничтожен")
    }

    // ------------------------------------------------------------------
    //  Уведомление
    // ------------------------------------------------------------------

    private fun startForegroundSafely() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            FileLogger.i("SERVICE", "startForeground OK")
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground упал", t)
            FileLogger.e("SERVICE", "startForeground упал — ${t.message}")
            stopSelf()
        }
    }

    private fun stopForegroundSafely() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            FileLogger.i("SERVICE", "stopForeground OK")
        } catch (t: Throwable) {
            Log.w(TAG, "stopForeground упал", t)
            FileLogger.w("SERVICE", "stopForeground упал — ${t.message}")
        }
    }

    private fun stopSelfSafely() {
        try {
            stopForegroundSafely()
        } finally {
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TripTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.trip_notification_title))
            .setContentText(getString(R.string.trip_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(
                0,
                getString(R.string.trip_notification_stop),
                stopIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.trip_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.trip_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    // ------------------------------------------------------------------
    //  Хелперы для доступа к PID из LiveSnapshot
    // ------------------------------------------------------------------

    private fun LiveSnapshot.floatOf(pid: Pid): Float? =
        values[pid]?.value

    private fun LiveSnapshot.rpmInt(): Int? =
        values[Pid.RPM]?.value?.toInt()

    companion object {

        private const val TAG = "TripTrackingService"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 4211

        const val ACTION_START = "com.carpulse.obd.trips.START"

        const val ACTION_STOP = "com.carpulse.obd.trips.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun startIntent(ctx: Context): Intent =
            Intent(ctx, TripTrackingService::class.java).setAction(ACTION_START)

        fun stopIntent(ctx: Context): Intent =
            Intent(ctx, TripTrackingService::class.java).setAction(ACTION_STOP)
    }

    /**
     * Последний снимок live-данных OBD. @Volatile — читается из GPS-корутины,
     * пишется из OBD-корутины. Обновляется целиком, поэтому гонок за поля нет.
     */
    @Volatile
    private var latestSnapshot: LiveSnapshot? = null
}