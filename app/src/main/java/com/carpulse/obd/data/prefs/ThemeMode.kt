package com.carpulse.obd.data.prefs


enum class ThemeMode {
    /** Как в системе (Android 10+ переключает по расписанию или вручную) */
    SYSTEM,

    /** Всегда светлая */
    LIGHT,

    /** Всегда тёмная */
    DARK,

    /** CarPulse сам решает по времени суток: ночь с 20:00 до 7:00 */
    AUTO
}