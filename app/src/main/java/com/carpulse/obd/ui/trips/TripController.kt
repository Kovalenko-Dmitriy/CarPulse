package com.carpulse.obd.ui.trips

import android.content.Context
import androidx.core.content.ContextCompat
import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.bt.ConnState
import com.carpulse.obd.data.prefs.SettingsStore
import com.carpulse.obd.domain.ObdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.carpulse.obd.trips.TripTrackingService
class TripController(
    private val ctx: Context,
    private val obd: ObdManager,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var recordTrips = false
    @Volatile private var running = false

    fun startObserving() {
        scope.launch {
            settings.settings.collect { s ->
                recordTrips = s.recordTrips
            }
        }
        scope.launch {
            obd.connection?.collect { state ->
                when (state) {
                    is ConnState.ObdConnected -> {
                        if (!recordTrips || running) return@collect
                        FileLogger.write("TRIPS: OBD подключён — запускаю трекинг")
                        ContextCompat.startForegroundService(
                            ctx,
                            TripTrackingService.startIntent(ctx)
                        )
                        running = true
                    }
                    is ConnState.Disconnected -> if (running) {
                        FileLogger.write("TRIPS: OBD отключён — завершаю трекинг")
                        ctx.startService(
                            TripTrackingService.startIntent(ctx)
                                .setAction("com.carpulse.obd.trips.STOP")
                        )
                        running = false
                    }
                    else -> Unit
                }
            }
        }
    }
}