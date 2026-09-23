package com.carpulse.obd.domain

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * Источник на FusedLocationProviderClient (Google Play Services).
 *
 * Требует наличия GMS на устройстве. Проверка — isAvailable() через
 * GoogleApiAvailability: на устройствах без GMS конструирование клиента
 * или подписка падают (IllegalStateException / failed Task).
 *
 * Выбор этого источника — ответственность фасада [GpsTracker].
 * Сам источник повторно GMS не проверяет.
 */
class FusedLocationSource(private val context: Context) : LocationSource {

    override val name: String get() = "FusedLocationProviderClient"

    private val fused: FusedLocationProviderClient? = try {
        LocationServices.getFusedLocationProviderClient(context)
    } catch (e: Exception) {
        // На устройствах без GMS getFusedLocationProviderClient может
        // кинуть IllegalStateException ещё на конструировании.
        Log.w(TAG, "FusedLocationProviderClient не создан", e)
        null
    }

    @Volatile
    private var lastAccepted: Location? = null

    override fun isAvailable(): Boolean {
        if (fused == null) return false
        val status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        // SUCCESS = 0. Остальные коды (SERVICE_MISSING=1, SERVICE_INVALID=9, ...)
        // означают, что fused-источник недоступен.
        return status == ConnectionResult.SUCCESS
    }

    @SuppressLint("MissingPermission")
    override fun locationFlow(): Flow<Location> = callbackFlow {
        val client = fused
        if (client == null) {
            Log.w(TAG, "locationFlow(): клиент не создан — завершаю flow ошибкой")
            close(IllegalStateException("FusedLocationProviderClient недоступен"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    if (isAcceptable(loc)) {
                        lastAccepted = loc
                        trySend(loc)
                    } else {
                        Log.v(TAG, "точка отброшена: acc=${loc.accuracy} dt=${loc.time}")
                    }
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    // Ключевая строка для фолбэка: failed Task закрывает flow
                    // ошибкой, фасад её ловит и переключается на LocationManager.
                    Log.e(TAG, "requestLocationUpdates упал", e)
                    close(e)
                }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException при подписке", se)
            close(se)
        }

        awaitClose {
            client.removeLocationUpdates(callback)
            Log.d(TAG, "locationFlow(): отписка от FusedLocation")
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged(GeoUtils::samePoint)

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnown(): Location? {
        val client = fused ?: return null
        return try {
            Tasks.await(client.lastLocation)?.takeIf { isAcceptable(it) }
        } catch (e: Exception) {
            Log.w(TAG, "getLastKnown() не удалось", e)
            null
        }
    }

    /** Фильтр мусорных точек: точность, устаревание, «прыжки». */
    private fun isAcceptable(loc: Location): Boolean {
        if (!loc.hasAccuracy() || loc.accuracy > MAX_ACCURACY_METERS) return false

        val prev = lastAccepted ?: return true
        if (loc.time <= prev.time) return false

        val dtSec = (loc.time - prev.time) / 1000.0
        if (dtSec <= 0.0) return false

        val distance = GeoUtils.haversineMeters(
            prev.latitude, prev.longitude,
            loc.latitude, loc.longitude
        )
        return distance / dtSec <= MAX_SPEED_MPS
    }

    companion object {
        private const val TAG = "FusedLocationSource"

        private const val INTERVAL_MS = 2_000L
        private const val MIN_INTERVAL_MS = 1_000L
        private const val MIN_DISTANCE_METERS = 3f
        private const val MAX_ACCURACY_METERS = 30f

        /** Отсечка «прыжков»: 70 м/с ≈ 252 км/ч. */
        private const val MAX_SPEED_MPS = 70.0
    }
}