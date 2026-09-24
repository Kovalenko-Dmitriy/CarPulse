package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

class VinDecoderDiagnosticsTest {

    companion object {
        init {
            val dir = File("build/test-logs")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "vin_decoder_diagnostics.log").writeText("")
        }
    }

    private val testWmiDb = WmiDatabase(
        listOf(
            WmiEntry("XTA", "АвтоВАЗ", "Россия", Region.RU, listOf("Россия", "СНГ")),
            WmiEntry("XW7", "Лада Ижевск", "Россия", Region.RU, listOf("Россия", "СНГ")),
            WmiEntry("KNA", "Kia", "Корея", Region.ASIA, listOf("Азия", "Россия", "Европа")),
            WmiEntry("KMH", "Hyundai", "Корея", Region.ASIA, listOf("Азия", "Россия", "Европа")),
            WmiEntry("JTD", "Toyota (Europe)", "Япония", Region.ASIA, listOf("Европа", "Россия", "Глобальный")),
            WmiEntry("WVW", "Volkswagen", "Германия", Region.EU, listOf("Европа", "Глобальный")),
            WmiEntry("WBA", "BMW", "Германия", Region.EU, listOf("Европа", "Глобальный")),
            WmiEntry("LVV", "Chery", "Китай", Region.ASIA, listOf("Китай", "Россия")),
            WmiEntry("LGW", "Great Wall (Haval)", "Китай", Region.ASIA, listOf("Китай", "Россия")),
            WmiEntry("LN5", "Geely", "Китай", Region.ASIA, listOf("Китай", "Россия")),
            WmiEntry("VF1", "Renault", "Франция", Region.EU, listOf("Европа", "Африка")),
        )
    )

    private val decoder = VinDecoder(
        wmiDb = testWmiDb,
        vdsLookup = VdsLookup.Empty,
    )

    private fun openLog(): PrintWriter {
        val dir = File("build/test-logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "vin_decoder_diagnostics.log")
        return PrintWriter(FileWriter(file, true), true)
    }

    private fun yearToCode(year: Int): Char = when (year) {
        2000 -> 'Y'
        2001 -> '1'
        2002 -> '2'
        2003 -> '3'
        2004 -> '4'
        2005 -> '5'
        2006 -> '6'
        2007 -> '7'
        2008 -> '8'
        2009 -> '9'
        2010 -> 'A'
        2011 -> 'B'
        2012 -> 'C'
        2013 -> 'D'
        2014 -> 'E'
        2015 -> 'F'
        2016 -> 'G'
        2017 -> 'H'
        2018 -> 'J'
        2019 -> 'K'
        2020 -> 'L'
        2021 -> 'M'
        2022 -> 'N'
        2023 -> 'P'
        2024 -> 'R'
        2025 -> 'S'
        2026 -> 'T'
        else -> throw IllegalArgumentException("Год $year вне диапазона 2000..2026")
    }

    private fun buildVin(wmi: String, year: Int, seed: Int): String {
        require(wmi.length == 3)
        val seventh = if (year <= 2009) '1' else 'A'
        val yearCode = yearToCode(year)
        val serial = (seed % 10_000_000).toString().padStart(7, '0')
        return buildString(17) {
            append(wmi)
            append('A').append('B').append('C')
            append(seventh)
            append('1').append('2')
            append(yearCode)
            append(serial)
        }
    }

    @Test
    fun `VIN generation - все производители, 10 лет каждый`() {
        openLog().use { log ->
            data class Spec(val wmi: String, val manufacturer: String, val country: String, val region: Region)
            val specs = listOf(
                Spec("XTA", "АвтоВАЗ", "Россия", Region.RU),
                Spec("KNA", "Kia", "Корея", Region.ASIA),
                Spec("KMH", "Hyundai", "Корея", Region.ASIA),
                Spec("JTD", "Toyota (Europe)", "Япония", Region.ASIA),
                Spec("WVW", "Volkswagen", "Германия", Region.EU),
                Spec("WBA", "BMW", "Германия", Region.EU),
                Spec("LVV", "Chery", "Китай", Region.ASIA),
                Spec("LGW", "Great Wall (Haval)", "Китай", Region.ASIA),
                Spec("LN5", "Geely", "Китай", Region.ASIA),
                Spec("VF1", "Renault", "Франция", Region.EU),
            )
            val years = listOf(2000, 2002, 2005, 2008, 2010, 2013, 2016, 2019, 2022, 2025)
            var failedChecks = 0
            for (spec in specs) {
                log.println("--- ${spec.wmi} (${spec.manufacturer}) ---")
                for (year in years) {
                    val vin = buildVin(spec.wmi, year, seed = year)
                    val result = decoder.decode(vin)
                    if (result.manufacturer != spec.manufacturer) failedChecks++
                    if (result.country != spec.country) failedChecks++
                    if (result.region != spec.region) failedChecks++
                    if (result.year != year) failedChecks++
                    if (result.modelHint != null) failedChecks++
                    if (!result.isValid) failedChecks++
                    log.println("  $vin -> ${result.manufacturer} | ${result.country} | ${result.region} | year=${result.year}")
                }
            }
            assertEquals("Ошибок: $failedChecks", 0, failedChecks)
        }
    }

    @Test
    fun `JTDKB20U700123456 - полная диагностика`() {
        openLog().use { log ->
            val result = decoder.decode("JTDKB20U700123456")
            log.println("manufacturer = ${result.manufacturer}")
            log.println("country      = ${result.country}")
            log.println("region       = ${result.region}")
            log.println("year         = ${result.year?.toString() ?: "null"}")
            log.println("modelHint    = ${result.modelHint ?: "null"}")
            assertEquals("Toyota (Europe)", result.manufacturer)
            assertEquals("Япония", result.country)
            assertEquals(Region.ASIA, result.region)
            assertNull(result.year)
            assertNull(result.modelHint)
        }
    }

    @Test
    fun `JTDKB20U700123456 - год должен быть null`() {
        openLog().use { log ->
            val year = ModelYearDecoder.decode("JTDKB20U700123456")
            log.println("ModelYearDecoder.decode = ${year?.toString() ?: "null"}")
            assertNull(year)
        }
    }

    @Test
    fun `regionByFirstChar - fallback`() {
        openLog().use { log ->
            val cases = mapOf(
                '1' to Region.US, '5' to Region.US,
                '6' to Region.OTHER, '9' to Region.OTHER,
                'J' to Region.ASIA, 'R' to Region.ASIA,
                'S' to Region.EU, 'Z' to Region.EU,
            )
            for ((c, expected) in cases) {
                val vin = "$c" + "B".repeat(16)
                val result = decoder.decode(vin)
                log.println("  '$c...' -> ${result.region} (ожидалось $expected)")
                assertEquals("Регион для '$c'", expected, result.region)
            }
        }
    }

    @Test
    fun `VDS lookup - реальные VIN`() {
        val vdsDb = VdsDatabase(
            listOf(
                VdsEntry("XTAGFL", "Lada", "Vesta"),
                VdsEntry("XTA2115", "Lada", "2115"),
                VdsEntry("XTA2190", "Lada", "Priora"),
                VdsEntry("XW7BF4", "Lada", "Vesta"),
                VdsEntry("XW7RF4", "Lada", "XRAY"),
                VdsEntry("KNAFU", "Kia", "Rio"),
                VdsEntry("KNAGN", "Kia", "Sportage"),
                VdsEntry("KMHCT", "Hyundai", "Solaris"),
                VdsEntry("KMHDU", "Hyundai", "Creta"),
                VdsEntry("JTDKB", "Toyota", "Corolla"),
                VdsEntry("WVWZZ", "Volkswagen", "Golf"),
            )
        )
        val vdsDecoder = VinDecoder(
            wmiDb = testWmiDb,
            vdsLookup = VdsLookup.Database(vdsDb),
        )
        openLog().use { log ->
            val cases = listOf(
                Triple("XTAGFL110JY123456", "Lada", "Vesta"),
                Triple("XTA21150001234567", "Lada", "2115"),
                Triple("XTA21900012345678", "Lada", "Priora"),
                Triple("XW7BF4FK0A0012345", "Lada", "Vesta"),
                Triple("XW7RF4FK0A0123456", "Lada", "XRAY"),
                Triple("KNAFU411BC5123456", "Kia", "Rio"),
                Triple("KNAGN418BD5123456", "Kia", "Sportage"),
                Triple("KMHCT41BAAU123456", "Hyundai", "Solaris"),
                Triple("KMHDU41BADU123456", "Hyundai", "Creta"),
                Triple("JTDKB20U700123456", "Toyota", "Corolla"),
                Triple("WVWZZZ1KZAW123456", "Volkswagen", "Golf"),
                Triple("WVWZZZ1KZAW12345X", "Volkswagen", "Golf"),
                Triple("XTA0000000000000X", null, null),
            )
            for ((vin, _, expectedModel) in cases) {
                val result = vdsDecoder.decode(vin)
                log.println("  VIN=$vin model=${result.modelHint}")
                assertEquals("modelHint для VIN $vin", expectedModel, result.modelHint)
            }
        }
    }

    @Test
    fun `VDS - longest prefix match`() {
        val db = VdsDatabase(
            listOf(
                VdsEntry("XTA", "Lada", "Общая Lada"),
                VdsEntry("XTAGFL", "Lada", "Vesta"),
                VdsEntry("XTA2115", "Lada", "2115"),
            )
        )
        assertEquals("Vesta", db.find("XTAGFL110JY123456")?.model)
        assertEquals("2115", db.find("XTA21150001234567")?.model)
        assertEquals("Общая Lada", db.find("XTA0000000000000X")?.model)
    }
}
