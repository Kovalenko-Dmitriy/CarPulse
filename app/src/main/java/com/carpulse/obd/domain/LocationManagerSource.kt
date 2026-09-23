package com.carpulse.obd.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * Fallback-источник на платформенном LocationManager.
 *
 * Не требует Google Play Services — работает на китайских прошивках,
 * microG, эмуляторах без GMS. Подписываемся сразу на GPS и NETWORK:
 * NETWORK даёт точку сразу (~20–100 м, сотовые/Wi-Fi), GPS догоняет
 * точную (~3–5 м) после холодного старта.
 *
 * Используем классический API requestLocationUpdates(String, ...)
 * вместо API 30+ (LocationRequest + Executor) ради minSdk 24.
 * Он deprecated, но полностью работоспособен.
 */
class LocationManagerSource(private val context: Context) : LocationSource {

    override val name: String get() = "LocationManager"

    private val manager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    private var lastAccepted: Location? = null

    override fun isAvailable(): Boolean =
        manager.allProviders.any { it != LocationManager.PASSIVE_PROVIDER }

    @SuppressLint("MissingPermission")
    override fun locationFlow(): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            Log.w(TAG, "locationFlow(): нет разрешения — flow завершается")
            close(SecurityException("ACCESS_FINE/COARSE_LOCATION не выдан"))
            return@callbackFlow
        }

        val wanted = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { manager.allProviders.contains(it) }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (isAcceptable(location)) {
                    lastAccepted = location
                    trySend(location)
                } else {
                    Log.v(TAG, "точка отброшена: acc=${location.accuracy}")
                }
            }

            @Deprecated("Обязателен для minSdk < 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "провайдер включён: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "провайдер выключен: $provider")
            }
        }

        // Подписываемся на каждый провайдер отдельно: при выданном только
        // COARSE подписка на GPS кинет SecurityException — пропускаем такие.
        var registered = 0
        for (provider in wanted) {
            try {
                manager.requestLocationUpdates(
                    provider,
                    MIN_INTERVAL_MS,
                    MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper()
                )
                registered++
                Log.d(TAG, "подписан на $provider")
            } catch (se: SecurityException) {
                Log.w(TAG, "$provider: нет разрешения — пропускаю")
            }
        }
        if (registered == 0) {
            close(SecurityException("Ни один провайдер не подписался"))
            return@callbackFlow
        }

        awaitClose {
            manager.removeUpdates(listener)
            Log.d(TAG, "locationFlow(): отписка от LocationManager")
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged(GeoUtils::samePoint)

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnown(): Location? {
        if (!hasPermission()) return null
        return manager.allProviders
            .asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.hasAccuracy() && it.accuracy <= MAX_ACCURACY_METERS }
            .maxByOrNull { it.time }
            .also { it?.let { loc -> lastAccepted = loc } }
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

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
        private const val TAG = "LocationManagerSource"

        private const val MIN_INTERVAL_MS = 1_000L
        private const val MIN_DISTANCE_METERS = 3f
        private const val MAX_ACCURACY_METERS = 30f
        private const val MAX_SPEED_MPS = 70.0
    }
}