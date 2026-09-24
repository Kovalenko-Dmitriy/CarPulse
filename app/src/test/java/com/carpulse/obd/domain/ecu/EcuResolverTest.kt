package com.carpulse.obd.domain.ecu

import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.profile.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты резолвера ЭБУ. Не требуют Android — чистая доменная логика.
 *
 * Данные для тестов встроены в этот файл (не читаем assets),
 * чтобы тесты запускались без Robolectric.
 */
class EcuResolverTest {

    private val testEntries = listOf(
        EcuEntry(
            id = "test_vaz_m86",
            make = "Lada",
            model = "Vesta, XRAY, Largus",
            yearFrom = 2015,
            yearTo = 2024,
            ecuName = "Ителма M86",
            protocol = Protocol.CAN_EXTENDED,
            canRequestId = "7E0",
            canResponseId = "7E8",
            initLines = listOf("AT SH 7E0"),
            pidClass = PidClass.VAZ_M86,
        ),
        EcuEntry(
            id = "test_vaz_m74",
            make = "Lada",
            model = "Granta, Kalina",
            yearFrom = 2013,
            yearTo = 2018,
            ecuName = "Ителма M74CAN",
            protocol = Protocol.CAN_EXTENDED,
            canRequestId = "7E0",
            canResponseId = "7E8",
            initLines = listOf("AT SH 7E0"),
            pidClass = PidClass.VAZ_M74,
        ),
        EcuEntry(
            id = "test_kia_rio",
            make = "Kia",
            model = "Rio (1.6)",
            yearFrom = 2017,
            yearTo = 2026,
            ecuName = "Hyundai/Kia ECM",
            protocol = Protocol.CAN_EXTENDED,
            canRequestId = "7E0",
            canResponseId = "7E8",
            initLines = listOf("AT SH 7E0"),
            pidClass = PidClass.HYUNDAI_UDS,
        ),
    )

    private val resolver = EcuResolver(EcuDatabase(testEntries))

    @Test
    fun `exact match - lada vesta 2020`() {
        val profile = CarProfile(make = "Lada", model = "Vesta", year = 2020)
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.EXACT, result.confidence)
        assertEquals("test_vaz_m86", result.entry?.id)
        assertEquals(Protocol.CAN_EXTENDED, result.protocol)
    }

    @Test
    fun `exact match - kia rio 2022`() {
        val profile = CarProfile(make = "Kia", model = "Rio", year = 2022)
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.EXACT, result.confidence)
        assertEquals("test_kia_rio", result.entry?.id)
    }

    @Test
    fun `partial match - make and model without year`() {
        val profile = CarProfile(make = "Lada", model = "Vesta", year = null)
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.PARTIAL, result.confidence)
        assertEquals("test_vaz_m86", result.entry?.id)
    }

    @Test
    fun `partial match - make and year without model`() {
        val profile = CarProfile(make = "Lada", model = null, year = 2020)
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.PARTIAL, result.confidence)
        assertEquals("test_vaz_m86", result.entry?.id)
    }

    @Test
    fun `make only - no model no year`() {
        val profile = CarProfile(make = "Lada", model = null, year = null)
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.MAKE_ONLY, result.confidence)
        assertNotNull(result.entry)
        assertTrue(result.entry!!.make == "Lada")
    }

    @Test
    fun `fallback - empty profile`() {
        val profile = CarProfile()
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.FALLBACK, result.confidence)
        assertNull(result.entry)
        assertEquals(Protocol.OBD2_STANDARD, result.protocol)
    }

    @Test
    fun `fallback - unknown make`() {
        val profile = CarProfile(make = "Ferrari", model = "F40", year = 1990)
        val result = resolver.resolve(profile)

        assertEquals(EcuResolutionResult.Confidence.FALLBACK, result.confidence)
        assertNull(result.entry)
    }

    @Test
    fun `alternatives are returned for partial match`() {
        val profile = CarProfile(make = "Lada", model = null, year = 2015)
        val result = resolver.resolve(profile)

        // 2015 год подходит и M74 (2013-2018), и M86 (2015-2024)
        assertTrue(result.alternatives.isNotEmpty())
    }

    @Test
    fun `matchesYear handles null yearTo`() {
        val entry = testEntries.first().copy(yearTo = null)
        assertTrue(entry.matchesYear(2030))
        assertTrue(entry.matchesYear(2015))
        assertTrue(entry.matchesYear(null))   // год неизвестен — подходит
    }

    @Test
    fun `matchesYear rejects out of range`() {
        val entry = testEntries.first()   // 2015..2024
        assertTrue(entry.matchesYear(2015))
        assertTrue(entry.matchesYear(2024))
        assertTrue(!entry.matchesYear(2014))
        assertTrue(!entry.matchesYear(2025))
    }
}