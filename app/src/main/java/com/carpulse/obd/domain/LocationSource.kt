package com.carpulse.obd.domain

import android.location.Location
import kotlinx.coroutines.flow.Flow

/**
 * Контракт источника геолокации.
 *
 * Реализации:
 *  - [FusedLocationSource] — через Google Play Services (точнее, экономичнее).
 *  - [LocationManagerSource] — через платформенный LocationManager
 *    (работает без GMS, на Huawei/Honor, microG, эмуляторах).
 *
 * Фасад [GpsTracker] выбирает реализацию по доступности GMS
 * и делает однократный рантайм-фолбэк, если выбранный источник
 * упал после старта.
 */
interface LocationSource {

    /** Человекочитаемое имя для логов. */
    val name: String

    /** Можно ли вообще использовать этот источник на данном устройстве. */
    fun isAvailable(): Boolean

    /**
     * Поток точек.
     *
     * Завершение flow с ошибкой означает, что источник неработоспособен —
     * фасад [GpsTracker] поймает это и переключится на запасной.
     */
    fun locationFlow(): Flow<Location>

    /** Последняя известная точка. null — если нет/недоступна. */
    suspend fun getLastKnown(): Location?
}