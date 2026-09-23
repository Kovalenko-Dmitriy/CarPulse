package com.carpulse.obd.domain

import androidx.annotation.StringRes
import com.carpulse.obd.R

/**
 * Расчёт мгновенного расхода топлива.
 * Два метода:
 * 1. Через MAF (PID 0110) — точный, для машин с MAF-датчиком.
 * 2. Через MAP + RPM + IAT — оценочный, для машин без MAF (Honda Civic 2001).
 */
object FuelCalculator {
    private const val DEFAULT_DISPLACEMENT_L = 1.6f
    private const val DEFAULT_VE = 0.85f

    /**
     * Мгновенный расход в литрах в час.
     * @param maf расход воздуха (г/с) — PID 0110, может быть null
     * @param map давление во впуске (кПа) — PID 010B
     * @param rpm обороты — PID 010C
     * @param iat температура впуска (°C) — PID 010F
     * @param displacement литраж двигателя
     * @param ve объёмный КПД (0.7–1.0)
     * @param fuelType бензин / дизель
     */
    fun calculateLph(
        maf: Float?,
        map: Float?,
        rpm: Float?,
        iat: Float?,
        displacement: Float = DEFAULT_DISPLACEMENT_L,
        ve: Float = DEFAULT_VE,
        fuelType: FuelType = FuelType.GASOLINE
    ): Float? {
        // Метод 1: через MAF (если поддерживается)
        if (maf != null && maf > 0f) {
            return mafToLph(maf, fuelType)
        }
        // Метод 2: через MAP + RPM + IAT (без MAF)
        if (map != null && rpm != null && iat != null && rpm > 0f) {
            return speedDensityToLph(map, rpm, iat, displacement, ve, fuelType)
        }
        return null
    }

    /** MAF-метод: расход воздуха / (AFR × плотность). */
    private fun mafToLph(mafGs: Float, fuelType: FuelType): Float {
        val afr = fuelType.stoichAfr
        val density = fuelType.densityGPerL
        return mafGs * 3600f / (afr * density)
    }

    /** Speed-Density метод (без MAF). */
    private fun speedDensityToLph(
        mapKpa: Float,
        rpm: Float,
        iatC: Float,
        displacementL: Float,
        ve: Float,
        fuelType: FuelType
    ): Float {
        val iatK = iatC + 273.15f
        val mapPa = mapKpa * 1000f
        val r = 287.05f
        val vdM3 = displacementL / 1000f
        val airKgS = (mapPa * vdM3 * rpm * ve) / (r * iatK * 120f)
        val airGs = airKgS * 1000f
        return mafToLph(airGs, fuelType)
    }

    /** Мгновенный расход л/100 км (только при скорости > 5 км/ч). */
    fun toL100km(lph: Float?, speedKmh: Float?): Float? {
        if (lph == null || speedKmh == null || speedKmh < 5f) return null
        return (lph / speedKmh) * 100f
    }

    /** Стоимость 100 км. */
    fun costPer100km(l100: Float?, pricePerLiter: Float): Float? {
        if (l100 == null || pricePerLiter <= 0f) return null
        return (l100 / 100f) * pricePerLiter
    }
}

/**
 * Тип топлива. stoichAfr и densityGPerL — физические константы,
 * labelRes — локализованное название (для UI).
 *
 * ВАЖНО: labelRes используется только для отображения. Для хранения
 * в настройках и сравнения используется name (GASOLINE, DIESEL, ...) —
 * это стабильный технический идентификатор, не зависящий от локали.
 */
enum class FuelType(
    val stoichAfr: Float,
    val densityGPerL: Float,
    @StringRes val labelRes: Int,
) {
    GASOLINE(14.7f, 745f, R.string.fuel_type_gasoline),
    DIESEL(14.5f, 840f, R.string.fuel_type_diesel),
    LPG(15.7f, 510f, R.string.fuel_type_lpg),
    CNG(17.2f, 128f, R.string.fuel_type_cng),
    E85(9.8f, 785f, R.string.fuel_type_e85)
}