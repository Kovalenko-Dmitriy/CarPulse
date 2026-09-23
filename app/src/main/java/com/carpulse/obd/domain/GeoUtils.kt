package com.carpulse.obd.domain

import android.location.Location
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Общие геометрические функции для источников локации.
 *
 * Вынесены, чтобы не дублировать haversine и samePoint в двух реализациях:
 * если завтра понадобится поправить формулу — правим в одном месте.
 */
internal object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Считаем две точки одинаковыми, если совпали время и координаты
     * с точностью до 1e-6 градуса (~0.1 м). Используется в
     * distinctUntilChanged, чтобы отсеять дубликаты.
     */
    fun samePoint(old: Location, new: Location): Boolean =
        old.time == new.time &&
                abs(old.latitude - new.latitude) < 1e-6 &&
                abs(old.longitude - new.longitude) < 1e-6

    /** Расстояние между двумя точками по haversine, метры. */
    fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * asin(sqrt(a))
    }
}