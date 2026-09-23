package com.carpulse.obd.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Фасад над источниками геолокации с автопереключением.
 *
 *  1. Основной источник — FusedLocationProviderClient (GMS): точнее,
 *     экономичнее, фьюзит GPS + сети + датчики.
 *  2. Если GMS недоступна (SERVICE_MISSING/INVALID, устройства без
 *     Play Services, microG, некоторые эмуляторы) — прозрачно уходим
 *     на платформенный LocationManager.
 *
 * Переключение двухуровневое:
 *  - при выборе источника: GoogleApiAvailability.isGooglePlayServicesAvailable;
 *  - в рантайме: если Fused-flow всё же завершился ошибкой
 *    (addOnFailureListener → close(e)), фасад ловит её и делает
 *    однократный фолбэк на LocationManager.
 *
 * API идентичен старому GpsTracker — вызывающий код (TripTrackingService)
 * не меняется.
 */
class GpsTracker(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var source: LocationSource? = null

    private fun selectSource(): LocationSource =
        source ?: synchronized(this) {
            source ?: run {
                val fused = FusedLocationSource(appContext)
                val chosen: LocationSource =
                    if (fused.isAvailable()) fused
                    else LocationManagerSource(appContext)
                Log.i(TAG, "Источник локации: ${chosen.name}")
                chosen.also { source = it }
            }
        }

    /**
     * Сброс кэшированного выбора.
     * Полезно, если пользователь установил/обновил Google Play Services
     * в процессе работы приложения.
     */
    @Synchronized
    fun resetSource() {
        source = null
    }

    fun locationFlow(): Flow<Location> = flow {
        val first = selectSource()
        try {
            emitAll(first.locationFlow())
        } catch (ce: CancellationException) {
            throw ce                                   // отмену не перехватываем
        } catch (e: Exception) {
            if (first !is FusedLocationSource) throw e // LM упал — не наше дело
            Log.w(TAG, "${first.name} упал в рантайме, фолбэк на LocationManager", e)
            val fallback = LocationManagerSource(appContext)
            synchronized(this@GpsTracker) { source = fallback }
            emitAll(fallback.locationFlow())
        }
    }

    suspend fun getLastKnown(): Location? {
        val s = selectSource()
        return try {
            s.getLastKnown()
        } catch (e: Exception) {
            if (s is FusedLocationSource) {
                Log.w(TAG, "Fused getLastKnown упал, фолбэк на LocationManager", e)
                val fallback = LocationManagerSource(appContext)
                synchronized(this) { source = fallback }
                fallback.getLastKnown()
            } else null
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        private const val TAG = "GpsTracker"
    }
}