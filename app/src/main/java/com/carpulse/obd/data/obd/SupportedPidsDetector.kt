package com.carpulse.obd.data.obd

import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.bt.Elm327Client

/**
 * Определяет, какие PID Mode 01 поддерживает конкретный автомобиль.
 * Отправляет 0100, 0120, 0140, 0160, 0180, 01A0, 01C0, 01E0
 * и читает битовую маску поддерживаемых PID.
 */
class SupportedPidsDetector(private val client: Elm327Client) {

    suspend fun detect(): Set<Pid> {
        FileLogger.write("SUPPORTED: определение поддерживаемых PID...")
        val supported = mutableSetOf<Pid>()

        val probes = listOf(
            "0100" to 0x00,
            "0120" to 0x20,
            "0140" to 0x40,
            "0160" to 0x60,
            "0180" to 0x80,
            "01A0" to 0xA0,
            "01C0" to 0xC0,
            "01E0" to 0xE0,
        )

        for ((cmd, base) in probes) {
            val resp = client.requestObd(cmd, 3000)
            if (resp == null) {
                FileLogger.write("SUPPORTED: $cmd -> нет ответа")
                continue
            }
            parseMask(resp, base, supported)
        }

        FileLogger.write("SUPPORTED: найдено ${supported.size} PID")
        return supported
    }

    private fun parseMask(resp: String, base: Int, into: MutableSet<Pid>) {
        val clean = resp.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()
        val prefix = "41" + String.format("%02X", base)
        val idx = clean.indexOf(prefix)
        if (idx < 0) return
        val maskHex = clean.substring(idx + 4).take(8)
        if (maskHex.length < 8) return

        for (byteIdx in 0 until 4) {
            val byte = maskHex.substring(byteIdx * 2, byteIdx * 2 + 2).toInt(16)
            for (bit in 0 until 8) {
                if ((byte shr (7 - bit)) and 1 == 1) {
                    val pidNum = base + byteIdx * 8 + bit + 1
                    val cmd = String.format("01%02X", pidNum)
                    Pid.entries.firstOrNull { it.cmd == cmd }?.let { into.add(it) }
                }
            }
        }
    }
}