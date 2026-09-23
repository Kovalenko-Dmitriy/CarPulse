package com.carpulse.obd.data.obd

/**
 * Данные об автомобиле, считанные через Mode 09 OBD-II.
 *
 * На старых авто (Honda Civic 2001) большинство полей будут null —
 * это нормально, Mode 09 поддерживается не всеми производителями.
 */
data class VehicleInfo(
    val vin: String? = null,
    val calibrationId: String? = null,
    val cvn: String? = null,
    val ecuName: String? = null,
    /** Список поддерживаемых PID в Mode 09. */
    val supportedPids: List<String> = emptyList()
) {
    val hasAnyData: Boolean
        get() = vin != null || calibrationId != null || cvn != null || ecuName != null
}