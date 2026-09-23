package com.carpulse.obd.domain

import androidx.compose.runtime.Immutable
import com.carpulse.obd.data.obd.Pid
import com.carpulse.obd.data.obd.TimedValue

/**
 * Снимок live-данных. Помечен @Immutable, чтобы Compose мог заскипать
 * поддеревья, которые не изменились, при обновлении отдельных PID.
 */
@Immutable
data class LiveSnapshot(
    val values: Map<Pid, TimedValue>,
    val updatedAt: Long
) {
    companion object {
        val EMPTY = LiveSnapshot(emptyMap(), 0L)
    }
}