package com.carpulse.obd.data.obd

import androidx.annotation.StringRes
import com.carpulse.obd.R
import com.carpulse.obd.domain.units.UnitType

/**
 * Полный каталог PID Mode 01 (SAE J1979).
 *
 * Поддерживаемые конкретной машиной определяются через SupportedPidsDetector.
 *
 * @param cmd          команда ELM327 ("010C")
 * @param labelRes     ресурс названия (локализованный)
 * @param unitType     тип физической величины → определяет единицы отображения.
 *                     min/max/warn/danger хранятся в МЕТРИЧЕСКИХ единицах
 *                     (unitType.metricRes).
 * @param min          минимум для шкалы (метрика)
 * @param max          максимум для шкалы (метрика)
 * @param warn         порог предупреждения (метрика)
 * @param danger       порог опасности (метрика)
 * @param lowerIsWorse true для параметров, где плохо НИЗКОЕ значение
 *                     (например, напряжение бортовой сети, уровень топлива)
 * @param category     группа для UI (двигатель, топливо, воздух, ...)
 */
enum class Pid(
    val cmd: String,
    @StringRes val labelRes: Int,
    val unitType: UnitType,
    val min: Float,
    val max: Float,
    val warn: Float? = null,
    val danger: Float? = null,
    val lowerIsWorse: Boolean = false,
    val category: PidCategory = PidCategory.OTHER
) {
    // ============================================================
    // Двигатель и топливная система
    // ============================================================
    FUEL_STATUS(
        "0103", R.string.pid_0103_label, UnitType.NONE,
        0f, 1f, category = PidCategory.FUEL
    ),
    LOAD(
        "0104", R.string.pid_0104_label, UnitType.PERCENT,
        0f, 100f, 80f, 95f, category = PidCategory.ENGINE
    ),
    COOLANT(
        "0105", R.string.pid_0105_label, UnitType.CELSIUS,
        -40f, 120f, 100f, 110f, category = PidCategory.ENGINE
    ),
    STFT1(
        "0106", R.string.pid_0106_label, UnitType.PERCENT,
        -100f, 100f, category = PidCategory.FUEL
    ),
    LTFT1(
        "0107", R.string.pid_0107_label, UnitType.PERCENT,
        -100f, 100f, category = PidCategory.FUEL
    ),
    STFT2(
        "0108", R.string.pid_0108_label, UnitType.PERCENT,
        -100f, 100f, category = PidCategory.FUEL
    ),
    LTFT2(
        "0109", R.string.pid_0109_label, UnitType.PERCENT,
        -100f, 100f, category = PidCategory.FUEL
    ),
    FUEL_PRESSURE(
        "010A", R.string.pid_010a_label, UnitType.KPA,
        0f, 765f, category = PidCategory.FUEL
    ),
    FUEL_RAIL_REL(
        "0122", R.string.pid_0122_label, UnitType.KPA,
        0f, 5178f, category = PidCategory.FUEL
    ),
    FUEL_RAIL_ABS(
        "0123", R.string.pid_0123_label, UnitType.KPA,
        0f, 655350f, category = PidCategory.FUEL
    ),
    FUEL_LEVEL(
        "012F", R.string.pid_012f_label, UnitType.PERCENT,
        0f, 100f, 20f, null, lowerIsWorse = true, category = PidCategory.FUEL
    ),

    // ============================================================
    // Впуск и воздух
    // ============================================================
    MAP(
        "010B", R.string.pid_010b_label, UnitType.KPA,
        0f, 255f, category = PidCategory.AIR
    ),
    INTAKE(
        "010F", R.string.pid_010f_label, UnitType.CELSIUS,
        -40f, 80f, 60f, 70f, category = PidCategory.AIR
    ),
    MAF(
        "0110", R.string.pid_0110_label, UnitType.MAF,
        0f, 655f, category = PidCategory.AIR
    ),
    THROTTLE(
        "0111", R.string.pid_0111_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),
    BARO(
        "0133", R.string.pid_0133_label, UnitType.KPA,
        0f, 255f, category = PidCategory.AIR
    ),
    AMBIENT(
        "0146", R.string.pid_0146_label, UnitType.CELSIUS,
        -40f, 80f, category = PidCategory.AIR
    ),
    THROTTLE_REL(
        "0145", R.string.pid_0145_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),
    THROTTLE_B(
        "0147", R.string.pid_0147_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),
    THROTTLE_C(
        "0148", R.string.pid_0148_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),
    ACCEL_D(
        "0149", R.string.pid_0149_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),
    ACCEL_E(
        "014A", R.string.pid_014a_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),
    ACCEL_F(
        "014B", R.string.pid_014b_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.AIR
    ),

    // ============================================================
    // Обороты, скорость, зажигание
    // ============================================================
    RPM(
        "010C", R.string.pid_010c_label, UnitType.RPM,
        0f, 8000f, 5500f, 6500f, category = PidCategory.ENGINE
    ),
    SPEED(
        "010D", R.string.pid_010d_label, UnitType.SPEED,
        0f, 255f, 120f, 160f, category = PidCategory.ENGINE
    ),
    TIMING(
        "010E", R.string.pid_010e_label, UnitType.DEGREE,
        -64f, 64f, category = PidCategory.ENGINE
    ),

    // ============================================================
    // Датчики кислорода
    // ============================================================
    O2_B1S1_V(
        "0114", R.string.pid_0114_label, UnitType.VOLT,
        0f, 1.275f, category = PidCategory.O2
    ),
    O2_B1S2_V(
        "0115", R.string.pid_0115_label, UnitType.VOLT,
        0f, 1.275f, category = PidCategory.O2
    ),
    O2_B1S3_V(
        "0116", R.string.pid_0116_label, UnitType.VOLT,
        0f, 1.275f, category = PidCategory.O2
    ),
    O2_B1S4_V(
        "0117", R.string.pid_0117_label, UnitType.VOLT,
        0f, 1.275f, category = PidCategory.O2
    ),
    O2_B2S1_V(
        "0118", R.string.pid_0118_label, UnitType.VOLT,
        0f, 1.275f, category = PidCategory.O2
    ),
    O2_B2S2_V(
        "0119", R.string.pid_0119_label, UnitType.VOLT,
        0f, 1.275f, category = PidCategory.O2
    ),

    // ============================================================
    // Экология и выхлоп
    // ============================================================
    EGR_CMD(
        "012C", R.string.pid_012c_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.EMISSIONS
    ),
    EGR_ERR(
        "012D", R.string.pid_012d_label, UnitType.PERCENT,
        -100f, 100f, category = PidCategory.EMISSIONS
    ),
    EVAP_CMD(
        "012E", R.string.pid_012e_label, UnitType.PERCENT,
        0f, 100f, category = PidCategory.EMISSIONS
    ),
    WARMUPS(
        "0130", R.string.pid_0130_label, UnitType.NONE,
        0f, 255f, category = PidCategory.EMISSIONS
    ),
    DIST_CLEARED(
        "0131", R.string.pid_0131_label, UnitType.KM,
        0f, 65535f, category = PidCategory.EMISSIONS
    ),
    EVAP_PRESSURE(
        "0132", R.string.pid_0132_label, UnitType.PA,
        -8192f, 8191f, category = PidCategory.EMISSIONS
    ),
    CAT_B1S1(
        "013C", R.string.pid_013c_label, UnitType.CELSIUS,
        -40f, 1200f, category = PidCategory.EMISSIONS
    ),
    CAT_B1S2(
        "013D", R.string.pid_013d_label, UnitType.CELSIUS,
        -40f, 1200f, category = PidCategory.EMISSIONS
    ),
    CAT_B2S1(
        "013E", R.string.pid_013e_label, UnitType.CELSIUS,
        -40f, 1200f, category = PidCategory.EMISSIONS
    ),
    CAT_B2S2(
        "013F", R.string.pid_013f_label, UnitType.CELSIUS,
        -40f, 1200f, category = PidCategory.EMISSIONS
    ),

    // ============================================================
    // Электрика
    // ============================================================
    VOLTAGE(
        "0142", R.string.pid_0142_label, UnitType.VOLT,
        0f, 20f, 12.2f, 11.8f, lowerIsWorse = true, category = PidCategory.ELECTRICAL
    ),

    // ============================================================
    // Дополнительные
    // ============================================================
    RUN_TIME(
        "011F", R.string.pid_011f_label, UnitType.SECOND,
        0f, 65535f, category = PidCategory.OTHER
    ),
    MIL_DISTANCE(
        "0121", R.string.pid_0121_label, UnitType.KM,
        0f, 65535f, category = PidCategory.OTHER
    ),
    MIL_TIME(
        "014D", R.string.pid_014d_label, UnitType.MINUTE,
        0f, 65535f, category = PidCategory.OTHER
    ),
    TIME_CLEARED(
        "014E", R.string.pid_014e_label, UnitType.MINUTE,
        0f, 65535f, category = PidCategory.OTHER
    ),
}

/**
 * Категории PID для группировки в UI.
 * Названия — через строковые ресурсы, чтобы работала локализация.
 */
enum class PidCategory(@StringRes val labelRes: Int) {
    ENGINE(R.string.category_engine),
    FUEL(R.string.category_fuel),
    AIR(R.string.category_air),
    O2(R.string.category_o2),
    EMISSIONS(R.string.category_emissions),
    ELECTRICAL(R.string.category_electrical),
    OTHER(R.string.category_other)
}